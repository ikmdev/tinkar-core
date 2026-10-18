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
import dev.ikm.tinkar.common.service.DataActivity;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.SemanticRecordBuilder;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import dev.ikm.tinkar.entity.SemanticVersionRecordBuilder;
import dev.ikm.tinkar.entity.graph.DiTreeCorrelation;
import dev.ikm.tinkar.entity.graph.DiTreeCorrelation.Phase;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.DiTreeIsomorphism;
import dev.ikm.tinkar.entity.graph.VertexDerivation;
import dev.ikm.tinkar.entity.graph.VertexIdentityWrite;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpressionBuilder;
import dev.ikm.tinkar.entity.transaction.StampedWriter;
import dev.ikm.tinkar.entity.PublicIdentifierRecord;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.graph.VertexIdentityChains.Version;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import dev.ikm.tinkar.terms.State;
import network.ike.foundation.ike.bindings.IkeConcept;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static dev.ikm.tinkar.integration.graph.DiTreeShuffle.shuffle;
import static dev.ikm.tinkar.integration.graph.DiTreeShuffle.strip;
import static dev.ikm.tinkar.integration.graph.DiTreeShuffle.uuidAt;
import static dev.ikm.tinkar.integration.graph.DiTreeShuffle.withUuids;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Derivation and the write path for vertex identity (arch-vertex-identity-birth,
 * arch-vertex-identity-correlation "Where It Runs"; IKE-Network/ike-issues#1310).
 *
 * <p>A chain of versions from {@link VertexIdentityChains}, every version stripped of its UUIDs,
 * is replayed: the first version derives every UUID, and each later one correlates against the
 * replayed versions before it. The replayed identities must follow the rule exactly as the
 * chain's own do (a vertex continues the one the rule says, a newborn is new), and replaying
 * again, from other strips and shuffles, must give back the same UUIDs: the fixed point of
 * arch-vertex-identity-verification. The same chains written through the store must come out
 * the same way.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VertexIdentityWriteIT {

    private static final long SEED = 20261018L;
    private static final int CHAINS = 200;
    private static final int STORED_CHAINS = 30;
    private static final int EDITS = 12;

    private List<List<Version>> chains;

    @BeforeAll
    void startStore() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
        Random random = new Random(SEED);
        chains = new ArrayList<>();
        for (int i = 0; i < CHAINS; i++) {
            chains.add(VertexIdentityChains.chain(random, EDITS));
        }
    }

    @AfterAll
    void stopStore() {
        TestHelper.stopDatabase();
    }

    // ----- replay -----

    /** For each version, by vertex index of the chain's own tree, the UUID a replay gave it. */
    private static List<UUID[]> replay(List<Version> chain, PublicId semantic, Random random, Map<Phase, Integer> phases) {
        List<DiTreeEntity> history = new ArrayList<>();
        List<UUID[]> replayed = new ArrayList<>();
        for (Version version : chain) {
            DiTreeEntity truth = VertexIdentityChains.build(version.root());
            DiTreeShuffle.Shuffled incoming = shuffle(strip(truth, random), random);
            DiTreeCorrelation.Correlation correlation = DiTreeCorrelation.correlate(
                    shuffled(history, random), incoming.tree(), VertexDerivation.root(semantic, 0));
            history.add(correlation.tree());
            correlation.phases().forEach(phase -> phases.merge(phase, 1, Integer::sum));
            UUID[] uuids = new UUID[truth.vertexMap().size()];
            for (int j = 0; j < uuids.length; j++) {
                uuids[j] = uuidAt(correlation.tree(), incoming.newIndex()[j]);
            }
            replayed.add(uuids);
        }
        return replayed;
    }

    private static List<DiTreeEntity> shuffled(List<DiTreeEntity> history, Random random) {
        return history.stream().map(tree -> shuffle(tree, random).tree()).toList();
    }

    /**
     * Whether replayed UUIDs follow the chain's identities: one replayed UUID per chain UUID and
     * one chain UUID per replayed UUID, across every version.
     */
    private static String followsTheRule(List<Version> chain, List<UUID[]> replayed) {
        Map<UUID, UUID> forward = new HashMap<>();
        Map<UUID, UUID> backward = new HashMap<>();
        for (int i = 0; i < chain.size(); i++) {
            DiTreeEntity truth = VertexIdentityChains.build(chain.get(i).root());
            for (int j = 0; j < replayed.get(i).length; j++) {
                UUID expected = uuidAt(truth, j);
                UUID got = replayed.get(i)[j];
                UUID before = forward.putIfAbsent(expected, got);
                UUID back = backward.putIfAbsent(got, expected);
                if (before != null && !before.equals(got) || back != null && !back.equals(expected)) {
                    return "version " + i + " (" + chain.get(i).edit() + ") vertex " + j + "\n" + chain.get(i).root();
                }
            }
        }
        return null;
    }

    @Test
    void aReplayFollowsTheRuleAndComesBackTheSame() {
        Map<Phase, Integer> phases = new EnumMap<>(Phase.class);
        Random random = new Random(SEED + 1);
        int vertices = 0;
        for (int c = 0; c < chains.size(); c++) {
            List<Version> chain = chains.get(c);
            PublicId semantic = PublicIds.of(UuidT5Generator.get(VertexDerivation.NAMESPACE_V1, "chain " + c));
            List<UUID[]> first = replay(chain, semantic, random, phases);
            String broken = followsTheRule(chain, first);
            assertEquals(null, broken, "chain " + c + ": " + broken);
            List<UUID[]> again = replay(chain, semantic, random, new EnumMap<>(Phase.class));
            for (int i = 0; i < chain.size(); i++) {
                assertEquals(List.of(first.get(i)), List.of(again.get(i)), "chain " + c + " version " + i);
                vertices += first.get(i).length;
            }
            assertEquals(VertexDerivation.root(semantic, 0), first.getFirst()[rootIndex(chain.getFirst())],
                    "the first root derives from the semantic");
        }
        System.out.println("VertexIdentityWriteIT, replay: " + vertices + " vertices; phases " + phases);
        assertTrue(phases.getOrDefault(Phase.DERIVED, 0) > 0, () -> "derived lookups exercised: " + phases);
    }

    private static int rootIndex(Version version) {
        return VertexIdentityChains.build(version.root()).root().vertexIndex();
    }

    /** Two independent births of the same structure derive the same UUIDs. */
    @Test
    void independentBirthsConverge() {
        Random random = new Random(SEED + 2);
        PublicId semantic = PublicIds.of(UUID.randomUUID());
        for (List<Version> chain : chains) {
            DiTreeEntity truth = VertexIdentityChains.build(chain.getFirst().root());
            DiTreeShuffle.Shuffled one = shuffle(strip(truth, random), random);
            DiTreeShuffle.Shuffled two = shuffle(strip(truth, random), random);
            DiTreeEntity a = DiTreeCorrelation.correlate(List.of(), one.tree(), VertexDerivation.root(semantic, 0)).tree();
            DiTreeEntity b = DiTreeCorrelation.correlate(List.of(), two.tree(), VertexDerivation.root(semantic, 0)).tree();
            for (int j = 0; j < truth.vertexMap().size(); j++) {
                assertEquals(uuidAt(a, one.newIndex()[j]), uuidAt(b, two.newIndex()[j]));
            }
        }
    }

    // ----- the write path -----

    private static StampedWriter open(String name, long time) {
        return StampedWriter.open(name, State.ACTIVE, time,
                KernelConcept.USER, IkeConcept.DEVELOPMENT_MODULE, KernelConcept.DEVELOPMENT_PATH);
    }

    /** The stored versions' trees, oldest first, with the input each was written from. */
    @Test
    void theStoreGivesWhatAReplayGives() {
        Random random = new Random(SEED + 3);
        long time = 1_700_000_000_000L;
        for (int c = 0; c < STORED_CHAINS; c++) {
            List<Version> chain = chains.get(c);
            PublicId concept = PublicIds.newRandom();
            PublicId semantic = PublicIds.newRandom();
            List<DiTreeShuffle.Shuffled> written = new ArrayList<>();
            for (int i = 0; i < chain.size(); i++) {
                DiTreeShuffle.Shuffled incoming = shuffle(strip(VertexIdentityChains.build(chain.get(i).root()), random), random);
                written.add(incoming);
                StampedWriter writer = open("vertex identity " + c + "." + i, time++);
                if (i == 0) {
                    writer.concept(concept);
                }
                writer.semantic(semantic, KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.publicId(), concept,
                        incoming.tree());
                writer.commit();
            }
            SemanticEntity<SemanticEntityVersion> stored = EntityHandle.get(EntityService.get()
                    .nidForSemantic(KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.publicId(), semantic)).expectSemantic();
            List<SemanticEntityVersion> versions = new ArrayList<>(stored.versions().castToList());
            versions.sort(Comparator.comparingLong(version -> version.stamp().time()));
            assertEquals(chain.size(), versions.size());
            List<UUID[]> uuids = new ArrayList<>();
            for (int i = 0; i < chain.size(); i++) {
                DiTreeEntity tree = (DiTreeEntity) versions.get(i).fieldValues().get(0);
                assertTrue(DiTreeIsomorphism.isomorphic(tree, written.get(i).tree()), "the store keeps the content");
                UUID[] byTruth = new UUID[tree.vertexMap().size()];
                for (int j = 0; j < byTruth.length; j++) {
                    byTruth[j] = uuidAt(tree, written.get(i).newIndex()[j]);
                }
                uuids.add(byTruth);
            }
            String broken = followsTheRule(chain, uuids);
            assertEquals(null, broken, "stored chain " + c + ": " + broken);
            assertEquals(VertexDerivation.root(semantic, 0), uuids.getFirst()[rootIndex(chain.getFirst())]);
            List<UUID[]> replayed = replay(chain, semantic, random, new EnumMap<>(Phase.class));
            for (int i = 0; i < chain.size(); i++) {
                assertEquals(List.of(replayed.get(i)), List.of(uuids.get(i)), "the store derives as a replay does");
            }
        }
    }

    /** A set with two successors is not a shape the identity table admits, and is refused. */
    @Test
    void aTreeOfTheWrongShapeIsRefused() {
        LogicalExpressionBuilder builder = new LogicalExpressionBuilder();
        builder.NecessarySet(builder.ConceptAxiom(KernelConcept.LANGUAGE), builder.ConceptAxiom(KernelConcept.PHENOMENON));
        DiTreeEntity tree = (DiTreeEntity) builder.build().sourceGraph();
        PublicId concept = PublicIds.newRandom();
        StampedWriter writer = open("wrong shape", 1_800_000_000_000L);
        writer.concept(concept);
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> writer.semantic(
                PublicIds.newRandom(), KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.publicId(), concept, tree));
        assertTrue(refused.getMessage().contains("NECESSARY_SET"), refused::getMessage);
        writer.cancel();
    }

    /**
     * A change set keeps its UUIDs, never re-derived, and one that gives a kept UUID another
     * meaning is refused when refusal is on ({@link VertexIdentityWrite#CARRIED_BREAKS}).
     */
    @Test
    void aChangeSetKeepsItsUuidsAndIsHeldToTheInvariants() {
        String before = System.getProperty(VertexIdentityWrite.CARRIED_BREAKS);
        System.setProperty(VertexIdentityWrite.CARRIED_BREAKS, "refuse");
        try {
            changeSetKeepsItsUuidsAndIsHeldToTheInvariants();
        } finally {
            if (before == null) {
                System.clearProperty(VertexIdentityWrite.CARRIED_BREAKS);
            } else {
                System.setProperty(VertexIdentityWrite.CARRIED_BREAKS, before);
            }
        }
    }

    private void changeSetKeepsItsUuidsAndIsHeldToTheInvariants() {
        List<Version> chain = chains.getFirst();
        PublicId concept = PublicIds.newRandom();
        PublicId semantic = PublicIds.newRandom();
        DiTreeEntity first = VertexIdentityChains.build(chain.get(0).root());
        DiTreeEntity second = VertexIdentityChains.build(chain.get(1).root());

        StampedWriter writer = open("change set", 1_900_000_000_000L);
        writer.concept(concept);
        writer.commit();
        long nid = EntityService.get().nidForSemantic(KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.publicId(), semantic);

        // a change set: kept as it arrives, its own UUIDs
        StampedWriter loading = open("change set, loaded", 1_900_000_000_001L);
        putLoaded(nid, semantic, concept, loading.stamp().nid(), first);
        DiTreeEntity stored = storedTree(nid, loading.stamp().nid());
        assertNotNull(stored);
        for (int j = 0; j < first.vertexMap().size(); j++) {
            assertEquals(uuidAt(first, j), uuidAt(stored, j), "a change set's UUIDs are kept");
        }
        loading.commit();

        // a later version that gives the root's UUID to another vertex
        DiTreeEntity swapped = withUuids(second, j -> j == second.root().vertexIndex()
                ? uuidAt(first, first.successors(first.root().vertexIndex()).get(0)) : uuidAt(second, j));
        StampedWriter breaking = open("change set, broken", 1_900_000_000_002L);
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> putLoaded(nid, semantic, concept, breaking.stamp().nid(), swapped));
        assertTrue(refused.getMessage().contains("changed meaning"), refused::getMessage);
        breaking.cancel();
    }

    private static void putLoaded(long nid, PublicId semantic, PublicId concept, long stampNid, DiTreeEntity tree) {
        PublicIdentifierRecord identifier = PublicIdentifierRecord.make(semantic);
        RecordListBuilder<SemanticVersionRecord> versions = RecordListBuilder.make();
        SemanticRecord record = SemanticRecordBuilder.builder()
                .nid(nid)
                .mostSignificantBits(identifier.mostSignificantBits())
                .leastSignificantBits(identifier.leastSignificantBits())
                .additionalUuidLongs(identifier.additionalUuidLongs())
                .patternNid(EntityService.get().nidForPattern(KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.publicId()))
                .referencedComponentNid(EntityService.get().nidForPublicId(concept))
                .versions(versions)
                .build();
        versions.add(SemanticVersionRecordBuilder.builder().chronology(record).stampNid(stampNid)
                .fieldValues(Lists.immutable.of(tree)).build());
        EntityService.get().putEntity(SemanticRecordBuilder.builder(record).versions(versions.toImmutable()).build(),
                DataActivity.LOADING_CHANGE_SET);
    }

    private static DiTreeEntity storedTree(long nid, long stampNid) {
        SemanticEntity<SemanticEntityVersion> semantic = EntityHandle.get(nid).expectSemantic();
        for (SemanticEntityVersion version : semantic.versions()) {
            if (version.stampNid() == stampNid) {
                return (DiTreeEntity) version.fieldValues().get(0);
            }
        }
        return null;
    }
}
