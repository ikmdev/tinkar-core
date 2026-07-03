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
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.ConceptEntity;
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
import dev.ikm.tinkar.entity.builder.Namespace;
import dev.ikm.tinkar.entity.builder.PatternBuilder;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.EntityProxy;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for the ledger-form {@link ConceptBuilder}: birth-FQN identity,
 * declared-stamp idempotence, the add/revise/retire grammar, and chronology replay
 * into an ephemeral store.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChronologyBuilderIT {

    private static final Namespace TEST_NAMESPACE =
            Namespace.of("f7f5c2a4-4b1e-5b6a-9d3c-2e8f0a1b4c6d");

    private static final ActiveStamp W1 = Stamp.active("2026-07-15T00:00:00Z",
            TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);
    private static final ActiveStamp W2 = Stamp.active("2026-09-01T00:00:00Z",
            TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);
    private static final InactiveStamp W3_RETIRE = Stamp.inactive("2026-10-01T00:00:00Z",
            TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);

    @BeforeAll
    static void beforeAll() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
    }

    @AfterAll
    static void afterAll() {
        TestHelper.stopDatabase();
    }

    @Test
    @DisplayName("Identity derives from namespace + FQN at birth, deterministically")
    void birthFqnIdentity() {
        String fqn = "Identity probe (Test)";
        ConceptBuilder builder = TEST_NAMESPACE.concept(fqn);
        assertEquals(UuidT5Generator.get(TEST_NAMESPACE.uuid(), fqn),
                builder.publicId().asUuidArray()[0]);
        assertEquals(builder.publicId(), TEST_NAMESPACE.conceptRef(fqn).publicId(),
                "conceptRef must derive the same identity as the declaration");
    }

    @Test
    @DisplayName("Same stamp tuple, same stamp identity — declared stamps are idempotent")
    void stampTupleIdentity() {
        ActiveStamp again = Stamp.active("2026-07-15T00:00:00Z",
                TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);
        assertEquals(W1.publicId(), again.publicId());
        assertEquals(W1.time(), Instant.parse("2026-07-15T00:00:00Z").toEpochMilli());
    }

    @Test
    @DisplayName("A full ledger replays: concept, descriptions, dialects, axioms, revisions")
    void ledgerReplay() {
        EntityProxy.Concept concept = TEST_NAMESPACE.concept("Journal element (Test)")
                .at(W1)
                    .synonym("Journal element")
                    .definition("Root kind of the blocks a conversation journal orders.")
                    .statedAxioms(leb -> leb.NecessarySet(leb.And(
                            leb.ConceptAxiom(TinkarTerm.MODEL_CONCEPT))))
                .at(W2)
                    .synonym("Journal block")
                    .reviseSynonym("Journal element", "Journal atom")
                .build();

        int conceptNid = concept.nid();
        ConceptEntity<?> conceptEntity = EntityHandle.get(conceptNid).expectConcept();
        assertNotNull(conceptEntity);
        assertEquals(1, conceptEntity.versions().size(),
                "Only the birth scope adds a concept version");

        StampEntity<?> birthStamp = Entity.getStamp(conceptEntity.versions().get(0).stampNid());
        assertEquals(State.ACTIVE, birthStamp.state());
        assertEquals(W1.time(), birthStamp.time());

        // Descriptions: FQN + two synonyms + one definition.
        int[] descriptionNids = EntityService.get().semanticNidsForComponentOfPattern(
                conceptNid, TinkarTerm.DESCRIPTION_PATTERN.nid());
        assertEquals(4, descriptionNids.length);

        // The revised synonym is one semantic with two versions, oldest first.
        SemanticEntity<?> revised = findDescriptionByLatestText(descriptionNids, "Journal atom");
        assertEquals(2, revised.versions().size());
        assertEquals("Journal element", textOf(revised.versions().get(0)));
        assertEquals("Journal atom", textOf(revised.versions().get(1)));
        assertEquals(W2.time(), Entity.getStamp(revised.versions().get(1).stampNid()).time());

        // Every description carries exactly one US-dialect acceptability semantic.
        for (int descriptionNid : descriptionNids) {
            int[] dialectNids = EntityService.get().semanticNidsForComponentOfPattern(
                    descriptionNid, TinkarTerm.US_DIALECT_PATTERN.nid());
            assertEquals(1, dialectNids.length);
        }

        // Stated axioms: one singleton semantic whose value is the LogicalExpressionBuilder tree.
        int[] axiomNids = EntityService.get().semanticNidsForComponentOfPattern(
                conceptNid, TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid());
        assertEquals(1, axiomNids.length);
        SemanticEntity<?> axioms = EntityHandle.get(axiomNids[0]).expectSemantic();
        Object axiomField = ((SemanticEntityVersion) axioms.versions().get(0)).fieldValues().get(0);
        assertInstanceOf(DiTreeEntity.class, axiomField);
        assertEquals(4, ((DiTreeEntity) axiomField).vertexCount(),
                "definition-root, necessary-set, and, concept-reference");
    }

    @Test
    @DisplayName("Retirement scopes append inactive-stamped versions; content verbs absent")
    void retirementLedger() {
        EntityProxy.Concept concept = TEST_NAMESPACE.concept("Retiring kind (Test)")
                .at(W1)
                    .synonym("Temporary name")
                .at(W3_RETIRE)
                    .retire()
                    .retireSynonym("Temporary name")
                .build();

        ConceptEntity<?> conceptEntity = EntityHandle.get(concept.nid()).expectConcept();
        assertEquals(2, conceptEntity.versions().size(), "birth + retirement");
        StampEntity<?> lastStamp = Entity.getStamp(conceptEntity.versions().get(1).stampNid());
        assertEquals(State.INACTIVE, lastStamp.state());
        assertEquals(W3_RETIRE.time(), lastStamp.time());

        int[] descriptionNids = EntityService.get().semanticNidsForComponentOfPattern(
                concept.nid(), TinkarTerm.DESCRIPTION_PATTERN.nid());
        SemanticEntity<?> synonym = findDescriptionByLatestText(descriptionNids, "Temporary name");
        assertEquals(2, synonym.versions().size());
        assertEquals(State.INACTIVE, Entity.getStamp(synonym.versions().get(1).stampNid()).state());
    }

    @Test
    @DisplayName("Ledger scopes must be chronological")
    void chronologyEnforced() {
        ConceptBuilder.ActiveScope scope = TEST_NAMESPACE.concept("Out of order (Test)").at(W2);
        assertThrows(IllegalArgumentException.class, () -> scope.at(W1),
                "a later scope may not precede an earlier stamp's time");
    }

    @Test
    @DisplayName("Revising an unknown or ambiguous synonym fails the build")
    void reviseValidation() {
        ConceptBuilder.ActiveScope scope = TEST_NAMESPACE.concept("Validation probe (Test)")
                .at(W1).synonym("Only name");
        assertThrows(IllegalArgumentException.class,
                () -> scope.reviseSynonym("No such name", "Anything"));

        ConceptBuilder.ActiveScope ambiguous = TEST_NAMESPACE.concept("Ambiguity probe (Test)")
                .at(W1).synonym("Twin").synonym("Twin");
        assertThrows(IllegalArgumentException.class,
                () -> ambiguous.reviseSynonym("Twin", "Renamed"));
    }

    @Test
    @DisplayName("A retirement scope requires a prior birth scope; build() runs once")
    void lifecycleValidation() {
        assertThrows(IllegalStateException.class,
                () -> TEST_NAMESPACE.concept("Never born (Test)").at(W3_RETIRE));

        ConceptBuilder.ActiveScope scope = TEST_NAMESPACE.concept("Build once (Test)").at(W1);
        scope.build();
        assertThrows(IllegalStateException.class, scope::build);
    }

    @Test
    @DisplayName("Replaying the same declaration is idempotent — same identities, same versions")
    void replayIdempotence() {
        String fqn = "Replayed concept (Test)";
        EntityProxy.Concept first = TEST_NAMESPACE.concept(fqn)
                .at(W1).synonym("Replay name").build();
        EntityProxy.Concept second = TEST_NAMESPACE.concept(fqn)
                .at(W1).synonym("Replay name").build();

        assertEquals(first.publicId(), second.publicId());
        ConceptEntity<?> conceptEntity = EntityHandle.get(first.nid()).expectConcept();
        assertEquals(1, conceptEntity.versions().size(),
                "replay must not duplicate versions — same stamps, same identities");
        int[] descriptionNids = EntityService.get().semanticNidsForComponentOfPattern(
                first.nid(), TinkarTerm.DESCRIPTION_PATTERN.nid());
        assertEquals(2, descriptionNids.length, "FQN + one synonym, not duplicated by replay");
    }

    @Test
    @DisplayName("A pattern ledger replays: version tuple, ordered field definitions, descriptions")
    void patternLedgerReplay() {
        EntityProxy.Pattern pattern = TEST_NAMESPACE.pattern("Journal manifest pattern (Test)")
                .at(W1)
                    .meaning(TinkarTerm.MODEL_CONCEPT).purpose(TinkarTerm.USER)
                    .field(TinkarTerm.MODEL_CONCEPT, TinkarTerm.USER, TinkarTerm.COMPONENT_ID_LIST_FIELD)
                    .field(TinkarTerm.USER, TinkarTerm.MODEL_CONCEPT, TinkarTerm.STRING)
                    .synonym("Journal manifest")
                .build();

        assertEquals(TEST_NAMESPACE.patternRef("Journal manifest pattern (Test)").publicId(),
                pattern.publicId());

        PatternEntity<?> patternEntity = EntityHandle.get(pattern.nid()).expectPattern();
        assertEquals(1, patternEntity.versions().size());
        PatternEntityVersion version = (PatternEntityVersion) patternEntity.versions().get(0);
        assertEquals(TinkarTerm.MODEL_CONCEPT.nid(), version.semanticMeaningNid());
        assertEquals(TinkarTerm.USER.nid(), version.semanticPurposeNid());
        assertEquals(2, version.fieldDefinitions().size());
        assertEquals(TinkarTerm.COMPONENT_ID_LIST_FIELD.nid(),
                version.fieldDefinitions().get(0).dataTypeNid());
        assertEquals(0, version.fieldDefinitions().get(0).indexInPattern());
        assertEquals(TinkarTerm.STRING.nid(), version.fieldDefinitions().get(1).dataTypeNid());
        assertEquals(1, version.fieldDefinitions().get(1).indexInPattern());

        int[] descriptionNids = EntityService.get().semanticNidsForComponentOfPattern(
                pattern.nid(), TinkarTerm.DESCRIPTION_PATTERN.nid());
        assertEquals(2, descriptionNids.length, "FQN + one synonym");
    }

    @Test
    @DisplayName("Pattern restatement in a later scope is a revision; retirement carries content")
    void patternRestatementAndRetirement() {
        EntityProxy.Pattern pattern = TEST_NAMESPACE.pattern("Evolving pattern (Test)")
                .at(W1)
                    .meaning(TinkarTerm.MODEL_CONCEPT).purpose(TinkarTerm.USER)
                .at(W2)
                    .meaning(TinkarTerm.MODEL_CONCEPT).purpose(TinkarTerm.USER)
                    .field(TinkarTerm.MODEL_CONCEPT, TinkarTerm.USER, TinkarTerm.STRING)
                .at(W3_RETIRE)
                    .retire()
                .build();

        PatternEntity<?> patternEntity = EntityHandle.get(pattern.nid()).expectPattern();
        assertEquals(3, patternEntity.versions().size(), "birth + restatement + retirement");
        PatternEntityVersion birth = (PatternEntityVersion) patternEntity.versions().get(0);
        assertEquals(0, birth.fieldDefinitions().size(), "membership-pattern shape at birth");
        PatternEntityVersion restated = (PatternEntityVersion) patternEntity.versions().get(1);
        assertEquals(1, restated.fieldDefinitions().size());
        PatternEntityVersion retired = (PatternEntityVersion) patternEntity.versions().get(2);
        assertEquals(1, retired.fieldDefinitions().size(), "retirement carries prior content");
        assertEquals(State.INACTIVE, Entity.getStamp(retired.stampNid()).state());
    }

    @Test
    @DisplayName("A pattern version restates as a whole — partial scopes fail")
    void patternVersionValidation() {
        PatternBuilder.ActiveScope partial = TEST_NAMESPACE.pattern("Partial pattern (Test)")
                .at(W1)
                .field(TinkarTerm.MODEL_CONCEPT, TinkarTerm.USER, TinkarTerm.STRING);
        assertThrows(IllegalStateException.class, partial::build,
                "a scope declaring fields must restate meaning and purpose");

        PatternBuilder.ActiveScope empty = TEST_NAMESPACE.pattern("Empty pattern (Test)").at(W1);
        assertThrows(IllegalStateException.class, empty::build,
                "the birth scope must declare meaning and purpose");
    }

    private static SemanticEntity<?> findDescriptionByLatestText(int[] descriptionNids, String text) {
        List<String> latestTexts = new ArrayList<>();
        for (int nid : descriptionNids) {
            SemanticEntity<?> semantic = EntityHandle.get(nid).expectSemantic();
            String latest = textOf(semantic.versions().get(semantic.versions().size() - 1));
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
