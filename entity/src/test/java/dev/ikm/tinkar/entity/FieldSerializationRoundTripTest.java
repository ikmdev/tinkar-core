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
package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.component.FieldDataType;
import dev.ikm.tinkar.entity.graph.DiGraphEntity;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntIntMaps;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Native (byte-buffer) round-trip coverage for the field forms the Data Type Defaults
 * Pattern exercises for the first time (IKE-Network/ike-issues#885): object arrays
 * (OBJECT_ARRAY write and read), DiGraph values (the DIGRAPH write case, the root-index
 * read, and the predecessor-map entry count), and buffer alignment — a value written
 * after a graph must read back intact, which fails if the graph leaves unread bytes.
 */
class FieldSerializationRoundTripTest {

    /**
     * Writes the given field values to a buffer and reads them back, token-dispatched,
     * asserting the buffer is fully consumed in step.
     *
     * @param values the field values to round-trip
     * @return the values as read back, in order
     */
    private static Object[] roundTrip(Object... values) {
        ByteBuf writeBuf = ByteBufPool.allocate(4096);
        for (Object value : values) {
            EntityRecordFactory.writeField(writeBuf, value);
        }
        ByteBuf readBuf = ByteBuf.wrapForReading(writeBuf.asArray());
        Object[] readBack = new Object[values.length];
        for (int index = 0; index < values.length; index++) {
            FieldDataType dataType = FieldDataType.fromToken(readBuf.readByte());
            readBack[index] = EntityRecordFactory.readFieldData(readBuf, dataType,
                    EntityRecordFactory.ENTITY_FORMAT_VERSION);
        }
        return readBack;
    }

    /**
     * Builds the two-vertex simple cycle the DiGraph loud default uses: vertices A and B,
     * edges A → B and B → A, no roots (a pure cycle has none).
     *
     * @return the cycle graph
     */
    private static DiGraphEntity<EntityVertex> simpleCycle() {
        EntityVertex vertexA = EntityVertex.make(UUID.fromString("11111111-1111-1111-1111-111111111111"), -100);
        vertexA.setVertexIndex(0);
        EntityVertex vertexB = EntityVertex.make(UUID.fromString("22222222-2222-2222-2222-222222222222"), -100);
        vertexB.setVertexIndex(1);
        MutableIntObjectMap<ImmutableIntList> successors = IntObjectMaps.mutable.empty();
        successors.put(0, IntLists.immutable.of(1));
        successors.put(1, IntLists.immutable.of(0));
        MutableIntObjectMap<ImmutableIntList> predecessors = IntObjectMaps.mutable.empty();
        predecessors.put(1, IntLists.immutable.of(0));
        predecessors.put(0, IntLists.immutable.of(1));
        return new DiGraphEntity<>(Lists.immutable.empty(),
                Lists.immutable.of(vertexA, vertexB),
                successors.toImmutable(), predecessors.toImmutable());
    }

    @Test
    @DisplayName("An Object[] field round-trips element for element")
    void objectArrayRoundTrips() {
        Object[] original = new Object[]{"UNINITIALIZED", Boolean.FALSE, 777_777_777, 777_777_777_777_777_777L};

        Object[] readBack = roundTrip(new Object[][]{original});

        assertInstanceOf(Object[].class, readBack[0], "OBJECT_ARRAY must read back as Object[]");
        assertArrayEquals(original, (Object[]) readBack[0],
                "the round-tripped array must equal the original element for element");
    }

    @Test
    @DisplayName("A DiGraph cycle field round-trips, and a field written after it stays aligned")
    void diGraphCycleRoundTripsAligned() {
        DiGraphEntity<EntityVertex> cycle = simpleCycle();

        Object[] readBack = roundTrip(cycle, 777_777_777_777_777_777L);

        DiGraphEntity<?> readGraph = (DiGraphEntity<?>) readBack[0];
        assertEquals(2, readGraph.vertexMap().size(), "both cycle vertices must survive");
        assertEquals(0, readGraph.roots().size(), "a pure cycle has no roots");
        assertEquals(IntLists.immutable.of(1), readGraph.successorMap().get(0), "edge A → B must survive");
        assertEquals(IntLists.immutable.of(0), readGraph.successorMap().get(1), "edge B → A must survive");
        assertEquals(IntLists.immutable.of(0), readGraph.predecessorMap().get(1), "predecessor of B must survive");
        assertEquals(IntLists.immutable.of(1), readGraph.predecessorMap().get(0), "predecessor of A must survive");
        assertEquals(777_777_777_777_777_777L, readBack[1],
                "the field after the graph must read back intact — the graph must consume exactly its bytes");
    }

    @Test
    @DisplayName("A DiGraph with declared roots round-trips its root vertices by index")
    void diGraphRootsRoundTrip() {
        EntityVertex root = EntityVertex.make(UUID.fromString("33333333-3333-3333-3333-333333333333"), -100);
        root.setVertexIndex(0);
        EntityVertex leaf = EntityVertex.make(UUID.fromString("44444444-4444-4444-4444-444444444444"), -100);
        leaf.setVertexIndex(1);
        MutableIntObjectMap<ImmutableIntList> successors = IntObjectMaps.mutable.empty();
        successors.put(0, IntLists.immutable.of(1));
        MutableIntObjectMap<ImmutableIntList> predecessors = IntObjectMaps.mutable.empty();
        predecessors.put(1, IntLists.immutable.of(0));
        DiGraphEntity<EntityVertex> graph = new DiGraphEntity<>(Lists.immutable.of(root),
                Lists.immutable.of(root, leaf), successors.toImmutable(), predecessors.toImmutable());

        Object[] readBack = roundTrip(graph, "after");

        DiGraphEntity<?> readGraph = (DiGraphEntity<?>) readBack[0];
        assertEquals(1, readGraph.roots().size(), "the declared root must survive");
        assertEquals(0, ((EntityVertex) readGraph.roots().get(0)).vertexIndex(),
                "the root must be resolved by its written vertex index");
        assertEquals("after", readBack[1], "the field after the graph must read back intact");
    }

    @Test
    @DisplayName("A single-vertex DiTree field round-trips, and a field written after it stays aligned")
    void singleVertexDiTreeRoundTripsAligned() {
        EntityVertex vertex = EntityVertex.make(UUID.fromString("55555555-5555-5555-5555-555555555555"), -100);
        vertex.setVertexIndex(0);
        DiTreeEntity tree = new DiTreeEntity(vertex, Lists.immutable.of(vertex),
                IntObjectMaps.mutable.<ImmutableIntList>empty().toImmutable(),
                IntIntMaps.mutable.empty().toImmutable());

        Object[] readBack = roundTrip(tree, "after");

        DiTreeEntity readTree = (DiTreeEntity) readBack[0];
        assertEquals(1, readTree.vertexMap().size(), "the single vertex must survive");
        assertEquals(0, readTree.root().vertexIndex(), "the root must survive by index");
        assertTrue(readTree.successorMap().isEmpty(), "a single-vertex tree has no successors");
        assertEquals("after", readBack[1], "the field after the tree must read back intact");
    }
}
