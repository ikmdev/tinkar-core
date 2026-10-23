package dev.ikm.tinkar.integration.builder;

import dev.ikm.tinkar.terms.PatternFacade;
import network.ike.foundation.ike.bindings.IkeConcept;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.EntityCountSummary;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.SemanticRecordBuilder;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import dev.ikm.tinkar.entity.aggregator.AllowlistEntityAggregator;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.KnowledgeSet;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.ConceptFacade;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.eclipse.collections.api.factory.Lists;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link AllowlistEntityAggregator}: a change set filtered to an allowlisted module includes only
 * that module's content, emits patterns before the semantics that reference them, and treats the
 * optional purpose predicate as an off-by-default, fail-open refinement.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AllowlistEntityAggregatorIT {

    private ConceptFacade moduleA;
    private ConceptFacade moduleB;
    private ConceptFacade thingA;
    private ConceptFacade thingB;
    private PatternFacade probePattern;
    /** A semantic in module A whose pattern the store does not hold, so its purpose cannot be resolved. */
    private long unclassifiable;

    @BeforeAll
    void beforeAll() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        // The base the set's references resolve to: the kernel and the starter set.
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);

        KnowledgeSet set = KnowledgeSet.of("aa1e5000-0000-5000-9000-0000000000aa");
        moduleA = set.conceptRef("Module A (Probe)");
        moduleB = set.conceptRef("Module B (Probe)");
        thingA = set.conceptRef("Thing A (Probe)");
        thingB = set.conceptRef("Thing B (Probe)");
        probePattern = set.patternRef("Probe pattern (Probe)");

        ActiveStamp stampA = Stamp.active("2026-07-10T00:00:00Z", KernelConcept.USER, moduleA, KernelConcept.DEVELOPMENT_PATH);
        ActiveStamp stampB = Stamp.active("2026-07-10T00:00:00Z", KernelConcept.USER, moduleB, KernelConcept.DEVELOPMENT_PATH);

        // Two modules, each with a module concept (stamped under itself) and a thing.
        set.concept("Module A (Probe)").at(stampA).synonym("Module A");
        set.concept("Module B (Probe)").at(stampB).synonym("Module B");
        set.concept("Thing A (Probe)").at(stampA).synonym("Thing A");
        set.concept("Thing B (Probe)").at(stampB).synonym("Thing B");
        // A pattern in module A, so an in-store pattern exists to test emission ordering.
        set.pattern("Probe pattern (Probe)").at(stampA)
                .meaning(IkeConcept.MEANING).purpose(IkeConcept.PURPOSE)
                .field(IkeConcept.MEANING, IkeConcept.PURPOSE, KernelConcept.STRING)
                .synonym("Probe pattern");
        set.write();
        unclassifiable = writeSemanticOfAnAbsentPattern(stampA);
    }

    /**
     * Writes a semantic about Thing A, under module A's STAMP, whose pattern the store does not
     * hold: written past the set, which would refuse it, as content from a source whose patterns
     * were not brought along.
     */
    private long writeSemanticOfAnAbsentPattern(ActiveStamp stampA) {
        PublicId absentPattern = PublicIds.of(UUID.nameUUIDFromBytes("AllowlistEntityAggregatorIT absent pattern".getBytes()));
        UUID semanticUuid = UUID.nameUUIDFromBytes("AllowlistEntityAggregatorIT unclassifiable semantic".getBytes());
        long patternNid = EntityService.get().nidForPattern(absentPattern);
        long semanticNid = EntityService.get().nidForSemantic(absentPattern, PublicIds.of(semanticUuid));
        long stampNid = EntityHandle.get(stampA.publicId()).expectStamp().nid();
        RecordListBuilder<SemanticVersionRecord> versions = RecordListBuilder.make();
        SemanticRecord semantic = SemanticRecordBuilder.builder()
                .leastSignificantBits(semanticUuid.getLeastSignificantBits())
                .mostSignificantBits(semanticUuid.getMostSignificantBits())
                .nid(semanticNid)
                .patternNid(patternNid)
                .referencedComponentNid(thingA.nid())
                .versions(versions).build();
        versions.add(new SemanticVersionRecord(semantic, stampNid, Lists.immutable.of("of a pattern the store does not hold"))).build();
        EntityService.get().putEntity(semantic);
        return semanticNid;
    }

    @AfterAll
    void afterAll() {
        TestHelper.stopDatabase();
    }

    @Test
    void allowlistIncludesOnlyTheAllowedModule() {
        List<Long> emitted = collect(new AllowlistEntityAggregator(List.of(moduleA.publicId())));

        assertTrue(emitted.contains(moduleA.nid()), "the allowed module's own concept is exported");
        assertTrue(emitted.contains(thingA.nid()), "content authored in the allowed module is exported");
        assertFalse(emitted.contains(moduleB.nid()), "the excluded module's concept is not exported");
        assertFalse(emitted.contains(thingB.nid()), "content authored in the excluded module is not exported");
    }

    @Test
    void patternsAreEmittedBeforeSemantics() {
        List<Long> emitted = collect(new AllowlistEntityAggregator(List.of(moduleA.publicId())));

        int lastPatternIndex = -1;
        int firstSemanticIndex = Integer.MAX_VALUE;
        for (int i = 0; i < emitted.size(); i++) {
            Entity<?> entity = EntityHandle.get(emitted.get(i)).orNull();
            if (entity instanceof PatternEntity<?>) {
                lastPatternIndex = i;
            } else if (entity instanceof SemanticEntity<?> && firstSemanticIndex == Integer.MAX_VALUE) {
                firstSemanticIndex = i;
            }
        }
        assertTrue(lastPatternIndex >= 0, "a pattern was exported");
        assertTrue(firstSemanticIndex != Integer.MAX_VALUE, "a semantic was exported");
        assertTrue(lastPatternIndex < firstSemanticIndex,
                "every pattern is emitted before the semantics that reference it");
    }

    @Test
    void purposePredicateIsOffByDefaultAndFailOpen() {
        List<PublicId> allow = List.of(moduleA.publicId());
        List<Long> noPredicate = collect(new AllowlistEntityAggregator(allow));
        // A predicate rejecting every purpose drops each semantic whose pattern-purpose resolves —
        // the descriptions, on patterns the store holds — and keeps the one whose pattern it does
        // not hold: purpose never drops content it cannot classify.
        List<Long> rejectAll = collect(new AllowlistEntityAggregator(allow, List.of(), List.of(), List.of(), nid -> false));

        assertTrue(noPredicate.contains(unclassifiable), "with no predicate, every semantic of the module is exported");
        assertTrue(noPredicate.stream().anyMatch(nid -> nid != unclassifiable
                        && EntityHandle.get(nid).orNull() instanceof SemanticEntity<?>),
                "the allowed module has description semantics");
        assertEquals(List.of(unclassifiable), rejectAll.stream()
                        .filter(nid -> EntityHandle.get(nid).orNull() instanceof SemanticEntity<?>)
                        .toList(),
                "only the semantic whose purpose cannot be resolved survives a predicate rejecting every purpose");
    }

    @Test
    void excludingAPatternDropsItButKeepsSemanticsOnOtherPatterns() {
        List<PublicId> mod = List.of(moduleA.publicId());
        EntityCountSummary baseline = new AllowlistEntityAggregator(mod).aggregate(nid -> { });
        AllowlistEntityAggregator excluding = new AllowlistEntityAggregator(
                mod, List.of(), List.of(), List.of(probePattern.publicId()), null);
        List<Long> emitted = collect(excluding);
        EntityCountSummary excluded = new AllowlistEntityAggregator(
                mod, List.of(), List.of(), List.of(probePattern.publicId()), null).aggregate(nid -> { });

        assertFalse(emitted.contains(probePattern.nid()), "an excluded pattern is not exported");
        assertEquals(baseline.patternCount() - 1, excluded.patternCount(), "exactly the excluded pattern is dropped");
        assertEquals(baseline.semanticCount(), excluded.semanticCount(),
                "excluding a pattern does not drop semantics that use other patterns");
    }

    @Test
    void includingOnlyOnePatternKeepsOnlyThatPattern() {
        List<PublicId> mod = List.of(moduleA.publicId());
        EntityCountSummary included = new AllowlistEntityAggregator(
                mod, List.of(), List.of(probePattern.publicId()), List.of(), null).aggregate(nid -> { });

        assertEquals(1, included.patternCount(), "only the included pattern is exported");
        // The module's description semantics sit on external kernel patterns, so an include-set
        // limited to the probe pattern drops them — nothing in this fixture uses the probe pattern.
        assertEquals(0, included.semanticCount(), "only semantics on the included pattern would cross");
    }

    private static List<Long> collect(AllowlistEntityAggregator aggregator) {
        List<Long> emitted = new ArrayList<>();
        aggregator.aggregate(emitted::add);
        return emitted;
    }
}
