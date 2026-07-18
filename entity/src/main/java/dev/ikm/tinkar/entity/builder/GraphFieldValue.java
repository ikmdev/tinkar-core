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
package dev.ikm.tinkar.entity.builder;

import dev.ikm.tinkar.entity.graph.DiGraphEntity;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.terms.EntityProxy;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.MutableIntIntMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntIntMaps;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;

import java.util.List;
import java.util.UUID;

/**
 * A compose-safe, replay-stable graph field value for the ledger builders
 * (IKE-Network/ike-issues#885): DiTree- and DiGraph-typed pattern fields carry their
 * values as these specs — vertices in identity form ({@link Vertex}: a declared vertex
 * UUID plus the meaning concept's identity handle), edges by vertex index — and the
 * ledger materializes the nid-based {@link DiTreeEntity}/{@link DiGraphEntity} at
 * {@link KnowledgeSet#write()}, when the store is open. Composing stays store-free, and
 * replay into a fresh store reproduces the same graph, because nothing here is a nid.
 * <p>
 * Vertex indexes are positional: a vertex's index is its position in the spec's vertex
 * list, and edges and roots reference those positions. An {@link Edge} is the directed
 * edge {@code parent → child} — the successor direction, matching
 * {@code DiGraphEntity.Builder#addEdge(child, parent)}.
 * <p>
 * Stated axioms remain out of scope: an axiom semantic's tree is composed by
 * {@code LogicalExpressionBuilder} through {@code ConceptBuilder#statedAxioms}, which
 * derives its vertex identities itself. This carrier is for ordinary DiTree/DiGraph
 * pattern fields — first exercised by the Data Type Defaults Pattern's loud defaults.
 */
public sealed interface GraphFieldValue permits GraphFieldValue.Tree, GraphFieldValue.Graph {

    /**
     * One vertex of a graph field value, in identity form.
     *
     * @param vertexId the vertex's own identity — declared by the composer, typically a
     *                 type-5 derivation such as {@code KnowledgeSet.uuidFor(name)}, so
     *                 replay reproduces the same vertex
     * @param meaning  the concept this vertex means, as an identity handle
     */
    record Vertex(UUID vertexId, EntityProxy.Concept meaning) {

        /**
         * Validates the vertex.
         *
         * @throws IllegalArgumentException if the vertex id or meaning is null
         */
        public Vertex {
            if (vertexId == null || meaning == null) {
                throw new IllegalArgumentException(
                        "A graph vertex requires both a declared vertex UUID and a meaning concept");
            }
        }
    }

    /**
     * One directed edge of a graph field value: {@code parent → child} in the successor
     * direction, both ends referencing vertex positions in the spec's vertex list.
     *
     * @param parentIndex the position of the edge's source (predecessor) vertex
     * @param childIndex  the position of the edge's destination (successor) vertex
     */
    record Edge(int parentIndex, int childIndex) {

        /**
         * Validates the edge.
         *
         * @throws IllegalArgumentException if either index is negative
         */
        public Edge {
            if (parentIndex < 0 || childIndex < 0) {
                throw new IllegalArgumentException(
                        "Edge vertex indexes must be non-negative: " + parentIndex + " → " + childIndex);
            }
        }
    }

    /**
     * A DiTree-typed field value: a rooted tree — every vertex except the root has
     * exactly one parent.
     *
     * @param vertices  the tree's vertices; a vertex's index is its position here
     * @param rootIndex the root vertex's position
     * @param edges     the tree's edges, parent → child
     */
    record Tree(List<Vertex> vertices, int rootIndex, List<Edge> edges) implements GraphFieldValue {

        /**
         * Validates and defensively copies the spec.
         *
         * @throws IllegalArgumentException if the vertex list is empty, the root index is
         *                                  out of range, an edge references a vertex out
         *                                  of range, or a vertex has more than one parent
         */
        public Tree {
            vertices = List.copyOf(vertices);
            edges = List.copyOf(edges);
            if (vertices.isEmpty()) {
                throw new IllegalArgumentException("A tree field value requires at least its root vertex");
            }
            if (rootIndex < 0 || rootIndex >= vertices.size()) {
                throw new IllegalArgumentException(
                        "Root index " + rootIndex + " is out of range for " + vertices.size() + " vertices");
            }
            boolean[] hasParent = new boolean[vertices.size()];
            for (Edge edge : edges) {
                requireInRange(edge, vertices.size());
                if (hasParent[edge.childIndex()]) {
                    throw new IllegalArgumentException(
                            "Vertex " + edge.childIndex() + " has more than one parent — not a tree");
                }
                hasParent[edge.childIndex()] = true;
            }
        }

        /**
         * Materializes the nid-based tree entity. Requires an open store: meaning
         * concepts resolve to nids here.
         *
         * @return the tree entity, vertices indexed as declared
         */
        public DiTreeEntity toDiTreeEntity() {
            MutableList<EntityVertex> vertexMap = materializeVertices(vertices);
            MutableIntObjectMap<ImmutableIntList> successors =
                    successorsOf(edges);
            MutableIntIntMap predecessors = IntIntMaps.mutable.ofInitialCapacity(edges.size());
            for (Edge edge : edges) {
                predecessors.put(edge.childIndex(), edge.parentIndex());
            }
            return new DiTreeEntity(vertexMap.get(rootIndex), vertexMap.toImmutable(),
                    successors.toImmutable(), predecessors.toImmutable());
        }
    }

