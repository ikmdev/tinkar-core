/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.entity.graph;

import dev.ikm.tinkar.common.service.DataActivity;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.IdentityRule;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiomSemantic;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import dev.ikm.tinkar.entity.RecordListBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongFunction;
import java.util.function.Supplier;

/**
 * Vertex identity in the entity write path (arch-vertex-identity-correlation, "Where It Runs";
 * IKE-Network/ike-issues#1310): every semantic version written whose fields hold a logical
 * definition tree passes through here once, inside the put lock, before its bytes are merged.
 *
 * <p>What happens depends on where the version comes from (arch-vertex-identity-rederivation,
 * "The Import Boundary"):
 * <ul>
 *   <li><em>Derived sources</em> -- edits, builders, importers, the reasoner: each new version's
 *       trees are correlated ({@link DiTreeCorrelation}) against the semantic's earlier versions,
 *       which are the identity ledger, and their newborns derived ({@link VertexDerivation}). A
 *       tree whose shape is not the identity table's (a set or a role with other than one
 *       successor, a leaf with any) is refused.
 *   <li><em>Tinkar-native input</em> -- a change set being loaded, or a repair: identity minted
 *       elsewhere must survive the trip, so its UUIDs are kept and never re-derived, and checked
 *       against the invariants: a UUID's meaning never changes, and an operand's identifying
 *       properties never change under a kept UUID. A version that breaks one is logged, or
 *       refused when {@value #CARRIED_BREAKS} is {@code refuse}: stores built before derivation
 *       carry the false continuity of ordinal minting (a published GUDID or SNOMED archive does),
 *       so refusing waits until they are re-derived (arch-vertex-identity-rederivation).
 * </ul>
 *
 * <p>Earlier versions are ordered by STAMP time, then STAMP UUID (an uncommitted version, its time
 * not yet set, comes last). A version correlates against the latest earlier version on its own
 * path, or on any path when its path has none; every earlier version on every path is history,
 * so paths share identity. Several new versions in one write correlate in order, each against
 * those before it.
 *
 * <p>A version written under a STAMP the store already holds replaces the stored one (the store
 * keeps the newer version under a STAMP), as does one written to withdraw it. When its trees
 * hold what the stored one held, UUIDs included, it is the same version and passes untouched:
 * settled identities stay frozen. Otherwise it is correlated, and the version it replaces is
 * history only so far as the UUIDs it held are recognized as carried; it is never the version
 * the new one continues.
 *
 * <p>A tree is correlated by the identity table that covers every vertex of it
 * ({@link VertexIdentityTable#forTree}): {@link LogicalAxiomSemantic}'s, or one a plugin provides
 * for its own vocabulary. A tree no table covers passes untouched.
 */
public final class VertexIdentityWrite {

    /**
     * The system property that decides what a broken invariant in Tinkar-native input does:
     * {@code refuse} throws; anything else, or nothing, logs a warning.
     */
    public static final String CARRIED_BREAKS = "ike.vertexIdentity.carriedBreaks";

    private static final Logger LOG = LoggerFactory.getLogger(VertexIdentityWrite.class);

    private VertexIdentityWrite() {
    }

    /** A version in the order identity reads them. */
    private record Ordered(long time, String stamp, long pathNid, boolean stored, MutableList<Object> fields,
                           long stampNid) {
    }

    /** What the stored versions are to a write. */
    private record Stored(List<Ordered> settled, List<Ordered> replaced) {
    }

