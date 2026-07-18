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
package dev.ikm.tinkar.integration.coordinate;

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StampPositionRecord;
import dev.ikm.tinkar.coordinate.stamp.StateSet;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.KnowledgeSet;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.DefaultsTemplateTerm;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Default value and template semantics as ordinary working-path content
 * (IKE-Network/ike-issues#886), against a real ephemeral store with hand-composed
 * content: the {@code getDefault}/{@code getTemplate} accessors resolve by computed
 * identity ({@code UuidT5Generator.singleSemanticUuid(pattern, attachmentConcept)});
 * version-iteration operations exclude everything stamped in
 * {@link DefaultsTemplateTerm#DEFAULTS_AND_TEMPLATES_MODULE} while chronology-level
 * enumeration still returns the nids (store truth); per-path preference and retirement
 * resolve through the ordinary path calculus.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DefaultsTemplateCalculatorIT {

    private static final KnowledgeSet TEST_SET =
            KnowledgeSet.of("ca8e537f-7bb0-4658-9b5c-89102545fc25");

    private static final String P1_FQN = "Defaults probe pattern (Test)";
    private static final String P2_FQN = "Retirement probe pattern (Test)";
    private static final String PURPOSE_FQN = "Probe template purpose (Test)";
    private static final String CHILD_PATH_FQN = "Probe child path (Test)";

    /** Identity of P1's default value semantic — the stock single-semantic convention. */
    private static UUID p1DefaultId;
    /** Identity of P2's default value semantic. */
    private static UUID p2DefaultId;
    /** Identity of P1's template semantic for the probe purpose. */
    private static UUID templateId;
    /** Identity of the ordinary (non-defaults) P1 semantic on the probe subject. */
    private static UUID ordinaryId;

    @BeforeAll
    static void composeAndWrite() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);

        EntityProxy.Pattern p1 = TEST_SET.patternRef(P1_FQN);
        EntityProxy.Pattern p2 = TEST_SET.patternRef(P2_FQN);
        p1DefaultId = UuidT5Generator.singleSemanticUuid(
                p1.publicId(), DefaultsTemplateTerm.DEFAULT_VALUE_CONCEPT.publicId());
        p2DefaultId = UuidT5Generator.singleSemanticUuid(
                p2.publicId(), DefaultsTemplateTerm.DEFAULT_VALUE_CONCEPT.publicId());
        templateId = UuidT5Generator.singleSemanticUuid(
                p1.publicId(), TEST_SET.conceptRef(PURPOSE_FQN).publicId());
        ordinaryId = UuidT5Generator.singleSemanticUuid(
                p1.publicId(), TEST_SET.conceptRef("Probe subject (Test)").publicId());

        // Ordinary working content is born on the development path in the development
        // module; only the defaults/template SEMANTICS carry the defaults module — the
        // category's live-and-die invariant is about defaults/template chronologies,
        // and the anchor concepts themselves are ordinary content.
        ActiveStamp birth = Stamp.active("2020-01-01T00:00:00Z",
                TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);
        ActiveStamp defaultsAuthored = Stamp.active("2020-02-01T00:00:00Z", TinkarTerm.USER,
                DefaultsTemplateTerm.DEFAULTS_AND_TEMPLATES_MODULE, TinkarTerm.DEVELOPMENT_PATH);

        // The three seam concepts, minted here with the identities tinkar-core declares
        // (in ike-starter-set the IkeFoundation ledger mints them from the same birth
        // FQNs — the declared-identity discipline).
        TEST_SET.concept("Default value concept (IkeFoundation)",
                        DefaultsTemplateTerm.DEFAULT_VALUE_CONCEPT.publicId())
                .at(birth)
                // Defaults semantics attach here; authored under the defaults module.
                .at(defaultsAuthored)
                .semantic(p1, PublicIds.of(p1DefaultId), "parent default value")
                .semantic(p2, PublicIds.of(p2DefaultId), "retired default value")
                // Retirement of P2's default: an inactive version, same module, same path.
                .at(Stamp.inactive("2020-03-01T00:00:00Z", TinkarTerm.USER,
                        DefaultsTemplateTerm.DEFAULTS_AND_TEMPLATES_MODULE, TinkarTerm.DEVELOPMENT_PATH))
                .retireSemantic(p2, PublicIds.of(p2DefaultId))
                // A later default for P1 authored on the child path overrides the
                // parent-path default for calculators positioned on the child path.
                .at(Stamp.active("2021-01-01T00:00:00Z", TinkarTerm.USER,
                        DefaultsTemplateTerm.DEFAULTS_AND_TEMPLATES_MODULE,
                        TEST_SET.conceptRef(CHILD_PATH_FQN)))
                .semantic(p1, PublicIds.of(p1DefaultId), "child default value");

        TEST_SET.concept("Template concept (IkeFoundation)",
                        DefaultsTemplateTerm.TEMPLATE_CONCEPT.publicId())
                .at(birth);

        TEST_SET.concept("Defaults and templates module (IkeFoundation)",
                        DefaultsTemplateTerm.DEFAULTS_AND_TEMPLATES_MODULE.publicId())
                .at(birth).synonym("Defaults");

        // Self-minted pattern metadata: a bare ephemeral store materializes only the
        // TinkarTerm concepts the STAMP dimensions need, so probe patterns declare
        // their own meaning/purpose/field concepts.
        TEST_SET.concept("Probe meaning (Test)").at(birth);
        TEST_SET.concept("Probe purpose (Test)").at(birth);
        TEST_SET.concept("Probe field meaning (Test)").at(birth);
        TEST_SET.concept("Probe field purpose (Test)").at(birth);
        TEST_SET.concept("Probe field data type (Test)").at(birth);

        TEST_SET.pattern(P1_FQN).at(birth)
                .meaning(TEST_SET.conceptRef("Probe meaning (Test)"))
                .purpose(TEST_SET.conceptRef("Probe purpose (Test)"))
                .field(TEST_SET.conceptRef("Probe field meaning (Test)"),
                        TEST_SET.conceptRef("Probe field purpose (Test)"),
                        TEST_SET.conceptRef("Probe field data type (Test)"));
        TEST_SET.pattern(P2_FQN).at(birth)
                .meaning(TEST_SET.conceptRef("Probe meaning (Test)"))
                .purpose(TEST_SET.conceptRef("Probe purpose (Test)"))
                .field(TEST_SET.conceptRef("Probe field meaning (Test)"),
                        TEST_SET.conceptRef("Probe field purpose (Test)"),
                        TEST_SET.conceptRef("Probe field data type (Test)"));

        // One ordinary semantic of P1 — must keep surfacing through version iteration.
        TEST_SET.concept("Probe subject (Test)").at(birth)
                .semantic(p1, PublicIds.of(ordinaryId), "ordinary value");

        // A template purpose concept (child of the template parent) carrying P1's
        // template semantic, authored under the defaults module.
        TEST_SET.concept(PURPOSE_FQN)
                .at(birth).isA(DefaultsTemplateTerm.TEMPLATE_CONCEPT)
                .at(defaultsAuthored).semantic(p1, PublicIds.of(templateId), "template value");

        // A child path forked from the development path at 2020-06-01: a paths-pattern
        // membership semantic plus a path-origins semantic (origin path, origin instant).
        TEST_SET.concept(CHILD_PATH_FQN).at(birth)
                .semantic(TinkarTerm.PATHS_PATTERN,
                        PublicIds.of(UuidT5Generator.singleSemanticUuid(
                                TinkarTerm.PATHS_PATTERN.publicId(),
                                TEST_SET.conceptRef(CHILD_PATH_FQN).publicId())))
                .semantic(TinkarTerm.PATH_ORIGINS_PATTERN,
                        PublicIds.of(UuidT5Generator.singleSemanticUuid(
                                TinkarTerm.PATH_ORIGINS_PATTERN.publicId(),
                                TEST_SET.conceptRef(CHILD_PATH_FQN).publicId())),
                        TinkarTerm.DEVELOPMENT_PATH, Instant.parse("2020-06-01T00:00:00Z"));

        TEST_SET.write();
    }

    @AfterAll
    static void afterAll() {
        TestHelper.stopDatabase();
    }

    private static StampCalculatorWithCache calculatorOn(int pathNid, StateSet allowedStates) {
        return StampCoordinateRecord.make(allowedStates,
                StampPositionRecord.make(Long.MAX_VALUE, pathNid)).stampCalculator();
    }

    private static StampCalculatorWithCache developmentCalculator() {
        return calculatorOn(TinkarTerm.DEVELOPMENT_PATH.nid(), StateSet.ACTIVE_AND_INACTIVE);
    }

    private static int nidOf(UUID semanticUuid) {
        return EntityService.get().nidForPublicId(PublicIds.of(semanticUuid));
    }

    @Test
    @DisplayName("getDefault resolves a pattern's default by computed identity")
    void defaultResolvesByComputedIdentity() {
        Latest<SemanticEntityVersion> latestDefault =
                developmentCalculator().getDefault(TEST_SET.patternRef(P1_FQN));

        assertTrue(latestDefault.isPresent(), "the default value semantic must resolve");
        assertEquals(nidOf(p1DefaultId), latestDefault.get().nid(),
                "resolution must land on the computed single-semantic identity");
        assertEquals("parent default value", latestDefault.get().fieldValues().get(0));
    }

    @Test
    @DisplayName("getTemplate resolves a purpose's template, with and without the pattern")
    void templateResolvesForPurpose() {
        EntityProxy.Concept purpose = TEST_SET.conceptRef(PURPOSE_FQN);

        Latest<SemanticEntityVersion> byPurpose = developmentCalculator().getTemplate(purpose);
        assertTrue(byPurpose.isPresent(), "the template semantic must resolve from the purpose alone");
        assertEquals(nidOf(templateId), byPurpose.get().nid());
        assertEquals("template value", byPurpose.get().fieldValues().get(0));

        Latest<SemanticEntityVersion> byPatternAndPurpose =
                developmentCalculator().getTemplate(TEST_SET.patternRef(P1_FQN), purpose);
        assertTrue(byPatternAndPurpose.isPresent());
        assertEquals(byPurpose.get().nid(), byPatternAndPurpose.get().nid(),
                "both accessor forms must resolve the same template semantic");
    }

    @Test
    @DisplayName("version iteration excludes defaults/template semantics; chronology enumeration keeps them")
    void versionIterationExcludesDefaultsButChronologyEnumerationIncludesThem() {
        StampCalculatorWithCache calculator = developmentCalculator();
        int p1Nid = TEST_SET.patternRef(P1_FQN).nid();
        int defaultsNid = nidOf(p1DefaultId);
        int templateNid = nidOf(templateId);
        int ordinaryNid = nidOf(ordinaryId);

        MutableIntSet iterated = IntSets.mutable.empty();
        calculator.forEachSemanticVersionOfPattern(p1Nid,
                (semanticVersion, patternVersion) -> iterated.add(semanticVersion.nid()));
        assertTrue(iterated.contains(ordinaryNid), "ordinary semantics must keep surfacing");
        assertFalse(iterated.contains(defaultsNid), "the default value semantic must be excluded");
        assertFalse(iterated.contains(templateNid), "the template semantic must be excluded");

        MutableIntSet streamed = IntSets.mutable.empty();
        calculator.streamLatestVersionForPattern(p1Nid)
                .filter(Latest::isPresent)
                .forEach(latest -> streamed.add(latest.get().nid()));
        assertTrue(streamed.contains(ordinaryNid));
        assertFalse(streamed.contains(defaultsNid));
        assertFalse(streamed.contains(templateNid));

        // Chronology-level enumeration is store truth — the excluded nids are still there.
        MutableIntSet patternChronologyNids =
                IntSets.mutable.of(PrimitiveData.get().semanticNidsOfPattern(p1Nid));
        assertTrue(patternChronologyNids.contains(defaultsNid),
                "chronology enumeration for the pattern must still return the defaults semantic nid");
        assertTrue(patternChronologyNids.contains(templateNid));

        // The same boundary on the component surface: iteration over the attachment
        // concept's semantics excludes the defaults semantics, enumeration keeps them.
        int attachmentNid = DefaultsTemplateTerm.DEFAULT_VALUE_CONCEPT.nid();
        MutableIntSet componentIterated = IntSets.mutable.empty();
        calculator.forEachSemanticVersionForComponent(attachmentNid,
                (semanticVersion, entityVersion) -> componentIterated.add(semanticVersion.nid()));
        assertFalse(componentIterated.isEmpty(),
                "iteration must still present the attachment concept's ordinary semantics (its FQN description)");
        assertFalse(componentIterated.contains(defaultsNid));
        assertFalse(componentIterated.contains(nidOf(p2DefaultId)));
        MutableIntSet componentChronologyNids =
                IntSets.mutable.of(PrimitiveData.get().semanticNidsForComponent(attachmentNid));
        assertTrue(componentChronologyNids.contains(defaultsNid));
    }

    @Test
    @DisplayName("a default authored on a child path overrides the parent-path default")
    void childPathDefaultOverridesParentPathDefault() {
        int childPathNid = TEST_SET.conceptRef(CHILD_PATH_FQN).nid();
        StampCalculatorWithCache childCalculator =
                calculatorOn(childPathNid, StateSet.ACTIVE_AND_INACTIVE);

        Latest<SemanticEntityVersion> onChild = childCalculator.getDefault(TEST_SET.patternRef(P1_FQN));
        assertTrue(onChild.isPresent());
        assertEquals("child default value", onChild.get().fieldValues().get(0),
                "the version on the caller's path must override the one inherited through origins");

        Latest<SemanticEntityVersion> onParent =
                developmentCalculator().getDefault(TEST_SET.patternRef(P1_FQN));
        assertTrue(onParent.isPresent());
        assertEquals("parent default value", onParent.get().fieldValues().get(0),
                "the child-path version must be unreachable from the parent path");
    }

    @Test
    @DisplayName("retirement of a default is honored — no resurrection of the active version")
    void retirementReflectedWithoutResurrection() {
        Latest<SemanticEntityVersion> withInactive =
                developmentCalculator().getDefault(TEST_SET.patternRef(P2_FQN));
        assertTrue(withInactive.isPresent(),
                "an ACTIVE_AND_INACTIVE calculator must see the retirement version");
        assertFalse(withInactive.get().active(), "the latest version must be the inactive one");
        assertEquals("retired default value", withInactive.get().fieldValues().get(0),
                "retirement restates the prior version's fields");

        Latest<SemanticEntityVersion> activeOnly =
                calculatorOn(TinkarTerm.DEVELOPMENT_PATH.nid(), StateSet.ACTIVE)
                        .getDefault(TEST_SET.patternRef(P2_FQN));
        assertTrue(activeOnly.isAbsent(),
                "an ACTIVE-only calculator must not resurrect the earlier active default");
    }
}