    /**
     * A DiGraph-typed field value: a directed graph — vertices may have any number of
     * parents, cycles included, and the root list holds the entry vertices (empty for a
     * pure cycle, which has none).
     *
     * @param vertices    the graph's vertices; a vertex's index is its position here
     * @param rootIndexes the entry vertices' positions, possibly empty
     * @param edges       the graph's edges, parent → child
     */
    record Graph(List<Vertex> vertices, List<Integer> rootIndexes, List<Edge> edges)
            implements GraphFieldValue {

        /**
         * Validates and defensively copies the spec.
         *
         * @throws IllegalArgumentException if the vertex list is empty, or a root or edge
         *                                  references a vertex out of range
         */
        public Graph {
            vertices = List.copyOf(vertices);
            rootIndexes = List.copyOf(rootIndexes);
            edges = List.copyOf(edges);
            if (vertices.isEmpty()) {
                throw new IllegalArgumentException("A graph field value requires at least one vertex");
            }
            for (int rootIndex : rootIndexes) {
                if (rootIndex < 0 || rootIndex >= vertices.size()) {
                    throw new IllegalArgumentException(
                            "Root index " + rootIndex + " is out of range for " + vertices.size() + " vertices");
                }
            }
            for (Edge edge : edges) {
                requireInRange(edge, vertices.size());
            }
        }

        /**
         * Materializes the nid-based graph entity. Requires an open store: meaning
         * concepts resolve to nids here.
         *
         * @return the graph entity, vertices indexed as declared
         */
        public DiGraphEntity<EntityVertex> toDiGraphEntity() {
            MutableList<EntityVertex> vertexMap = materializeVertices(vertices);
            MutableList<EntityVertex> roots = Lists.mutable.ofInitialCapacity(rootIndexes.size());
            for (int rootIndex : rootIndexes) {
                roots.add(vertexMap.get(rootIndex));
            }
            MutableIntObjectMap<ImmutableIntList> successors =
                    successorsOf(edges);
            MutableIntObjectMap<MutableIntList> predecessorLists = IntObjectMaps.mutable.empty();
            for (Edge edge : edges) {
                predecessorLists.getIfAbsentPut(edge.childIndex(), IntLists.mutable::empty)
                        .add(edge.parentIndex());
            }
            MutableIntObjectMap<ImmutableIntList> predecessors =
                    IntObjectMaps.mutable.ofInitialCapacity(predecessorLists.size());
            predecessorLists.forEachKeyValue((childIndex, parentList) ->
                    predecessors.put(childIndex, parentList.toImmutable()));
            return new DiGraphEntity<>(roots.toImmutable(), vertexMap.toImmutable(),
                    successors.toImmutable(), predecessors.toImmutable());
        }
    }

    /**
     * Materializes the vertex list: each spec vertex becomes an {@link EntityVertex}
     * carrying its declared UUID and its meaning's nid, indexed by position.
     *
     * @param vertices the spec vertices
     * @return the entity vertices, indexed as declared
     */
    private static MutableList<EntityVertex> materializeVertices(List<Vertex> vertices) {
        MutableList<EntityVertex> vertexMap = Lists.mutable.ofInitialCapacity(vertices.size());
        for (int index = 0; index < vertices.size(); index++) {
            Vertex vertex = vertices.get(index);
            EntityVertex entityVertex = EntityVertex.make(vertex.vertexId(), vertex.meaning().nid());
            entityVertex.setVertexIndex(index);
            vertexMap.add(entityVertex);
        }
        return vertexMap;
    }

    /**
     * Builds the successor map (parent → children, in edge order) from the edge list.
     *
     * @param edges the spec edges
     * @return the successor map
     */
    private static MutableIntObjectMap<ImmutableIntList>
            successorsOf(List<Edge> edges) {
        MutableIntObjectMap<MutableIntList> successorLists = IntObjectMaps.mutable.empty();
        for (Edge edge : edges) {
            successorLists.getIfAbsentPut(edge.parentIndex(), IntLists.mutable::empty)
                    .add(edge.childIndex());
        }
        MutableIntObjectMap<ImmutableIntList> successors =
                IntObjectMaps.mutable.ofInitialCapacity(successorLists.size());
        successorLists.forEachKeyValue((parentIndex, childList) ->
                successors.put(parentIndex, childList.toImmutable()));
        return successors;
    }

    /**
     * Requires both ends of an edge to reference declared vertex positions.
     *
     * @param edge        the edge to check
     * @param vertexCount the number of declared vertices
     * @throws IllegalArgumentException if either end is out of range
     */
    private static void requireInRange(Edge edge, int vertexCount) {
        if (edge.parentIndex() >= vertexCount || edge.childIndex() >= vertexCount) {
            throw new IllegalArgumentException(
                    "Edge " + edge.parentIndex() + " → " + edge.childIndex()
                            + " references a vertex out of range for " + vertexCount + " vertices");
        }
    }
}
