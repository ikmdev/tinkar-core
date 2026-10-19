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

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.entity.graph.DiTreeCorrelation;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.DiTreeIsomorphism;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.entity.graph.VertexDerivation;
import dev.ikm.tinkar.entity.graph.VertexIdentityTable;
import dev.ikm.tinkar.entity.graph.VertexIdentityWrite;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.IdentityRule;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.KernelConcept;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.map.primitive.MutableIntIntMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntIntMaps;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.eclipse.collections.impl.factory.primitive.LongObjectMaps;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static dev.ikm.tinkar.integration.graph.DiTreeShuffle.permute;
import static dev.ikm.tinkar.integration.graph.DiTreeShuffle.strip;
import static dev.ikm.tinkar.integration.graph.DiTreeShuffle.uuidAt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vertex identity in a tree whose vocabulary has ordered successors (arch-vertex-identity-rule,
 * "Grounding": a constructor that is not commutative identifies its operands by position;
 * IKE-Network/ike-issues#1310), under a test identity table over a few kernel concepts:
 *
 * <ul>
 *   <li>{@code Language}: the root, an optional successor;
 *   <li>{@code English language}: a conjunction, unordered operands;
 *   <li>{@code Equal to}: a comparison, an ordered pair, left then right;
 *   <li>{@code Phenomenon}: a literal, a leaf whose value identifies nothing.
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderedTreeIdentityIT {

    private VertexIdentityTable table;

    @BeforeAll
    void startStore() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
        table = new VertexIdentityTable() {
            @Override
            public boolean covers(long meaningNid) {
                return meaningNid == KernelConcept.LANGUAGE.nid() || meaningNid == KernelConcept.ENGLISH_LANGUAGE.nid()
                        || meaningNid == KernelConcept.EQUAL_TO.nid() || meaningNid == KernelConcept.PHENOMENON.nid();
            }

            @Override
            public IdentityRule rule(long meaningNid) {
                IdentityRule.Successors successors = meaningNid == KernelConcept.LANGUAGE.nid() ? IdentityRule.Successors.OPTIONAL
                        : meaningNid == KernelConcept.ENGLISH_LANGUAGE.nid() ? IdentityRule.Successors.OPERANDS
                        : meaningNid == KernelConcept.EQUAL_TO.nid() ? IdentityRule.Successors.ORDERED
                        : IdentityRule.Successors.NONE;
                return new IdentityRule(successors, Lists.immutable.empty());
            }
        };
    }

    @AfterAll
    void stopStore() {
        TestHelper.stopDatabase();
    }

    // ----- trees -----

    /** A vertex to build: its meaning, a literal value or none, and its children in order. */
    private record V(ConceptFacade meaning, Integer value, List<V> children) {
    }

    private static V root(V child) {
        return new V(KernelConcept.LANGUAGE, null, List.of(child));
    }

    private static V and(V... operands) {
        return new V(KernelConcept.ENGLISH_LANGUAGE, null, List.of(operands));
    }

    private static V equal(V left, V right) {
        return new V(KernelConcept.EQUAL_TO, null, List.of(left, right));
    }

    private static V literal(int value) {
        return new V(KernelConcept.PHENOMENON, value, List.of());
    }

    /** The tree, vertices numbered in preorder, children in the order given, random UUIDs. */
    private static DiTreeEntity build(V root) {
        List<EntityVertex> vertices = new ArrayList<>();
        MutableIntObjectMap<ImmutableIntList> successors = IntObjectMaps.mutable.empty();
        MutableIntIntMap predecessors = IntIntMaps.mutable.empty();
        add(root, vertices, successors, predecessors);
        return new DiTreeEntity(vertices.getFirst(), Lists.immutable.ofAll(vertices), successors.toImmutable(),
                predecessors.toImmutable());
    }

    private static int add(V node, List<EntityVertex> vertices, MutableIntObjectMap<ImmutableIntList> successors,
                           MutableIntIntMap predecessors) {
        EntityVertex vertex = EntityVertex.make(UUID.randomUUID(), node.meaning().nid());
        MutableLongObjectMap<Object> properties = LongObjectMaps.mutable.empty();
        if (node.value() != null) {
            properties.put(KernelConcept.LITERAL_VALUE.nid(), node.value());
        }
        vertex.setProperties(properties);
        int index = vertices.size();
        vertex.setVertexIndex(index);
        vertices.add(vertex);
        var children = IntLists.mutable.empty();
        for (V child : node.children()) {
            int childIndex = add(child, vertices, successors, predecessors);
            children.add(childIndex);
            predecessors.put(childIndex, index);
        }
        if (!children.isEmpty()) {
            successors.put(index, children.toImmutable());
        }
        return index;
    }

    private DiTreeEntity correlate(List<DiTreeEntity> history, DiTreeEntity incoming) {
        return DiTreeCorrelation.correlate(history, incoming, VertexDerivation.root(PublicIds.of(
                UUID.fromString("6c1fdb7e-0d2f-4a9c-9e1b-6a0e7f5f2a11")), 0), table).tree();
    }

    // ----- tests -----

    @Test
    void anOrderedPairHashesByPosition() {
        DiTreeEntity ab = build(root(equal(literal(1), literal(2))));
        DiTreeEntity ba = build(root(equal(literal(2), literal(1))));
        assertEquals(DiTreeIsomorphism.vertexHashes(ab)[0], DiTreeIsomorphism.vertexHashes(ba)[0],
                "unordered, the swap is the same tree");
        assertNotEquals(DiTreeIsomorphism.vertexHashes(ab, table::ordered)[0],
                DiTreeIsomorphism.vertexHashes(ba, table::ordered)[0], "ordered, it is another");
        assertNull(DiTreeIsomorphism.correspondence(ab, DiTreeIsomorphism.vertexHashes(ab, table::ordered), 0,
                ba, DiTreeIsomorphism.vertexHashes(ba, table::ordered), 0, table::ordered));
    }

    /** Swapping a comparison's sides is an update at each position, never a move. */
    @Test
    void swappedSidesAreUpdatedInPlace() {
        DiTreeEntity first = correlate(List.of(), build(root(and(equal(literal(1), literal(2)), equal(literal(3), literal(4))))));
        DiTreeEntity swapped = correlate(List.of(first),
                build(root(and(equal(literal(2), literal(1)), equal(literal(3), literal(4))))));
        // preorder: 0 root, 1 and, 2 equal, 3 left, 4 right, 5 equal, 6 left, 7 right
        for (int i = 0; i < 8; i++) {
            assertEquals(uuidAt(first, i), uuidAt(swapped, i), "vertex " + i + " keeps its UUID");
        }
        DiTreeEntity changed = correlate(List.of(first, swapped),
                build(root(and(equal(literal(2), literal(9)), equal(literal(3), literal(4))))));
        for (int i = 0; i < 8; i++) {
            assertEquals(uuidAt(first, i), uuidAt(changed, i), "a changed right side is updated: vertex " + i);
        }
    }

    /** Identical operands of an ordered pair, x = x, are told apart by position. */
    @Test
    void identicalSidesDeriveApart() {
        DiTreeEntity same = correlate(List.of(), build(root(equal(literal(5), literal(5)))));
        assertNotEquals(uuidAt(same, 2), uuidAt(same, 3));
        DiTreeEntity again = correlate(List.of(), build(root(equal(literal(5), literal(5)))));
        for (int i = 0; i < 4; i++) {
            assertEquals(uuidAt(same, i), uuidAt(again, i), "independent births converge: vertex " + i);
        }
    }

    /** The same inputs with their vertex indexes permuted, each sequence kept in order, get the same UUIDs. */
    @Test
    void indexOrderDoesNotMatter() {
        Random random = new Random(20261018L);
        DiTreeEntity first = correlate(List.of(), build(root(and(equal(literal(1), literal(2)), equal(literal(1), literal(3))))));
        DiTreeEntity next = strip(build(root(and(equal(literal(2), literal(2)), equal(literal(1), literal(3)), literal(7)))), random);
        DiTreeEntity answer = correlate(List.of(first), next);
        for (int s = 0; s < 10; s++) {
            DiTreeShuffle.Shuffled history = permute(first, random);
            DiTreeShuffle.Shuffled incoming = permute(next, random);
            DiTreeEntity again = correlate(List.of(history.tree()), incoming.tree());
            for (int i = 0; i < next.vertexMap().size(); i++) {
                assertEquals(uuidAt(answer, i), uuidAt(again, incoming.newIndex()[i]), "vertex " + i);
            }
        }
    }

    /** The table's shape: an optional successor holds at most one, a leaf none. */
    @Test
    void theTablesShapeIsChecked() {
        assertNull(VertexIdentityWrite.shapeBreak(build(root(equal(literal(1), literal(2)))), table));
        V twoUnderRoot = new V(KernelConcept.LANGUAGE, null, List.of(literal(1), literal(2)));
        assertTrue(VertexIdentityWrite.shapeBreak(build(twoUnderRoot), table) != null);
        assertTrue(VertexIdentityWrite.shapeBreak(build(new V(KernelConcept.LANGUAGE, null, List.of())), table) == null,
                "an optional successor may be absent");
    }
}
