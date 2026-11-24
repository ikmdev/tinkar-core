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
package dev.ikm.tinkar.integration.boundary;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.boundary.BoundaryViolation;
import dev.ikm.tinkar.entity.boundary.BoundaryViolation.Rule;
import dev.ikm.tinkar.entity.boundary.BoundaryViolationException;
import dev.ikm.tinkar.entity.boundary.CommitBoundary;
import dev.ikm.tinkar.entity.boundary.DataKind;
import dev.ikm.tinkar.entity.boundary.EntityBoundaries;
import dev.ikm.tinkar.entity.boundary.ModuleKinds;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpressionBuilder;
import dev.ikm.tinkar.entity.transaction.StampedWriter;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import dev.ikm.tinkar.terms.State;
import network.ike.foundation.ike.bindings.IkeConcept;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The commit validator of IKE-Network/ike-issues#1313 on real commits: knowledge never references
 * shared or local data, data refers only to data shared at least as widely, local data saved in a
 * file and other local data do not cross, birth is final, a local pattern holds only its own kind,
 * and no module is a kind of both the Shared module and the Local module.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommitBoundaryIT {

    private static final long TIME = 1_760_000_000_000L;

    /** A module under a module under Local module: kinds are inherited at any depth. */
    private ConceptFacade journals;
    /** A module under File module. */
    private ConceptFacade gitDocuments;
    /** A module under Shared module. */
    private ConceptFacade team;
    private long time = TIME;

    @BeforeAll
    void beforeAll() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
        ConceptFacade sessions = concept();
        journals = concept();
        gitDocuments = concept();
        team = concept();
        try (StampedWriter writer = knowledge("parents")) {
            module(writer, KernelConcept.LOCAL_MODULE, KernelConcept.SOLOR_MODULE);
            module(writer, KernelConcept.FILE_MODULE, KernelConcept.LOCAL_MODULE);
            module(writer, KernelConcept.SHARED_MODULE, KernelConcept.SOLOR_MODULE);
            module(writer, team, KernelConcept.SHARED_MODULE);
            module(writer, sessions, KernelConcept.LOCAL_MODULE);
            module(writer, journals, sessions);
            module(writer, gitDocuments, KernelConcept.FILE_MODULE);
            writer.commit();
        }
    }

    @AfterAll
    void afterAll() {
        System.clearProperty(CommitBoundary.MODE_PROPERTY);
        TestHelper.stopDatabase();
    }

    @BeforeEach
    void failMode() {
        System.setProperty(CommitBoundary.MODE_PROPERTY, "fail");
    }

    @AfterEach
    void forget() {
        ModuleKinds.clear();
        EntityBoundaries.clear();
    }

    @Test
    void modulesTakeTheKindOfTheirParentAtAnyDepth() {
        assertEquals(DataKind.LOCAL, ModuleKinds.resolve(journals.nid()).kind());
        assertEquals(DataKind.FILE, ModuleKinds.resolve(gitDocuments.nid()).kind(), "a kind of File module, itself a kind of Local module");
        assertEquals(DataKind.SHARED, ModuleKinds.resolve(team.nid()).kind());
        assertEquals(DataKind.KNOWLEDGE, ModuleKinds.resolve(IkeConcept.DEVELOPMENT_MODULE.nid()).kind());
    }

    @Test
    void localDataMayReferenceKnowledge() {
        PublicId note = PublicIds.newRandom();
        commit(writer(journals, "local note"), w -> comment(w, note, KernelConcept.USER.publicId(), "a journal line"));
        assertEquals(DataKind.LOCAL, EntityBoundaries.kindOf(EntityHandle.get(note).expectEntity().nid()));
    }

    @Test
    void knowledgeReferencingLocalDataIsRefused() {
        PublicId note = PublicIds.newRandom();
        commit(writer(journals, "local note"), w -> comment(w, note, KernelConcept.USER.publicId(), "a journal line"));
        BoundaryViolationException refused = assertThrows(BoundaryViolationException.class,
                () -> commit(knowledge("knowledge about a note"), w -> comment(w, PublicIds.newRandom(), note, "cites the journal")));
        assertTrue(rules(refused).contains(Rule.KNOWLEDGE_REFERENCES_DATA), refused.getMessage());
    }

    @Test
    void warnModeLetsTheCommitThroughAndReportsIt() {
        System.setProperty(CommitBoundary.MODE_PROPERTY, "warn");
        PublicId note = PublicIds.newRandom();
        commit(writer(journals, "local note"), w -> comment(w, note, KernelConcept.USER.publicId(), "a journal line"));
        PublicId citing = PublicIds.newRandom();
        commit(knowledge("knowledge about a note"), w -> comment(w, citing, note, "cites the journal"));
        List<EntityVersion> written = List.copyOf(EntityHandle.get(citing).expectSemantic().versions().castToList());
        assertEquals(List.of(Rule.KNOWLEDGE_REFERENCES_DATA),
                CommitBoundary.check(written).stream().map(BoundaryViolation::rule).toList());
    }

    @Test
    void sharedDataReferencingLocalDataIsRefused() {
        PublicId note = PublicIds.newRandom();
        commit(writer(journals, "local note"), w -> comment(w, note, KernelConcept.USER.publicId(), "a journal line"));
        BoundaryViolationException refused = assertThrows(BoundaryViolationException.class,
                () -> commit(writer(team, "a shared line about the note"), w -> comment(w, PublicIds.newRandom(), note, "cites it")));
        assertTrue(rules(refused).contains(Rule.CROSSES_KINDS), refused.getMessage());
    }

    @Test
    void localDataMayReferenceSharedData() {
        PublicId shared = PublicIds.newRandom();
        commit(writer(team, "a shared line"), w -> comment(w, shared, KernelConcept.USER.publicId(), "shared with the team"));
        assertEquals(DataKind.SHARED, EntityBoundaries.kindOf(EntityHandle.get(shared).expectEntity().nid()));
        commit(writer(journals, "a local note about it"), w -> comment(w, PublicIds.newRandom(), shared, "cites it"));
        commit(writer(gitDocuments, "a file line about it"), w -> comment(w, PublicIds.newRandom(), shared, "cites it"));
    }

    @Test
    void fileAndOtherLocalDataDoNotCross() {
        PublicId note = PublicIds.newRandom();
        commit(writer(journals, "local note"), w -> comment(w, note, KernelConcept.USER.publicId(), "a journal line"));
        BoundaryViolationException refused = assertThrows(BoundaryViolationException.class,
                () -> commit(writer(gitDocuments, "a git line about the note"), w -> comment(w, PublicIds.newRandom(), note, "cites it")));
        assertTrue(rules(refused).contains(Rule.CROSSES_KINDS), refused.getMessage());
    }

    /**
     * A STAMP is of the kind of the module it names: knowledge commenting on a git document's commit
     * refers to file data, as the commit narrator once did (IKE-Network/ike-issues#1355).
     */
    @Test
    void knowledgeCommentingOnAFileStampIsRefused() {
        PublicId line = PublicIds.newRandom();
        commit(writer(gitDocuments, "a git commit"), w -> comment(w, line, KernelConcept.USER.publicId(), "a document line"));
        PublicId stamp = stampOf(line);
        assertEquals(DataKind.FILE, EntityBoundaries.kindOf(EntityHandle.get(stamp).expectEntity().nid()));
        BoundaryViolationException refused = assertThrows(BoundaryViolationException.class,
                () -> commit(knowledge("a narration of the git commit"), w -> comment(w, PublicIds.newRandom(), stamp, "narrates it")));
        assertTrue(rules(refused).contains(Rule.KNOWLEDGE_REFERENCES_DATA), refused.getMessage());
    }

    @Test
    void aRefusedCommitLeavesNothingUncommitted() {
        PublicId line = PublicIds.newRandom();
        commit(writer(gitDocuments, "a git commit"), w -> comment(w, line, KernelConcept.USER.publicId(), "a document line"));
        PublicId stamp = stampOf(line);
        PublicId narration = PublicIds.newRandom();
        // Untimed, as the narrator writes: a STAMP minted with its time is committed at birth and is never cancelled.
        // Local, since a version others are to get is refused before it is stored.
        StampedWriter untimed = StampedWriter.open("a conversation about the git commit", State.ACTIVE, KernelConcept.USER,
                journals, KernelConcept.DEVELOPMENT_PATH);
        assertThrows(BoundaryViolationException.class, () -> commit(untimed, w -> comment(w, narration, stamp, "narrates it")));
        assertTrue(EntityHandle.get(narration).entity().map(e -> e.versions().stream().allMatch(v -> v.canceled())).orElse(true),
                "the refused comment is cancelled, not left uncommitted to keep what it names from being removed");
        assertTrue(EntityHandle.get(narration).entity().isPresent(), "it was stored, so there was something to cancel");
    }

    @Test
    void aRefusedKnowledgeVersionIsNeverStoredEvenUnderAStampWithItsOwnTime() {
        PublicId line = PublicIds.newRandom();
        commit(writer(gitDocuments, "a git commit"), w -> comment(w, line, KernelConcept.USER.publicId(), "a document line"));
        PublicId stamp = stampOf(line);
        PublicId narration = PublicIds.newRandom();
        assertThrows(BoundaryViolationException.class,
                () -> commit(knowledge("a timed narration of the git commit"), w -> comment(w, narration, stamp, "narrates it")));
        assertTrue(EntityHandle.get(EntityService.get().nidForSemantic(KernelPattern.COMMENT_PATTERN.publicId(), narration)).entity().isEmpty(),
                "a STAMP minted with its time is committed at birth, so the version is refused before it is stored");
    }

    @Test
    void knowledgeMayCommentOnAKnowledgeStamp() {
        PublicId concept = PublicIds.newRandom();
        commit(knowledge("a knowledge commit"), w -> comment(w, concept, KernelConcept.USER.publicId(), "knowledge"));
        PublicId stamp = stampOf(concept);
        assertEquals(DataKind.KNOWLEDGE, EntityBoundaries.kindOf(EntityHandle.get(stamp).expectEntity().nid()));
        commit(knowledge("a narration of the knowledge commit"), w -> comment(w, PublicIds.newRandom(), stamp, "narrates it"));
    }

    @Test
    void birthIsFinal() {
        PublicId note = PublicIds.newRandom();
        commit(writer(journals, "local note"), w -> comment(w, note, KernelConcept.USER.publicId(), "first"));
        BoundaryViolationException refused = assertThrows(BoundaryViolationException.class,
                () -> commit(knowledge("the note as knowledge"), w -> comment(w, note, KernelConcept.USER.publicId(), "second")));
        assertTrue(rules(refused).contains(Rule.BIRTH_IS_FINAL), refused.getMessage());
    }

    @Test
    void aLocalPatternHoldsOnlyItsOwnKind() {
        PublicId pattern = PublicIds.newRandom();
        commit(writer(journals, "a local pattern"), w -> w.pattern(pattern, KernelConcept.USER, KernelConcept.USER,
                StampedWriter.field(KernelConcept.USER, KernelConcept.USER, KernelConcept.STRING)));
        BoundaryViolationException refused = assertThrows(BoundaryViolationException.class,
                () -> commit(writer(gitDocuments, "a file element"),
                        w -> w.semantic(PublicIds.newRandom(), pattern, KernelConcept.USER.publicId(), "text")));
        assertTrue(rules(refused).contains(Rule.ELEMENT_OUTSIDE_ITS_PATTERN), refused.getMessage());
    }

    @Test
    void aKnowledgePatternMayHoldLocalElements() {
        PublicId note = PublicIds.newRandom();
        commit(writer(journals, "a journal comment on a knowledge pattern"),
                w -> comment(w, note, KernelConcept.USER.publicId(), "the comment pattern is knowledge"));
        assertEquals(DataKind.KNOWLEDGE, EntityBoundaries.birthKind(KernelPattern.COMMENT_PATTERN.nid()));
        assertEquals(DataKind.LOCAL, EntityBoundaries.kindOf(EntityHandle.get(note).expectEntity().nid()));
    }

    @Test
    void aModuleResolvedBeforeItsParentIsWrittenIsNotRememberedAsKnowledge() {
        ConceptFacade late = concept();
        try (StampedWriter writer = knowledge("a module, its parent not stated yet")) {
            writer.concept(late.publicId());
            writer.commit();
        }
        assertEquals(DataKind.KNOWLEDGE, ModuleKinds.resolve(late.nid()).kind(), "no parent yet");
        try (StampedWriter writer = knowledge("its parent")) {
            module(writer, late, KernelConcept.FILE_MODULE);
            writer.commit();
        }
        assertEquals(DataKind.FILE, ModuleKinds.resolve(late.nid()).kind(),
                "once its parent is written it is file data, with no clear in between");
    }

    @Test
    void noModuleIsAKindOfBothSharedAndLocal() {
        ConceptFacade both = concept();
        ConceptFacade fileAndLocal = concept();
        try (StampedWriter writer = knowledge("a module under both")) {
            module(writer, both, KernelConcept.SHARED_MODULE, KernelConcept.LOCAL_MODULE);
            module(writer, fileAndLocal, KernelConcept.FILE_MODULE, KernelConcept.LOCAL_MODULE);
            writer.commit();
        }
        assertTrue(ModuleKinds.resolve(both.nid()).conflicting());
        assertEquals(new ModuleKinds.Resolution(DataKind.FILE, false), ModuleKinds.resolve(fileAndLocal.nid()),
                "File module is a kind of Local module, so naming both is no conflict");
        BoundaryViolationException refused = assertThrows(BoundaryViolationException.class,
                () -> commit(writer(both, "data under it"), w -> comment(w, PublicIds.newRandom(), KernelConcept.USER.publicId(), "x")));
        assertTrue(rules(refused).contains(Rule.MODULE_UNDER_BOTH_PARENTS), refused.getMessage());
    }

    @Test
    void offModeSkipsTheCheck() {
        System.setProperty(CommitBoundary.MODE_PROPERTY, "off");
        PublicId note = PublicIds.newRandom();
        commit(writer(journals, "local note"), w -> comment(w, note, KernelConcept.USER.publicId(), "a journal line"));
        commit(knowledge("knowledge about a note"), w -> comment(w, PublicIds.newRandom(), note, "cites the journal"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static List<Rule> rules(BoundaryViolationException refused) {
        return refused.violations().stream().map(BoundaryViolation::rule).toList();
    }

    private static ConceptFacade concept() {
        return EntityProxy.Concept.make(PublicIds.newRandom());
    }

    private StampedWriter knowledge(String name) {
        return writer(IkeConcept.DEVELOPMENT_MODULE, name);
    }

    private StampedWriter writer(ConceptFacade module, String name) {
        return StampedWriter.open(name, State.ACTIVE, ++time, KernelConcept.USER, module, KernelConcept.DEVELOPMENT_PATH);
    }

    private static void commit(StampedWriter writer, Consumer<StampedWriter> body) {
        try (writer) {
            body.accept(writer);
            writer.commit();
        }
    }

    /** The public id of the STAMP a component's first version was written under. */
    private static PublicId stampOf(PublicId component) {
        return EntityHandle.get(component).expectEntity().versions().getFirst().stamp().publicId();
    }

    private static void comment(StampedWriter writer, PublicId semantic, PublicId about, String text) {
        writer.semantic(semantic, KernelPattern.COMMENT_PATTERN.publicId(), about, text);
    }

    /** Writes a module concept whose stated axioms make it a kind of the parents. */
    private static void module(StampedWriter writer, ConceptFacade module, ConceptFacade... parents) {
        writer.concept(module.publicId());
        LogicalExpressionBuilder leb = new LogicalExpressionBuilder();
        dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom.Atom.ConceptAxiom[] atoms =
                new dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom.Atom.ConceptAxiom[parents.length];
        for (int i = 0; i < parents.length; i++) {
            atoms[i] = leb.ConceptAxiom(parents[i]);
        }
        leb.NecessarySet(leb.And(atoms));
        writer.semantic(PublicIds.newRandom(), KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.publicId(), module.publicId(),
                (DiTreeEntity) leb.build().sourceGraph());
    }
}
