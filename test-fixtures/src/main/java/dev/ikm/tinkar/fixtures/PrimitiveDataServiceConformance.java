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
package dev.ikm.tinkar.fixtures;

import dev.ikm.tinkar.common.id.EntityKey;
import dev.ikm.tinkar.common.id.Nid;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.PrimitiveDataService;
import dev.ikm.tinkar.common.service.internal.EntityStore;
import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.ConceptVersionRecord;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityRecordFactory;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.FieldDefinitionRecord;
import dev.ikm.tinkar.entity.PatternRecord;
import dev.ikm.tinkar.entity.PatternVersionRecord;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.StampVersionRecord;
import dev.ikm.tinkar.terms.EntityBinding;
import dev.ikm.tinkar.terms.EntityProxy;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.primitive.LongLists;
import org.eclipse.collections.api.factory.primitive.LongLists;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contract every {@link PrimitiveDataService} implementation keeps, as one set of tests: nid
 * assignment, the bytes stored for a nid and how versions merge, and the enumerations and indexes
 * of {@link EntityStore} (design {@code design-2026-09-30-64-bit-nids}, step "Specification and
 * fixtures"). The move to {@code long} nids widens this contract in place, and the suite is how
 * each provider shows that it kept it.
 * <p>A provider runs the suite with a subclass that opens an empty store of its kind for the class,
 * for example:
 * <pre>{@code
 * @WithKeyValueProvider(controllerClass = ProviderEphemeral.NewController.class)
 * class EphemeralConformanceTest extends PrimitiveDataServiceConformance {
 * }
 * }</pre>
 * Each test makes its own components, with fresh UUIDs, so the tests do not depend on one another
 * or on the order they run in, and each asserts only about what it made.
 * <p>Nids are minted as the entity layer mints them, under the pattern of the entity being
 * identified ({@link EntityService#nidForConcept}, {@code nidForPattern}, {@code nidForStamp},
 * {@code nidForSemantic}), because the pattern-encoded providers refuse a nid for a UUID with no
 * pattern in scope.
 */
public abstract class PrimitiveDataServiceConformance {

    /**
     * The pattern and referenced component passed to {@code merge} for anything but a semantic:
     * the not-applicable sentinel, {@link Nid#NOT_APPLICABLE}, as the {@code int} the contract
     * takes until it is widened.
     */
    private static final int NOT_A_SEMANTIC = Integer.MAX_VALUE;

    private static final long TIME = 1_767_225_600_777L;

    /** The components one test makes: a stamp, two concepts, a pattern, and a semantic of it. */
    protected record World(StampRecord stamp, ConceptRecord concept, ConceptRecord otherConcept,
                           PatternRecord pattern, SemanticRecord semantic) {
    }

    // ---------- nids ----------

    @Test
    void aUuidKeepsItsNid() {
        PublicId id = randomId();
        long nid = EntityService.get().nidForConcept(id);
        assertEquals(nid, EntityService.get().nidForConcept(id));
        assertEquals(nid, PrimitiveData.get().nidForUuids(id.asUuidArray()));
        assertEquals(nid, PrimitiveData.get().nidForPublicId(id));
    }

    @Test
    void distinctUuidsGetDistinctNids() {
        Set<Long> nids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            assertTrue(nids.add(EntityService.get().nidForConcept(randomId())), "nid repeated at " + i);
        }
    }

    @Test
    void everyUuidOfAPublicIdHasItsNid() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        long nid = EntityService.get().nidForConcept(PublicIds.of(first, second));
        assertEquals(nid, PrimitiveData.get().nidForUuids(first));
        assertEquals(nid, PrimitiveData.get().nidForUuids(second));
        assertEquals(nid, PrimitiveData.get().nidForUuids(second, first));
    }

    /**
     * No assigned nid is one of the three sentinels that can never be a nid: unset, none, and not
     * applicable. The fourth, not found (-1), is a value the packed layouts can produce.
     */
    @Test
    void anAssignedNidIsNoSentinel() {
        for (int i = 0; i < 20; i++) {
            long nid = EntityService.get().nidForConcept(randomId());
            assertTrue(Nid.isValid(nid), "nid " + nid);
        }
    }

    @Test
    void uuidsAreKnownOnceTheyHaveANid() {
        UUID uuid = UUID.randomUUID();
        PublicId id = PublicIds.of(uuid);
        assertFalse(PrimitiveData.get().hasUuid(uuid));
        assertFalse(PrimitiveData.get().hasPublicId(id));
        EntityService.get().nidForConcept(id);
        assertTrue(PrimitiveData.get().hasUuid(uuid));
        assertTrue(PrimitiveData.get().hasPublicId(id));
    }

    @Test
    void anEntityKeyIsFoundForAUuidWithANid() {
        UUID uuid = UUID.randomUUID();
        assertTrue(PrimitiveData.get().getEntityKey(uuid).isEmpty());
        long nid = EntityService.get().nidForConcept(PublicIds.of(uuid));
        Optional<EntityKey> key = PrimitiveData.get().getEntityKey(uuid);
        assertTrue(key.isPresent());
        assertEquals(nid, key.get().nid());
    }

    // ---------- bytes ----------

    @Test
    void storedBytesReadBackAsTheEntity() {
        World world = makeWorld();
        for (Entity<? extends EntityVersion> entity : entities(world)) {
            byte[] stored = EntityStore.current().getBytes(entity.nid());
            assertNotNull(stored, entity.getClass().getSimpleName());
            assertArrayEquals(EntityRecordFactory.getBytes(entity), stored, entity.getClass().getSimpleName());
        }
    }

    @Test
    void aNidWithNoEntityHasNoBytes() {
        long nid = EntityService.get().nidForConcept(randomId());
        assertNull(EntityStore.current().getBytes(nid));
    }

    @Test
    void mergingAnotherVersionKeepsBoth() {
        World world = makeWorld();
        StampRecord laterStamp = stamp(TIME + 1_000);
        byte[] returned = merge(laterVersionOf(world.concept(), laterStamp));

        Set<Long> expected = Set.of(world.stamp().nid(), laterStamp.nid());
        assertEquals(expected, stampNids(returned), "the bytes merge returns");
        assertEquals(expected, stampNids(EntityStore.current().getBytes(world.concept().nid())), "the bytes stored");
    }

    /**
     * A version merged after the store has saved the earlier ones joins them, in what merge
     * returns, which the entity layer caches, and in what is read back. A store that writes
     * behind must not answer from the new version alone (IKE-Network/ike-issues#1245).
     */
    @Test
    void mergingAnotherVersionAfterASaveKeepsBoth() {
        World world = makeWorld();
        StampRecord laterStamp = stamp(TIME + 1_000);
        PrimitiveData.save();
        byte[] returned = merge(laterVersionOf(world.concept(), laterStamp));

        Set<Long> expected = Set.of(world.stamp().nid(), laterStamp.nid());
        assertEquals(expected, stampNids(returned), "the bytes merge returns");
        assertEquals(expected, stampNids(EntityStore.current().getBytes(world.concept().nid())), "the bytes stored");
        PrimitiveData.save();
        assertEquals(expected, stampNids(EntityStore.current().getBytes(world.concept().nid())), "the bytes saved");
    }

    @Test
    void mergingTheSameVersionAgainChangesNothing() {
        World world = makeWorld();
        byte[] before = EntityStore.current().getBytes(world.concept().nid());
        merge(world.concept());
        Entity<? extends EntityVersion> after = EntityRecordFactory.make(EntityStore.current().getBytes(world.concept().nid()));
        assertEquals(EntityRecordFactory.make(before).versions().size(), after.versions().size());
    }

    // ---------- enumerations and indexes ----------

    @Test
    void eachKindIsEnumerated() {
        World world = makeWorld();
        assertTrue(collect(EntityStore.current()::forEachConceptNid).contains(world.concept().nid()));
        assertTrue(collect(EntityStore.current()::forEachConceptNid).contains(world.otherConcept().nid()));
        assertTrue(collect(EntityStore.current()::forEachPatternNid).contains(world.pattern().nid()));
        assertTrue(collect(EntityStore.current()::forEachSemanticNid).contains(world.semantic().nid()));
        assertTrue(collect(EntityStore.current()::forEachStampNid).contains(world.stamp().nid()));
    }

    @Test
    void eachKindIsEnumeratedOnlyAsItself() {
        World world = makeWorld();
        assertFalse(collect(EntityStore.current()::forEachConceptNid).contains(world.semantic().nid()));
        assertFalse(collect(EntityStore.current()::forEachSemanticNid).contains(world.concept().nid()));
        assertFalse(collect(EntityStore.current()::forEachPatternNid).contains(world.stamp().nid()));
        assertFalse(collect(EntityStore.current()::forEachStampNid).contains(world.pattern().nid()));
    }

    @Test
    void forEachVisitsEveryEntityWithItsBytes() {
        World world = makeWorld();
        ConcurrentHashMap<Long, byte[]> visited = new ConcurrentHashMap<>();
        EntityStore.current().forEach((bytes, nid) -> visited.put(nid, bytes));
        for (Entity<? extends EntityVersion> entity : entities(world)) {
            assertArrayEquals(EntityStore.current().getBytes(entity.nid()), visited.get(entity.nid()),
                    entity.getClass().getSimpleName());
        }
    }

    @Test
    void forEachOfNidsVisitsThoseNids() {
        World world = makeWorld();
        ConcurrentHashMap<Long, byte[]> visited = new ConcurrentHashMap<>();
        EntityStore.current().forEach(LongLists.immutable.of(world.concept().nid(), world.semantic().nid()),
                (bytes, nid) -> visited.put(nid, bytes));
        assertEquals(Set.of(world.concept().nid(), world.semantic().nid()), visited.keySet());
    }

    @Test
    void aSemanticIsIndexedUnderItsPattern() {
        World world = makeWorld();
        assertTrue(collect(procedure -> EntityStore.current().forEachSemanticNidOfPattern(world.pattern().nid(), procedure))
                .contains(world.semantic().nid()));
        assertArrayEquals(new long[]{world.semantic().nid()}, EntityStore.current().semanticNidsOfPattern(world.pattern().nid()));
    }

    @Test
    void aSemanticIsIndexedUnderItsReferencedComponent() {
        World world = makeWorld();
        assertArrayEquals(new long[]{world.semantic().nid()},
                EntityStore.current().semanticNidsForComponent(world.concept().nid()));
        assertArrayEquals(new long[]{world.semantic().nid()},
                EntityStore.current().semanticNidsForComponentOfPattern(world.concept().nid(), world.pattern().nid()));
        assertEquals(0, EntityStore.current().semanticNidsForComponent(world.otherConcept().nid()).length);
        assertEquals(0, EntityStore.current()
                .semanticNidsForComponentOfPattern(world.otherConcept().nid(), world.pattern().nid()).length);
    }

    @Test
    void aPatternsSemanticsAreCountedAndStreamed() {
        World world = makeWorld();
        assertEquals(1, EntityService.get().countSemanticsOfPattern(world.pattern().nid()));
        assertEquals(List.of(world.semantic().nid()), EntityService.get().semanticsOfPattern(world.pattern().nid())
                .map(semantic -> semantic.nid()).toList());
    }

    /**
     * Concepts and semantics are counted without reading them (IKE-Network/ike-issues#1249). A
     * pattern-keyed store also counts a concept nid assigned and never written, as this world's
     * stamp and pattern fields are, so concepts are counted at least, semantics exactly.
     */
    @Test
    void conceptsAndSemanticsAreCounted() {
        long concepts = EntityService.get().countConcepts();
        long semantics = EntityService.get().countSemantics();
        makeWorld();
        assertTrue(EntityService.get().countConcepts() - concepts >= 2, "two concepts written");
        assertEquals(1, EntityService.get().countSemantics() - semantics, "one semantic written");
    }

    /**
     * The concept, stamp and pattern binding patterns have no semantics. A pattern-keyed store
     * keys concepts, stamps and patterns under them, so their index lists those; the entity layer
     * must not give them out as semantics (IKE-Network/ike-issues#1248).
     */
    @Test
    void theBindingPatternsHaveNoSemantics() {
        makeWorld();
        for (EntityProxy.Pattern binding : List.of(EntityBinding.Concept.pattern(), EntityBinding.Stamp.pattern(),
                EntityBinding.Pattern.pattern())) {
            long patternNid = EntityService.get().nidForPattern(binding.publicId());
            List<Long> given = new java.util.ArrayList<>();
            EntityService.get().forEachSemanticOfPattern(patternNid, semantic -> given.add(semantic.nid()));
            assertEquals(List.of(), given, binding.description());
            assertEquals(0, EntityService.get().semanticsOfPattern(patternNid).count(), binding.description());
            assertEquals(0, EntityService.get().countSemanticsOfPattern(patternNid), binding.description());
        }
    }

    // ---------- the world ----------

    /**
     * Makes and stores a stamp, two concepts, a pattern with one field, and a semantic of that
     * pattern referencing the first concept, all with fresh UUIDs. The concepts a stamp or pattern
     * version names are given nids and no entities: the store is empty, and nothing here reads them.
     *
     * @return what was made
     */
    protected World makeWorld() {
        StampRecord stamp = stamp(TIME);
        ConceptRecord concept = concept(stamp);
        ConceptRecord otherConcept = concept(stamp);
        PatternRecord pattern = pattern(stamp);
        SemanticRecord semantic = semantic(stamp, pattern, concept);
        return new World(stamp, concept, otherConcept, pattern, semantic);
    }

    private static StampRecord stamp(long time) {
        UUID uuid = UUID.randomUUID();
        long nid = EntityService.get().nidForStamp(PublicIds.of(uuid));
        RecordListBuilder<StampVersionRecord> versions = RecordListBuilder.make();
        StampRecord stamp = new StampRecord(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits(),
                LongLists.immutable.empty(), nid, versions);
        long stampField = EntityService.get().nidForConcept(randomId());
        versions.add(new StampVersionRecord(stamp, stampField, time, stampField, stampField, stampField));
        versions.build();
        merge(stamp);
        return stamp;
    }

    private static ConceptRecord concept(StampRecord stamp) {
        UUID uuid = UUID.randomUUID();
        long nid = EntityService.get().nidForConcept(PublicIds.of(uuid));
        RecordListBuilder<ConceptVersionRecord> versions = RecordListBuilder.make();
        ConceptRecord concept = new ConceptRecord(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits(),
                LongLists.immutable.empty(), nid, versions);
        versions.add(new ConceptVersionRecord(concept, stamp.nid()));
        versions.build();
        merge(concept);
        return concept;
    }

    private static PatternRecord pattern(StampRecord stamp) {
        UUID uuid = UUID.randomUUID();
        long nid = EntityService.get().nidForPattern(PublicIds.of(uuid));
        RecordListBuilder<PatternVersionRecord> versions = RecordListBuilder.make();
        PatternRecord pattern = new PatternRecord(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits(),
                LongLists.immutable.empty(), nid, versions);
        long meaning = EntityService.get().nidForConcept(randomId());
        versions.add(new PatternVersionRecord(pattern, stamp.nid(), meaning, meaning, Lists.immutable.of(
                new FieldDefinitionRecord(meaning, meaning, meaning, stamp.nid(), nid, 0))));
        versions.build();
        merge(pattern);
        return pattern;
    }

    private static SemanticRecord semantic(StampRecord stamp, PatternRecord pattern, ConceptRecord referencedComponent) {
        UUID uuid = UUID.randomUUID();
        long nid = EntityService.get().nidForSemantic(pattern.publicId(), PublicIds.of(uuid));
        RecordListBuilder<SemanticVersionRecord> versions = RecordListBuilder.make();
        SemanticRecord semantic = new SemanticRecord(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits(),
                LongLists.immutable.empty(), nid, pattern.nid(), referencedComponent.nid(), versions);
        versions.add(new SemanticVersionRecord(semantic, stamp.nid(), Lists.immutable.of("conformance text")));
        versions.build();
        merge(semantic);
        return semantic;
    }

    private static PublicId randomId() {
        return PublicIds.of(UUID.randomUUID());
    }

    private static byte[] merge(Entity<? extends EntityVersion> entity) {
        if (entity instanceof SemanticRecord semantic) {
            return EntityStore.current().merge(semantic.nid(), semantic.patternNid(), semantic.referencedComponentNid(),
                    EntityRecordFactory.getBytes(semantic), semantic);
        }
        return EntityStore.current().merge(entity.nid(), NOT_A_SEMANTIC, NOT_A_SEMANTIC,
                EntityRecordFactory.getBytes(entity), entity);
    }

    /** The concept again, with one version on another stamp, as a later edit writes it. */
    private static ConceptRecord laterVersionOf(ConceptRecord concept, StampRecord stamp) {
        RecordListBuilder<ConceptVersionRecord> versions = RecordListBuilder.make();
        ConceptRecord sameConcept = new ConceptRecord(concept.mostSignificantBits(), concept.leastSignificantBits(),
                concept.additionalUuidLongs(), concept.nid(), versions);
        versions.add(new ConceptVersionRecord(sameConcept, stamp.nid()));
        versions.build();
        return sameConcept;
    }

    private static Set<Long> stampNids(byte[] entityBytes) {
        Set<Long> stampNids = new HashSet<>();
        EntityRecordFactory.make(entityBytes).versions().forEach(version -> stampNids.add(((EntityVersion) version).stampNid()));
        return stampNids;
    }

    private static java.util.List<Entity<? extends EntityVersion>> entities(World world) {
        return java.util.List.of(world.stamp(), world.concept(), world.otherConcept(), world.pattern(), world.semantic());
    }

    private interface NidEnumeration {
        void forEach(org.eclipse.collections.api.block.procedure.primitive.LongProcedure procedure);
    }

    private static Set<Long> collect(NidEnumeration enumeration) {
        Set<Long> nids = ConcurrentHashMap.newKeySet();
        enumeration.forEach(nids::add);
        return nids;
    }
}
