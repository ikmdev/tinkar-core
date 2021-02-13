package org.hl7.tinkar.entity.graph;

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
import org.hl7.tinkar.component.graph.Graph;
import org.hl7.tinkar.component.graph.Vertex;

import java.util.NoSuchElementException;
import java.util.UUID;

public abstract class DiGraphAbstract {
    public static final byte ENTITY_FORMAT_VERSION = 1;

    final ImmutableList<VertexEntity> vertexMap;
    final ImmutableIntObjectMap<ImmutableIntList> successorMap;

    public DiGraphAbstract(ImmutableList<VertexEntity> vertexMap,
                           ImmutableIntObjectMap<ImmutableIntList> successorMap) {
        this.vertexMap = vertexMap;
        this.successorMap = successorMap;
    }

    public ImmutableList<VertexEntity> vertexMap() {
        return vertexMap;
    }

    public ImmutableIntObjectMap<ImmutableIntList> successorMap() {
        return successorMap;
    }

    public VertexEntity vertex(int vertexSequence) {
        return vertexMap.get(vertexSequence);
    }

    public VertexEntity vertex(UUID vertexId) {
        for (VertexEntity vertexEntity: this.vertexMap) {
            if (vertexEntity.vertexId().asUuid().equals(vertexId)) {
                return vertexEntity;
            }
        }
        throw new NoSuchElementException("VertexId: " + vertexId);
    }

    public ImmutableList<VertexEntity> successors(VertexEntity vertex) {
        ImmutableIntList successorList = successorMap.get(vertex.vertexIndex());
        MutableList<VertexEntity> successors = Lists.mutable.ofInitialCapacity(successorList.size());
        successorList.forEach(successorIndex -> {
            successors.add(vertex(successorIndex));
        });
        return successors.toImmutable();
    }

    public int size() {
        // Empty vertex is 34 bytes
        return vertexMap.size() * 64;
    }

    protected static ImmutableList<VertexEntity> getVertexEntities(Graph<Vertex> tree) {
        MutableList<VertexEntity> vertexMap =  Lists.mutable.ofInitialCapacity(tree.vertexMap().size());
        for (Vertex vertex: tree.vertexMap()) {
            vertexMap.add(VertexEntity.make(vertex));
        }
        return vertexMap.toImmutable();
    }

    protected static ImmutableList<VertexEntity> readVertexEntities(ByteBuf readBuf, byte entityFormatVersion) {
        int vertexMapSize = readBuf.readInt();
        MutableList<VertexEntity> vertexMap = Lists.mutable.ofInitialCapacity(vertexMapSize);
        for (int i = 0; i < vertexMapSize; i++) {
            VertexEntity vertexEntity = VertexEntity.make(readBuf, entityFormatVersion);
            vertexMap.add(vertexEntity);
        }
        return vertexMap.toImmutable();
    }

    protected static ImmutableIntObjectMap<ImmutableIntList> readIntIntListMap(ByteBuf readBuf) {
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

    protected void writeIntIntListMap(ByteBuf byteBuf, ImmutableIntObjectMap<ImmutableIntList> map) {
        byteBuf.writeInt(successorMap().size());
        map.forEachKeyValue((int vertexIndex, ImmutableIntList destinationVertexes) -> {
            byteBuf.writeInt(vertexIndex);
            byteBuf.writeInt(destinationVertexes.size());
            destinationVertexes.forEach(destinationIndex -> byteBuf.writeInt(destinationIndex));
        });
    }

    protected void writeVertexMap(ByteBuf byteBuf) {
        byteBuf.writeInt(vertexMap.size());
        vertexMap.forEach(vertexEntity -> {
            byteBuf.write(vertexEntity.getBytes());
        });
    }


}
