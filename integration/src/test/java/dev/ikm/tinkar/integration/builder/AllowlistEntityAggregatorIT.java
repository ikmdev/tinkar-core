package dev.ikm.tinkar.integration.builder;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.EntityCountSummary;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.aggregator.AllowlistEntityAggregator;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.KnowledgeSet;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    private EntityProxy.Concept moduleA;
    private EntityProxy.Concept moduleB;
    private EntityProxy.Concept thingA;
    private EntityProxy.Concept thingB;
    private EntityProxy.Pattern probePattern;

    @BeforeAll
    void beforeAll() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);

        KnowledgeSet set = KnowledgeSet.of("aa1e5000-0000-5000-9000-0000000000aa");
        moduleA = set.conceptRef("Module A (Probe)");
        moduleB = set.conceptRef("Module B (Probe)");
        thingA = set.conceptRef("Thing A (Probe)");
        thingB = set.conceptRef("Thing B (Probe)");
        probePattern = set.patternRef("Probe pattern (Probe)");

        ActiveStamp stampA = Stamp.active("2026-07-10T00:00:00Z", TinkarTerm.USER, moduleA, TinkarTerm.DEVELOPMENT_PATH);
        ActiveStamp stampB = Stamp.active("2026-07-10T00:00:00Z", TinkarTerm.USER, moduleB, TinkarTerm.DEVELOPMENT_PATH);

        // Two modules, each with a module concept (stamped under itself) and a thing.
        set.concept("Module A (Probe)").at(stampA).synonym("Module A");
        set.concept("Module B (Probe)").at(stampB).synonym("Module B");
        set.concept("Thing A (Probe)").at(stampA).synonym("Thing A");
        set.concept("Thing B (Probe)").at(stampB).synonym("Thing B");
        // A pattern in module A, so an in-store pattern exists to test emission ordering.
        set.pattern("Probe pattern (Probe)").at(stampA)
                .meaning(TinkarTerm.MEANING).purpose(TinkarTerm.PURPOSE)
                .field(TinkarTerm.MEANING, TinkarTerm.PURPOSE, TinkarTerm.STRING)
                .synonym("Probe pattern");
        set.write();
    }

    @AfterAll
    void afterAll() {
        TestHelper.stopDatabase();
    }

    @Test
    void allowlistIncludesOnlyTheAllowedModule() {
        List<Integer> emitted = collect(new AllowlistEntityAggregator(Set.of(moduleA.publicId())));

        assertTrue(emitted.contains(moduleA.nid()), "the allowed module's own concept is exported");
        assertTrue(emitted.contains(thingA.nid()), "content authored in the allowed module is exported");
        assertFalse(emitted.contains(moduleB.nid()), "the excluded module's concept is not exported");
        assertFalse(emitted.contains(thingB.nid()), "content authored in the excluded module is not exported");
    }

    @Test
    void patternsAreEmittedBeforeSemantics() {
        List<Integer> emitted = collect(new AllowlistEntityAggregator(Set.of(moduleA.publicId())));

        int lastPatternIndex = -1;
        int firstSemanticIndex = Integer.MAX_VALUE;
        for (int i = 0; i < emitted.size(); i++) {
            Entity<?> entity = EntityService.get().getEntityFast(emitted.get(i));
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
        Set<PublicId> allow = Set.of(moduleA.publicId());
        EntityCountSummary noPredicate = new AllowlistEntityAggregator(allow).aggregate(nid -> { });
        // Fail-open: purpose never drops a semantic whose pattern-purpose it cannot classify (the
        // description semantics here sit on TinkarTerm patterns absent from this replay-seeded store),
        // so a reject-everything predicate leaves the semantic count unchanged.
        EntityCountSummary rejectAll =
                new AllowlistEntityAggregator(allow, Set.of(), Set.of(), Set.of(), nid -> false).aggregate(nid -> { });

        assertTrue(noPredicate.semanticCount() > 0, "the allowed module has description semantics");
        assertEquals(noPredicate.semanticCount(), rejectAll.semanticCount(),
                "a complementary purpose refinement never drops content it cannot classify");
        // NOTE: positive purpose exclusion (a reject predicate dropping a semantic on an in-store
        // pattern) needs a semantic instance, which KnowledgeSet does not yet author — follow-up.
    }

    @Test
    void excludingAPatternDropsItButKeepsSemanticsOnOtherPatterns() {
        Set<PublicId> mod = Set.of(moduleA.publicId());
        EntityCountSummary baseline = new AllowlistEntityAggregator(mod).aggregate(nid -> { });
        AllowlistEntityAggregator excluding = new AllowlistEntityAggregator(
                mod, Set.of(), Set.of(), Set.of(probePattern.publicId()), null);
        List<Integer> emitted = collect(excluding);
        EntityCountSummary excluded = new AllowlistEntityAggregator(
                mod, Set.of(), Set.of(), Set.of(probePattern.publicId()), null).aggregate(nid -> { });

        assertFalse(emitted.contains(probePattern.nid()), "an excluded pattern is not exported");
        assertEquals(baseline.patternCount() - 1, excluded.patternCount(), "exactly the excluded pattern is dropped");
        assertEquals(baseline.semanticCount(), excluded.semanticCount(),
                "excluding a pattern does not drop semantics that use other patterns");
    }

    @Test
    void includingOnlyOnePatternKeepsOnlyThatPattern() {
        Set<PublicId> mod = Set.of(moduleA.publicId());
        EntityCountSummary included = new AllowlistEntityAggregator(
                mod, Set.of(), Set.of(probePattern.publicId()), Set.of(), null).aggregate(nid -> { });

        assertEquals(1, included.patternCount(), "only the included pattern is exported");
        // The module's description semantics sit on external TinkarTerm patterns, so an include-set
        // limited to the probe pattern drops them — nothing in this fixture uses the probe pattern.
        assertEquals(0, included.semanticCount(), "only semantics on the included pattern would cross");
    }

    private static List<Integer> collect(AllowlistEntityAggregator aggregator) {
        List<Integer> emitted = new ArrayList<>();
        aggregator.aggregate(emitted::add);
        return emitted;
    }
}