    /**
     * The entity to write in place of the one given.
     *
     * @param incoming          the semantic being put
     * @param stored            the semantic as stored, or null; asked for only when the incoming
     *                          semantic holds a logical tree
     * @param withdrawnStampNid the STAMP of a version the put replaces, or 0
     * @param stamps            STAMP lookup by nid
     * @param activity          what the put is doing
     * @return the incoming semantic, or a copy whose new versions' trees carry correlated UUIDs
     * @throws IllegalStateException when a tree breaks the identity table's shape or, for
     *                               Tinkar-native input, an invariant
     */
    public static SemanticEntity<?> forWrite(SemanticEntity<?> incoming, Supplier<SemanticEntity<?>> storedSupplier,
                                             long withdrawnStampNid, LongFunction<StampEntity<?>> stamps,
                                             DataActivity activity) {
        if (!(incoming instanceof SemanticRecord record) || !holdsIdentifiedTree(record)) {
            return incoming;
        }
        boolean carry = activity == DataActivity.LOADING_CHANGE_SET || activity == DataActivity.DATA_REPAIR;
        SemanticEntity<?> stored = storedSupplier.get();

        Map<Long, SemanticEntityVersion> storedByStamp = new HashMap<>();
        if (stored != null) {
            for (SemanticEntityVersion version : stored.versions()) {
                storedByStamp.putIfAbsent(version.stampNid(), version);
            }
        }
        List<Ordered> versions = new ArrayList<>();
        List<Ordered> replaced = new ArrayList<>();
        Set<Long> rewritten = new HashSet<>();
        boolean anyNew = false;
        for (SemanticVersionRecord version : record.versions()) {
            SemanticEntityVersion same = storedByStamp.get(version.stampNid());
            if (same != null && sameVersion(same, version)) {
                continue;
            }
            if (same != null) {
                rewritten.add(version.stampNid());
            }
            versions.add(ordered(version, false, stamps));
            anyNew = true;
        }
        if (!anyNew) {
            return incoming;
        }
        storedByStamp.forEach((stampNid, version) -> {
            if (stampNid == withdrawnStampNid || rewritten.contains(stampNid)) {
                replaced.add(ordered(version, true, stamps));
            } else if (versions.stream().noneMatch(v -> v.stampNid() == stampNid)) {
                versions.add(ordered(version, true, stamps));
            }
        });
        versions.sort(Comparator.comparingLong(Ordered::time).thenComparing(Ordered::stamp));
        replaced.sort(Comparator.comparingLong(Ordered::time).thenComparing(Ordered::stamp));

        boolean changed = false;
        for (int v = 0; v < versions.size(); v++) {
            Ordered version = versions.get(v);
            if (version.stored()) {
                continue;
            }
            for (int f = 0; f < version.fields().size(); f++) {
                if (!(version.fields().get(f) instanceof DiTreeEntity tree)) {
                    continue;
                }
                Optional<VertexIdentityTable> covering = VertexIdentityTable.forTree(tree);
                if (covering.isEmpty()) {
                    continue;
                }
                VertexIdentityTable table = covering.get();
                List<DiTreeEntity> history = history(replaced, versions, v, f, table);
                if (carry) {
                    List<String> broken = invariantBreaks(history, tree, table);
                    if (!broken.isEmpty()) {
                        String message = "Vertex identity broken in " + record.publicId() + " field " + f
                                + ": " + String.join("; ", broken);
                        if ("refuse".equals(System.getProperty(CARRIED_BREAKS))) {
                            throw new IllegalStateException(message);
                        }
                        LOG.warn(message);
                    }
                    continue;
                }
                String shape = shapeBreak(tree, table);
                if (shape != null) {
                    throw new IllegalStateException("Not a tree the " + table + " identity table admits, in "
                            + record.publicId() + " field " + f + ": " + shape);
                }
                DiTreeEntity correlated = DiTreeCorrelation.correlate(history, tree,
                        VertexDerivation.root(record.publicId(), f), table).tree();
                version.fields().set(f, correlated);
                changed = true;
            }
        }
        return changed ? rebuilt(record, versions) : incoming;
    }

    /**
     * The earlier versions' trees in a field, the one a version continues last, after the trees
     * of the versions the write replaces, whose UUIDs are recognized but never continued.
     */
    private static List<DiTreeEntity> history(List<Ordered> replaced, List<Ordered> versions, int v, int f,
                                              VertexIdentityTable table) {
        long path = versions.get(v).pathNid();
        List<DiTreeEntity> earlier = new ArrayList<>();
        int previous = -1;
        for (int e = 0; e < v; e++) {
            if (f < versions.get(e).fields().size() && versions.get(e).fields().get(f) instanceof DiTreeEntity tree
                    && table.covers(tree)) {
                earlier.add(tree);
                if (versions.get(e).pathNid() == path) {
                    previous = earlier.size() - 1;
                }
            }
        }
        if (!earlier.isEmpty()) {
            earlier.add(earlier.remove(previous >= 0 ? previous : earlier.size() - 1));
        }
        List<DiTreeEntity> history = new ArrayList<>();
        for (Ordered version : replaced) {
            if (f < version.fields().size() && version.fields().get(f) instanceof DiTreeEntity tree && table.covers(tree)) {
                history.add(tree);
            }
        }
        if (earlier.isEmpty() && !history.isEmpty()) {
            // a first version replacing a first version: what it replaces is all the history there is
            return history;
        }
        history.addAll(earlier);
        return history;
    }

