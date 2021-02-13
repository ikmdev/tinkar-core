package org.hl7.tinkar.entity.graph;

import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.map.primitive.ImmutableIntIntMap;
import org.eclipse.collections.api.map.primitive.ImmutableIntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntIntMap;
import org.eclipse.collections.impl.factory.primitive.IntIntMaps;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.hl7.tinkar.component.graph.DiTree;
import org.hl7.tinkar.component.graph.Vertex;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.graph.builder.GraphBuilder;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class DiTreeEntity extends DiGraphAbstract implements DiTree<VertexEntity> {
    final VertexEntity root;
    final ImmutableIntIntMap predecessorMap;
    final Graph<VertexEntity, DefaultEdge> treeEntity;

    public DiTreeEntity(VertexEntity root, ImmutableList<VertexEntity> vertexMap,
                        ImmutableIntObjectMap<ImmutableIntList> successorMap, ImmutableIntIntMap predecessorMap) {
        super(vertexMap, successorMap);
        this.root = root;
        this.predecessorMap = predecessorMap;

        GraphBuilder<VertexEntity, DefaultEdge, ? extends SimpleDirectedGraph<VertexEntity, DefaultEdge>> sdgb =  SimpleDirectedGraph.createBuilder(DefaultEdge.class);
        sdgb.addVertex(this.root);
        recursiveWalk(this.root, sdgb);
        this.treeEntity = sdgb.buildAsUnmodifiable();
    }

    private void recursiveWalk(VertexEntity parentVertex, GraphBuilder<VertexEntity, DefaultEdge, ? extends SimpleDirectedGraph<VertexEntity, DefaultEdge>> sdgb) {
        for (int successorIndex: successorMap().getIfAbsent(parentVertex.vertexIndex(), () -> IntLists.immutable.empty()).toArray()) {
            VertexEntity successorVertex = vertex(successorIndex);
            sdgb.addVertex(successorVertex);
            sdgb.addEdge(parentVertex, successorVertex);
            recursiveWalk(successorVertex, sdgb);
        }
    }

    @Override
    public ImmutableIntIntMap predecessorMap() {
        return predecessorMap;
    }

    @Override
    public VertexEntity root() {
        return root;
    }

    @Override
    public Optional<VertexEntity> predecessor(VertexEntity vertex) {
        if (vertex.equals(root)) {
            return Optional.empty();
        }
        DefaultEdge outgoingEdge = treeEntity.outgoingEdgesOf(vertex).stream().findFirst().get();
        return Optional.of(treeEntity.getEdgeTarget(outgoingEdge));
    }

    public static DiTreeEntity make(DiTree<Vertex> tree) {
        ImmutableList<VertexEntity> vertexMap = getVertexEntities(tree);
        VertexEntity root = vertexMap.get(tree.root().vertexIndex());
        return new DiTreeEntity(root, vertexMap, tree.successorMap(), tree.predecessorMap());
    }

    public static DiTreeEntity make(ByteBuf readBuf, byte entityFormatVersion) {
        if (entityFormatVersion != ENTITY_FORMAT_VERSION) {
            throw new IllegalStateException("Unsupported entity format version: " + entityFormatVersion);
        }

        ImmutableList<VertexEntity> vertexMap = readVertexEntities(readBuf, entityFormatVersion);
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

}
