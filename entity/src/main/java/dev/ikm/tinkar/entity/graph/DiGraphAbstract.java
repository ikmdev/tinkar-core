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

import dev.ikm.tinkar.component.graph.DiGraph;
import dev.ikm.tinkar.component.graph.Graph;
import dev.ikm.tinkar.component.graph.GraphAdaptorFactory;
import dev.ikm.tinkar.component.graph.Vertex;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityFacade;
import io.activej.bytebuf.ByteBuf;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.ImmutableIntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public abstract class DiGraphAbstract<V extends EntityVertex>  {


    public static final byte ENTITY_FORMAT_VERSION = 1;

    final ImmutableList<V> vertexMap;
    final ImmutableIntObjectMap<ImmutableIntList> successorMap;
    AtomicReference<ConcurrentHashMap<GraphAdaptorFactory, Object>> adaptorsReference = new AtomicReference<>();

    public DiGraphAbstract(ImmutableList<V> vertexMap,
                           ImmutableIntObjectMap<ImmutableIntList> successorMap) {
        this.vertexMap = vertexMap;
        this.successorMap = successorMap;
    }

    public DiGraph.VertexType vertexType(V vertex) {
        if (successorMap.get(vertex.vertexIndex) == null || successorMap.get(vertex.vertexIndex).isEmpty()) {
            return DiGraph.VertexType.LEAF;
        }
        return DiGraph.VertexType.PREDECESSOR;
    }
    public DiGraph.VertexType vertexType(int vertexIndex) {
        if (successorMap.get(vertexIndex) == null || successorMap.get(vertexIndex).isEmpty()) {
            return DiGraph.VertexType.LEAF;
        }
        return DiGraph.VertexType.PREDECESSOR;
    }

    protected static ImmutableList<EntityVertex> getVertexEntities(Graph<Vertex> tree) {
        MutableList<EntityVertex> vertexMap = Lists.mutable.ofInitialCapacity(tree.vertexMap().size());
        for (Vertex vertex : tree.vertexMap()) {
            vertexMap.add(EntityVertex.make(vertex));
        }
        return vertexMap.toImmutable();
    }

    protected static ImmutableList<EntityVertex> readVertexEntities(ByteBuf readBuf, byte entityFormatVersion) {
        int vertexMapSize = readBuf.readInt();
        MutableList<EntityVertex> vertexMap = Lists.mutable.ofInitialCapacity(vertexMapSize);
        for (int i = 0; i < vertexMapSize; i++) {
            EntityVertex entityVertex = EntityVertex.make(readBuf, entityFormatVersion);
            vertexMap.add(entityVertex);
        }
        return vertexMap.toImmutable();
    }

    protected static ImmutableIntObjectMap<ImmutableIntList> readIntIntListMap(ByteBuf readBuf) {
        return readIntIntListMap(readBuf, ENTITY_FORMAT_VERSION);
    }

    /** Reads a vertex-to-vertices map in entity format 1. */
    protected static ImmutableIntObjectMap<ImmutableIntList> readIntIntListMap(ByteBuf readBuf, byte entityFormatVersion) {
        int successorMapSize = readBuf.readInt();
        MutableIntObjectMap<ImmutableIntList> successorMap = IntObjectMaps.mutable.ofInitialCapacity(successorMapSize);
        for (int i = 0; i < successorMapSize; i++) {
            int vertexSequence = readBuf.readInt();
            int successorListSize = readBuf.readInt();
            MutableIntList successorList = IntLists.mutable.empty();
            for (int j = 0; j < successorListSize; j++) {
                successorList.add(readBuf.readInt());
            }
            successorMap.put(vertexSequence, successorList.toImmutable());
        }
        return successorMap.toImmutable();
    }

    /**
     * @param adaptorFactory
     * @param <A>
     * @return
     */
    public <A> A adapt(GraphAdaptorFactory<A> adaptorFactory) {
        adaptorsReference.accumulateAndGet(null, (prev, x) -> {
            if (x == null) {
                return new ConcurrentHashMap(4);
            }
            return x;
        });
        return (A) adaptorsReference.get().getIfAbsentPut(adaptorFactory, () -> adaptorFactory.adapt((Graph) this));
    }

    public ImmutableList<V> vertexMap() {
        return vertexMap;
    }

    public Optional<ImmutableIntList> successorNids(int vertexIndex) {
        return Optional.ofNullable(successorMap.get(vertexIndex));
    }
    public Optional<V> firstVertexWithMeaning(ConceptFacade vertexMeaning) {
        return firstVertexWithMeaning(vertexMeaning.nid());
    }
    public Optional<V>  firstVertexWithMeaning(long vertexMeaningNid) {
        for (V vertex : vertexMap) {
            if (vertex.meaningNid == vertexMeaningNid) {
                return Optional.of(vertex);
            }
        }
        return Optional.empty();
    }

    public boolean containsVertexWithMeaning(EntityFacade meaning) {
        return containsVertexWithMeaning(meaning.nid());
    }

    public boolean containsVertexWithMeaning(long meaningNid) {
        for (V vertex : vertexMap) {
            if (vertex.meaningNid == meaningNid) {
                return true;
            }
        }
        return false;
    }

    public V vertex(UUID vertexId) {
        for (V vertexEntity : this.vertexMap) {
            if (vertexEntity.vertexId().asUuid().equals(vertexId)) {
                return vertexEntity;
            }
        }
        throw new NoSuchElementException("VertexId: " + vertexId);
    }

    public ImmutableIntList successors(int vertexIndex) {
        ImmutableIntList successorList = successorMap.get(vertexIndex);
        if (successorList != null) {
            return successorList;
        }
        return IntLists.immutable.empty();
    }

    public ImmutableList<V> successors(V vertex) {
        ImmutableIntList successorList = successorMap.get(vertex.vertexIndex());
        if (successorList != null) {
            MutableList<V> successors = Lists.mutable.ofInitialCapacity(successorList.size());
            successorList.forEach(successorIndex -> {
                successors.add(vertex(successorIndex));
            });
            return successors.toImmutable();
        }
        return Lists.immutable.empty();
    }

    public V vertex(int vertexIndex) {
        return vertexMap.get(vertexIndex);
    }

    // ---------------------------------------------------------------- entity format 3

    /**
     * Writes the vertices in entity format 3 (IKE-Network/ike-issues#1313): a count, then each
     * vertex without its index, which is its position.
     *
     * @param buf the buffer
     * @throws IllegalStateException if a vertex's index is not its position
     */
    protected void writeVertices3(ByteBuf buf) {
        int count = vertexMap.size();
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            V vertex = vertexMap.get(i);
            if (vertex.vertexIndex() != i) {
                throw new IllegalStateException("Vertex at position " + i + " has index " + vertex.vertexIndex()
                        + ": entity format 3 writes a vertex's index as its position");
            }
            vertex.write3(buf, false);
        }
    }

    /** Reads what {@link #writeVertices3} wrote, each vertex given its position as its index. */
    protected static ImmutableList<EntityVertex> readVertices3(ByteBuf buf) {
        int count = buf.readVarInt();
        EntityVertex[] vertices = new EntityVertex[count];
        for (int i = 0; i < count; i++) {
            vertices[i] = EntityVertex.read3(buf, i);
        }
        return Lists.immutable.of(vertices);
    }

    /**
     * Writes a vertex-to-vertices map in entity format 3: for each vertex in order, 0 when the
     * map has no entry for it, else one more than its list's size, then the list. The map's keys
     * are the positions, so they are not written.
     *
     * @param buf         the buffer
     * @param vertexCount the vertices
     * @param map         the map
     * @throws IllegalStateException if the map has an entry for no vertex
     */
    protected static void writeAdjacency3(ByteBuf buf, int vertexCount, ImmutableIntObjectMap<ImmutableIntList> map) {
        int written = 0;
        for (int i = 0; i < vertexCount; i++) {
            ImmutableIntList list = map.get(i);
            if (list == null) {
                buf.writeVarInt(0);
            } else {
                written++;
                buf.writeVarInt(list.size() + 1);
                for (int j = 0; j < list.size(); j++) {
                    buf.writeVarInt(list.get(j));
                }
            }
        }
        if (written != map.size()) {
            throw new IllegalStateException("A graph's map has " + map.size() + " entries but " + written
                    + " of its " + vertexCount + " vertices: entity format 3 keys it by position");
        }
    }

    /** Reads what {@link #writeAdjacency3} wrote. */
    protected static ImmutableIntObjectMap<ImmutableIntList> readAdjacency3(ByteBuf buf, int vertexCount) {
        MutableIntObjectMap<ImmutableIntList> map = IntObjectMaps.mutable.ofInitialCapacity(vertexCount);
        for (int i = 0; i < vertexCount; i++) {
            int marked = buf.readVarInt();
            if (marked > 0) {
                int[] list = new int[marked - 1];
                for (int j = 0; j < list.length; j++) {
                    list[j] = buf.readVarInt();
                }
                map.put(i, IntLists.immutable.of(list));
            }
        }
        return map.toImmutable();
    }

    public int estimatedBytes() {
        // Empty vertex is 34 bytes
        return vertexMap.size() * 64;
    }

    /**
     * Writes an int-to-int-list map (a successor or predecessor map) to the buffer:
     * entry count, then each entry as (vertex index, destination count, destinations).
     * The count written is {@code map.size()} — it previously wrote
     * {@code successorMap().size()} regardless of which map was being written, silently
     * corrupting the wire form of any graph whose predecessor map has a different entry
     * count than its successor map (IKE-Network/ike-issues#885).
     *
     * @param byteBuf the buffer to write to
     * @param map     the map to write
     */
    protected void writeIntIntListMap(ByteBuf byteBuf, ImmutableIntObjectMap<ImmutableIntList> map) {
        writeIntIntListMap(byteBuf, map, ENTITY_FORMAT_VERSION);
    }

    /** Writes a vertex-to-vertices map as {@link #readIntIntListMap(ByteBuf, byte)} reads it. */
    protected void writeIntIntListMap(ByteBuf byteBuf, ImmutableIntObjectMap<ImmutableIntList> map, byte entityFormatVersion) {
        byteBuf.writeInt(map.size());
        map.forEachKeyValue((int vertexIndex, ImmutableIntList destinationVertexes) -> {
            byteBuf.writeInt(vertexIndex);
            byteBuf.writeInt(destinationVertexes.size());
            destinationVertexes.forEach(destinationIndex -> byteBuf.writeInt(destinationIndex));
        });
    }

    public ImmutableIntObjectMap<ImmutableIntList> successorMap() {
        return successorMap;
    }

    protected void writeVertexMap(ByteBuf byteBuf) {
        writeVertexMap(byteBuf, ENTITY_FORMAT_VERSION);
    }

    /** Writes the vertices as {@link #readVertexEntities(ByteBuf, byte)} reads them: a count, then each vertex's bytes in format 1. */
    protected void writeVertexMap(ByteBuf byteBuf, byte entityFormatVersion) {
        byteBuf.writeInt(vertexMap.size());
        vertexMap.forEach(vertexEntity -> {
            byteBuf.write(vertexEntity.getBytes(entityFormatVersion));
        });
    }

    public final void breadthFirstProcess(int startNid, VertexVisitData vertexVisitData) {
        final Queue<Integer> bfsQueue      = new LinkedList<>();

        vertexVisitData.startVertexVisit(startNid, 0);
        bfsQueue.add(startNid);

        while (!bfsQueue.isEmpty()) {
            final int   currentIndex      = bfsQueue.remove();
            final int   currentDistance = vertexVisitData.distance(currentIndex);
            final EntityVertex currentVertex = vertex(currentIndex);
            // A leaf has no entry in the successor map, so read it with an empty default as
            // the depth-first traversal does.
            final ImmutableIntList childIndexes =
                    successorMap.getIfAbsent(currentIndex, () -> IntLists.immutable.empty());

            if (childIndexes.isEmpty()) {
                vertexVisitData.setLeafVertex(currentIndex);
            }

            vertexVisitData.vertexStartProcess(currentVertex, (DiGraphAbstract<EntityVertex>) this);

            childIndexes.forEach(childIndex -> {
                if (vertexVisitData.vertexStatus(childIndex) == VertexStatus.UNDISCOVERED) {
                    vertexVisitData.startVertexVisit(childIndex, currentDistance + 1);
                    vertexVisitData.setPredecessorIndex(childIndex, currentIndex);
                    bfsQueue.add(childIndex);
                } else {
                    // second path to node. Could be multi-parent or cycle...
                    // TODO decide if to put in generic cycle detection using this circumstance.
                }
            });

            vertexVisitData.vertexEndProcess(currentVertex, (DiGraphAbstract<EntityVertex>) this);
            vertexVisitData.endVertexVisit(currentIndex);
        }
    }
    /**
     * Visits the graph depth first from {@code startNid}, in the order the recursive form
     * visited it: a vertex is discovered and its start process run before any child, each
     * child is entered in successor order once its earlier siblings' subtrees have finished,
     * and the end process and finish run after the last child. The walk keeps its own stack of
     * frames rather than recursing, so a graph's depth is bounded by memory alone, not by the
     * call stack: the recursive form threw past depth 100, a ceiling a long sequential chain
     * reaches ({@code dev-digraph-readiness-spike}).
     */
    public final void depthFirstProcess(int startNid, VertexVisitData vertexVisitData) {
        final Deque<DfsFrame> stack = new ArrayDeque<>();
        stack.push(enter(startNid, 0, vertexVisitData));

        while (!stack.isEmpty()) {
            final DfsFrame frame = stack.peek();
            if (frame.nextChild < frame.childIndexes.size()) {
                final int childIndex = frame.childIndexes.get(frame.nextChild++);
                if (vertexVisitData.vertexStatus(childIndex) == VertexStatus.UNDISCOVERED) {
                    vertexVisitData.setPredecessorIndex(childIndex, frame.index);
                    stack.push(enter(childIndex, frame.depth + 1, vertexVisitData));
                } else {
                    // second path to node. Could be multi-parent or cycle...
                    // TODO decide if to put in generic cycle detection using this circumstance.
                }
            } else {
                stack.pop();
                vertexVisitData.vertexEndProcess(frame.vertex, (DiGraphAbstract<EntityVertex>) this);
                // Change to NodeStatus.FINISHED
                vertexVisitData.endVertexVisit(frame.index);
            }
        }
    }

    /** Discovers a vertex and runs its start process; the frame then walks its children. */
    private DfsFrame enter(int index, int depth, VertexVisitData vertexVisitData) {
        // Change to NodeStatus.PROCESSING
        vertexVisitData.startVertexVisit(index, depth);

        final ImmutableIntList childIndexes = successorMap.getIfAbsent(index, () -> IntLists.immutable.empty());

        if (childIndexes.isEmpty()) {
            vertexVisitData.setLeafVertex(index);
        }
        EntityVertex vertex = vertex(index);
        vertexVisitData.vertexStartProcess(vertex, (DiGraphAbstract<EntityVertex>) this);
        return new DfsFrame(index, depth, vertex, childIndexes);
    }

    /** One vertex under visit on the depth-first stack, with the next child to enter. */
    private static final class DfsFrame {
        final int index;
        final int depth;
        final EntityVertex vertex;
        final ImmutableIntList childIndexes;
        int nextChild;

        DfsFrame(int index, int depth, EntityVertex vertex, ImmutableIntList childIndexes) {
            this.index = index;
            this.depth = depth;
            this.vertex = vertex;
            this.childIndexes = childIndexes;
        }
    }


    public boolean equivalentVertexes(int thisIndex, EntityVertex thatVertex) {
        return this.vertexMap.get(thisIndex).equivalent(thatVertex);
    }

    public int vertexCount() {
        return this.vertexMap.size();
    }

}
