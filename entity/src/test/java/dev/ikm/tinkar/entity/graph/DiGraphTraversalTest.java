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
package dev.ikm.tinkar.entity.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic checks on {@link DiGraphEntity.Builder} and the two traversals of
 * {@link DiGraphAbstract}, covering the defects the DiGraph readiness spike recorded
 * ({@code dev-digraph-readiness-spike}): the first vertex re-added on every edge, the
 * breadth-first walk failing on a leaf, and the depth-first walk refusing depth past 100.
 */
class DiGraphTraversalTest {

    private static EntityVertex vertex(int ordinal) {
        return EntityVertex.make(new UUID(0L, ordinal + 1L), -100L);
    }

    /** A chain v0 → v1 → … → v(length-1), rooted at v0. */
    private static DiGraphEntity<EntityVertex> chain(int length) {
        DiGraphEntity.Builder<EntityVertex> builder = DiGraphEntity.builder();
        EntityVertex previous = vertex(0);
        builder.addRoot(previous);
        for (int ordinal = 1; ordinal < length; ordinal++) {
            EntityVertex next = vertex(ordinal);
            builder.addEdge(next, previous);
            previous = next;
        }
        return builder.build();
    }

    @Test
    @DisplayName("The builder adds the root at index 0 once, however many edges touch it")
    void builderAddsTheFirstVertexOnce() {
        EntityVertex root = vertex(0);
        EntityVertex left = vertex(1);
        EntityVertex right = vertex(2);
        EntityVertex leaf = vertex(3);

        DiGraphEntity<EntityVertex> graph = DiGraphEntity.<EntityVertex>builder()
                .addRoot(root)
                .addEdge(left, root)
                .addEdge(right, root)
                .addEdge(leaf, left)
                .build();

        assertEquals(4, graph.vertexCount(), "each vertex is in the graph exactly once");
        assertEquals(1, graph.roots().size(), "the root is listed once");
        assertEquals(0, root.vertexIndex(), "the root keeps index 0");
        assertEquals(2, graph.successors(root).size(), "both edges from the root are kept");
    }

    @Test
    @DisplayName("A breadth-first walk reaches a leaf and marks it, with no successor entry")
    void breadthFirstReachesALeaf() {
        DiGraphEntity<EntityVertex> graph = chain(3);
        VertexVisitData visit = new VertexVisitData(graph.vertexCount());

        graph.breadthFirstProcess(0, visit);

        assertEquals(3, visit.vertexesVisitedCount(), "every vertex of the chain is visited");
        assertTrue(visit.leafVertexIndexes().get(2), "the last vertex is the leaf");
        assertEquals(2, visit.distance(2), "the leaf is two edges from the root");
    }

    @Test
    @DisplayName("A depth-first walk visits a thousand-deep chain, where recursion stopped at 100")
    void depthFirstWalksAThousandDeepChain() {
        int length = 1_000;
        DiGraphEntity<EntityVertex> graph = chain(length);
        VertexVisitData visit = new VertexVisitData(graph.vertexCount());

        graph.depthFirstProcess(0, visit);

        assertEquals(length, visit.vertexesVisitedCount(), "every vertex of the chain is visited");
        assertEquals(length - 1, visit.maxDepth(), "the leaf is at the chain's depth");
        assertTrue(visit.leafVertexIndexes().get(length - 1), "the last vertex is the leaf");
        // Discovery and finish share one sequence counter, as in the recursive form: the
        // chain's vertices are discovered 0 … length-1, then finished length … 2·length-1.
        assertEquals(length - 1, visit.discoverySequence(length - 1), "the leaf is discovered last");
        assertEquals(length, visit.finishSequence(length - 1), "the leaf finishes first");
        assertEquals(2 * length - 1, visit.finishSequence(0), "the root finishes last");
    }

    @Test
    @DisplayName("A depth-first walk discovers and finishes vertices in the recursive order")
    void depthFirstKeepsTheRecursiveOrder() {
        EntityVertex root = vertex(0);
        EntityVertex left = vertex(1);
        EntityVertex right = vertex(2);
        EntityVertex leaf = vertex(3);
        DiGraphEntity<EntityVertex> graph = DiGraphEntity.<EntityVertex>builder()
                .addRoot(root)
                .addEdge(left, root)
                .addEdge(right, root)
                .addEdge(leaf, left)
                .build();
        VertexVisitData visit = new VertexVisitData(graph.vertexCount());

        graph.depthFirstProcess(0, visit);

        // One sequence counter stamps discoveries and finishes alike, as in the recursive form.
        assertEquals(0, visit.discoverySequence(root.vertexIndex()), "the root is discovered first");
        assertEquals(1, visit.discoverySequence(left.vertexIndex()), "then the first child");
        assertEquals(2, visit.discoverySequence(leaf.vertexIndex()), "then that child's child");
        assertEquals(3, visit.finishSequence(leaf.vertexIndex()), "the deepest leaf finishes first");
        assertEquals(4, visit.finishSequence(left.vertexIndex()), "then its parent");
        assertEquals(5, visit.discoverySequence(right.vertexIndex()),
                "the second child is discovered only after the first child's subtree finished");
        assertEquals(6, visit.finishSequence(right.vertexIndex()), "then it finishes");
        assertEquals(7, visit.finishSequence(root.vertexIndex()), "the root finishes last");
        assertEquals(left.vertexIndex(), visit.predecessorIndex(leaf.vertexIndex()).orElseThrow(),
                "the leaf's predecessor is the child it was reached through");
    }
}
