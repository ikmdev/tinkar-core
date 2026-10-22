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
import dev.ikm.tinkar.component.graph.Vertex;
import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.ImmutableIntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

public class DiGraphEntity<V extends EntityVertex> extends DiGraphAbstract<V> implements DiGraph<V> {
    private static final Logger LOG = LoggerFactory.getLogger(DiGraphEntity.class);
    final ImmutableList<V> roots;
    final ImmutableIntObjectMap<ImmutableIntList> predecessorMap;

    public DiGraphEntity(ImmutableList<V> roots, ImmutableList<V> vertexMap,
                         ImmutableIntObjectMap<ImmutableIntList> successorMap, ImmutableIntObjectMap<ImmutableIntList> predecessorMap) {
        super(vertexMap, successorMap);
        this.roots = roots;
        this.predecessorMap = predecessorMap;
    }

    public static DiGraphEntity make(DiGraph<Vertex> tree) {
        ImmutableList<EntityVertex> vertexMap = getVertexEntities(tree);
        MutableList<EntityVertex> rootList = Lists.mutable.ofInitialCapacity(tree.roots().size());
        for (Vertex vertex : tree.roots()) {
            rootList.add(EntityVertex.make(vertex));
        }
        return new DiGraphEntity(rootList.toImmutable(), vertexMap, tree.successorMap(), tree.predecessorMap());
    }

    public static DiGraphEntity make(ByteBuf readBuf, byte entityFormatVersion) {
        if (entityFormatVersion == dev.ikm.tinkar.entity.EntityCodec3.FORMAT) {
            return read3(readBuf);
        }
        if (entityFormatVersion != ENTITY_FORMAT_VERSION) {
            throw new IllegalStateException("Unsupported entity format version: " + entityFormatVersion);
        }
        ImmutableList<EntityVertex> vertexMap = readVertexEntities(readBuf, entityFormatVersion);
        ImmutableIntObjectMap<ImmutableIntList> successorMap = readIntIntListMap(readBuf, entityFormatVersion);
        ImmutableIntObjectMap<ImmutableIntList> predecessorMap = readIntIntListMap(readBuf, entityFormatVersion);

        // Symmetric with getBytes(): the root count is followed by each root's vertex
        // index, which must be consumed and resolved. The previous read consumed only
        // the count and guessed roots as the first vertices, leaving the written root
        // indexes unread — misaligning every field that follows the graph in the same
        // buffer (IKE-Network/ike-issues#885).
        int rootCount = readBuf.readInt();
        MutableList<EntityVertex> roots = Lists.mutable.ofInitialCapacity(rootCount);
        for (int i = 0; i < rootCount; i++) {
            roots.add(vertexMap.get(readBuf.readInt()));
        }

        return new DiGraphEntity(roots.toImmutable(), vertexMap.toImmutable(),
                successorMap, predecessorMap);

    }

    public static <V extends EntityVertex> Builder<V> builder() {
        return new Builder<>();
    }

    @Override
    public ImmutableList<V> roots() {
        return roots;
    }

    @Override
    public ImmutableList<V> predecessors(V vertex) {
        ImmutableIntList predecessorlist = predecessorMap.get(vertex.vertexIndex());
        MutableList<V> predecessors = Lists.mutable.ofInitialCapacity(predecessorlist.size());
        predecessorlist.forEach(successorIndex -> {
            predecessors.add(vertex(successorIndex));
        });
        return predecessors.toImmutable();
    }

    @Override
    public ImmutableIntObjectMap<ImmutableIntList> predecessorMap() {
        return predecessorMap;
    }

    /** The graph in entity format 1. */
    public final byte[] getBytes() {
        return getBytes(ENTITY_FORMAT_VERSION);
    }

    /**
     * The graph's bytes in an entity format. Format 1: the vertices, the successor and
     * predecessor maps, and the roots' indexes, every count and index an int. Format 3:
     * {@link #write3}.
     *
     * @param entityFormatVersion the format, 1 or 3
     * @return the bytes
     */
    public final byte[] getBytes(byte entityFormatVersion) {
        boolean format3 = entityFormatVersion == dev.ikm.tinkar.entity.EntityCodec3.FORMAT;
        int defaultSize = estimatedBytes();
        int bufSize = defaultSize;
        AtomicReference<ByteBuf> byteBufRef =
                new AtomicReference<>(ByteBufPool.allocate(bufSize));
        while (true) {
            try {
                ByteBuf byteBuf = byteBufRef.get();
                if (format3) {
                    write3(byteBuf);
                    return byteBuf.asArray();
                }
                writeVertexMap(byteBuf, entityFormatVersion);
                writeIntIntListMap(byteBuf, successorMap(), entityFormatVersion);
                writeIntIntListMap(byteBuf, predecessorMap(), entityFormatVersion);

                byteBuf.writeInt(roots.size());
                roots.forEach(root -> byteBuf.writeInt(root.vertexIndex()));
                return byteBuf.asArray();
            } catch (ArrayIndexOutOfBoundsException e) {
                LOG.info("Tree: " + e.getMessage());
                byteBufRef.get().recycle();
                bufSize = bufSize + defaultSize;
                byteBufRef.set(ByteBufPool.allocate(bufSize));
            }
        }
    }