    /** Whether two versions under one STAMP hold the same values, trees with the same UUIDs. */
    private static boolean sameVersion(SemanticEntityVersion stored, SemanticEntityVersion incoming) {
        ImmutableList<Object> a = stored.fieldValues();
        ImmutableList<Object> b = incoming.fieldValues();
        if (a.size() != b.size()) {
            return false;
        }
        for (int f = 0; f < a.size(); f++) {
            if (a.get(f) instanceof DiTreeEntity treeA && b.get(f) instanceof DiTreeEntity treeB) {
                if (!sameIdentities(treeA, treeB)) {
                    return false;
                }
            } else if (!Objects.equals(a.get(f), b.get(f))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameIdentities(DiTreeEntity a, DiTreeEntity b) {
        if (a.vertexMap().size() != b.vertexMap().size()) {
            return false;
        }
        long[] hashesA = DiTreeIsomorphism.vertexHashes(a);
        long[] hashesB = DiTreeIsomorphism.vertexHashes(b);
        int[] images = DiTreeIsomorphism.correspondence(a, hashesA, a.root().vertexIndex(),
                b, hashesB, b.root().vertexIndex());
        if (images == null) {
            return false;
        }
        for (int i = 0; i < images.length; i++) {
            if (!EntityVertexUuid.of(a.vertex(i)).equals(EntityVertexUuid.of(b.vertex(images[i])))) {
                return false;
            }
        }
        return true;
    }


    private static Ordered ordered(SemanticEntityVersion version, boolean stored, LongFunction<StampEntity<?>> stamps) {
        StampEntity<?> stamp = stamps.apply(version.stampNid());
        long time = stamp == null ? Long.MAX_VALUE : stamp.time();
        long path = stamp == null ? 0 : stamp.pathNid();
        String stampKey = stamp != null ? stamp.publicId().leastUuid().toString() : absentStampKey(version.stampNid());
        return new Ordered(time, stampKey, path, stored,
                Lists.mutable.withAll(version.fieldValues()), version.stampNid());
    }

    /**
     * The ordering key of a STAMP the store does not hold yet, as when a parallel load writes a
     * semantic before its STAMP: the STAMP's least UUID from the identity map, or its nid from a
     * provider that keeps no reverse map (the legacy Rocks layout). Such a version already sorts
     * after every stored STAMP's time; the key only breaks ties among absent STAMPs.
     */
    private static String absentStampKey(long stampNid) {
        try {
            return PrimitiveData.publicId(stampNid).leastUuid().toString();
        } catch (UnsupportedOperationException noReverseMap) {
            return "nid " + stampNid;
        }
    }

    private static SemanticRecord rebuilt(SemanticRecord record, List<Ordered> versions) {
        Map<Long, List<Object>> fieldsByStamp = new HashMap<>();
        for (Ordered version : versions) {
            if (!version.stored()) {
                fieldsByStamp.put(version.stampNid(), version.fields());
            }
        }
        RecordListBuilder<SemanticVersionRecord> versionRecords = RecordListBuilder.make();
        SemanticRecord copy = new SemanticRecord(record.mostSignificantBits(), record.leastSignificantBits(),
                record.additionalUuidLongs(), record.nid(), record.patternNid(), record.referencedComponentNid(),
                versionRecords);
        for (SemanticVersionRecord version : record.versions()) {
            List<Object> fields = fieldsByStamp.get(version.stampNid());
            ImmutableList<Object> values = fields == null ? version.fieldValues() : Lists.immutable.withAll(fields);
            versionRecords.add(new SemanticVersionRecord(copy, version.stampNid(), values));
        }
        versionRecords.build();
        return copy;
    }

    // ----- what the write path checks -----

    private static boolean holdsIdentifiedTree(SemanticRecord record) {
        for (SemanticVersionRecord version : record.versions()) {
            for (Object field : version.fieldValues()) {
                if (field instanceof DiTreeEntity tree && VertexIdentityTable.forTree(tree).isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether every vertex's meaning is a logical-axiom meaning the identity table covers.
     *
     * @param tree the tree
     * @return true when the write path correlates it
     */
    public static boolean logical(DiTreeEntity tree) {
        return VertexIdentityTable.LOGICAL.covers(tree);
    }

    /**
     * Why a logical tree's shape is not the identity table's, or null when it is.
     *
     * @param tree a logical tree
     * @return the first break found, or null
     * @see #shapeBreak(DiTreeEntity, VertexIdentityTable)
     */
    public static String shapeBreak(DiTreeEntity tree) {
        return shapeBreak(tree, VertexIdentityTable.LOGICAL);
    }

    /**
     * Why a tree's shape is not its identity table's, or null when it is: a vertex whose
     * successor is sole must have exactly one, one whose successor is optional at most one, and a
     * leaf none (arch-vertex-identity-verification, "Shape at write").
     *
     * @param tree  a tree the table covers
     * @param table its identity table
     * @return the first break found, or null
     */
    public static String shapeBreak(DiTreeEntity tree, VertexIdentityTable table) {
        for (EntityVertex vertex : tree.vertexMap()) {
            int successors = tree.successors(vertex.vertexIndex()).size();
            IdentityRule.Successors rule = table.rule(vertex.getMeaningNid()).successors();
            if (rule == IdentityRule.Successors.SOLE && successors != 1
                    || rule == IdentityRule.Successors.OPTIONAL && successors > 1
                    || rule == IdentityRule.Successors.NONE && successors != 0) {
                return PrimitiveData.text(vertex.getMeaningNid()) + " " + EntityVertexUuid.of(vertex)
                        + " has " + successors + " successors";
            }
        }
        return null;
    }

    /**
     * How a tree that keeps its UUIDs breaks the invariants against history: a kept UUID with
     * another meaning, or an operand's kept UUID with other identifying properties.
     *
     * @param history the earlier versions' trees, oldest first
     * @param tree    the tree
     * @return one line per break, empty when there is none
     */
    public static List<String> invariantBreaks(List<DiTreeEntity> history, DiTreeEntity tree) {
        return invariantBreaks(history, tree, VertexIdentityTable.LOGICAL);
    }

    /**
     * How a tree that keeps its UUIDs breaks the invariants against history, by its identity
     * table's rules.
     *
     * @param history the earlier versions' trees, oldest first
     * @param tree    the tree
     * @param table   the identity table covering them
     * @return one line per break, empty when there is none
     */
    public static List<String> invariantBreaks(List<DiTreeEntity> history, DiTreeEntity tree,
                                               VertexIdentityTable table) {
        Map<UUID, Long> meanings = new HashMap<>();
        Map<UUID, List<Object>> operandKeys = new HashMap<>();
        for (DiTreeEntity earlier : history) {
            for (EntityVertex vertex : earlier.vertexMap()) {
                UUID uuid = EntityVertexUuid.of(vertex);
                meanings.put(uuid, vertex.getMeaningNid());
                if (isOperand(earlier, vertex, table)) {
                    operandKeys.put(uuid, identifying(vertex, table));
                }
            }
        }
        List<String> broken = new ArrayList<>();
        for (EntityVertex vertex : tree.vertexMap()) {
            UUID uuid = EntityVertexUuid.of(vertex);
            Long meaning = meanings.get(uuid);
            if (meaning != null && meaning != vertex.getMeaningNid()) {
                broken.add(uuid + " changed meaning");
            } else if (operandKeys.containsKey(uuid) && isOperand(tree, vertex, table)
                    && !operandKeys.get(uuid).equals(identifying(vertex, table))) {
                broken.add(uuid + " changed an identifying property");
            }
        }
        return broken;
    }

    private static boolean isOperand(DiTreeEntity tree, EntityVertex vertex, VertexIdentityTable table) {
        return tree.predecessor(vertex).map(parent ->
                table.rule(parent.getMeaningNid()).successors() == IdentityRule.Successors.OPERANDS)
                .orElse(false);
    }

    private static List<Object> identifying(EntityVertex vertex, VertexIdentityTable table) {
        List<Object> values = new ArrayList<>();
        table.rule(vertex.getMeaningNid()).identifying().forEach(property -> {
            Object value = vertex.properties().get(property.nid());
            values.add(value instanceof dev.ikm.tinkar.terms.ComponentWithNid component ? component.nid()
                    : Objects.requireNonNullElse(value, ""));
        });
        return values;
    }
}
