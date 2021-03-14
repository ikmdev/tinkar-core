package org.hl7.tinkar.entity.graph;

import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.ImmutableIntIntMap;
import org.eclipse.collections.api.map.primitive.ImmutableIntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntIntMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntIntMaps;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.hl7.tinkar.component.graph.DiTree;
import org.hl7.tinkar.component.graph.Vertex;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class DiTreeEntity<V extends EntityVertex> extends DiGraphAbstract<V> implements DiTree<V> {
    final V root;
    final ImmutableIntIntMap predecessorMap;

    public DiTreeEntity(V root, ImmutableList<V> vertexMap,
                        ImmutableIntObjectMap<ImmutableIntList> successorMap, ImmutableIntIntMap predecessorMap) {
        super(vertexMap, successorMap);
        this.root = root;
        this.predecessorMap = predecessorMap;
    }

    @Override
    public ImmutableIntIntMap predecessorMap() {
        return predecessorMap;
    }

    @Override
    public V root() {
        return root;
    }

    @Override
    public Optional<V> predecessor(V vertex) {
        if (this.predecessorMap.containsKey(vertex.vertexIndex())) {
            return Optional.of(vertex(this.predecessorMap.get(vertex.vertexIndex())));
        }
        return Optional.empty();
    }

    public static DiTreeEntity make(DiTree<Vertex> tree) {
        ImmutableList<EntityVertex> vertexMap = getVertexEntities(tree);
        EntityVertex root = vertexMap.get(tree.root().vertexIndex());
        return new DiTreeEntity(root, vertexMap, tree.successorMap(), tree.predecessorMap());
    }

    public static DiTreeEntity make(ByteBuf readBuf, byte entityFormatVersion) {
        if (entityFormatVersion != ENTITY_FORMAT_VERSION) {
            throw new IllegalStateException("Unsupported entity format version: " + entityFormatVersion);
        }

        ImmutableList<EntityVertex> vertexMap = readVertexEntities(readBuf, entityFormatVersion);
        ImmutableIntObjectMap<ImmutableIntList> successorMap = readIntIntListMap(readBuf);

        int predecessorMapSize = readBuf.readInt();
        MutableIntIntMap predecessorMap = IntIntMaps.mutable.ofInitialCapacity(predecessorMapSize);
        for (int i = 0; i < predecessorMapSize; i++) {
            predecessorMap.put(readBuf.readInt(), readBuf.readInt());
        }

        int rootVertexIndex = readBuf.readInt();

        return new DiTreeEntity(vertexMap.get(rootVertexIndex), vertexMap,
                successorMap, predecessorMap.toImmutable());

    }

    public final byte[] getBytes() {
        int defaultSize = size();
        int bufSize = defaultSize;
        AtomicReference<ByteBuf> byteBufRef =
                new AtomicReference<>(ByteBufPool.allocate(bufSize));
        while (true) {
            try {
                ByteBuf byteBuf = byteBufRef.get();
                writeVertexMap(byteBuf);
                writeIntIntListMap(byteBuf, successorMap());

                byteBuf.writeInt(predecessorMap.size());
                predecessorMap.forEachKeyValue((vertex, predecessor) -> {
                    byteBuf.writeInt(vertex);
                    byteBuf.writeInt(predecessor);
                });

                byteBuf.writeInt(root.vertexIndex());
                return byteBuf.asArray();
            } catch (ArrayIndexOutOfBoundsException e) {
                System.out.println("Tree: " + e.getMessage());
                byteBufRef.get().recycle();
                bufSize = bufSize + defaultSize;
                byteBufRef.set(ByteBufPool.allocate(bufSize));
            }
        }
    }

    public static <V extends EntityVertex> Builder<V> builder() {
        return new Builder();
    }
    public static class Builder<V extends EntityVertex> {
        private final MutableList<V> vertexMap = Lists.mutable.empty();
        private final MutableIntObjectMap<MutableIntList> successorMap = IntObjectMaps.mutable.empty();
        private final MutableIntIntMap predecessorMap = IntIntMaps.mutable.empty();
        private V root;

        private Builder() {
        }

        public V getRoot() {
            return root;
        }

        public Builder<V> setRoot(V root) {
            addVertex(root);
            this.root = root;
            return this;
        }

        public Builder<V> addVertex(V vertex) {
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

        public Builder<V> addEdge(V child, V parent) {
            vertexMap.add(child.vertexIndex(), child);
            successorMap.getIfAbsent(parent.vertexIndex(), () -> IntLists.mutable.empty()).add(child.vertexIndex());
            predecessorMap.put(child.vertexIndex(), parent.vertexIndex());
            return this;
        }

        public DiTreeEntity<V> build() {

            MutableIntObjectMap<ImmutableIntList> intermediateSuccessorMap = IntObjectMaps.mutable.ofInitialCapacity(successorMap.size());
            successorMap.forEachKeyValue((vertex, successorList) -> intermediateSuccessorMap.put(vertex, successorList.toImmutable()));

            return new DiTreeEntity(root,
                    vertexMap.toImmutable(),
                    intermediateSuccessorMap.toImmutable(),
                    predecessorMap.toImmutable());
        }
    }

}
