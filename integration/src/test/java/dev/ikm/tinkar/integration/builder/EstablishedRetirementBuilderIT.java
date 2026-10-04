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

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.ConceptEntityVersion;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.InactiveStamp;
import dev.ikm.tinkar.entity.builder.KnowledgeSet;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the retirement scope on a concept the ledger never declared
 * (IKE-Network/ike-issues#1130). A first knowledge set plays the base: it births three
 * concepts with declared identities, each with a fully qualified name, a stated
 * definition, and a base-model membership. A second knowledge set plays the ledger: it
 * opens the base concepts at an inactive stamp, without a birth scope, and retires what
 * the base holds. Real ephemeral store, one lifecycle — the integration module forks a
 * fresh JVM per test class.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EstablishedRetirementBuilderIT {

    private static final KnowledgeSet BASE_SET =
            KnowledgeSet.of("4d2c8e1a-7f3b-5a6c-9d0e-1b2c3d4e5f60");
    private static final KnowledgeSet LEDGER_SET =
            KnowledgeSet.of("5e3d9f2b-8a4c-5b7d-8e1f-2c3d4e5f6a71");

    // ---- The base's established identities. ----
    private static final PublicId RETIRED_ID =
            PublicIds.of(UUID.fromString("9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d"));
    private static final PublicId RETIRED_FQN_ID =
            PublicIds.of(UUID.fromString("8b7c6d5e-4f3a-4b2c-9d1e-0f9a8b7c6d5e"));
    private static final PublicId RETIRED_AXIOMS_ID =
            PublicIds.of(UUID.fromString("7c6d5e4f-3a2b-4c1d-8e0f-9a8b7c6d5e4f"));
    private static final PublicId RETIRED_MEMBERSHIP_ID =
            PublicIds.of(UUID.fromString("6d5e4f3a-2b1c-4d0e-9f8a-8b7c6d5e4f3a"));

    private static final PublicId KEPT_ID =
            PublicIds.of(UUID.fromString("5e4f3a2b-1c0d-4e9f-8a7b-7c6d5e4f3a2b"));
    private static final PublicId KEPT_FQN_ID =
            PublicIds.of(UUID.fromString("4f3a2b1c-0d9e-4f8a-9b6c-6d5e4f3a2b1c"));
    private static final PublicId KEPT_AXIOMS_ID =
            PublicIds.of(UUID.fromString("3a2b1c0d-9e8f-4a7b-8c5d-5e4f3a2b1c0d"));

    private static final PublicId OTHER_ID =
            PublicIds.of(UUID.fromString("2b1c0d9e-8f7a-4b6c-9d4e-4f3a2b1c0d9e"));
    private static final PublicId OTHER_FQN_ID =
            PublicIds.of(UUID.fromString("1c0d9e8f-7a6b-4c5d-8e3f-3a2b1c0d9e8f"));

    private static final PublicId UNKNOWN_ID =
            PublicIds.of(UUID.fromString("0d9e8f7a-6b5c-4d4e-9f2a-2b1c0d9e8f7a"));

    private static ActiveStamp baseStamp;
    private static ActiveStamp birth;
    private static InactiveStamp retirement;

    @BeforeAll
    static void composeAndWrite() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);

        baseStamp = Stamp.active("2020-01-01T00:00:00Z",
                TinkarTerm.USER, TinkarTerm.PRIMORDIAL_MODULE, TinkarTerm.PRIMORDIAL_PATH);
        birth = Stamp.active("2026-07-15T00:00:00Z",
                TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);
        retirement = Stamp.inactive("2026-09-01T00:00:00Z",
                TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);

        // The base: three established concepts, each with its own names and definition.
        baseConcept("Retired kind (Test)", RETIRED_ID, RETIRED_FQN_ID, RETIRED_AXIOMS_ID, TinkarTerm.USER)
                .semantic(TinkarTerm.TINKAR_BASE_MODEL_COMPONENT_PATTERN, RETIRED_MEMBERSHIP_ID);
        baseConcept("Kept kind (Test)", KEPT_ID, KEPT_FQN_ID, KEPT_AXIOMS_ID, TinkarTerm.USER);
        baseConcept("Other kind (Test)", OTHER_ID, OTHER_FQN_ID,
                PublicIds.of(UUID.fromString("f9e8d7c6-b5a4-4392-8170-6f5e4d3c2b1a")), TinkarTerm.USER);
        BASE_SET.write();

        // The ledger: retirement scopes on concepts it never declared.
        LEDGER_SET.concept("Retired kind (Test)", RETIRED_ID).at(retirement)
                .retire()
                .retireStatedAxioms(RETIRED_AXIOMS_ID,
                        leb -> leb.NecessarySet(leb.And(leb.ConceptAxiom(TinkarTerm.USER))))
                .retireSemantic(TinkarTerm.TINKAR_BASE_MODEL_COMPONENT_PATTERN, RETIRED_MEMBERSHIP_ID);
        // Semantics only: the concept's own versions stay as the base has them.
        LEDGER_SET.concept("Kept kind (Test)", KEPT_ID).at(retirement)
                .retireStatedAxioms(KEPT_AXIOMS_ID,
                        leb -> leb.NecessarySet(leb.And(leb.ConceptAxiom(TinkarTerm.USER))));
        // A born concept retiring its own definition at a later inactive stamp.
        LEDGER_SET.concept("Born kind (Test)").at(birth)
                .synonym("Born")
                .isA(TinkarTerm.MODEL_CONCEPT)
                .at(retirement)
                .retireStatedAxioms(leb -> leb.NecessarySet(leb.And(leb.ConceptAxiom(TinkarTerm.MODEL_CONCEPT))));
        LEDGER_SET.write();
    }

    private static dev.ikm.tinkar.entity.builder.ConceptBuilder.ActiveScope baseConcept(
            String fqn, PublicId conceptId, PublicId fqnId, PublicId axiomsId, ConceptFacade parent) {
        return BASE_SET.concept(fqn, conceptId).at(baseStamp)
                .semantic(TinkarTerm.DESCRIPTION_PATTERN, fqnId,
                        TinkarTerm.ENGLISH_LANGUAGE, fqn,
                        TinkarTerm.DESCRIPTION_NOT_CASE_SENSITIVE,
                        TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE)
                .statedAxioms(axiomsId, leb -> leb.NecessarySet(leb.And(leb.ConceptAxiom(parent))));
    }

    @AfterAll
    static void stop() {
        TestHelper.stopDatabase();
    }

    @Test
    @DisplayName("The retired concept gains exactly one version, inactive, at the retirement stamp")
    void retiredConceptGainsOneInactiveVersion() {
        ConceptEntity<? extends ConceptEntityVersion> concept = concept(RETIRED_ID);
        assertEquals(2, concept.versions().size(), "the base's version and the retirement");
        List<? extends ConceptEntityVersion> inactive = concept.versions().stream()
                .filter(version -> version.stamp().state() == State.INACTIVE)
                .toList();
        assertEquals(1, inactive.size(), "one inactive version");
        assertEquals(PrimitiveData.nid(retirement.publicId()), inactive.getFirst().stampNid(),
                "bound to the retirement stamp");
    }

    @Test
    @DisplayName("The retired definition carries the restated expression under the established identity")
    void retiredAxiomsCarryTheRestatedExpression() {
        SemanticEntity<? extends SemanticEntityVersion> axioms = semantic(RETIRED_AXIOMS_ID);
        assertEquals(RETIRED_ID.asUuidArray()[0], PrimitiveData.publicId(axioms.referencedComponentNid()).asUuidArray()[0],
                "the base's axiom semantic, on the base's concept");
        assertEquals(2, axioms.versions().size(), "the base's version and the retirement");
        SemanticEntityVersion inactive = onlyInactive(axioms);
        DiTreeEntity tree = (DiTreeEntity) inactive.fieldValues().get(0);
        assertTrue(namesConcept(tree, TinkarTerm.USER.nid()), "the retired version restates the parent");
    }

    @Test
    @DisplayName("A retired membership carries no fields")
    void retiredMembershipHasNoFields() {
        SemanticEntity<? extends SemanticEntityVersion> membership = semantic(RETIRED_MEMBERSHIP_ID);
        assertEquals(2, membership.versions().size(), "the base's version and the retirement");
        assertEquals(0, onlyInactive(membership).fieldValues().size(), "a membership declares no fields");
    }

    @Test
    @DisplayName("No description is written for a concept opened by a retirement scope: its names stay with the base")
    void noDescriptionIsWritten() {
        List<SemanticEntity<SemanticEntityVersion>> descriptions = EntityService.get().semanticsForComponentOfPattern(
                PrimitiveData.nid(RETIRED_ID), TinkarTerm.DESCRIPTION_PATTERN.nid()).toList();
        assertEquals(1, descriptions.size(), "the base's fully qualified name only");
        assertEquals(1, descriptions.getFirst().versions().size(),
                "and it gained no version");
    }

    @Test
    @DisplayName("A scope that names only semantics leaves the concept's own versions as the base has them")
    void semanticsOnlyScopeLeavesTheConceptAsTheBaseHasIt() {
        assertEquals(1, concept(KEPT_ID).versions().size(), "the base's version only");
        SemanticEntity<? extends SemanticEntityVersion> axioms = semantic(KEPT_AXIOMS_ID);
        assertEquals(2, axioms.versions().size(), "the definition was retired all the same");
        assertEquals(State.INACTIVE, onlyInactive(axioms).stamp().state());
    }

    @Test
    @DisplayName("A born concept retires its own definition at a later inactive stamp")
    void bornConceptRetiresItsAxiomsLater() {
        int conceptNid = LEDGER_SET.conceptRef("Born kind (Test)").nid();
        List<SemanticEntity<SemanticEntityVersion>> axioms = EntityService.get().semanticsForComponentOfPattern(conceptNid,
                TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid()).toList();
        assertEquals(1, axioms.size(), "one stated-axiom semantic");
        SemanticEntity<? extends SemanticEntityVersion> semantic = axioms.getFirst();
        assertEquals(2, semantic.versions().size(), "the birth statement and the retirement");
        assertEquals(State.INACTIVE, onlyInactive(semantic).stamp().state());
    }

    @Test
    @DisplayName("A derived identity cannot open a retirement scope: nothing established to retire")
    void derivedIdentityCannotOpenARetirementScope() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> LEDGER_SET.concept("Never born kind (Test)").at(retirement));
        assertTrue(thrown.getMessage().contains("concept(fqn, uuid)"), thrown.getMessage());
    }

    @Test
    @DisplayName("On a born concept, retiring an undeclared semantic identity still fails at compose time")
    void undeclaredSemanticStillRequiresDeclarationOnABornConcept() {
        assertThrows(IllegalArgumentException.class,
                () -> LEDGER_SET.concept("Born kind (Test)").at(retirement)
                        .retireSemantic(TinkarTerm.IDENTIFIER_PATTERN, UNKNOWN_ID));
    }

    @Test
    @DisplayName("The base's stated axioms are retired under their established identity, never a derived one")
    void identitylessAxiomRetirementNeedsTheEstablishedIdentity() {
        assertThrows(IllegalStateException.class,
                () -> LEDGER_SET.concept("Other kind (Test)", OTHER_ID).at(retirement)
                        .retireStatedAxioms(leb -> leb.NecessarySet(leb.And(leb.ConceptAxiom(TinkarTerm.USER)))));
    }

    @Test
    @DisplayName("One version per stamp holds in the new scope")
    void oneVersionPerStampHoldsInTheNewScope() {
        assertThrows(IllegalArgumentException.class,
                () -> LEDGER_SET.concept("Retired kind (Test)", RETIRED_ID).at(retirement).retire());
    }

    private static ConceptEntity<? extends ConceptEntityVersion> concept(PublicId id) {
        return EntityHandle.get(PrimitiveData.nid(id)).expectConcept();
    }

    private static SemanticEntity<? extends SemanticEntityVersion> semantic(PublicId id) {
        return EntityHandle.get(PrimitiveData.nid(id)).expectSemantic();
    }

    /** The one inactive version; the store re-sorts merged versions, so state, not position, finds it. */
    private static SemanticEntityVersion onlyInactive(SemanticEntity<? extends SemanticEntityVersion> semantic) {
        List<? extends SemanticEntityVersion> inactive = semantic.versions().stream()
                .filter(version -> version.stamp().state() == State.INACTIVE)
                .toList();
        assertEquals(1, inactive.size(), "exactly one inactive version");
        return inactive.getFirst();
    }

    /** Whether any concept-reference vertex of the expression names the concept. */
    private static boolean namesConcept(DiTreeEntity tree, int conceptNid) {
        for (EntityVertex vertex : tree.vertexMap()) {
            if (vertex != null && vertex.getMeaningNid() == TinkarTerm.CONCEPT_REFERENCE.nid()) {
                Object reference = vertex.propertyFast(TinkarTerm.CONCEPT_REFERENCE);
                if (reference instanceof ConceptFacade facade && facade.nid() == conceptNid) {
                    return true;
                }
            }
        }
        return false;
    }
}
