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
package dev.ikm.tinkar.integration.builder;

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
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpression;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.DefaultsTemplateTerm;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The defaults/template sugar verbs (IKE-Network/ike-issues#885) — {@code fieldDefaults},
 * {@code template}, {@code templatePurpose} — through the full loop: author through the
 * verbs into a real ephemeral store, read back through the calculator's
 * {@code getDefault}/{@code getTemplate} accessors, and confirm the verb-authored
 * semantics honor the category boundary (version-iteration exclusion). The compose-time
 * gates are exercised negatively: the module gate (live-and-die invariant — validation of
 * the declared stamp, not substitution), the complete typed tuple (arity, null, declared
 * data-type conformance), and the in-set pattern requirement.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DefaultsTemplateDslIT {

    private static final KnowledgeSet TEST_SET =
            KnowledgeSet.of("5b39f7a2-6a51-49bd-9c81-4e0cc0a6d2f4");

    private static final String PATTERN_FQN = "Verb probe pattern (Test)";
    private static final String RETIRED_PATTERN_FQN = "Verb retirement probe pattern (Test)";
    private static final String PURPOSE_FQN = "Verb probe template purpose (Test)";
    private static final String SUBJECT_FQN = "Verb probe subject (Test)";

    private static final ActiveStamp BIRTH = Stamp.active("2020-01-01T00:00:00Z",
            TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);
    private static final ActiveStamp SUPPORT = Stamp.active("2020-02-01T00:00:00Z",
            TinkarTerm.USER, DefaultsTemplateTerm.DEFAULTS_AND_TEMPLATES_MODULE,
            TinkarTerm.DEVELOPMENT_PATH);
    private static final ActiveStamp SUPPORT_LATER = Stamp.active("2020-03-01T00:00:00Z",
            TinkarTerm.USER, DefaultsTemplateTerm.DEFAULTS_AND_TEMPLATES_MODULE,
            TinkarTerm.DEVELOPMENT_PATH);

    /** Identity the fieldDefaults verb must compute for the probe pattern's default. */
    private static UUID defaultId;
    /** Identity the template verb must compute for the (pattern, purpose) pair. */
    private static UUID templateId;

    @BeforeAll
    static void composeAndWrite() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);

        defaultId = UuidT5Generator.singleSemanticUuid(
                TEST_SET.patternRef(PATTERN_FQN).publicId(),
                DefaultsTemplateTerm.DEFAULT_VALUE_CONCEPT.publicId());
        templateId = UuidT5Generator.singleSemanticUuid(
                TEST_SET.patternRef(PATTERN_FQN).publicId(),
                TEST_SET.conceptRef(PURPOSE_FQN).publicId());

        // The seam concepts, minted with the identities tinkar-core declares — the same
        // declared-identity discipline DefaultsTemplateCalculatorIT uses.
        TEST_SET.concept("Default value concept (IkeFoundation)",
                DefaultsTemplateTerm.DEFAULT_VALUE_CONCEPT.publicId()).at(BIRTH);
        TEST_SET.concept("Template concept (IkeFoundation)",
                DefaultsTemplateTerm.TEMPLATE_CONCEPT.publicId()).at(BIRTH);
        TEST_SET.concept("Defaults and templates module (IkeFoundation)",
                DefaultsTemplateTerm.DEFAULTS_AND_TEMPLATES_MODULE.publicId()).at(BIRTH);

        // Self-minted pattern metadata and a subject to use as a concept-field value.
        TEST_SET.concept("Verb probe meaning (Test)").at(BIRTH);
        TEST_SET.concept("Verb probe purpose (Test)").at(BIRTH);
        TEST_SET.concept("Verb text field meaning (Test)").at(BIRTH);
        TEST_SET.concept("Verb concept field meaning (Test)").at(BIRTH);
        TEST_SET.concept("Verb field purpose (Test)").at(BIRTH);
        TEST_SET.concept(SUBJECT_FQN).at(BIRTH);

        TEST_SET.pattern(PATTERN_FQN).at(BIRTH)
                .meaning(TEST_SET.conceptRef("Verb probe meaning (Test)"))
                .purpose(TEST_SET.conceptRef("Verb probe purpose (Test)"))
                .field(TEST_SET.conceptRef("Verb text field meaning (Test)"),
                        TEST_SET.conceptRef("Verb field purpose (Test)"), TinkarTerm.STRING)
                .field(TEST_SET.conceptRef("Verb concept field meaning (Test)"),
                        TEST_SET.conceptRef("Verb field purpose (Test)"), TinkarTerm.CONCEPT_FIELD);
        TEST_SET.pattern(RETIRED_PATTERN_FQN).at(BIRTH)
                .meaning(TEST_SET.conceptRef("Verb probe meaning (Test)"))
                .purpose(TEST_SET.conceptRef("Verb probe purpose (Test)"))
                .field(TEST_SET.conceptRef("Verb text field meaning (Test)"),
                        TEST_SET.conceptRef("Verb field purpose (Test)"), TinkarTerm.STRING);

        // The purpose: minted through the verb, two scopes — the isA parentage under the
        // Template concept must be stated once, in the birth scope only.
        TEST_SET.templatePurpose(PURPOSE_FQN)
                .at(SUPPORT).synonym("Verb probe purpose")
                .at(SUPPORT_LATER).definition("Identifies the probe templates.");

        // The tuples: authored through the verbs — computed identity, fixed attachment,
        // complete typed tuple, module-validated stamp.
        TEST_SET.fieldDefaults(PATTERN_FQN)
                .at(SUPPORT).values("UNINITIALIZED", TEST_SET.conceptRef(SUBJECT_FQN));
        TEST_SET.template(PATTERN_FQN, PURPOSE_FQN)
                .at(SUPPORT).values("Template text", TEST_SET.conceptRef(SUBJECT_FQN));

        // A default authored then retired through the verb's retirement scope.
        TEST_SET.fieldDefaults(RETIRED_PATTERN_FQN)
                .at(SUPPORT).values("short-lived default")
                .at(Stamp.inactive("2020-04-01T00:00:00Z", TinkarTerm.USER,
                        DefaultsTemplateTerm.DEFAULTS_AND_TEMPLATES_MODULE,
                        TinkarTerm.DEVELOPMENT_PATH))
                .retire();

        TEST_SET.write();
    }

    @AfterAll
    static void afterAll() {
        TestHelper.stopDatabase();
    }

    private static StampCalculatorWithCache calculator(StateSet allowedStates) {
        return StampCoordinateRecord.make(allowedStates,
                StampPositionRecord.make(Long.MAX_VALUE, TinkarTerm.DEVELOPMENT_PATH.nid()))
                .stampCalculator();
    }

    private static int nidOf(UUID semanticUuid) {
        return EntityService.get().nidForPublicId(PublicIds.of(semanticUuid));
    }

    @Test
    @DisplayName("a default authored through fieldDefaults resolves via getDefault")
    void fieldDefaultsRoundTripsThroughGetDefault() {
        Latest<SemanticEntityVersion> latestDefault = calculator(StateSet.ACTIVE)
                .getDefault(TEST_SET.patternRef(PATTERN_FQN));

        assertTrue(latestDefault.isPresent(), "the verb-authored default must resolve");
        assertEquals(nidOf(defaultId), latestDefault.get().nid(),
                "the verb must derive the stock single-semantic identity — never a named one");
        assertEquals("UNINITIALIZED", latestDefault.get().fieldValues().get(0));
        EntityFacade conceptValue = assertInstanceOf(EntityFacade.class,
                latestDefault.get().fieldValues().get(1),
                "a concept-field value must read back as an entity reference");
        assertEquals(TEST_SET.conceptRef(SUBJECT_FQN).nid(), conceptValue.nid());
        assertEquals(DefaultsTemplateTerm.DEFAULT_VALUE_CONCEPT.nid(),
                latestDefault.get().chronology().referencedComponentNid(),
                "the attachment is fixed: the referenced component is the Default value concept");
    }

    @Test
    @DisplayName("a template authored through template(pattern, purpose) resolves via getTemplate")
    void templateRoundTripsThroughGetTemplate() {
        Latest<SemanticEntityVersion> template = calculator(StateSet.ACTIVE)
                .getTemplate(TEST_SET.patternRef(PATTERN_FQN), TEST_SET.conceptRef(PURPOSE_FQN));

        assertTrue(template.isPresent(), "the verb-authored template must resolve by (pattern, purpose)");
        assertEquals(nidOf(templateId), template.get().nid());
        assertEquals("Template text", template.get().fieldValues().get(0));
        assertEquals(TEST_SET.conceptRef(PURPOSE_FQN).nid(),
                template.get().chronology().referencedComponentNid(),
                "the attachment is fixed: the referenced component is the purpose concept");
    }

    @Test
    @DisplayName("templatePurpose states the isA parentage under the Template concept, once")
    void templatePurposeParentsUnderTemplateConcept() {
        int purposeNid = TEST_SET.conceptRef(PURPOSE_FQN).nid();
        int[] axiomNids = EntityService.get().semanticNidsForComponentOfPattern(
                purposeNid, TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid());
        assertEquals(1, axiomNids.length, "the purpose must carry its stated-axiom semantic");

        Latest<SemanticEntityVersion> latestAxioms =
                calculator(StateSet.ACTIVE).latest(axiomNids[0]);
        assertTrue(latestAxioms.isPresent());
        DiTreeEntity diTree = assertInstanceOf(DiTreeEntity.class,
                latestAxioms.get().fieldValues().get(0));
        boolean parented = new LogicalExpression(diTree)
                .nodesOfType(LogicalAxiom.Atom.ConceptAxiom.class).stream()
                .anyMatch(axiom -> axiom.concept().nid() == DefaultsTemplateTerm.TEMPLATE_CONCEPT.nid());
        assertTrue(parented, "the verb must state isA(Template concept) — a purpose cannot be"
                + " minted detached from the template taxonomy");

        assertEquals(1, EntityService.get().getEntityFast(axiomNids[0]).versions().size(),
                "the parentage is stated in the birth scope only — a resumed scope must not restate it");
    }

    @Test
    @DisplayName("verb-authored tuples honor the category boundary: excluded from version iteration")
    void verbAuthoredTuplesExcludedFromVersionIteration() {
        int patternNid = TEST_SET.patternRef(PATTERN_FQN).nid();
        MutableIntSet iterated = IntSets.mutable.empty();
        calculator(StateSet.ACTIVE_AND_INACTIVE).forEachSemanticVersionOfPattern(patternNid,
                (semanticVersion, patternVersion) -> iterated.add(semanticVersion.nid()));
        assertFalse(iterated.contains(nidOf(defaultId)),
                "the default value semantic must be excluded from version iteration");
        assertFalse(iterated.contains(nidOf(templateId)),
                "the template semantic must be excluded from version iteration");

        MutableIntSet chronologyNids =
                IntSets.mutable.of(PrimitiveData.get().semanticNidsOfPattern(patternNid));
        assertTrue(chronologyNids.contains(nidOf(defaultId)),
                "chronology enumeration is store truth — the nids are still there");
        assertTrue(chronologyNids.contains(nidOf(templateId)));
    }

    @Test
    @DisplayName("a default retired through the verb's retirement scope stays retired")
    void retiredDefaultStaysRetired() {
        Latest<SemanticEntityVersion> activeOnly = calculator(StateSet.ACTIVE)
                .getDefault(TEST_SET.patternRef(RETIRED_PATTERN_FQN));
        assertTrue(activeOnly.isAbsent(),
                "an ACTIVE-only calculator must not resurrect the earlier active default");

        Latest<SemanticEntityVersion> withInactive = calculator(StateSet.ACTIVE_AND_INACTIVE)
                .getDefault(TEST_SET.patternRef(RETIRED_PATTERN_FQN));
        assertTrue(withInactive.isPresent());
        assertFalse(withInactive.get().active(), "the latest version must be the retirement");
        assertEquals("short-lived default", withInactive.get().fieldValues().get(0),
                "retirement restates the prior version's fields");
    }

    // ------------------------------------------------------------ compose-time gates

    /** A fresh, never-written set with a two-field pattern, for the negative gates. */
    private static KnowledgeSet gateProbeSet(String setUuid) {
        KnowledgeSet set = KnowledgeSet.of(setUuid);
        set.concept("Gate meaning (Test)").at(BIRTH);
        set.concept("Gate purpose (Test)").at(BIRTH);
        set.concept("Gate text field meaning (Test)").at(BIRTH);
        set.concept("Gate concept field meaning (Test)").at(BIRTH);
        set.concept("Gate field purpose (Test)").at(BIRTH);
        set.pattern("Gate pattern (Test)").at(BIRTH)
                .meaning(set.conceptRef("Gate meaning (Test)"))
                .purpose(set.conceptRef("Gate purpose (Test)"))
                .field(set.conceptRef("Gate text field meaning (Test)"),
                        set.conceptRef("Gate field purpose (Test)"), TinkarTerm.STRING)
                .field(set.conceptRef("Gate concept field meaning (Test)"),
                        set.conceptRef("Gate field purpose (Test)"), TinkarTerm.CONCEPT_FIELD);
        return set;
    }

    @Test
    @DisplayName("the module gate: a stamp outside the Defaults module fails compose, loudly")
    void moduleGateRejectsOtherModules() {
        KnowledgeSet set = gateProbeSet("e0d7c9b4-3f2a-45e8-9d16-8a5b2c7f4e21");

        IllegalArgumentException onDefaults = assertThrows(IllegalArgumentException.class,
                () -> set.fieldDefaults("Gate pattern (Test)").at(BIRTH));
        assertTrue(onDefaults.getMessage().contains("Defaults and templates module"),
                "the failure must name the violated invariant: " + onDefaults.getMessage());
        assertTrue(onDefaults.getMessage().contains("live-and-die"),
                "the failure must name the violated invariant: " + onDefaults.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> set.template("Gate pattern (Test)", "Gate purpose concept (Test)").at(BIRTH));
        assertThrows(IllegalArgumentException.class,
                () -> set.templatePurpose("Gate purpose concept (Test)").at(BIRTH));
    }

    @Test
    @DisplayName("the complete-tuple gate: wrong arity, null field, and wrong type fail compose")
    void completeTupleGateRejectsMalformedTuples() {
        KnowledgeSet set = gateProbeSet("f4a81d26-95c3-4b07-a3e9-1c6d0b8e5a73");
        EntityProxy.Concept subject = set.conceptRef("Gate meaning (Test)");

        IllegalArgumentException arity = assertThrows(IllegalArgumentException.class,
                () -> set.fieldDefaults("Gate pattern (Test)").at(SUPPORT).values("only one"));
        assertTrue(arity.getMessage().contains("complete tuple"),
                "arity failure must state the completeness requirement: " + arity.getMessage());

        IllegalArgumentException nullField = assertThrows(IllegalArgumentException.class,
                () -> set.fieldDefaults("Gate pattern (Test)").at(SUPPORT).values("text", null));
        assertTrue(nullField.getMessage().contains("null"),
                "a null field must be named: " + nullField.getMessage());

        IllegalArgumentException wrongType = assertThrows(IllegalArgumentException.class,
                () -> set.fieldDefaults("Gate pattern (Test)").at(SUPPORT).values(777, subject));
        assertTrue(wrongType.getMessage().contains("declares data type"),
                "a nonconforming value must name the declared data type: " + wrongType.getMessage());

        IllegalArgumentException templateType = assertThrows(IllegalArgumentException.class,
                () -> set.template("Gate pattern (Test)", subject).at(SUPPORT)
                        .values("text", "not a concept"));
        assertTrue(templateType.getMessage().contains("declares data type"),
                "the template tuple shares the conformance gate: " + templateType.getMessage());
    }

    @Test
    @DisplayName("the in-set requirement: a pattern not declared in the knowledge set fails compose")
    void undeclaredPatternRejected() {
        KnowledgeSet set = gateProbeSet("a9c5e7f1-2b84-4d36-8e05-7f3a1d9c6b42");

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> set.fieldDefaults("Never declared pattern (Test)"));
        assertTrue(missing.getMessage().contains("requires the pattern's declaration"),
                "the failure must say what to declare: " + missing.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> set.template("Never declared pattern (Test)", "Gate purpose (Test)"));
    }
}
