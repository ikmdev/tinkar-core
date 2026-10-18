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
package dev.ikm.tinkar.importer;

import dev.ikm.tinkar.common.id.LongIdList;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.ConceptEntityVersion;
import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.ConceptRecordBuilder;
import dev.ikm.tinkar.entity.ConceptVersionRecord;
import dev.ikm.tinkar.entity.ConceptVersionRecordBuilder;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.PublicIdentifierRecord;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.SemanticRecordBuilder;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import dev.ikm.tinkar.entity.SemanticVersionRecordBuilder;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.StampVersionRecord;
import dev.ikm.tinkar.entity.builder.InactiveStamp;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpressionBuilder;
import dev.ikm.tinkar.terms.*;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Writes knowledge into the running store from code: concepts, stated parents, semantics,
 * descriptions with their dialects, and retirement, each under the stamp the call names. Every
 * identity is the caller's, derived and never random, so writing the same thing twice finds the
 * same component (IKE-Network/ike-issues#1112, #1114).
 *
 * <p>Writing is version-aware. Before a component is written its latest version on the view is
 * read: the same state and content writes nothing, a different state or content appends a version
 * under the call's stamp, and retirement appends an inactive version, never deleting. The store
 * keeps one version per stamp, so a component that already has a version under the call's stamp
 * is left alone: replaying a source writes nothing new.
 *
 * <p>The stamp travels with each call so a source that carries its own history writes it as
 * history: an RF2 full release gives every row its effective time, active flag and module, and
 * each row lands under the stamp those say. A source with one stamp for everything passes the
 * same stamp to every call. Stamps are written on first use and remembered.
 *
 * <p>A writer may be shared by threads writing disjoint components; its counts are thread safe.
 */
public final class StoreWriter {

    /** What the writer did, counted since it was created. */
    public static final class Counts {
        private final LongAdder written = new LongAdder();
        private final LongAdder unchanged = new LongAdder();
        private final LongAdder versioned = new LongAdder();
        private final LongAdder retired = new LongAdder();

        /**
         * Components created for the first time.
         *
         * @return the count
         */
        public long written() {
            return written.sum();
        }

        /**
         * Components found with the same state and content, left untouched.
         *
         * @return the count
         */
        public long unchanged() {
            return unchanged.sum();
        }

        /**
         * Components found with a different state or content, given a new version.
         *
         * @return the count
         */
        public long versioned() {
            return versioned.sum();
        }

        /**
         * Semantics retired with an inactive version.
         *
         * @return the count
         */
        public long retired() {
            return retired.sum();
        }

        /**
         * The counts as they stand now, fixed.
         *
         * @return the tally
         */
        public Tally tally() {
            return new Tally(written(), unchanged(), versioned(), retired());
        }

        @Override
        public String toString() {
            return tally().toString();
        }
    }

    /**
     * Counts fixed at a moment, so one import's share of a shared writer's work can be told:
     * the tally after it minus the tally before.
     *
     * @param written   components created for the first time
     * @param unchanged components found the same, left untouched
     * @param versioned components given a new version
     * @param retired   semantics retired
     */
    public record Tally(long written, long unchanged, long versioned, long retired) {

        /**
         * This tally less an earlier one.
         *
         * @param earlier the tally taken before
         * @return the difference
         */
        public Tally minus(Tally earlier) {
            return new Tally(written - earlier.written, unchanged - earlier.unchanged,
                    versioned - earlier.versioned, retired - earlier.retired);
        }

        @Override
        public String toString() {
            return "written " + written + ", unchanged " + unchanged + ", versioned " + versioned + ", retired " + retired;
        }
    }

    private final StampCalculator calculator;
    /**
     * The stamps this writer has written, by nid. Keyed by nid rather than public id: public ids are
     * equal when they share any UUID and their hash codes are not consistent with that, so a hash
     * map keyed by them can miss a stamp it already holds. The store's public-id-to-nid mapping
     * decides identity, and the map only makes each stamp's first write happen once.
     */
    private final ConcurrentHashMap<Long, Long> stampNids = new ConcurrentHashMap<>();
    private final Counts counts = new Counts();

    /**
     * Creates a writer that reads existing versions on a view.
     *
     * @param calculator the view that decides which existing versions count
     */
    public StoreWriter(StampCalculator calculator) {
        this.calculator = Objects.requireNonNull(calculator, "calculator");
    }

    /**
     * What the writer has done so far.
     *
     * @return the counts
     */
    public Counts counts() {
        return counts;
    }

    /**
     * The stamps this writer has written under, by nid: every stamp a write went through, whether
     * the stamp was new to the store or not.
     *
     * @return their nids
     */
    public java.util.Set<Long> stampsWritten() {
        return java.util.Set.copyOf(stampNids.keySet());
    }

    /**
     * The view this writer reads existing versions under.
     *
     * @return the calculator
     */
    public StampCalculator calculator() {
        return calculator;
    }

    /**
     * The nid of a stamp, written to the store on first use.
     *
     * @param stamp the stamp
     * @return its nid
     */
    public long stamp(Stamp stamp) {
        long stampNid = EntityService.get().nidForStamp(stamp.publicId());
        return stampNids.computeIfAbsent(stampNid, nid -> writeStamp(stamp, nid));
    }

    /**
     * Writes a concept version under a stamp. A concept has no content of its own, so only its
     * state can differ: an absent concept is created, and an existing one gains a version only
     * when the stamp's state differs from its latest. A public id naming UUIDs the existing
     * concept lacks adds them to it, so a source that identifies a concept two ways (RxNorm's
     * concept that is a SNOMED CT concept) merges the two into one component.
     *
     * @param stamp     the stamp the version is written under
     * @param conceptId the concept's public id
     * @return the concept's nid
     */
    public long concept(Stamp stamp, PublicId conceptId) {
        long stampNid = stamp(stamp);
        long nid = EntityService.get().nidForConcept(conceptId);
        Optional<ConceptRecord> existing = EntityHandle.get(nid).entity().filter(e -> !e.canceled())
                .map(e -> (ConceptRecord) e);
        if (existing.isEmpty()) {
            RecordListBuilder<ConceptVersionRecord> versions = RecordListBuilder.make();
            ConceptRecord bootstrap = ConceptRecord.makeNew(conceptId, versions);
            versions.add(ConceptVersionRecordBuilder.builder().chronology(bootstrap).stampNid(stampNid).build());
            EntityService.get().putEntity(ConceptRecordBuilder.builder(bootstrap).versions(versions.toImmutable()).build());
            counts.written.increment();
            return nid;
        }
        ConceptRecord record = withUuids(existing.get(), conceptId);
        Latest<ConceptEntityVersion> latest = calculator.latest(nid);
        if (hasVersionUnder(record.versions(), stampNid)
                || (latest.isPresent() && latest.get().active() == active(stamp))) {
            if (record != existing.get()) {
                EntityService.get().putEntity(record);
            }
            counts.unchanged.increment();
            return nid;
        }
        ConceptVersionRecord version = ConceptVersionRecordBuilder.builder().chronology(record).stampNid(stampNid).build();
        EntityService.get().putEntity(ConceptRecordBuilder.builder(record)
                .versions(record.versions().newWith(version)).build());
        counts.versioned.increment();
        return nid;
    }

    /**
     * States a concept's parent: its stated-axiom semantic says it is necessarily a kind of
     * the parent, and nothing more. Written version-aware like any semantic.
     *
     * @param stamp     the stamp the version is written under
     * @param conceptId the concept
     * @param parentId  the parent concept
     * @return the axiom semantic's nid
     */
    public long statedParent(Stamp stamp, PublicId conceptId, PublicId parentId) {
        UUID conceptUuid = conceptId.leastUuid();
        PublicId axiomId = PublicIds.of(UuidT5Generator.get(conceptUuid, "stated axioms"));
        int[] ordinal = {0};
        LogicalExpressionBuilder builder = new LogicalExpressionBuilder(
                UuidT5Generator.get(conceptUuid, "definition root"),
                () -> UuidT5Generator.get(conceptUuid, "axiom vertex " + ordinal[0]++));
        builder.NecessarySet(builder.And(builder.ConceptAxiom(EntityProxy.Concept.make(EntityService.get().nidForConcept(parentId)))));
        DiTreeEntity tree = (DiTreeEntity) builder.build().sourceGraph();
        return semantic(stamp, axiomId, KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN, EntityService.get().nidForConcept(conceptId),
                Lists.immutable.of(tree));
    }

    /**
     * Writes a semantic version under a stamp, version-aware: nothing when the latest version has
     * the stamp's state and the same fields, or when a version under this stamp already exists; a
     * new version when either differs; a new semantic when none exists.
     *
     * @param stamp                  the stamp the version is written under
     * @param semanticId             the semantic's public id
     * @param pattern                its pattern
     * @param referencedComponentNid the component it is about
     * @param fieldValues            its field values, in the pattern's order
     * @return the semantic's nid
     */
    public long semantic(Stamp stamp, PublicId semanticId, PatternFacade pattern, long referencedComponentNid,
                         ImmutableList<Object> fieldValues) {
        long stampNid = stamp(stamp);
        PublicIdentifierRecord identifier = PublicIdentifierRecord.make(semanticId);
        long nid = ScopedValue.where(PrimitiveData.SCOPED_PATTERN_PUBLICID_FOR_NID, pattern.publicId())
                .call(() -> PrimitiveData.nid(semanticId));
        Optional<SemanticRecord> existing = EntityHandle.get(nid).entity().filter(e -> !e.canceled())
                .map(e -> (SemanticRecord) e);
        if (existing.isPresent()) {
            SemanticRecord record = existing.get();
            if (hasVersionUnder(record.versions(), stampNid)) {
                counts.unchanged.increment();
                return nid;
            }
            Latest<SemanticEntityVersion> latest = calculator.latest(nid);
            if (latest.isPresent() && latest.get().active() == active(stamp)
                    && sameFields(latest.get().fieldValues(), fieldValues)) {
                counts.unchanged.increment();
                return nid;
            }
            SemanticVersionRecord version = SemanticVersionRecordBuilder.builder()
                    .chronology(record)
                    .stampNid(stampNid)
                    .fieldValues(fieldValues)
                    .build();
            EntityService.get().putEntity(SemanticRecordBuilder.builder(record)
                    .versions(record.versions().newWith(version)).build());
            counts.versioned.increment();
            return nid;
        }
        RecordListBuilder<SemanticVersionRecord> versions = RecordListBuilder.make();
        SemanticRecord bootstrap = SemanticRecordBuilder.builder()
                .mostSignificantBits(identifier.mostSignificantBits())
                .leastSignificantBits(identifier.leastSignificantBits())
                .additionalUuidLongs(identifier.additionalUuidLongs())
                .nid(nid)
                .patternNid(pattern.nid())
                .referencedComponentNid(referencedComponentNid)
                .versions(versions)
                .build();
        versions.add(SemanticVersionRecordBuilder.builder()
                .chronology(bootstrap)
                .stampNid(stampNid)
                .fieldValues(fieldValues)
                .build());
        EntityService.get().putEntity(SemanticRecordBuilder.builder(bootstrap).versions(versions.toImmutable()).build());
        counts.written.increment();
        return nid;
    }

    /**
     * Records the concept version a source states under a stamp, as history: appended unless a
     * version under that stamp already exists, with no comparison to the latest version. For a
     * source that carries its own history, such as an RF2 full release, where every row is a
     * version and rows do not arrive in time order.
     *
     * @param stamp     the stamp the source states the version under
     * @param conceptId the concept's public id
     * @return the concept's nid
     */
    public long recordConcept(Stamp stamp, PublicId conceptId) {
        long stampNid = stamp(stamp);
        long nid = EntityService.get().nidForConcept(conceptId);
        Optional<ConceptRecord> existing = EntityHandle.get(nid).entity().filter(e -> !e.canceled())
                .map(e -> (ConceptRecord) e);
        if (existing.isEmpty()) {
            return concept(stamp, conceptId);
        }
        ConceptRecord record = withUuids(existing.get(), conceptId);
        if (hasVersionUnder(record.versions(), stampNid)) {
            if (record != existing.get()) {
                EntityService.get().putEntity(record);
            }
            counts.unchanged.increment();
            return nid;
        }
        ConceptVersionRecord version = ConceptVersionRecordBuilder.builder().chronology(record).stampNid(stampNid).build();
        EntityService.get().putEntity(ConceptRecordBuilder.builder(record)
                .versions(record.versions().newWith(version)).build());
        counts.versioned.increment();
        return nid;
    }

    /**
     * Records the semantic version a source states under a stamp, as history: appended unless a
     * version under that stamp already exists, with no comparison to the latest version.
     *
     * @param stamp                  the stamp the source states the version under
     * @param semanticId             the semantic's public id
     * @param pattern                its pattern
     * @param referencedComponentNid the component it is about
     * @param fieldValues            its field values, in the pattern's order
     * @return the semantic's nid
     * @see #recordConcept(Stamp, PublicId)
     */
    public long recordSemantic(Stamp stamp, PublicId semanticId, PatternFacade pattern,
                               long referencedComponentNid, ImmutableList<Object> fieldValues) {
        long stampNid = stamp(stamp);
        long nid = ScopedValue.where(PrimitiveData.SCOPED_PATTERN_PUBLICID_FOR_NID, pattern.publicId())
                .call(() -> PrimitiveData.nid(semanticId));
        Optional<SemanticRecord> existing = EntityHandle.get(nid).entity().filter(e -> !e.canceled())
                .map(e -> (SemanticRecord) e);
        if (existing.isEmpty()) {
            return semantic(stamp, semanticId, pattern, referencedComponentNid, fieldValues);
        }
        SemanticRecord record = existing.get();
        if (hasVersionUnder(record.versions(), stampNid)) {
            counts.unchanged.increment();
            return nid;
        }
        SemanticVersionRecord version = SemanticVersionRecordBuilder.builder()
                .chronology(record)
                .stampNid(stampNid)
                .fieldValues(fieldValues)
                .build();
        EntityService.get().putEntity(SemanticRecordBuilder.builder(record)
                .versions(record.versions().newWith(version)).build());
        counts.versioned.increment();
        return nid;
    }

    /**
     * Writes a description of a component in English, not case sensitive, with its US dialect
     * acceptability set to preferred.
     *
     * @param stamp           the stamp the versions are written under
     * @param aboutNid        the component described
     * @param descriptionUuid the description's identity
     * @param text            the text
     * @param type            the description type, a fully qualified name or a regular name
     * @return the description semantic's nid
     */
    public long describe(Stamp stamp, long aboutNid, UUID descriptionUuid, String text, ConceptFacade type) {
        return describe(stamp, aboutNid, descriptionUuid, text, type, KernelConcept.PREFERRED);
    }

    /**
     * Writes a description of a component in English, not case sensitive, with its US dialect
     * acceptability as given.
     *
     * @param stamp           the stamp the versions are written under
     * @param aboutNid        the component described
     * @param descriptionUuid the description's identity
     * @param text            the text
     * @param type            the description type, a fully qualified name, a regular name, or a definition
     * @param usAcceptability preferred or acceptable in the US dialect
     * @return the description semantic's nid
     */
    public long describe(Stamp stamp, long aboutNid, UUID descriptionUuid, String text, ConceptFacade type,
                         ConceptFacade usAcceptability) {
        long descriptionNid = semantic(stamp, PublicIds.of(descriptionUuid), KernelPattern.DESCRIPTION_PATTERN, aboutNid,
                Lists.immutable.of(KernelConcept.ENGLISH_LANGUAGE, text, KernelConcept.DESCRIPTION_NOT_CASE_SENSITIVE, type));
        dialect(stamp, descriptionNid, UuidT5Generator.get(descriptionUuid, "us-dialect"), KernelPattern.US_DIALECT_PATTERN,
                usAcceptability);
        return descriptionNid;
    }

    /**
     * Records a description's acceptability in a dialect.
     *
     * @param stamp          the stamp the version is written under
     * @param descriptionNid the description
     * @param dialectUuid    the dialect semantic's identity
     * @param dialectPattern the dialect's pattern, one field: acceptability
     * @param acceptability  preferred or acceptable
     * @return the dialect semantic's nid
     */
    public long dialect(Stamp stamp, long descriptionNid, UUID dialectUuid, PatternFacade dialectPattern,
                        ConceptFacade acceptability) {
        return semantic(stamp, PublicIds.of(dialectUuid), dialectPattern, descriptionNid, Lists.immutable.of(acceptability));
    }

    /**
     * Retires a semantic: appends a version carrying its latest content under the inactive twin
     * of the stamp (same time, author, module and path). A semantic already inactive on the view,
     * or absent, is left alone.
     *
     * @param stamp      the stamp whose inactive twin the version is written under
     * @param semanticId the semantic's public id
     * @return true when a version was appended
     */
    public boolean retire(Stamp stamp, PublicId semanticId) {
        if (!PrimitiveData.get().hasPublicId(semanticId)) {
            return false;
        }
        long nid = PrimitiveData.nid(semanticId);
        Optional<SemanticRecord> existing = EntityHandle.get(nid).entity().filter(e -> !e.canceled())
                .map(e -> (SemanticRecord) e);
        Latest<SemanticEntityVersion> latest = calculator.latest(nid);
        if (existing.isEmpty() || latest.isAbsent() || !latest.get().active()) {
            return false;
        }
        Stamp inactive = active(stamp)
                ? new InactiveStamp(stamp.time(), stamp.author(), stamp.module(), stamp.path())
                : stamp;
        SemanticRecord record = existing.get();
        SemanticVersionRecord version = SemanticVersionRecordBuilder.builder()
                .chronology(record)
                .stampNid(stamp(inactive))
                .fieldValues(latest.get().fieldValues())
                .build();
        EntityService.get().putEntity(SemanticRecordBuilder.builder(record)
                .versions(record.versions().newWith(version)).build());
        counts.retired.increment();
        return true;
    }

    /**
     * Whether two field lists say the same thing: trees by structure, meaning, and properties
     * with no regard to child order; components by nid; lists of nids in order; everything
     * else by equality.
     *
     * @param a one list
     * @param b the other
     * @return true when they agree
     */
    public static boolean sameFields(ImmutableList<Object> a, ImmutableList<Object> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!sameValue(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The record with every UUID of a public id it lacks added after its own, the head kept; the
     * record itself when it has them all.
     */
    private static ConceptRecord withUuids(ConceptRecord record, PublicId wanted) {
        java.util.List<UUID> uuids = new java.util.ArrayList<>(java.util.Arrays.asList(record.publicId().asUuidArray()));
        boolean added = false;
        for (UUID uuid : wanted.asUuidArray()) {
            if (!uuids.contains(uuid)) {
                uuids.add(uuid);
                added = true;
            }
        }
        if (!added) {
            return record;
        }
        PublicIdentifierRecord merged = PublicIdentifierRecord.make(PublicIds.of(uuids.toArray(UUID[]::new)));
        return ConceptRecordBuilder.builder(record)
                .mostSignificantBits(merged.mostSignificantBits())
                .leastSignificantBits(merged.leastSignificantBits())
                .additionalUuidLongs(merged.additionalUuidLongs())
                .build();
    }

    private static boolean active(Stamp stamp) {
        return stamp.state() == State.ACTIVE;
    }

    private static boolean hasVersionUnder(ImmutableList<? extends EntityVersion> versions, long stampNid) {
        return versions.anySatisfy(version -> version.stampNid() == stampNid);
    }

    private static boolean sameValue(Object a, Object b) {
        if (a instanceof DiTreeEntity treeA && b instanceof DiTreeEntity treeB) {
            return sameTree(treeA, treeA.root(), treeB, treeB.root());
        }
        if (a instanceof EntityFacade facadeA && b instanceof EntityFacade facadeB) {
            return facadeA.nid() == facadeB.nid();
        }
        if (a instanceof LongIdList listA && b instanceof LongIdList listB) {
            return listA.equals(listB);
        }
        return Objects.equals(a, b);
    }

    private static boolean sameTree(DiTreeEntity treeA, EntityVertex a, DiTreeEntity treeB, EntityVertex b) {
        if (a.getMeaningNid() != b.getMeaningNid() || a.properties().size() != b.properties().size()) {
            return false;
        }
        for (long key : a.properties().keySet().toArray()) {
            if (!b.properties().containsKey(key) || !sameValue(a.properties().get(key), b.properties().get(key))) {
                return false;
            }
        }
        ImmutableIntList childrenA = treeA.successors(a.vertexIndex());
        ImmutableIntList childrenB = treeB.successors(b.vertexIndex());
        if (childrenA.size() != childrenB.size()) {
            return false;
        }
        boolean[] matched = new boolean[childrenB.size()];
        for (int i = 0; i < childrenA.size(); i++) {
            boolean found = false;
            for (int j = 0; j < childrenB.size() && !found; j++) {
                if (!matched[j] && sameTree(treeA, treeA.vertex(childrenA.get(i)), treeB, treeB.vertex(childrenB.get(j)))) {
                    matched[j] = true;
                    found = true;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static long writeStamp(Stamp stamp, long stampNid) {
        PublicId stampId = stamp.publicId();
        if (EntityHandle.get(stampNid).entity().filter(e -> !e.canceled()).isPresent()) {
            return stampNid;
        }
        PublicIdentifierRecord identifier = PublicIdentifierRecord.make(stampId);
        RecordListBuilder<StampVersionRecord> versionRecords = RecordListBuilder.make();
        StampRecord stampEntity = new StampRecord(identifier.mostSignificantBits(),
                identifier.leastSignificantBits(), identifier.additionalUuidLongs(), stampNid, versionRecords);
        versionRecords.add(new StampVersionRecord(stampEntity, stamp.state().nid(), stamp.time(),
                stamp.author().nid(), stamp.module().nid(), stamp.path().nid()));
        versionRecords.build();
        EntityService.get().putEntity(stampEntity);
        return stampNid;
    }
}
