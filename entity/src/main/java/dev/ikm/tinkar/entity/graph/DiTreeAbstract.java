package dev.ikm.tinkar.entity.graph;

import dev.ikm.tinkar.component.graph.DiTree;
import dev.ikm.tinkar.component.graph.GraphAdaptorFactory;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public abstract class DiTreeAbstract<V extends EntityVertex> extends DiGraphAbstract<V> implements DiTree<V> {
    private static final Logger LOG = LoggerFactory.getLogger(DiTreeEntity.class);
    final V root;
    /**
     * Note that a get() method on IntInt map returns 0 as a default even if there is no key.
     * Thus you must test for containsKey()
     * TODO consider changing data structure to one that does not require
     */
    final ImmutableIntIntMap predecessorMap;

    public DiTreeAbstract(V root, ImmutableList<V> vertexMap,
                          ImmutableIntObjectMap<ImmutableIntList> successorMap, ImmutableIntIntMap predecessorMap) {
        super(vertexMap, successorMap);
        this.root = root;
        this.predecessorMap = predecessorMap;
    }

    @Override
    public V root() {
        return root;
    }

    public OptionalInt predecessor(int vertexIndex) {
        if (this.predecessorMap.containsKey(vertexIndex)) {
            return OptionalInt.of(vertex(this.predecessorMap.get(vertexIndex)).vertexIndex);
        }
        return OptionalInt.empty();
    }
    @Override
    public Optional<EntityVertex> predecessor(EntityVertex vertex) {
        /**
         * Note that a get() method on IntInt map returns 0 as a default even if there is no key.
         * Thus you must test for containsKey()
         * TODO consider changing data structure to one that does not require
         */
        if (this.predecessorMap.containsKey(vertex.vertexIndex())) {
            return Optional.of(vertex(this.predecessorMap.get(vertex.vertexIndex())));
        }
        return Optional.empty();
    }

    /**
     * Note that a get() method on IntInt map returns 0 as a default even if there is no key.
     * Thus you must test for containsKey()
     * TODO consider changing data structure to one that does not require
     */
    @Override
    public ImmutableIntIntMap predecessorMap() {
        return predecessorMap;
    }

    public final byte[] getBytes() {
        int defaultSize = estimatedBytes();
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
        return toString("");
    }
    public String toString(String idSuffix) {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName()).append("{\n");

        MutableIntSet coveredIndexes = IntSets.mutable.empty();
        int nextIndex = root.vertexIndex;
        while (nextIndex > -1) {
            dfsProcess(root().vertexIndex, sb, 1, idSuffix, coveredIndexes);
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

    private void dfsProcess(int start, StringBuilder sb, int depth, String idSuffix, MutableIntSet coveredIndexes) {
        EntityVertex vertex = vertexMap.get(start);
        coveredIndexes.add(start);
        sb.append(vertex.toGraphFormatString("  ".repeat(depth), idSuffix, this));
        Optional<ImmutableIntList> optionalSuccessors = successorNids(start);
        if (optionalSuccessors.isPresent()) {
            optionalSuccessors.get().forEach(i -> dfsProcess(i, sb, depth + 1, idSuffix, coveredIndexes));
        }
    }

    protected static abstract class Builder<V extends EntityVertex> implements DiTree<V> {
        protected final MutableList<V> vertexMap = Lists.mutable.empty();
        protected final MutableIntObjectMap<MutableIntList> successorMap = IntObjectMaps.mutable.empty();
        protected final MutableIntIntMap predecessorMap = IntIntMaps.mutable.empty();
        protected V root;

        protected Builder() {
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
            for (V vertex : this.vertexMap) {
                if (vertex.vertexId().asUuid().equals(vertexId)) {
                    return vertex;
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
        public ImmutableList<EntityVertex> successors(EntityVertex vertex) {
            MutableIntList successorList = successorMap.get(vertex.vertexIndex());
            if (successorList != null) {
                MutableList<EntityVertex> successors = Lists.mutable.ofInitialCapacity(successorList.size());
                successorList.forEach(successorIndex -> {
                    successors.add(vertex(successorIndex));
                });
                return successors.toImmutable();
            }
            return Lists.immutable.empty();
        }

        public EntityVertex getRoot() {
            return root;
        }

        public DiTreeAbstract.Builder setRoot(V root) {
            addVertex(root);
            this.root = root;
            return this;
        }

        /**
         * If the vertex has an unassigned index, the index will be assigned to the size of the
         * vertex map.
         * @param vertex the vertex to add to the tree
         * @return the builder for fluent api.
         */
        public DiTreeAbstract.Builder addVertex(V vertex) {
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

        public DiTreeAbstract.Builder addEdge(V child, V parent) {
            addVertex(child);
            addVertex(parent);
            if (!successorMap.containsKey(parent.vertexIndex())) {
                successorMap.put(parent.vertexIndex(), IntLists.mutable.empty());
            }
            successorMap.get(parent.vertexIndex()).add(child.vertexIndex());
            predecessorMap.put(child.vertexIndex(), parent.vertexIndex());
            return this;
        }

        public DiTreeAbstract.Builder addEdge(int childIndex, int parentIndex) {
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

        @Override
        public V root() {
            return root;
        }

        @Override
        public Optional<EntityVertex> predecessor(EntityVertex vertex) {
            /**
             * Note that a get() method on IntInt map returns 0 as a default even if there is no key.
             * Thus you must test for containsKey()
             * TODO consider changing data structure to one that does not require
             */
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

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public boolean hasParentVertexWithMeaning(int vertexIndex, int meaningNid) {
        if (vertex(vertexIndex).meaningNid == meaningNid) {
            return true;
        }
        if (predecessorMap.containsKey(vertexIndex)) {
            return hasParentVertexWithMeaning(predecessorMap.get(vertexIndex), meaningNid);
        }
        return false;
    }

    /**
     * Fragment to string.
     *
     * @return A string representing the fragment of the tree
     * rooted in this vertex.
     */
    public String fragmentToString(EntityVertex fragmentRoot) {
        return fragmentToString("", fragmentRoot);
    }

    /**
     * Use to when printing out multiple expressions, and you want to differentiate the
     * identifiers so that they are unique across all fragments.
     *
     * @param nodeIdSuffix the identifier suffix for this expression.
     * @return A string representing the fragment of the expression
     * rooted in this node.
     */
    public String fragmentToString(String nodeIdSuffix, EntityVertex fragmentRoot) {
        final StringBuilder builder = new StringBuilder();
        VertexVisitData vertexVisitData = new VertexVisitData(this.vertexCount(),(vertex, graph, visitData) -> {
            for (int i = 0; i < visitData.distance(vertex.vertexIndex); i++) {
                builder.append("    ");
            }
            builder.append(vertex.toString(nodeIdSuffix));
            builder.append("\n");
        });

        this.depthFirstProcess(fragmentRoot.vertexIndex, vertexVisitData);
        return builder.toString();
    }
}

