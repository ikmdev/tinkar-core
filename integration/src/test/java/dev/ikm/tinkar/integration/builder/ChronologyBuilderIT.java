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

import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.entity.PatternEntityVersion;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.ConceptBuilder;
import dev.ikm.tinkar.entity.builder.InactiveStamp;
import dev.ikm.tinkar.entity.builder.BindingsWriter;
import dev.ikm.tinkar.entity.builder.KnowledgeSet;
import dev.ikm.tinkar.entity.builder.PatternBuilder;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the ledger-form builders under the session model: the knowledge set
 * is the session — builders resume by birth FQN, the source composes time-major
 * (stamps declared inline, edits under each), and {@link KnowledgeSet#write()} replays the
 * whole session, idempotently.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChronologyBuilderIT {

    private static final KnowledgeSet TEST_SET =
            KnowledgeSet.of("f7f5c2a4-4b1e-5b6a-9d3c-2e8f0a1b4c6d");

    private static ActiveStamp birth;
    private static ActiveStamp later;
    private static InactiveStamp retirement;

    @BeforeAll
    static void composeAndWrite() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);

        // ---- The ledger, time-major: a stamp, then the edits under it. ----

        birth = Stamp.active("2026-07-15T00:00:00Z",
                TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);

        TEST_SET.concept("Journal element (Test)").at(birth)
                .synonym("Journal element")
                .definition("Root kind of the blocks a conversation journal orders.")
                .isA(TinkarTerm.MODEL_CONCEPT);

        // A child under Journal element — the structural taxonomy the glossary reads.
        TEST_SET.concept("Prose element (Test)").at(birth)
                .synonym("Prose element")
                .isA(TEST_SET.conceptRef("Journal element (Test)"));

        TEST_SET.concept("Retiring kind (Test)").at(birth)
                .synonym("Temporary name");

        TEST_SET.pattern("Journal manifest pattern (Test)").at(birth)
                .meaning(TinkarTerm.MODEL_CONCEPT).purpose(TinkarTerm.USER)
                .field(TinkarTerm.MODEL_CONCEPT, TinkarTerm.USER, TinkarTerm.COMPONENT_ID_LIST_FIELD)
                .field(TinkarTerm.USER, TinkarTerm.MODEL_CONCEPT, TinkarTerm.STRING)
                .synonym("Journal manifest");

        TEST_SET.pattern("Evolving pattern (Test)").at(birth)
                .meaning(TinkarTerm.MODEL_CONCEPT).purpose(TinkarTerm.USER);

        later = Stamp.active("2026-09-01T00:00:00Z",
                TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);

        // Resume by FQN — no restatement; the ledger simply continues.
        TEST_SET.concept("Journal element (Test)").at(later)
                .synonym("Journal block")
                .reviseSynonym("Journal element", "Journal atom");

        TEST_SET.pattern("Evolving pattern (Test)").at(later)
                .meaning(TinkarTerm.MODEL_CONCEPT).purpose(TinkarTerm.USER)
                .field(TinkarTerm.MODEL_CONCEPT, TinkarTerm.USER, TinkarTerm.STRING);

        retirement = Stamp.inactive("2026-10-01T00:00:00Z",
                TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);

        TEST_SET.concept("Retiring kind (Test)").at(retirement)
                .retire()
                .retireSynonym("Temporary name");

        TEST_SET.pattern("Evolving pattern (Test)").at(retirement)
                .retire();

        // ---- One session write; a second write proves idempotence. ----
        TEST_SET.write();
        TEST_SET.write();
    }

    @AfterAll
    static void afterAll() {
        TestHelper.stopDatabase();
    }

    @Test
    @DisplayName("Builders resume by FQN — same builder, same identity, one continuous ledger")
    void buildersResume() {
        ConceptBuilder resumed = TEST_SET.concept("Journal element (Test)");
        assertSame(resumed, TEST_SET.concept("Journal element (Test)"));
        assertEquals(UuidT5Generator.get(TEST_SET.uuid(), "Journal element (Test)"),
                resumed.publicId().asUuidArray()[0]);
        assertEquals(resumed.publicId(),
                TEST_SET.conceptRef("Journal element (Test)").publicId());
    }

    @Test
    @DisplayName("Same stamp tuple, same stamp identity — declared stamps are idempotent")
    void stampTupleIdentity() {
        ActiveStamp restated = Stamp.active("2026-07-15T00:00:00Z",
                TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);
        assertEquals(birth.publicId(), restated.publicId());
        assertEquals(birth.time(), Instant.parse("2026-07-15T00:00:00Z").toEpochMilli());
    }

    @Test
    @DisplayName("The resumed concept replays as one chronology: revisions, additions, axioms")
    void conceptLedgerReplay() {
        int conceptNid = TEST_SET.conceptRef("Journal element (Test)").nid();
        ConceptEntity<?> conceptEntity = EntityHandle.get(conceptNid).expectConcept();
        assertEquals(1, conceptEntity.versions().size(),
                "birth only — later scopes touched descriptions, not the concept; and write() twice must not duplicate");

        StampEntity<?> birthStamp = Entity.getStamp(conceptEntity.versions().get(0).stampNid());
        assertEquals(State.ACTIVE, birthStamp.state());
        assertEquals(birth.time(), birthStamp.time());

        // FQN + two synonyms + one definition — across two resumed sections.
        int[] descriptionNids = EntityService.get().semanticNidsForComponentOfPattern(
                conceptNid, TinkarTerm.DESCRIPTION_PATTERN.nid());
        assertEquals(4, descriptionNids.length);

        SemanticEntity<?> revised = findDescriptionByLatestText(descriptionNids, "Journal atom");
        assertEquals(2, revised.versions().size());
        assertEquals("Journal element", textOf(revised.versions().get(0)));
        assertEquals("Journal atom", textOf(revised.versions().get(1)));
        assertEquals(later.time(), Entity.getStamp(revised.versions().get(1).stampNid()).time());

        for (int descriptionNid : descriptionNids) {
            assertEquals(1, EntityService.get().semanticNidsForComponentOfPattern(
                    descriptionNid, TinkarTerm.US_DIALECT_PATTERN.nid()).length);
        }

        int[] axiomNids = EntityService.get().semanticNidsForComponentOfPattern(
                conceptNid, TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid());
        assertEquals(1, axiomNids.length);
        SemanticEntity<?> axioms = EntityHandle.get(axiomNids[0]).expectSemantic();
        Object axiomField = ((SemanticEntityVersion) axioms.versions().get(0)).fieldValues().get(0);
        assertInstanceOf(DiTreeEntity.class, axiomField);
        assertEquals(4, ((DiTreeEntity) axiomField).vertexCount());
    }

    @Test
    @DisplayName("Retirement scopes append inactive-stamped versions")
    void retirementLedger() {
        int conceptNid = TEST_SET.conceptRef("Retiring kind (Test)").nid();
        ConceptEntity<?> conceptEntity = EntityHandle.get(conceptNid).expectConcept();
        assertEquals(2, conceptEntity.versions().size(), "birth + retirement");
        StampEntity<?> lastStamp = Entity.getStamp(conceptEntity.versions().get(1).stampNid());
        assertEquals(State.INACTIVE, lastStamp.state());
        assertEquals(retirement.time(), lastStamp.time());

        int[] descriptionNids = EntityService.get().semanticNidsForComponentOfPattern(
                conceptNid, TinkarTerm.DESCRIPTION_PATTERN.nid());
        SemanticEntity<?> synonym = findDescriptionByLatestText(descriptionNids, "Temporary name");
        assertEquals(2, synonym.versions().size());
        assertEquals(State.INACTIVE, Entity.getStamp(synonym.versions().get(1).stampNid()).state());
    }

    @Test
    @DisplayName("Patterns replay: version tuple, ordered fields, restatement revises, retirement carries")
    void patternLedgerReplay() {
        int manifestNid = TEST_SET.patternRef("Journal manifest pattern (Test)").nid();
        PatternEntity<?> manifest = EntityHandle.get(manifestNid).expectPattern();
        assertEquals(1, manifest.versions().size());
        PatternEntityVersion manifestVersion = (PatternEntityVersion) manifest.versions().get(0);
        assertEquals(TinkarTerm.MODEL_CONCEPT.nid(), manifestVersion.semanticMeaningNid());
        assertEquals(TinkarTerm.USER.nid(), manifestVersion.semanticPurposeNid());
        assertEquals(2, manifestVersion.fieldDefinitions().size());
        assertEquals(TinkarTerm.COMPONENT_ID_LIST_FIELD.nid(),
                manifestVersion.fieldDefinitions().get(0).dataTypeNid());
        assertEquals(0, manifestVersion.fieldDefinitions().get(0).indexInPattern());
        assertEquals(TinkarTerm.STRING.nid(),
                manifestVersion.fieldDefinitions().get(1).dataTypeNid());

        int evolvingNid = TEST_SET.patternRef("Evolving pattern (Test)").nid();
        PatternEntity<?> evolving = EntityHandle.get(evolvingNid).expectPattern();
        assertEquals(3, evolving.versions().size(), "birth + restatement + retirement");
        assertEquals(0, ((PatternEntityVersion) evolving.versions().get(0)).fieldDefinitions().size(),
                "membership-pattern shape at birth");
        assertEquals(1, ((PatternEntityVersion) evolving.versions().get(1)).fieldDefinitions().size());
        PatternEntityVersion retired = (PatternEntityVersion) evolving.versions().get(2);
        assertEquals(1, retired.fieldDefinitions().size(), "retirement carries prior content");
        assertEquals(State.INACTIVE, Entity.getStamp(retired.stampNid()).state());
    }

    @Test
    @DisplayName("Ledger scopes must be chronological, per component")
    void chronologyEnforced() {
        KnowledgeSet probe = KnowledgeSet.of("11111111-1111-5111-9111-111111111111");
        ConceptBuilder.ActiveScope scope = probe.concept("Out of order (Probe)").at(later);
        assertThrows(IllegalArgumentException.class, () -> scope.at(birth));
    }

    @Test
    @DisplayName("Revise references must resolve uniquely")
    void reviseValidation() {
        KnowledgeSet probe = KnowledgeSet.of("22222222-2222-5222-9222-222222222222");
        ConceptBuilder.ActiveScope scope = probe.concept("Validation probe (Probe)")
                .at(birth).synonym("Only name");
        assertThrows(IllegalArgumentException.class,
                () -> scope.reviseSynonym("No such name", "Anything"));

        ConceptBuilder.ActiveScope ambiguous = probe.concept("Ambiguity probe (Probe)")
                .at(birth).synonym("Twin").synonym("Twin");
        assertThrows(IllegalArgumentException.class,
                () -> ambiguous.reviseSynonym("Twin", "Renamed"));
    }

    @Test
    @DisplayName("Incomplete declarations fail the session write; retirement requires birth")
    void lifecycleValidation() {
        KnowledgeSet probe = KnowledgeSet.of("33333333-3333-5333-9333-333333333333");
        assertThrows(IllegalStateException.class,
                () -> probe.concept("Never born (Probe)").at(retirement));

        probe.concept("Opened but never scoped (Probe)");
        assertThrows(IllegalStateException.class, probe::write,
                "a declaration with no birth scope fails the session write");
    }

    @Test
    @DisplayName("A pattern version restates as a whole; field meanings must be unique")
    void patternValidation() {
        KnowledgeSet probe = KnowledgeSet.of("44444444-4444-5444-9444-444444444444");
        probe.pattern("Partial pattern (Probe)").at(birth)
                .field(TinkarTerm.MODEL_CONCEPT, TinkarTerm.USER, TinkarTerm.STRING);
        assertThrows(IllegalStateException.class, probe::write,
                "a scope declaring fields must restate meaning and purpose");

        KnowledgeSet duplicates = KnowledgeSet.of("55555555-5555-5555-9555-555555555555");
        PatternBuilder.ActiveScope duplicate = duplicates.pattern("Duplicate meanings (Probe)").at(birth)
                .meaning(TinkarTerm.MODEL_CONCEPT).purpose(TinkarTerm.USER)
                .field(TinkarTerm.MODEL_CONCEPT, TinkarTerm.USER, TinkarTerm.STRING)
                .field(TinkarTerm.MODEL_CONCEPT, TinkarTerm.MODEL_CONCEPT, TinkarTerm.STRING);
        assertThrows(IllegalStateException.class, () -> duplicate.at(later),
                "duplicate field meanings are rejected at version flush");
    }

    @Test
    @DisplayName("One FQN cannot name both a concept and a pattern in a knowledge set")
    void kindCollision() {
        KnowledgeSet probe = KnowledgeSet.of("66666666-6666-5666-9666-666666666666");
        probe.concept("Collider (Probe)");
        assertThrows(IllegalArgumentException.class, () -> probe.pattern("Collider (Probe)"));
    }

    @Test
    @DisplayName("isA composes the stated-axiom semantic; parents are read from the store")
    void isAWritesStatedAxiom() {
        // isA composes NecessarySet(And(ConceptAxiom(parent))) as the stated-axiom semantic;
        // the glossary extractor reads parents from this stored axiom (no builder read API).
        int[] axiomNids = EntityService.get().semanticNidsForComponentOfPattern(
                TEST_SET.conceptRef("Prose element (Test)").nid(),
                TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid());
        assertEquals(1, axiomNids.length);
        SemanticEntity<?> axiom = EntityHandle.get(axiomNids[0]).expectSemantic();
        Object field = ((SemanticEntityVersion) axiom.versions().get(0)).fieldValues().get(0);
        assertInstanceOf(DiTreeEntity.class, field);
    }

    @Test
    @DisplayName("Bindings generate from the composed set: constants, UUID literals, definition javadoc")
    void bindingsGeneration() throws Exception {
        Path outputDir = Path.of(System.getProperty("user.dir"))
                .resolve("target").resolve("generated-test-bindings");
        Path file = BindingsWriter.write(TEST_SET, "test.bindings", "TestSetTerms", outputDir);
        String source = Files.readString(file);

        assertEquals("JOURNAL_ELEMENT", BindingsWriter.constantName("Journal element (Test)"));
        assertTrue(source.contains("public static final EntityProxy.Concept JOURNAL_ELEMENT ="),
                "concept constant with tag-stripped name");
        assertTrue(source.contains("public static final EntityProxy.Pattern JOURNAL_MANIFEST_PATTERN ="),
                "pattern constant");
        assertTrue(source.contains(TEST_SET.conceptRef("Journal element (Test)")
                        .publicId().asUuidArray()[0].toString()),
                "resolved UUID literal embedded");
        assertTrue(source.contains("Root kind of the blocks a conversation journal orders."),
                "definition text as javadoc");
        assertTrue(source.contains("DO NOT EDIT"), "generation marker");
    }

    private static SemanticEntity<?> findDescriptionByLatestText(int[] descriptionNids, String text) {
        List<String> latestTexts = new ArrayList<>();
        for (int nid : descriptionNids) {
            SemanticEntity<?> semantic = EntityHandle.get(nid).expectSemantic();
            EntityVersion last = semantic.versions().get(semantic.versions().size() - 1);
            String latest = textOf(last);
            latestTexts.add(latest);
            if (text.equals(latest)) {
                return semantic;
            }
        }
        throw new AssertionError("No description with latest text \"" + text + "\" among " + latestTexts);
    }

    private static String textOf(EntityVersion version) {
        for (Object field : ((SemanticEntityVersion) version).fieldValues()) {
            if (field instanceof String string) {
                return string;
            }
        }
        throw new AssertionError("No string field in description version " + version);
    }
}
