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

import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.DiTreeIsomorphism;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.graph.VertexIdentityChains.Version;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static dev.ikm.tinkar.integration.graph.DiTreeShuffle.shuffle;
import static dev.ikm.tinkar.integration.graph.DiTreeShuffle.strip;
import static dev.ikm.tinkar.integration.graph.DiTreeShuffle.uuidAt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The determinism helpers of {@link DiTreeShuffle}: a copy with its vertex indexes and child
 * order shuffled, or its UUIDs replaced, is the same tree to {@link DiTreeIsomorphism}, hashes
 * alike, and (for a shuffle) keeps every vertex's identity, parent and content. The vertex
 * identity oracle relies on this to keep a matcher from leaning on index or child order.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DiTreeShuffleIT {

    private static final long SEED = 20261017L;
    private static final int TREES = 1_000;
    private static final int SHUFFLES = 5;

    @BeforeAll
    void startStore() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
    }

    @AfterAll
    void stopStore() {
        TestHelper.stopDatabase();
    }

    /** Random definitions, and the versions of an edit chain, which carry chosen UUIDs. */
    private static DiTreeEntity randomTree(Random random) {
        if (random.nextBoolean()) {
            return RandomDefinitions.build(RandomDefinitions.randomDefinition(random), null);
        }
        List<Version> chain = VertexIdentityChains.chain(random, 1 + random.nextInt(6));
        return VertexIdentityChains.build(chain.getLast().root());
    }

    @Test
    void aShuffledCopyIsTheSameTreeWithTheSameIdentities() {
        Random random = new Random(SEED);
        int indexesMoved = 0;
        int childrenReordered = 0;
        for (int t = 0; t < TREES; t++) {
            DiTreeEntity tree = randomTree(random);
            long hash = DiTreeIsomorphism.hash(tree);
            Map<UUID, String> identities = identities(tree);
            for (int s = 0; s < SHUFFLES; s++) {
                DiTreeShuffle.Shuffled shuffled = shuffle(tree, random);
                DiTreeEntity copy = shuffled.tree();
                assertTrue(DiTreeIsomorphism.isomorphic(tree, copy), () -> tree + "\n" + copy);
                assertTrue(DiTreeIsomorphism.isomorphic(copy, tree), "symmetric");
                assertEquals(hash, DiTreeIsomorphism.hash(copy), "a shuffle does not change the hash");
                assertEquals(identities, identities(copy), "every vertex keeps its UUID, parent and content");
                for (int i = 0; i < tree.vertexMap().size(); i++) {
                    assertEquals(uuidAt(tree, i), uuidAt(copy, shuffled.newIndex()[i]), "the index map is right");
                    if (shuffled.newIndex()[i] != i) {
                        indexesMoved++;
                    }
                    if (!tree.successors(i).isEmpty() && reordered(tree, copy, shuffled.newIndex(), i)) {
                        childrenReordered++;
                    }
                }
            }
        }
        assertTrue(indexesMoved > 0 && childrenReordered > 0, "the shuffle moves indexes and reorders children");
    }

    @Test
    void aStrippedCopyIsTheSameTreeSharingNoUuid() {
        Random random = new Random(SEED);
        for (int t = 0; t < TREES; t++) {
            DiTreeEntity tree = randomTree(random);
            DiTreeEntity stripped = strip(tree, random);
            assertTrue(DiTreeIsomorphism.isomorphic(tree, stripped));
            assertEquals(DiTreeIsomorphism.hash(tree), DiTreeIsomorphism.hash(stripped));
            Set<UUID> uuids = new HashSet<>(identities(tree).keySet());
            uuids.retainAll(identities(stripped).keySet());
            assertTrue(uuids.isEmpty(), "no UUID survives a strip");
            assertEquals(tree.vertexMap().size(), identities(stripped).size(), "every stripped UUID is distinct");
        }
    }

    /** Each vertex's identity: its UUID, with its parent's UUID and its own content. */
    private static Map<UUID, String> identities(DiTreeEntity tree) {
        Map<UUID, String> identities = new HashMap<>();
        for (EntityVertex vertex : tree.vertexMap()) {
            String parent = tree.predecessor(vertex).map(p -> p.asUuidList().getFirst().toString()).orElse("root");
            StringBuilder content = new StringBuilder().append(parent).append(' ').append(vertex.getMeaningNid());
            for (long key : vertex.properties().keySet().toSortedArray()) {
                content.append(' ').append(key).append('=').append(vertex.properties().get(key));
            }
            identities.put(vertex.asUuidList().getFirst(), content.toString());
        }
        return identities;
    }

    /** Whether a vertex's children come in another order in the copy, mapped back to the original's indexes. */
    private static boolean reordered(DiTreeEntity tree, DiTreeEntity copy, int[] newIndex, int index) {
        int[] original = tree.successors(index).toArray();
        int[] copied = copy.successors(newIndex[index]).toArray();
        for (int k = 0; k < original.length; k++) {
            if (newIndex[original[k]] != copied[k]) {
                return true;
            }
        }
        return false;
    }
}
