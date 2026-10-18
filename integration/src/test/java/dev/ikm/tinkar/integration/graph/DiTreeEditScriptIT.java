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
package dev.ikm.tinkar.integration.graph;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.stamp.change.ChangeChronology;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.graph.DiTreeCommonGraph;
import dev.ikm.tinkar.entity.graph.DiTreeEditScript;
import dev.ikm.tinkar.entity.graph.DiTreeEditScript.Delete;
import dev.ikm.tinkar.entity.graph.DiTreeEditScript.Edit;
import dev.ikm.tinkar.entity.graph.DiTreeEditScript.Insert;
import dev.ikm.tinkar.entity.graph.DiTreeEditScript.Move;
import dev.ikm.tinkar.entity.graph.DiTreeEditScript.Update;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpressionBuilder;
import dev.ikm.tinkar.entity.transaction.StampedWriter;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import dev.ikm.tinkar.terms.State;
import network.ike.foundation.ike.bindings.IkeConcept;
import org.eclipse.collections.api.factory.primitive.LongLists;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static dev.ikm.tinkar.integration.graph.DiTreeShuffle.strip;
import static dev.ikm.tinkar.integration.graph.DiTreeShuffle.uuidAt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The edit script and the common graph read off vertex UUIDs (arch-vertex-identity-analysis;
 * IKE-Network/ike-issues#1310): each of insert, delete, move and update on hand-built versions
 * that hold settled UUIDs, the common graph of three versions, and a role moved from the
 * necessary set to a sufficient set through the store, read by the change chronology as one move.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DiTreeEditScriptIT {

    private static final UUID ROOT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NECESSARY = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID NECESSARY_AND = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID PARENT = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID ROLE = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID FILLER = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final UUID SUFFICIENT = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final UUID SUFFICIENT_AND = UUID.fromString("00000000-0000-0000-0000-000000000008");
    private static final UUID OTHER_PARENT = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private static final UUID ADDED = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @BeforeAll
    void startStore() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
    }

    @AfterAll
    void stopStore() {
        TestHelper.stopDatabase();
    }

    /**
     * A definition: a necessary set with a parent and, unless it moved, a role; a sufficient set
     * with another parent and, if it moved there, the role.
     *
     * @param roleSufficient whether the role is in the sufficient set
     * @param filler         the role's filler
     * @param withParent     whether the necessary set keeps its parent
     * @param added          a parent added to the necessary set, or null
     */
    private record Definition(boolean roleSufficient, ConceptFacade filler, boolean withParent, ConceptFacade added) {
        static Definition first() {
            return new Definition(false, KernelConcept.PHENOMENON, true, null);
        }
    }

    private static DiTreeEntity build(Definition definition) {
        LogicalExpressionBuilder builder = new LogicalExpressionBuilder(ROOT);
        LogicalAxiom.Atom role = builder.SomeRole(ROLE, KernelConcept.ROLE_TYPE, builder.ConceptAxiom(FILLER, definition.filler()));
        List<LogicalAxiom.Atom> necessary = new ArrayList<>();
        if (definition.withParent()) {
            necessary.add(builder.ConceptAxiom(PARENT, KernelConcept.LANGUAGE));
        }
        if (definition.added() != null) {
            necessary.add(builder.ConceptAxiom(ADDED, definition.added()));
        }
        List<LogicalAxiom.Atom> sufficient = new ArrayList<>(List.of(builder.ConceptAxiom(OTHER_PARENT, KernelConcept.ENGLISH_LANGUAGE)));
        (definition.roleSufficient() ? sufficient : necessary).add(role);
        builder.NecessarySet(NECESSARY, builder.And(NECESSARY_AND, necessary.toArray(LogicalAxiom.Atom[]::new)));
        builder.SufficientSet(SUFFICIENT, builder.And(SUFFICIENT_AND, sufficient.toArray(LogicalAxiom.Atom[]::new)));
        return (DiTreeEntity) builder.build().sourceGraph();
    }

    private static DiTreeEditScript script(Definition before, Definition after) {
        return DiTreeEditScript.between(build(before), build(after));
    }

    // ----- each action -----

    @Test
    void theSameVersionHasNoEdits() {
        assertTrue(script(Definition.first(), Definition.first()).isEmpty());
    }

    @Test
    void aUuidThatAppearsIsAnInsert() {
        DiTreeEditScript script = script(Definition.first(),
                new Definition(false, KernelConcept.PHENOMENON, true, KernelConcept.DESCRIPTION_TYPE));
        assertEquals(1, script.edits().size(), script::toString);
        Insert insert = (Insert) script.edits().getFirst();
        assertEquals(ADDED, insert.uuid());
        assertEquals(NECESSARY_AND, insert.parent());
        assertTrue(script.toText().contains("Additions"), script::toText);
    }

    @Test
    void aUuidThatDisappearsIsADelete() {
        DiTreeEditScript script = script(Definition.first(), new Definition(false, KernelConcept.PHENOMENON, false, null));
        assertEquals(1, script.edits().size(), script::toString);
        Delete delete = (Delete) script.edits().getFirst();
        assertEquals(PARENT, delete.uuid());
        assertEquals(NECESSARY_AND, delete.parent());
        assertTrue(script.toText().contains("Deletions"), script::toText);
    }

    @Test
    void aRoleMovedToASufficientSetIsOneMove() {
        DiTreeEditScript script = script(Definition.first(), new Definition(true, KernelConcept.PHENOMENON, true, null));
        assertEquals(List.of(new Move(ROLE, script.moves().getFirst().beforeIndex(), script.moves().getFirst().afterIndex(),
                NECESSARY_AND, SUFFICIENT_AND)), script.edits().castToList(), "one move, the filler travelling with it");
        String text = script.toText();
        assertTrue(text.contains("Moves") && !text.contains("Additions") && !text.contains("Deletions"), text);
    }

    @Test
    void aRefinedFillerIsOneUpdate() {
        DiTreeEditScript script = script(Definition.first(), new Definition(false, KernelConcept.LANGUAGE, true, null));
        assertEquals(1, script.edits().size(), script::toString);
        Update update = (Update) script.edits().getFirst();
        assertEquals(FILLER, update.uuid());
        assertFalse(update.meaningChanged());
        assertEquals(LongLists.immutable.of(KernelConcept.CONCEPT_REFERENCE.nid()), update.propertyKeys());
        assertTrue(script.toText().contains("Updates"), script::toText);
    }

    @Test
    void aMoveAndAnUpdateAreBothRead() {
        DiTreeEditScript script = script(Definition.first(), new Definition(true, KernelConcept.LANGUAGE, false, KernelConcept.DESCRIPTION_TYPE));
        assertEquals(Set.of(ROLE), uuids(script.moves().castToList()));
        assertEquals(Set.of(FILLER), uuids(script.updates().castToList()));
        assertEquals(Set.of(ADDED), uuids(script.inserts().castToList()));
        assertEquals(Set.of(PARENT), uuids(script.deletes().castToList()));
        assertEquals(4, script.edits().size());
    }

    private static Set<UUID> uuids(List<? extends Edit> edits) {
        return Set.copyOf(edits.stream().map(Edit::uuid).toList());
    }

    /** Versions written before vertex identity share no UUID; they are correlated on reading. */
    @Test
    void versionsWithoutIdentityAreCorrelatedFirst() {
        Random random = new Random(1310);
        DiTreeEntity before = strip(build(Definition.first()), random);
        DiTreeEntity after = strip(build(new Definition(true, KernelConcept.PHENOMENON, true, null)), random);
        assertEquals(DiTreeEditScript.between(before, after).edits().size(),
                DiTreeEditScript.between(before, after).inserts().size() + DiTreeEditScript.between(before, after).deletes().size(),
                "by UUID alone, everything is replaced");
        DiTreeEditScript script = DiTreeEditScript.ofVersions(before, after);
        assertEquals(1, script.edits().size(), script::toString);
        assertTrue(script.edits().getFirst() instanceof Move);
    }

    // ----- the common graph -----

    @Test
    void theCommonGraphOfThreeVersions() {
        DiTreeEntity v1 = build(Definition.first());
        DiTreeEntity v2 = build(new Definition(true, KernelConcept.PHENOMENON, true, null));
        DiTreeEntity v3 = build(new Definition(false, KernelConcept.LANGUAGE, true, null));
        DiTreeCommonGraph common = DiTreeCommonGraph.of(List.of(v1, v2, v3));
        Set<UUID> all = Set.of(ROOT, NECESSARY, NECESSARY_AND, PARENT, ROLE, FILLER, SUFFICIENT, SUFFICIENT_AND, OTHER_PARENT);
        assertEquals(all, common.commonVertices());
        assertFalse(common.contentCommonVertices().contains(FILLER), "updated in v3");
        assertEquals(all.size() - 1, common.contentCommonVertices().size());
        assertFalse(common.commonEdges().contains(new DiTreeCommonGraph.Edge(NECESSARY_AND, ROLE)), "moved in v2");
        assertTrue(common.commonEdges().contains(new DiTreeCommonGraph.Edge(ROLE, FILLER)), "the filler travelled with it");
        assertEquals(Set.of(ROOT, NECESSARY, NECESSARY_AND, PARENT, SUFFICIENT, SUFFICIENT_AND, OTHER_PARENT), common.commonTree());
    }

    // ----- through the store and the change chronology -----

    /**
     * A role moved from the necessary set to a sufficient set, written through the store with no
     * UUIDs carried: the write path settles identity, and the change chronology reads one move.
     */
    @Test
    void theChangeChronologyShowsAMovedRoleAsOneMove() {
        Random random = new Random(20261018L);
        PublicId concept = PublicIds.newRandom();
        PublicId semantic = PublicIds.newRandom();
        long time = 1_700_000_000_000L;
        List<Definition> definitions = List.of(Definition.first(), new Definition(true, KernelConcept.PHENOMENON, true, null));
        for (int i = 0; i < definitions.size(); i++) {
            StampedWriter writer = StampedWriter.open("edit script " + i, State.ACTIVE, time + i,
                    KernelConcept.USER, IkeConcept.DEVELOPMENT_MODULE, KernelConcept.DEVELOPMENT_PATH);
            if (i == 0) {
                writer.concept(concept);
            }
            writer.semantic(semantic, KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.publicId(), concept,
                    strip(build(definitions.get(i)), random));
            writer.commit();
        }
        long nid = EntityService.get().nidForSemantic(KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.publicId(), semantic);
        SemanticEntity<SemanticEntityVersion> stored = EntityHandle.get(nid).expectSemantic();
        List<SemanticEntityVersion> versions = new ArrayList<>(stored.versions().castToList());
        versions.sort(Comparator.comparingLong(version -> version.stamp().time()));
        DiTreeEntity before = (DiTreeEntity) versions.get(0).fieldValues().get(0);
        DiTreeEntity after = (DiTreeEntity) versions.get(1).fieldValues().get(0);

        DiTreeEditScript script = DiTreeEditScript.between(before, after);
        assertEquals(1, script.edits().size(), script::toString);
        Move move = (Move) script.edits().getFirst();
        assertEquals(uuidAt(before, before.predecessor(move.beforeIndex()).getAsInt()), move.fromParent());

        ChangeChronology chronology = Calculators.Stamp.DevelopmentLatest().changeChronology(nid);
        String text = chronology.toString(Calculators.View.Default(), false);
        System.out.println(text);
        assertTrue(text.contains("Moves:"), text);
        assertFalse(text.contains("Additions:") || text.contains("Deletions:") || text.contains("Updates:"), text);
    }
}
