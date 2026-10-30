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
import org.eclipse.collections.api.factory.primitive.IntObjectMaps;
import org.eclipse.collections.api.factory.primitive.LongObjectMaps;
import org.eclipse.collections.api.factory.primitive.LongSets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import dev.ikm.tinkar.entity.RecordListBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
 *   <li><em>Derived sources</em> -- edits, builders, importers, the reasoner, and a change set
 *       whose manifest does not say its identities were derived
 *       ({@link dev.ikm.tinkar.common.service.DataActivity#LOADING_UNDERIVED_CHANGE_SET}: IKM's
 *       data, and anything written before derivation): each new version's
 *       trees are correlated ({@link DiTreeCorrelation}) against the semantic's earlier versions,
 *       which are the identity ledger, and their newborns derived ({@link VertexDerivation}). A
 *       tree whose shape is not the identity table's (a set or a role with other than one
 *       successor, a leaf with any) is refused.
 *   <li><em>Tinkar-native input</em> -- a change set whose manifest says its identities were
 *       derived ({@link dev.ikm.tinkar.entity.changeset.ChangeSetFormat#VERTEX_IDENTITY_ATTRIBUTE}),
 *       or a repair: identity minted elsewhere must survive the trip, so its UUIDs are kept and
 *       never re-derived, and checked against the invariants: a UUID's meaning never changes,
 *       and an operand's identifying properties never change under a kept UUID. A version that
 *       breaks one is refused, or only logged when {@value #CARRIED_BREAKS} is {@code warn}.
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
     * {@code warn} logs a warning; anything else, or nothing, throws.
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
     * @param storedSupplier    the semantic as stored, or null; asked for only when the incoming
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
        // LOADING_UNDERIVED_CHANGE_SET is not carried: its trees correlate as a derived source's do.
        boolean carry = activity == DataActivity.LOADING_CHANGE_SET || activity == DataActivity.DATA_REPAIR;
        SemanticEntity<?> stored = storedSupplier.get();

        MutableLongObjectMap<SemanticEntityVersion> storedByStamp = LongObjectMaps.mutable.empty();
        if (stored != null) {
            for (SemanticEntityVersion version : stored.versions()) {
                storedByStamp.getIfAbsentPut(version.stampNid(), version);
            }
        }
        List<Ordered> versions = new ArrayList<>();
        List<Ordered> replaced = new ArrayList<>();
        MutableLongSet rewritten = LongSets.mutable.empty();
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
        storedByStamp.forEachKeyValue((stampNid, version) -> {
            if (stampNid == withdrawnStampNid || rewritten.contains(stampNid)) {
                replaced.add(ordered(version, true, stamps));
            } else if (versions.stream().noneMatch(v -> v.stampNid() == stampNid)) {
                versions.add(ordered(version, true, stamps));
            }
        });
        versions.sort(Comparator.comparingLong(Ordered::time).thenComparing(Ordered::stamp));
        replaced.sort(Comparator.comparingLong(Ordered::time).thenComparing(Ordered::stamp));

        if (carry) {
            // Carried identity is kept, never re-derived: the write passes as it came, or is refused.
            checkCarried(record, replaced, versions);
            return incoming;
        }
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
                String shape = shapeBreak(tree, table);
                if (shape != null) {
                    throw new IllegalStateException("Not a tree the " + table + " identity table admits, in "
                            + record.publicId() + " field " + f + ": " + shape);
                }
                // An underived change set's UUIDs say nothing of identity: none is carried.
                DiTreeEntity correlated = DiTreeCorrelation.correlate(history, tree,
                        VertexDerivation.root(record.publicId(), f), table,
                        activity != DataActivity.LOADING_UNDERIVED_CHANGE_SET).tree();
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
            if (images[i] < 0) {
                // Not in the root's subtree: a vertex the tree holds but nothing reaches, which
                // identity never reads. The reachable trees are isomorphic, so neither has more.
                continue;
            }
            EntityVertex vertexA = a.vertex(i);
            EntityVertex vertexB = b.vertex(images[i]);
            if (vertexA.leastSignificantBits() != vertexB.leastSignificantBits()
                    || vertexA.mostSignificantBits() != vertexB.mostSignificantBits()) {
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
        MutableLongObjectMap<List<Object>> fieldsByStamp = LongObjectMaps.mutable.empty();
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
        IdentityHistory known = new IdentityHistory();
        for (DiTreeEntity earlier : history) {
            known.add(earlier, table);
        }
        return breaks(known, null, tree, table);
    }

    /**
     * Checks carried input against the invariants, each version against the history it reads:
     * the trees of the versions the write replaces, then the earlier versions' trees in order,
     * with the latest earlier version on the version's own path read last. The history of each
     * field is built once and extended version by version, so a semantic carrying every version
     * across all time is not read again at each one; the version read last is an overlay over
     * it, which answers as moving that version to the end of the history does.
     */
    private static void checkCarried(SemanticRecord record, List<Ordered> replaced, List<Ordered> versions) {
        MutableIntObjectMap<List<FieldHistory>> byField = IntObjectMaps.mutable.empty();
        for (int v = 0; v < versions.size(); v++) {
            Ordered version = versions.get(v);
            for (int f = 0; f < version.fields().size(); f++) {
                if (!(version.fields().get(f) instanceof DiTreeEntity tree)) {
                    continue;
                }
                Optional<VertexIdentityTable> covering = VertexIdentityTable.forTree(tree);
                if (covering.isEmpty()) {
                    continue;
                }
                if (!version.stored()) {
                    FieldHistory history = fieldHistory(byField, f, covering.get(), replaced, versions, v);
                    DiTreeEntity continued = history.continued(version.pathNid());
                    IdentityHistory overlay = continued == null ? null : IdentityHistory.of(continued, history.table);
                    List<String> broken = breaks(history.known, overlay, tree, history.table);
                    if (!broken.isEmpty()) {
                        String message = "Vertex identity broken in " + record.publicId() + " field " + f
                                + ": " + String.join("; ", broken);
                        if (!"warn".equals(System.getProperty(CARRIED_BREAKS))) {
                            throw new IllegalStateException(message);
                        }
                        LOG.warn(message);
                    }
                }
                // The version joins the history of every table that covers it, for the versions after
                // it; the last has none after it, so no history reads its tree.
                if (v == versions.size() - 1) {
                    continue;
                }
                for (FieldHistory history : byField.getIfAbsent(f, List::of)) {
                    if (history.table.covers(tree)) {
                        history.add(tree, version.pathNid());
                    }
                }
            }
        }
    }

    /** The history of a field under a table, built from what precedes version {@code v} when first asked for. */
    private static FieldHistory fieldHistory(MutableIntObjectMap<List<FieldHistory>> byField, int f,
                                             VertexIdentityTable table, List<Ordered> replaced,
                                             List<Ordered> versions, int v) {
        List<FieldHistory> histories = byField.getIfAbsentPut(f, ArrayList::new);
        for (FieldHistory history : histories) {
            if (history.table.equals(table)) {
                return history;
            }
        }
        FieldHistory history = new FieldHistory(table);
        for (Ordered version : replaced) {
            if (f < version.fields().size() && version.fields().get(f) instanceof DiTreeEntity tree && table.covers(tree)) {
                history.known.add(tree, table);
            }
        }
        for (int e = 0; e < v; e++) {
            Ordered earlier = versions.get(e);
            if (f < earlier.fields().size() && earlier.fields().get(f) instanceof DiTreeEntity tree && table.covers(tree)) {
                history.add(tree, earlier.pathNid());
            }
        }
        histories.add(history);
        return history;
    }

    /**
     * How a tree that keeps its UUIDs breaks the invariants against what history knows; an
     * overlay, when given, is read before the rest.
     */
    private static List<String> breaks(IdentityHistory known, IdentityHistory overlay, DiTreeEntity tree,
                                       VertexIdentityTable table) {
        List<String> broken = new ArrayList<>();
        for (EntityVertex vertex : tree.vertexMap()) {
            long most = vertex.mostSignificantBits();
            long least = vertex.leastSignificantBits();
            IdentityHistory.Known first = overlay == null ? null : overlay.find(most, least);
            IdentityHistory.Known rest = known.find(most, least);
            if (first == null && rest == null) {
                continue;
            }
            long meaning = first != null ? first.meaningNid() : rest.meaningNid();
            EntityVertex operand = first != null && first.operand() != null ? first.operand()
                    : rest == null ? null : rest.operand();
            if (meaning != vertex.getMeaningNid()) {
                broken.add(new UUID(most, least) + " changed meaning");
            } else if (operand != null && isOperand(tree, vertex, table)
                    && !sameIdentifying(operand, vertex, table)) {
                broken.add(new UUID(most, least) + " changed an identifying property");
            }
        }
        return broken;
    }

    /**
     * The history of one field under one identity table: what its earlier trees know, and the
     * latest of them, overall and on each path, for the version that continues one.
     */
    private static final class FieldHistory {
        private final VertexIdentityTable table;
        private final IdentityHistory known = new IdentityHistory();
        private final MutableLongObjectMap<DiTreeEntity> latestByPath = LongObjectMaps.mutable.empty();
        private DiTreeEntity latest;

        private FieldHistory(VertexIdentityTable table) {
            this.table = table;
        }

        private void add(DiTreeEntity tree, long pathNid) {
            known.add(tree, table);
            latestByPath.put(pathNid, tree);
            latest = tree;
        }

        /** The tree a version on the path continues: the latest on its path, else the latest; null with no earlier version. */
        private DiTreeEntity continued(long pathNid) {
            DiTreeEntity onPath = latestByPath.get(pathNid);
            return onPath != null ? onPath : latest;
        }
    }

    /**
     * What earlier trees say of each vertex identity: its meaning, and, for an operand, the
     * vertex whose identifying properties a kept identity must keep. A later tree's entry
     * replaces an earlier one's, the meaning always and the operand when the later vertex is
     * one, as the order of history says.
     *
     * <p>Keyed on the low half of the UUID and confirmed on the high half, so no UUID is built
     * and no nid boxed; low halves that collide, which name-based UUIDs make vanishingly rare,
     * chain. Built by one write, on its thread under the put lock, and never handed on.
     */
    private static final class IdentityHistory {

        /** One identity's entry, and the next whose low half is the same. */
        record Known(long mostSignificantBits, long meaningNid, EntityVertex operand, Known next) {
        }

        private final MutableLongObjectMap<Known> byLow = LongObjectMaps.mutable.empty();

        static IdentityHistory of(DiTreeEntity tree, VertexIdentityTable table) {
            IdentityHistory history = new IdentityHistory();
            history.add(tree, table);
            return history;
        }

        void add(DiTreeEntity tree, VertexIdentityTable table) {
            for (EntityVertex vertex : tree.vertexMap()) {
                put(vertex, isOperand(tree, vertex, table) ? vertex : null);
            }
        }

        Known find(long most, long least) {
            for (Known known = byLow.get(least); known != null; known = known.next()) {
                if (known.mostSignificantBits() == most) {
                    return known;
                }
            }
            return null;
        }

        private void put(EntityVertex vertex, EntityVertex operand) {
            long most = vertex.mostSignificantBits();
            long least = vertex.leastSignificantBits();
            Known head = byLow.get(least);
            Known previous = find(most, least);
            Known chain = previous == null ? head : without(head, previous);
            byLow.put(least, new Known(most, vertex.getMeaningNid(),
                    operand != null ? operand : previous == null ? null : previous.operand(), chain));
        }

        private static Known without(Known chain, Known removed) {
            if (chain == null) {
                return null;
            }
            if (chain == removed) {
                return chain.next();
            }
            return new Known(chain.mostSignificantBits(), chain.meaningNid(), chain.operand(),
                    without(chain.next(), removed));
        }
    }

    /**
     * Whether the vertex is an operand: its predecessor's rule takes operands. The root has no
     * predecessor, and a vertex nothing reaches from the root is held with predecessor -1, which
     * names no vertex: neither is an operand.
     */
    private static boolean isOperand(DiTreeEntity tree, EntityVertex vertex, VertexIdentityTable table) {
        int parent = tree.predecessorMap().getIfAbsent(vertex.vertexIndex(), -1);
        return parent >= 0
                && table.rule(tree.vertex(parent).getMeaningNid()).successors() == IdentityRule.Successors.OPERANDS;
    }

    /**
     * Whether two vertices hold the same identifying properties, each by its own meaning's rule,
     * compared in place: a component by its nid, anything else by its value, an absent one as
     * empty text.
     */
    private static boolean sameIdentifying(EntityVertex earlier, EntityVertex vertex, VertexIdentityTable table) {
        ImmutableList<dev.ikm.tinkar.terms.ConceptFacade> before = table.rule(earlier.getMeaningNid()).identifying();
        ImmutableList<dev.ikm.tinkar.terms.ConceptFacade> after = table.rule(vertex.getMeaningNid()).identifying();
        if (before.size() != after.size()) {
            return false;
        }
        for (int i = 0; i < before.size(); i++) {
            if (!sameValue(earlier.properties().get(before.get(i).nid()), vertex.properties().get(after.get(i).nid()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameValue(Object a, Object b) {
        if (a instanceof dev.ikm.tinkar.terms.ComponentWithNid componentA) {
            return b instanceof dev.ikm.tinkar.terms.ComponentWithNid componentB
                    ? componentA.nid() == componentB.nid()
                    : b instanceof Long nid && nid == componentA.nid();
        }
        if (b instanceof dev.ikm.tinkar.terms.ComponentWithNid componentB) {
            return a instanceof Long nid && nid == componentB.nid();
        }
        return Objects.requireNonNullElse(a, "").equals(Objects.requireNonNullElse(b, ""));
    }
}