    /**
     * Writes the graph in entity format 3 (IKE-Network/ike-issues#1313): the vertices without
     * their indexes, each vertex's successors and then each vertex's predecessors by position,
     * and the roots' indexes, every count and index a varint. A graph's predecessors are written,
     * not derived, since a vertex has several and their order is the graph's.
     *
     * @param buf the buffer
     */
    public final void write3(ByteBuf buf) {
        int count = vertexMap.size();
        writeVertices3(buf);
        writeAdjacency3(buf, count, successorMap);
        writeAdjacency3(buf, count, predecessorMap);
        buf.writeVarInt(roots.size());
        roots.forEach(root -> buf.writeVarInt(root.vertexIndex()));
    }

    /**
     * Reads a graph {@link #write3} wrote.
     *
     * @param readBuf the buffer
     * @return the graph
     */
    public static DiGraphEntity read3(ByteBuf readBuf) {
        ImmutableList<EntityVertex> vertexMap = readVertices3(readBuf);
        int count = vertexMap.size();
        ImmutableIntObjectMap<ImmutableIntList> successorMap = readAdjacency3(readBuf, count);
        ImmutableIntObjectMap<ImmutableIntList> predecessorMap = readAdjacency3(readBuf, count);
        int rootCount = readBuf.readVarInt();
        EntityVertex[] roots = new EntityVertex[rootCount];
        for (int i = 0; i < rootCount; i++) {
            roots[i] = vertexMap.get(readBuf.readVarInt());
        }
        return new DiGraphEntity(Lists.immutable.of(roots), vertexMap, successorMap, predecessorMap);
    }

    public static class Builder<V extends EntityVertex> {
        private final MutableList<V> vertexMap = Lists.mutable.empty();
        private final MutableIntObjectMap<MutableIntList> successorMap = IntObjectMaps.mutable.empty();
        private final MutableIntObjectMap<MutableIntList> predecessorMap = IntObjectMaps.mutable.empty();
        ;
        private final MutableList<V> roots = Lists.mutable.empty();

        protected Builder() {
        }

        public Builder addRoot(V root) {
            addVertex(root);
            roots.add(root);
            return this;
        }

        public Builder addVertex(V vertex) {
            if (vertex.vertexIndex() > 0 &&
                    vertex.vertexIndex() < vertexMap.size() &&
                    vertexMap.get(vertex.vertexIndex()) == vertex) {
                // already in the list.
            } else {
                vertex.setVertexIndex(vertexMap.size());
                vertexMap.add(vertex.vertexIndex(), vertex);
            }
            return this;
        }

        public Builder addEdge(V child, V parent) {
            addVertex(child);
            addVertex(parent);
            successorMap.getIfAbsentPut(parent.vertexIndex(), IntLists.mutable.empty()).add(child.vertexIndex());
            predecessorMap.getIfAbsentPut(child.vertexIndex(), IntLists.mutable.empty()).add(parent.vertexIndex());
            return this;
        }

        public DiGraphEntity<V> build() {

            MutableIntObjectMap<ImmutableIntList> intermediateSuccessorMap = IntObjectMaps.mutable.ofInitialCapacity(successorMap.size());
            successorMap.forEachKeyValue((vertex, successorList) -> intermediateSuccessorMap.put(vertex, successorList.toImmutable()));

            MutableIntObjectMap<ImmutableIntList> intermediatePredecessorMap = IntObjectMaps.mutable.ofInitialCapacity(predecessorMap.size());
            predecessorMap.forEachKeyValue((vertex, predecessorList) -> intermediatePredecessorMap.put(vertex, predecessorList.toImmutable()));

            return new DiGraphEntity(roots.toImmutable(),
                    vertexMap.toImmutable(),
                    intermediateSuccessorMap.toImmutable(),
                    intermediatePredecessorMap.toImmutable());
        }
    }

}
