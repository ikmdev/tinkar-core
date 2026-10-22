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

        Object[] readBack = roundTrip((Object) original);

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
    @Test
    @DisplayName("A vertex field round-trips with its meaning and properties in both formats, and a field after it stays aligned")
    void vertexFieldRoundTripsInBothFormats() {
        UUID id = UUID.fromString("66666666-6666-6666-6666-666666666666");
        // Format 1 holds the nids of a sequential or packed store; format 2 the 64-bit nids of a 64-bit store.
        long[] format1Nids = {-200, -201, -202};
        Object[] format1 = roundTrip(vertex(id, format1Nids), "after");
        assertVertex(id, format1Nids, (EntityVertex) format1[0]);
        assertEquals("after", format1[1], "the field after the vertex must read back intact in format 1");

        long[] format2Nids = {dev.ikm.tinkar.common.id.Nid.compose64(2, 200), dev.ikm.tinkar.common.id.Nid.compose64(2, 201),
                dev.ikm.tinkar.common.id.Nid.compose64(2, 202)};
        ByteBuf writeBuf = ByteBufPool.allocate(4096);
        EntityCodec3.writeField(writeBuf, vertex(id, format2Nids));
        EntityCodec3.writeField(writeBuf, "after");
        ByteBuf readBuf = ByteBuf.wrapForReading(writeBuf.asArray());
        assertVertex(id, format2Nids, (EntityVertex) EntityCodec3.readField(readBuf, FieldDataType.fromToken(readBuf.readByte())));
        assertEquals("after", EntityCodec3.readField(readBuf, FieldDataType.fromToken(readBuf.readByte())),
                "the field after the vertex must read back intact in format 2");
    }

    @Test
    @DisplayName("Format 2 reads id sets, strings and decimals in place, and the field after each stays aligned")
    void format2FieldsReadInPlace() {
        // IKE-Network/ike-issues#1313: strings decode from the record's own array, and an id set
        // of one pattern fills its result directly; a set across patterns still merges its groups.
        long[] onePattern = {dev.ikm.tinkar.common.id.Nid.compose64(2, 7), dev.ikm.tinkar.common.id.Nid.compose64(2, 300),
                dev.ikm.tinkar.common.id.Nid.compose64(2, 70_000)};
        long[] threePatterns = {dev.ikm.tinkar.common.id.Nid.compose64(2, 5), dev.ikm.tinkar.common.id.Nid.compose64(2, 9),
                dev.ikm.tinkar.common.id.Nid.compose64(3, 1), dev.ikm.tinkar.common.id.Nid.compose64(40, 2_000_000)};
        String text = "Größe — ≥ 3 µm ✓";
        java.math.BigDecimal decimal = new java.math.BigDecimal("-12345.678900");
        ByteBuf writeBuf = ByteBufPool.allocate(4096);
        EntityCodec3.writeField(writeBuf, dev.ikm.tinkar.common.id.LongIds.set.of(onePattern));
        EntityCodec3.writeField(writeBuf, text);
        EntityCodec3.writeField(writeBuf, dev.ikm.tinkar.common.id.LongIds.set.of(threePatterns));
        EntityCodec3.writeField(writeBuf, decimal);
        EntityCodec3.writeField(writeBuf, "");
        EntityCodec3.writeField(writeBuf, "after");
        ByteBuf readBuf = ByteBuf.wrapForReading(writeBuf.asArray());
        assertArrayEquals(onePattern, ((dev.ikm.tinkar.common.id.LongIdSet) next(readBuf)).toArray(),
                "a one-pattern id set reads back in order");
        assertEquals(text, next(readBuf), "a non-ASCII string reads back whole");
        assertArrayEquals(threePatterns, ((dev.ikm.tinkar.common.id.LongIdSet) next(readBuf)).toArray(),
                "an id set across patterns reads back in order");
        assertEquals(decimal, next(readBuf), "a decimal keeps its scale");
        assertEquals("", next(readBuf), "an empty string reads back");
        assertEquals("after", next(readBuf), "the field after them must read back intact");
        assertEquals(0, readBuf.readRemaining(), "and nothing is left");
    }

    @Test
    @DisplayName("Format 3 keeps a tree's predecessors as recorded when its successors do not give them")
    void format3KeepsPredecessorsTheSuccessorsDoNotGive() {
        // As in two SNOMED axiom trees: a vertex the root no longer reaches, its predecessor -1.
        long concept = dev.ikm.tinkar.common.id.Nid.compose64(2, 9);
        EntityVertex root = EntityVertex.make(UUID.fromString("77777777-7777-7777-7777-777777777701"), concept);
        root.setVertexIndex(0);
        EntityVertex child = EntityVertex.make(UUID.fromString("77777777-7777-7777-7777-777777777702"), concept);
        child.setVertexIndex(1);
        EntityVertex orphan = EntityVertex.make(UUID.fromString("77777777-7777-7777-7777-777777777703"), concept);
        orphan.setVertexIndex(2);
        MutableIntObjectMap<ImmutableIntList> successors = IntObjectMaps.mutable.empty();
        successors.put(0, IntLists.immutable.of(1));
        DiTreeEntity tree = new DiTreeEntity(root, Lists.immutable.of(root, child, orphan), successors.toImmutable(),
                IntIntMaps.mutable.empty().withKeyValue(1, 0).withKeyValue(2, -1).toImmutable());
        ByteBuf writeBuf = ByteBufPool.allocate(4096);
        EntityCodec3.writeField(writeBuf, tree);
        EntityCodec3.writeField(writeBuf, "after");
        ByteBuf readBuf = ByteBuf.wrapForReading(writeBuf.asArray());
        DiTreeEntity read = (DiTreeEntity) next(readBuf);
        assertEquals(tree.predecessorMap(), read.predecessorMap(), "the recorded predecessors, the orphan's -1 included");
        assertEquals(tree.successorMap(), read.successorMap());
        assertEquals("after", next(readBuf), "and the field after it stays aligned");
    }

    private static Object next(ByteBuf readBuf) {
        return EntityCodec3.readField(readBuf, FieldDataType.fromToken(readBuf.readByte()));
    }

    /** A vertex whose meaning is nids[0], with a string under nids[1] and an integer under nids[2]. */
    private static EntityVertex vertex(UUID id, long[] nids) {
        EntityVertex vertex = EntityVertex.make(id, nids[0]);
        vertex.setVertexIndex(0);
        org.eclipse.collections.api.map.primitive.MutableLongObjectMap<Object> properties =
                org.eclipse.collections.impl.factory.primitive.LongObjectMaps.mutable.empty();
        properties.put(nids[1], "== Machine identity");
        properties.put(nids[2], 2);
        vertex.setProperties(properties);
        return vertex;
    }

    private static void assertVertex(UUID id, long[] nids, EntityVertex read) {
        assertEquals(id, read.asUuidArray()[0], "the vertex keeps its identity");
        assertEquals(nids[0], read.getMeaningNid(), "and its meaning");
        assertEquals("== Machine identity", read.properties().get(nids[1]), "a string property survives");
        assertEquals(2, read.properties().get(nids[2]), "an integer property survives");
    }
}
