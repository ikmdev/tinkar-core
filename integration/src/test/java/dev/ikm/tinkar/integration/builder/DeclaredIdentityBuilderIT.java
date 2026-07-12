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

import dev.ikm.tinkar.common.id.IntIdList;
import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.common.id.IntIds;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.ConceptBuilder;
import dev.ikm.tinkar.entity.builder.InactiveStamp;
import dev.ikm.tinkar.entity.builder.KnowledgeSet;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the declared-identity extensions (IKE-Network/ike-issues#870):
 * declared-identity stamps (single- and multi-UUID), PublicId-typed declared component
 * identities, the generic declared-identity semantic verb with its referenced-component
 * form, declared-identity stated axioms, and the deferred FQN seeding that keeps
 * identity-exact ingest free of derived extras. Real ephemeral store, one lifecycle —
 * the integration module forks a fresh JVM per test class.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeclaredIdentityBuilderIT {

    private static final KnowledgeSet TEST_SET =
            KnowledgeSet.of("2a7b9c4e-6d1f-5e3a-8b0c-4f2d7e9a1c5b");

    // ---- Established identities, as an ingested starter set carries them. ----
    private static final PublicId CONCEPT_ID = PublicIds.of(
            UUID.fromString("8f3e2a1b-4c5d-4e6f-9a0b-1c2d3e4f5a6b"),
            UUID.fromString("7e2d1c0b-3a4b-4c5d-8e9f-0a1b2c3d4e5f"));
    private static final PublicId PATTERN_ID = PublicIds.of(
            UUID.fromString("6d1c0b9a-2938-4756-8d9e-afb0c1d2e3f4"),
            UUID.fromString("5c0b9a89-1827-4645-9cad-8e9fa0b1c2d3"));
    private static final PublicId STAMP_ID =
            PublicIds.of(UUID.fromString("4b0a9877-0716-4534-8b9c-7d8e9fa0b1c2"));
    private static final PublicId MULTI_STAMP_ID = PublicIds.of(
            UUID.fromString("3a998766-f605-4423-9a8b-6c7d8e9fa0b1"),
            UUID.fromString("29887655-e5f4-4312-898a-5b6c7d8e9fa0"));
    private static final PublicId FQN_DESCRIPTION_ID =
            PublicIds.of(UUID.fromString("18776544-d4e3-4201-987b-4a5b6c7d8e9f"));
    private static final PublicId DIALECT_ID =
            PublicIds.of(UUID.fromString("07665433-c3d2-4190-8a6b-394a5b6c7d8e"));
    private static final PublicId MEMBERSHIP_ID =
            PublicIds.of(UUID.fromString("f6554322-b2c1-4089-9b5a-28394a5b6c7d"));
    private static final PublicId IDENTIFIER_ID =
            PublicIds.of(UUID.fromString("e5443211-a1b0-4f78-8a49-172839405b6c"));
    private static final PublicId PATH_ORIGIN_ID =
            PublicIds.of(UUID.fromString("d4332100-90af-4e67-9938-061728394a5b"));
    private static final PublicId AXIOM_ID =
            PublicIds.of(UUID.fromString("c32210ff-8f9e-4d56-8827-f50617283949"));
    private static final PublicId REVISED_IDENTIFIER_ID =
            PublicIds.of(UUID.fromString("b2110fee-7e8d-4c45-9716-e4f506172838"));
    private static final PublicId PATTERN_MEMBERSHIP_ID =
            PublicIds.of(UUID.fromString("a10fedd0-6d7c-4b34-8605-d3e4f5061727"));
    private static final PublicId BARE_FQN_DESCRIPTION_ID =
            PublicIds.of(UUID.fromString("9fedcc01-5c6b-4a23-9594-c2d3e4f50616"));
    private static final PublicId TYPED_FIELDS_ID =
            PublicIds.of(UUID.fromString("8edcbb12-4b5a-4912-8483-b1c2d3e4f505"));
    private static final PublicId EXPLICIT_RETIRE_ID =
            PublicIds.of(UUID.fromString("7dcbaa23-3a49-4801-9372-a0b1c2d3e4f4"));
    private static final PublicId PATTERN_RETIRE_ID =
            PublicIds.of(UUID.fromString("6cba9934-2938-4790-8261-9fa0b1c2d3e3"));

    private static final Instant PATH_ORIGIN_TIME = Instant.parse("2020-01-01T00:00:00Z");
    private static final byte[] BYTES_FIELD = {1, 2, 3};

    private static ActiveStamp birth;
    private static ActiveStamp declaredBirth;
    private static ActiveStamp multiDeclared;
    private static ActiveStamp later;
    private static InactiveStamp retirement;

    @BeforeAll
    static void composeAndWrite() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);

        birth = Stamp.active("2026-07-15T00:00:00Z",
                TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);
        declaredBirth = Stamp.active(STAMP_ID, "2026-07-15T00:00:00Z",
                TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);
        multiDeclared = Stamp.active(MULTI_STAMP_ID, "2026-07-16T00:00:00Z",
                TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);
        later = Stamp.active("2026-07-17T00:00:00Z",
                TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);
        retirement = Stamp.inactive("2026-07-18T00:00:00Z",
                TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);

        // An ingested concept: every identity established elsewhere, nothing derived —
        // the FQN description arrives explicitly, so no derived-identity auto-seed.
        TEST_SET.concept("Ingested kind (Test)", CONCEPT_ID).at(declaredBirth)
                .semantic(TinkarTerm.DESCRIPTION_PATTERN, FQN_DESCRIPTION_ID,
                        TinkarTerm.ENGLISH_LANGUAGE, "Ingested kind (Test)",
                        TinkarTerm.DESCRIPTION_NOT_CASE_SENSITIVE,
                        TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE)
                .semanticOn(FQN_DESCRIPTION_ID, TinkarTerm.US_DIALECT_PATTERN, DIALECT_ID,
                        TinkarTerm.PREFERRED)
                .semantic(TinkarTerm.TINKAR_BASE_MODEL_COMPONENT_PATTERN, MEMBERSHIP_ID)
                .semantic(TinkarTerm.IDENTIFIER_PATTERN, IDENTIFIER_ID,
                        TinkarTerm.UNIVERSALLY_UNIQUE_IDENTIFIER, "8f3e2a1b-4c5d-4e6f-9a0b-1c2d3e4f5a6b")
                .semantic(TinkarTerm.PATH_ORIGINS_PATTERN, PATH_ORIGIN_ID,
                        TinkarTerm.DEVELOPMENT_PATH, PATH_ORIGIN_TIME)
                .statedAxioms(AXIOM_ID,
                        leb -> leb.NecessarySet(leb.And(leb.ConceptAxiom(TinkarTerm.MODEL_CONCEPT))));

        // An authored concept under a multi-UUID declared stamp: the auto-FQN seeds.
        // A later revise() records a second active concept version — the multi-version
        // chronology form an ingested history restates.
        TEST_SET.concept("Multi-stamp kind (Test)").at(multiDeclared)
                .synonym("Multi stamp")
                .at(later)
                .revise();

        // A machine emitter working PublicId-native: the FQN description's type field
        // arrives as a bare PublicId, and the suppression gate must still hold.
        TEST_SET.concept("Bare id kind (Test)",
                        PublicIds.of(UUID.fromString("5ba98845-1827-468f-9150-8e9fa0b1c2d2")))
                .at(declaredBirth)
                .semantic(TinkarTerm.DESCRIPTION_PATTERN, BARE_FQN_DESCRIPTION_ID,
                        TinkarTerm.ENGLISH_LANGUAGE.publicId(), "Bare id kind (Test)",
                        TinkarTerm.DESCRIPTION_NOT_CASE_SENSITIVE.publicId(),
                        TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.publicId());

        // Every remaining accepted field value type, through the write path once.
        TEST_SET.concept("Typed fields kind (Test)").at(birth)
                .semantic(TinkarTerm.COMMENT_PATTERN, TYPED_FIELDS_ID,
                        new java.math.BigDecimal("1.25"), BYTES_FIELD,
                        PublicIds.list.of(TinkarTerm.MODEL_CONCEPT.publicId(),
                                TinkarTerm.USER.publicId()),
                        PublicIds.set.of(TinkarTerm.DEVELOPMENT_PATH.publicId()));

        // Retirement with an explicit payload — retired versions carry field values.
        TEST_SET.concept("Explicit retire kind (Test)").at(birth)
                .semantic(TinkarTerm.IDENTIFIER_PATTERN, EXPLICIT_RETIRE_ID,
                        TinkarTerm.UNIVERSALLY_UNIQUE_IDENTIFIER, "before")
                .at(retirement)
                .retireSemantic(TinkarTerm.IDENTIFIER_PATTERN, EXPLICIT_RETIRE_ID,
                        TinkarTerm.UNIVERSALLY_UNIQUE_IDENTIFIER, "after");

        // A generic semantic versioning across scopes, then retiring by restatement.
        TEST_SET.concept("Revised semantic kind (Test)").at(birth)
                .semantic(TinkarTerm.IDENTIFIER_PATTERN, REVISED_IDENTIFIER_ID,
                        TinkarTerm.UNIVERSALLY_UNIQUE_IDENTIFIER, "first value")
                .at(later)
                .semantic(TinkarTerm.IDENTIFIER_PATTERN, REVISED_IDENTIFIER_ID,
                        TinkarTerm.UNIVERSALLY_UNIQUE_IDENTIFIER, "second value")
                .at(retirement)
                .retireSemantic(TinkarTerm.IDENTIFIER_PATTERN, REVISED_IDENTIFIER_ID);

        // An ingested pattern: multi-UUID declared identity plus a membership tag, and a
        // second declared semantic retired from the pattern's retire scope.
        TEST_SET.pattern("Ingested pattern (Test)", PATTERN_ID).at(declaredBirth)
                .meaning(TinkarTerm.MODEL_CONCEPT).purpose(TinkarTerm.USER)
                .semantic(TinkarTerm.TINKAR_BASE_MODEL_COMPONENT_PATTERN, PATTERN_MEMBERSHIP_ID)
                .semantic(TinkarTerm.IDENTIFIER_PATTERN, PATTERN_RETIRE_ID,
                        TinkarTerm.UNIVERSALLY_UNIQUE_IDENTIFIER, "pattern identifier")
                .at(retirement)
                .retireSemantic(TinkarTerm.IDENTIFIER_PATTERN, PATTERN_RETIRE_ID);

        TEST_SET.write();
    }

    @AfterAll
    static void stop() {
        TestHelper.stopDatabase();
    }

    @Test
    @DisplayName("A declared-identity stamp is written with its established identity, not the tuple derivation")
    void declaredIdentityStamp() {
        int stampNid = PrimitiveData.nid(STAMP_ID);
        StampEntity<?> stamp = Entity.getStamp(stampNid);
        assertEquals(STAMP_ID.asUuidArray()[0], stamp.publicId().asUuidArray()[0]);
        UUID tupleDerived = Stamp.stampUuid(State.ACTIVE, declaredBirth.time(),
                TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);
        assertNotEquals(tupleDerived, stamp.publicId().asUuidArray()[0],
                "the declared identity must win over the tuple derivation");
        assertEquals(State.ACTIVE, stamp.state());
        assertEquals(declaredBirth.time(), stamp.time());
        assertEquals(TinkarTerm.USER.nid(), stamp.authorNid());
        assertEquals(TinkarTerm.DEVELOPMENT_MODULE.nid(), stamp.moduleNid());
        assertEquals(TinkarTerm.DEVELOPMENT_PATH.nid(), stamp.pathNid());
    }

    @Test
    @DisplayName("A tuple-derived stamp still derives: declared identity is opt-in")
    void tupleDerivedStampUnchanged() {
        UUID tupleDerived = Stamp.stampUuid(State.ACTIVE, birth.time(),
                TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);
        assertEquals(tupleDerived, birth.publicId().asUuidArray()[0]);
        StampEntity<?> stamp = Entity.getStamp(PrimitiveData.nid(PublicIds.of(tupleDerived)));
        assertEquals(tupleDerived, stamp.publicId().asUuidArray()[0]);
    }

    @Test
    @DisplayName("A multi-UUID declared stamp resolves by every UUID to one stamp entity")
    void multiUuidDeclaredStamp() {
        UUID[] uuids = MULTI_STAMP_ID.asUuidArray();
        int byFirst = PrimitiveData.nid(PublicIds.of(uuids[0]));
        int bySecond = PrimitiveData.nid(PublicIds.of(uuids[1]));
        assertEquals(byFirst, bySecond, "every declared UUID registers to the one nid");
        StampEntity<?> stamp = Entity.getStamp(byFirst);
        assertEquals(2, stamp.publicId().uuidCount());
    }

    @Test
    @DisplayName("A multi-UUID declared concept identity is adopted in full")
    void multiUuidConceptIdentity() {
        UUID[] uuids = CONCEPT_ID.asUuidArray();
        int byFirst = PrimitiveData.nid(PublicIds.of(uuids[0]));
        int bySecond = PrimitiveData.nid(PublicIds.of(uuids[1]));
        assertEquals(byFirst, bySecond);
        Entity<?> concept = EntityHandle.get(byFirst).expectEntity();
        assertEquals(2, concept.publicId().uuidCount());
        assertEquals(uuids[0], concept.publicId().asUuidArray()[0], "the primordial UUID leads");
        assertEquals(1, concept.versions().size());
    }

    @Test
    @DisplayName("An explicitly declared FQN description suppresses the derived auto-seed — no extra semantics")
    void explicitFqnSuppressesAutoSeed() {
        int conceptNid = PrimitiveData.nid(CONCEPT_ID);
        List<SemanticEntity<SemanticEntityVersion>> attached = semanticsFor(conceptNid);
        // Description, membership, identifier, path-origin, axiom — and nothing derived.
        assertEquals(5, attached.size(),
                "an ingested concept carries exactly its declared semantics: " + attached);
        long descriptions = attached.stream()
                .filter(semantic -> semantic.patternNid() == TinkarTerm.DESCRIPTION_PATTERN.nid())
                .count();
        assertEquals(1, descriptions, "one description — the declared FQN, no derived twin");
    }

    @Test
    @DisplayName("The generic description semantic carries its declared identity and verbatim fields")
    void genericDescriptionSemantic() {
        SemanticEntity<SemanticEntityVersion> description = semanticFor(FQN_DESCRIPTION_ID);
        assertEquals(TinkarTerm.DESCRIPTION_PATTERN.nid(), description.patternNid());
        assertEquals(PrimitiveData.nid(CONCEPT_ID), description.referencedComponentNid());
        SemanticEntityVersion version = description.versions().getFirst();
        assertEquals(TinkarTerm.ENGLISH_LANGUAGE.nid(),
                ((EntityFacade) version.fieldValues().get(0)).nid());
        assertEquals("Ingested kind (Test)", version.fieldValues().get(1));
        assertEquals(TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid(),
                ((EntityFacade) version.fieldValues().get(3)).nid());
    }

    @Test
    @DisplayName("semanticOn references another declared component: the dialect rides its description")
    void semanticOnReferencesDeclaredComponent() {
        SemanticEntity<SemanticEntityVersion> dialect = semanticFor(DIALECT_ID);
        assertEquals(TinkarTerm.US_DIALECT_PATTERN.nid(), dialect.patternNid());
        assertEquals(PrimitiveData.nid(FQN_DESCRIPTION_ID), dialect.referencedComponentNid(),
                "the dialect references the declared description, not the concept");
    }

    @Test
    @DisplayName("A membership semantic declares no fields; an identifier declares component + string")
    void membershipAndIdentifierSemantics() {
        SemanticEntity<SemanticEntityVersion> membership = semanticFor(MEMBERSHIP_ID);
        assertEquals(TinkarTerm.TINKAR_BASE_MODEL_COMPONENT_PATTERN.nid(), membership.patternNid());
        assertEquals(0, membership.versions().getFirst().fieldValues().size());

        SemanticEntity<SemanticEntityVersion> identifier = semanticFor(IDENTIFIER_ID);
        assertEquals(TinkarTerm.IDENTIFIER_PATTERN.nid(), identifier.patternNid());
        assertEquals("8f3e2a1b-4c5d-4e6f-9a0b-1c2d3e4f5a6b",
                identifier.versions().getFirst().fieldValues().get(1));
    }

    @Test
    @DisplayName("Instant field values round-trip through the store")
    void instantFieldRoundTrip() {
        SemanticEntity<SemanticEntityVersion> pathOrigin = semanticFor(PATH_ORIGIN_ID);
        assertEquals(PATH_ORIGIN_TIME, pathOrigin.versions().getFirst().fieldValues().get(1));
    }

    @Test
    @DisplayName("Declared-identity stated axioms adopt the established semantic identity")
    void declaredIdentityAxioms() {
        SemanticEntity<SemanticEntityVersion> axioms = semanticFor(AXIOM_ID);
        assertEquals(TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid(), axioms.patternNid());
        assertEquals(PrimitiveData.nid(CONCEPT_ID), axioms.referencedComponentNid());
        assertInstanceOf(DiTreeEntity.class, axioms.versions().getFirst().fieldValues().get(0));
    }

    @Test
    @DisplayName("An authored concept still auto-seeds its FQN description — the convenience default holds")
    void autoSeedStillHoldsForAuthoredContent() {
        int conceptNid = PrimitiveData.nid(
                PublicIds.of(TEST_SET.uuidFor("Multi-stamp kind (Test)")));
        long descriptions = semanticsFor(conceptNid).stream()
                .filter(semantic -> semantic.patternNid() == TinkarTerm.DESCRIPTION_PATTERN.nid())
                .count();
        assertEquals(2, descriptions, "the derived FQN plus the authored synonym");
    }

    @Test
    @DisplayName("A generic semantic versions across scopes and retires by restatement")
    void genericSemanticLifecycle() {
        SemanticEntity<SemanticEntityVersion> identifier = semanticFor(REVISED_IDENTIFIER_ID);
        assertEquals(3, identifier.versions().size());
        // Resolve the retirement by stamp state, not list position — the store re-sorts
        // merged versions by bytes, never by time.
        List<SemanticEntityVersion> inactive = identifier.versions().stream()
                .filter(version -> version.stamp().state() == State.INACTIVE)
                .toList();
        assertEquals(1, inactive.size());
        assertEquals("second value", inactive.getFirst().fieldValues().get(1),
                "retirement restates the prior version's fields");
    }

    @Test
    @DisplayName("A multi-UUID declared pattern identity is adopted in full, memberships attached")
    void multiUuidPatternIdentity() {
        UUID[] uuids = PATTERN_ID.asUuidArray();
        assertEquals(PrimitiveData.nid(PublicIds.of(uuids[0])), PrimitiveData.nid(PublicIds.of(uuids[1])));
        Entity<?> pattern = EntityHandle.get(PrimitiveData.nid(PATTERN_ID)).expectEntity();
        assertEquals(2, pattern.publicId().uuidCount());
        SemanticEntity<SemanticEntityVersion> membership = semanticFor(PATTERN_MEMBERSHIP_ID);
        assertEquals(PrimitiveData.nid(PATTERN_ID), membership.referencedComponentNid());
    }

    @Test
    @DisplayName("Axiom graphs replay byte-stable: vertex UUIDs are deterministic across writes")
    void axiomVertexDeterminism() {
        List<UUID> before = axiomVertexUuids();
        TEST_SET.write();
        assertEquals(before, axiomVertexUuids(),
                "replaying the ledger must reproduce the same axiom graph identity —"
                        + " the store merge replaces same-stamp payloads silently otherwise");
    }

    @Test
    @DisplayName("A fresh session restating adopted content over the populated store merges as a no-op")
    void freshSessionRestatement() {
        List<UUID> vertexUuidsBefore = axiomVertexUuids();
        int semanticsBefore = semanticsFor(PrimitiveData.nid(CONCEPT_ID)).size();

        KnowledgeSet freshSession = KnowledgeSet.of("2a7b9c4e-6d1f-5e3a-8b0c-4f2d7e9a1c5b");
        freshSession.concept("Ingested kind (Test)", CONCEPT_ID).at(declaredBirth)
                .semantic(TinkarTerm.DESCRIPTION_PATTERN, FQN_DESCRIPTION_ID,
                        TinkarTerm.ENGLISH_LANGUAGE, "Ingested kind (Test)",
                        TinkarTerm.DESCRIPTION_NOT_CASE_SENSITIVE,
                        TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE)
                .semanticOn(FQN_DESCRIPTION_ID, TinkarTerm.US_DIALECT_PATTERN, DIALECT_ID,
                        TinkarTerm.PREFERRED)
                .semantic(TinkarTerm.TINKAR_BASE_MODEL_COMPONENT_PATTERN, MEMBERSHIP_ID)
                .semantic(TinkarTerm.IDENTIFIER_PATTERN, IDENTIFIER_ID,
                        TinkarTerm.UNIVERSALLY_UNIQUE_IDENTIFIER, "8f3e2a1b-4c5d-4e6f-9a0b-1c2d3e4f5a6b")
                .semantic(TinkarTerm.PATH_ORIGINS_PATTERN, PATH_ORIGIN_ID,
                        TinkarTerm.DEVELOPMENT_PATH, PATH_ORIGIN_TIME)
                .statedAxioms(AXIOM_ID,
                        leb -> leb.NecessarySet(leb.And(leb.ConceptAxiom(TinkarTerm.MODEL_CONCEPT))));
        freshSession.write();

        assertEquals(semanticsBefore, semanticsFor(PrimitiveData.nid(CONCEPT_ID)).size(),
                "restating the same declarations adds nothing");
        assertEquals(vertexUuidsBefore, axiomVertexUuids(),
                "a fresh session reproduces the same axiom graph identity");
        assertEquals(1,
                EntityHandle.get(PrimitiveData.nid(CONCEPT_ID)).expectEntity().versions().size());
    }

    @Test
    @DisplayName("A bare-PublicId FQN type field still suppresses the derived auto-seed")
    void bareIdFqnSuppression() {
        SemanticEntity<SemanticEntityVersion> description = semanticFor(BARE_FQN_DESCRIPTION_ID);
        assertEquals(TinkarTerm.DESCRIPTION_PATTERN.nid(), description.patternNid());
        long descriptions = semanticsFor(description.referencedComponentNid()).stream()
                .filter(semantic -> semantic.patternNid() == TinkarTerm.DESCRIPTION_PATTERN.nid())
                .count();
        assertEquals(1, descriptions, "one description — the declared FQN, no derived twin");
    }

    @Test
    @DisplayName("BigDecimal, byte[], PublicIdList, and PublicIdSet fields round-trip")
    void typedFieldsRoundTrip() {
        SemanticEntity<SemanticEntityVersion> semantic = semanticFor(TYPED_FIELDS_ID);
        SemanticEntityVersion version = semantic.versions().getFirst();
        assertEquals(new java.math.BigDecimal("1.25"), version.fieldValues().get(0));
        assertArrayEquals(BYTES_FIELD, (byte[]) version.fieldValues().get(1));
        IntIdList components = (IntIdList) version.fieldValues().get(2);
        assertEquals(2, components.size());
        assertTrue(components.contains(TinkarTerm.MODEL_CONCEPT.nid()));
        assertTrue(components.contains(TinkarTerm.USER.nid()));
        IntIdSet componentSet = (IntIdSet) version.fieldValues().get(3);
        assertTrue(componentSet.contains(TinkarTerm.DEVELOPMENT_PATH.nid()));
    }

    @Test
    @DisplayName("Retirement carries an explicit payload when one is declared; patterns retire semantics too")
    void retirementVariants() {
        SemanticEntity<SemanticEntityVersion> explicitRetire = semanticFor(EXPLICIT_RETIRE_ID);
        List<SemanticEntityVersion> inactive = explicitRetire.versions().stream()
                .filter(version -> version.stamp().state() == State.INACTIVE)
                .toList();
        assertEquals(1, inactive.size());
        assertEquals("after", inactive.getFirst().fieldValues().get(1),
                "an explicit retirement payload is recorded verbatim");

        SemanticEntity<SemanticEntityVersion> patternRetire = semanticFor(PATTERN_RETIRE_ID);
        assertEquals(PrimitiveData.nid(PATTERN_ID), patternRetire.referencedComponentNid());
        assertEquals(2, patternRetire.versions().size());
    }

    @Test
    @DisplayName("revise() records a second active concept version")
    void conceptRevision() {
        Entity<?> concept = EntityHandle.get(PrimitiveData.nid(
                PublicIds.of(TEST_SET.uuidFor("Multi-stamp kind (Test)")))).expectEntity();
        assertEquals(2, concept.versions().size(), "birth plus one revision");
    }

    @Test
    @DisplayName("Replay is idempotent: writing the session again changes nothing")
    void replayIdempotence() {
        SemanticEntity<SemanticEntityVersion> before = semanticFor(REVISED_IDENTIFIER_ID);
        int conceptVersionsBefore =
                EntityHandle.get(PrimitiveData.nid(CONCEPT_ID)).expectEntity().versions().size();
        TEST_SET.write();
        assertEquals(before.versions().size(), semanticFor(REVISED_IDENTIFIER_ID).versions().size());
        assertEquals(conceptVersionsBefore,
                EntityHandle.get(PrimitiveData.nid(CONCEPT_ID)).expectEntity().versions().size());
        assertEquals(5, semanticsFor(PrimitiveData.nid(CONCEPT_ID)).size(),
                "no derived FQN appears on re-write either");
    }

    @Test
    @DisplayName("The session surfaces declared identities in full: declarations and references")
    void declaredIdentitySurfaces() {
        KnowledgeSet.Declaration ingested = TEST_SET.declarations().stream()
                .filter(declaration -> declaration.birthFqn().equals("Ingested kind (Test)"))
                .findFirst().orElseThrow();
        assertEquals(2, ingested.publicId().uuidCount());
        assertEquals(2, TEST_SET.conceptRef("Ingested kind (Test)").publicId().uuidCount());
    }

    @Test
    @DisplayName("The grammar rejects what the store would corrupt silently")
    void grammarRejections() {
        KnowledgeSet scratch = KnowledgeSet.of("1b2c3d4e-5f60-5182-93a4-b5c6d7e8f901");
        ActiveStamp stamp = Stamp.active("2026-07-15T00:00:00Z",
                TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);
        PublicId identity = PublicIds.of(UUID.fromString("90fedcba-5c6b-4a23-8594-c2d3e4f50617"));

        // Declared identities are never null or empty.
        assertThrows(IllegalArgumentException.class, () -> Stamp.active((PublicId) null,
                "2026-07-15T00:00:00Z", TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE,
                TinkarTerm.DEVELOPMENT_PATH));
        assertThrows(IllegalArgumentException.class,
                () -> scratch.concept("Null identity (Test)", (PublicId) null));

        // Field values: no nulls, no silently narrowed doubles, no nid-based collections.
        assertThrows(IllegalArgumentException.class, () -> scratch.concept("Null field (Test)")
                .at(stamp).semantic(TinkarTerm.IDENTIFIER_PATTERN, identity,
                        TinkarTerm.UNIVERSALLY_UNIQUE_IDENTIFIER, null));
        assertThrows(IllegalArgumentException.class, () -> scratch.concept("Double field (Test)")
                .at(stamp).semantic(TinkarTerm.IDENTIFIER_PATTERN, identity, 1.5d));
        assertThrows(IllegalArgumentException.class, () -> scratch.concept("Nid field (Test)")
                .at(stamp).semantic(TinkarTerm.IDENTIFIER_PATTERN, identity, IntIds.list.of(1, 2)));

        // One version per stamp per semantic — merge would silently replace.
        assertThrows(IllegalArgumentException.class, () -> scratch.concept("Same stamp (Test)")
                .at(stamp)
                .semantic(TinkarTerm.IDENTIFIER_PATTERN, identity,
                        TinkarTerm.UNIVERSALLY_UNIQUE_IDENTIFIER, "once")
                .semantic(TinkarTerm.IDENTIFIER_PATTERN, identity,
                        TinkarTerm.UNIVERSALLY_UNIQUE_IDENTIFIER, "twice"));

        // Resume must agree: identity, pattern, referenced component.
        PublicId resumedIdentity =
                PublicIds.of(UUID.fromString("cdef0123-8f9e-4d56-b845-f5061728394a"));
        assertThrows(IllegalArgumentException.class,
                () -> scratch.concept("Ingested kind (Test)", CONCEPT_ID)
                        .at(stamp).semantic(TinkarTerm.IDENTIFIER_PATTERN, resumedIdentity,
                                TinkarTerm.UNIVERSALLY_UNIQUE_IDENTIFIER, "value")
                        .semantic(TinkarTerm.US_DIALECT_PATTERN, resumedIdentity, TinkarTerm.PREFERRED));
        assertThrows(IllegalArgumentException.class,
                () -> scratch.concept("Ingested kind (Test)",
                        PublicIds.of(UUID.fromString("8091a2b3-4b5a-4c78-b869-d0e1f2030415"))));

        // The session registry: one identity, one declaration — components and semantics.
        assertThrows(IllegalArgumentException.class,
                () -> scratch.concept("Identity thief (Test)", CONCEPT_ID),
                "two components adopting one identity cross-write a single chronology");
        assertThrows(IllegalArgumentException.class,
                () -> scratch.concept("Semantic thief (Test)").at(stamp)
                        .semantic(TinkarTerm.IDENTIFIER_PATTERN, resumedIdentity,
                                TinkarTerm.UNIVERSALLY_UNIQUE_IDENTIFIER, "stolen"),
                "the same declared semantic identity on two components is rejected");

        // A declared stamp identity binds one tuple, session-wide.
        PublicId conflictedStampId =
                PublicIds.of(UUID.fromString("bcde1234-7e8d-4c45-a734-e4f506172839"));
        scratch.concept("First tuple (Test)").at(Stamp.active(conflictedStampId,
                "2026-07-15T00:00:00Z", TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE,
                TinkarTerm.DEVELOPMENT_PATH)).synonym("First");
        assertThrows(IllegalArgumentException.class,
                () -> scratch.concept("Second tuple (Test)").at(Stamp.active(conflictedStampId,
                        "2026-08-15T00:00:00Z", TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE,
                        TinkarTerm.DEVELOPMENT_PATH)),
                "the same declared stamp identity with a different tuple is a silent fork");

        // A reference answered with the derived identity pins the later declaration.
        scratch.conceptRef("Forward referenced (Test)");
        assertThrows(IllegalArgumentException.class,
                () -> scratch.concept("Forward referenced (Test)",
                        PublicIds.of(UUID.fromString("abcd2345-6d7c-4b34-9623-d3e4f5061728"))),
                "a reference to a declared-identity component must follow its declaration");

        // One version per stamp holds for descriptions too.
        assertThrows(IllegalArgumentException.class,
                () -> scratch.concept("Same stamp description (Test)").at(stamp)
                        .synonym("Twice")
                        .reviseSynonym("Twice", "Thrice"),
                "a same-scope revision would silently replace the version just declared");

        // Retirement follows declaration.
        assertThrows(IllegalArgumentException.class, () -> scratch.concept("Unretireable (Test)")
                .at(stamp).synonym("Present")
                .at(Stamp.inactive("2026-08-01T00:00:00Z", TinkarTerm.USER,
                        TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH))
                .retireSemantic(TinkarTerm.IDENTIFIER_PATTERN, identity));

        // The axiom identity is declared at first statement, and only there.
        assertThrows(IllegalArgumentException.class, () -> scratch.concept("Late axiom identity (Test)")
                .at(stamp)
                .statedAxioms(leb -> leb.NecessarySet(leb.And(leb.ConceptAxiom(TinkarTerm.MODEL_CONCEPT))))
                .statedAxioms(identity,
                        leb -> leb.NecessarySet(leb.And(leb.ConceptAxiom(TinkarTerm.MODEL_CONCEPT)))));

        // An explicit FQN declaration cannot follow the derived seed.
        assertThrows(IllegalStateException.class, () -> {
            scratch.concept("Seed conflict (Test)").at(stamp).synonym("Present");
            ConceptBuilder.ActiveScope laterScope = scratch.concept("Seed conflict (Test)")
                    .at(Stamp.active("2026-09-01T00:00:00Z", TinkarTerm.USER,
                            TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH));
            laterScope.reviseFullyQualifiedName("Seed conflict revised (Test)");
            laterScope.semantic(TinkarTerm.DESCRIPTION_PATTERN, identity,
                    TinkarTerm.ENGLISH_LANGUAGE, "Seed conflict (Test)",
                    TinkarTerm.DESCRIPTION_NOT_CASE_SENSITIVE,
                    TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE);
        });
    }

    // ------------------------------------------------------------------ helpers

    private static List<UUID> axiomVertexUuids() {
        SemanticEntity<SemanticEntityVersion> axioms = semanticFor(AXIOM_ID);
        DiTreeEntity tree = (DiTreeEntity) axioms.versions().getFirst().fieldValues().get(0);
        List<UUID> uuids = new ArrayList<>();
        tree.vertexMap().forEach(vertex -> uuids.add(vertex.vertexId().asUuid()));
        return uuids;
    }

    private static SemanticEntity<SemanticEntityVersion> semanticFor(PublicId declaredIdentity) {
        Entity<?> entity = EntityHandle.get(PrimitiveData.nid(declaredIdentity)).expectEntity();
        assertInstanceOf(SemanticEntity.class, entity,
                "expected a semantic at " + declaredIdentity);
        @SuppressWarnings("unchecked")
        SemanticEntity<SemanticEntityVersion> semantic = (SemanticEntity<SemanticEntityVersion>) entity;
        assertTrue(semantic.publicId().asUuidArray()[0].equals(declaredIdentity.asUuidArray()[0]),
                "the semantic adopts its declared identity");
        return semantic;
    }

    private static List<SemanticEntity<SemanticEntityVersion>> semanticsFor(int componentNid) {
        List<SemanticEntity<SemanticEntityVersion>> attached = new ArrayList<>();
        EntityService.get().forEachSemanticForComponent(componentNid, attached::add);
        return attached;
    }
}
