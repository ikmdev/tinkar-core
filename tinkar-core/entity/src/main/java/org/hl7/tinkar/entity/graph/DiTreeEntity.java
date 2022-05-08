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
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntIntMaps;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.hl7.tinkar.component.graph.DiTree;
import org.hl7.tinkar.component.graph.GraphAdaptorFactory;
import org.hl7.tinkar.component.graph.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class DiTreeEntity<V extends EntityVertex> extends DiGraphAbstract<V> implements DiTree<V> {
    private static final Logger LOG = LoggerFactory.getLogger(DiTreeEntity.class);
    final V root;
    final ImmutableIntIntMap predecessorMap;

    public DiTreeEntity(V root, ImmutableList<V> vertexMap,
                        ImmutableIntObjectMap<ImmutableIntList> successorMap, ImmutableIntIntMap predecessorMap) {
        super(vertexMap, successorMap);
        this.root = root;
        this.predecessorMap = predecessorMap;
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

    public static <V extends EntityVertex> Builder<V> builder() {
        return new Builder();
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

    @Override
    public ImmutableIntIntMap predecessorMap() {
        return predecessorMap;
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
                LOG.info("Tree: " + e.getMessage());
                byteBufRef.get().recycle();
                bufSize = bufSize + defaultSize;
                byteBufRef.set(ByteBufPool.allocate(bufSize));
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName()).append("{\n");

        MutableIntSet coveredIndexes = IntSets.mutable.empty();
        int nextIndex = root.vertexIndex;
        while (nextIndex > -1) {
            dfsProcess(root().vertexIndex, sb, 1, coveredIndexes);
            if (coveredIndexes.size() < vertexMap.size()) {
                for (int i = 0; i < vertexMap.size(); i++) {
                    if (!coveredIndexes.contains(i)) {
                        nextIndex = i;
                        break;
                    }
                }
            } else {
                nextIndex = -1;
            }
        }
        sb.append('}');

        return sb.toString();
    }

    private void dfsProcess(int start, StringBuilder sb, int depth, MutableIntSet coveredIndexes) {
        V vertex = vertexMap.get(start);
        coveredIndexes.add(start);
        sb.append(vertex.toGraphFormatString("  ".repeat(depth), this));
        Optional<ImmutableIntList> optionalSuccessors = successorNids(start);
        if (optionalSuccessors.isPresent()) {
            optionalSuccessors.get().forEach(i -> dfsProcess(i, sb, depth + 1, coveredIndexes));
        }
    }

    public static class Builder<V extends EntityVertex> implements DiTree<V> {
        private final MutableList<V> vertexMap = Lists.mutable.empty();
        private final MutableIntObjectMap<MutableIntList> successorMap = IntObjectMaps.mutable.empty();
        private final MutableIntIntMap predecessorMap = IntIntMaps.mutable.empty();
        private V root;

        private Builder() {
        }

        /**
         * @param adaptorFactory
         * @param <A>
         * @return
         */
        @Override
        public <A> A adapt(GraphAdaptorFactory<A> adaptorFactory) {
            throw new UnsupportedOperationException("Builder does not adapt... ");
        }

        @Override
        public V vertex(UUID vertexId) {
            for (V vertexEntity : this.vertexMap) {
                if (vertexEntity.vertexId().asUuid().equals(vertexId)) {
                    return vertexEntity;
                }
            }
            throw new NoSuchElementException("VertexId: " + vertexId);
        }

        @Override
        public V vertex(int vertexIndex) {
            return vertexMap.get(vertexIndex);
        }

        @Override
        public ImmutableList<V> vertexMap() {
            return vertexMap.toImmutable();
        }

        @Override
        public ImmutableIntObjectMap<ImmutableIntList> successorMap() {
            MutableIntObjectMap<ImmutableIntList> tempMap = IntObjectMaps.mutable.ofInitialCapacity(successorMap.size());
            successorMap.forEachKeyValue((i, mutableIntList) -> tempMap.put(i, mutableIntList.toImmutable()));
            return tempMap.toImmutable();
        }

        @Override
        public ImmutableList<V> successors(V vertex) {
            MutableIntList successorList = successorMap.get(vertex.vertexIndex());
            if (successorList != null) {
                MutableList<V> successors = Lists.mutable.ofInitialCapacity(successorList.size());
                successorList.forEach(successorIndex -> {
                    successors.add(vertex(successorIndex));
                });
                return successors.toImmutable();
            }
            return Lists.immutable.empty();
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
            if (vertex.vertexIndex() > -1) {
                if (vertex.vertexIndex() < vertexMap.size() &&
                        vertex == vertexMap.get(vertex.vertexIndex())) {
                    // already in the list.
                } else {
                    while (vertexMap.size() <= vertex.vertexIndex()) {
                        vertexMap.add(null);
                    }
                    vertexMap.set(vertex.vertexIndex(), vertex);
                }
            } else {
                vertex.setVertexIndex(vertexMap.size());
                vertexMap.add(vertex.vertexIndex(), vertex);
            }
            return this;
        }

        public Builder<V> addEdge(V child, V parent) {
            addVertex(child);
            addVertex(parent);
            if (!successorMap.containsKey(parent.vertexIndex())) {
                successorMap.put(parent.vertexIndex(), IntLists.mutable.empty());
            }
            successorMap.get(parent.vertexIndex()).add(child.vertexIndex());
            predecessorMap.put(child.vertexIndex(), parent.vertexIndex());
            return this;
        }

        public Builder<V> addEdge(int childIndex, int parentIndex) {
            if (vertexMap.get(childIndex) == null || vertexMap.get(parentIndex) == null) {
                throw new IllegalStateException("Child Vertex or Parent Vertex is null. Add to vertex map before adding edge. ");
            }
            if (!successorMap.containsKey(parentIndex)) {
                successorMap.put(parentIndex, IntLists.mutable.empty());
            }
            successorMap.get(parentIndex).add(childIndex);
            predecessorMap.put(childIndex, parentIndex);
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

        @Override
        public ImmutableIntIntMap predecessorMap() {
            return predecessorMap.toImmutable();
        }

        @Override
        public ImmutableIntList successors(int vertexIndex) {
            return successorMap.get(vertexIndex).toImmutable();
        }
    }

}
