package org.hl7.tinkar.entity.graph;

import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.map.primitive.ImmutableIntObjectMap;
import org.hl7.tinkar.component.graph.DiGraph;
import org.hl7.tinkar.component.graph.Vertex;

import java.util.concurrent.atomic.AtomicReference;

public class DiGraphEntity extends DiGraphAbstract implements DiGraph<VertexEntity> {
    final ImmutableList<VertexEntity> roots;
    final ImmutableIntObjectMap<ImmutableIntList> predecessorMap;

    public DiGraphEntity(ImmutableList<VertexEntity> roots, ImmutableList<VertexEntity> vertexMap,
                         ImmutableIntObjectMap<ImmutableIntList> successorMap, ImmutableIntObjectMap<ImmutableIntList> predecessorMap) {
        super(vertexMap, successorMap);
        this.roots = roots;
        this.predecessorMap = predecessorMap;
    }

    @Override
    public ImmutableList<VertexEntity> roots() {
        return roots;
    }

    @Override
    public ImmutableList<VertexEntity> predecessors(VertexEntity vertex) {
        ImmutableIntList predecessorlist = predecessorMap.get(vertex.vertexIndex());
        MutableList<VertexEntity> predecessors = Lists.mutable.ofInitialCapacity(predecessorlist.size());
        predecessorlist.forEach(successorIndex -> {
            predecessors.add(vertex(successorIndex));
        });
        return predecessors.toImmutable();
    }

    @Override
    public ImmutableIntObjectMap<ImmutableIntList> predecessorMap() {
        return predecessorMap;
    }

    public static DiGraphEntity make(DiGraph<Vertex> tree) {
        ImmutableList<VertexEntity> vertexMap = getVertexEntities(tree);
        MutableList<VertexEntity> rootList =  Lists.mutable.ofInitialCapacity(tree.roots().size());
        for (Vertex vertex: tree.roots()) {
            rootList.add(VertexEntity.make(vertex));
        }
        return new DiGraphEntity(rootList.toImmutable(), vertexMap, tree.successorMap(), tree.predecessorMap());
    }

    public static DiGraphEntity make(ByteBuf readBuf, byte entityFormatVersion) {
        if (entityFormatVersion != ENTITY_FORMAT_VERSION) {
            throw new IllegalStateException("Unsupported entity format version: " + entityFormatVersion);
        }
        ImmutableList<VertexEntity> vertexMap = readVertexEntities(readBuf, entityFormatVersion);
        ImmutableIntObjectMap<ImmutableIntList> successorMap = readIntIntListMap(readBuf);
        ImmutableIntObjectMap<ImmutableIntList> predecessorMap = readIntIntListMap(readBuf);

        int rootCount = readBuf.readInt();
        MutableList<VertexEntity> roots = Lists.mutable.ofInitialCapacity(rootCount);
        for (int i = 0; i < rootCount; i++) {
            roots.add(vertexMap.get(i));
        }

        return new DiGraphEntity(roots.toImmutable(), vertexMap.toImmutable(),
                successorMap, predecessorMap);

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
                writeIntIntListMap(byteBuf, predecessorMap());

                byteBuf.writeInt(roots.size());
                roots.forEach(root -> byteBuf.writeInt(root.vertexIndex()));
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
