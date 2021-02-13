package org.hl7.tinkar.lombok.dto.graph;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.NonFinal;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.ImmutableIntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.hl7.tinkar.lombok.dto.binary.TinkarInput;
import org.hl7.tinkar.lombok.dto.binary.TinkarOutput;
import org.hl7.tinkar.component.graph.Graph;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.NoSuchElementException;
import java.util.UUID;

@Value
@Accessors(fluent = true)
@NonFinal
public class GraphDTO implements Graph<VertexDTO> {

    private static final int LOCAL_MARSHAL_VERSION = 3;

    @NonNull
    private final ImmutableList<VertexDTO> vertexMap;

    @NonNull
    private final ImmutableIntObjectMap<ImmutableIntList> successorMap;

    public GraphDTO(@NonNull ImmutableList<VertexDTO> vertexMap,
                    @NonNull ImmutableIntObjectMap<ImmutableIntList> successorMap) {

        this.vertexMap = vertexMap;
        this.successorMap = successorMap;
    }

    @Override
    public VertexDTO vertex(UUID vertexId) {
        for (VertexDTO vertexDTO: vertexMap) {
            if (vertexDTO.vertexId().asUuid().equals(vertexId)) {
                return vertexDTO;
            }
        }
        throw new NoSuchElementException("No vertex for: " + vertexId);
    }

    @Override
    public VertexDTO vertex(int vertexSequence) {
        return vertexMap.get(vertexSequence);
    }

    @Override
    public ImmutableList<VertexDTO> successors(VertexDTO vertex) {
        ImmutableIntList successorIntList = successorMap.getIfAbsent(vertex.vertexIndex(), () -> IntLists.immutable.empty());
        if (successorIntList.isEmpty()) {
            return Lists.immutable.empty();
        }
        MutableList<VertexDTO> successorList = Lists.mutable.ofInitialCapacity(successorIntList.size());
        successorIntList.forEach(vertexSequence -> successorList.add(vertexMap.get(vertexSequence)));
        return successorList.toImmutable();
    }

    protected static ImmutableList<VertexDTO>  unmarshalVertexMap(TinkarInput in) {
        if (LOCAL_MARSHAL_VERSION == in.getTinkerFormatVersion()) {
            int mapSize = in.getInt();
            MutableList<VertexDTO> vertexMap = Lists.mutable.ofInitialCapacity(mapSize);
            for (int i = 0; i < mapSize; i++) {
                VertexDTO vertexDTO = VertexDTO.make(in);
                vertexMap.add(vertexDTO);
            }
            return vertexMap.toImmutable();
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + in.getTinkerFormatVersion());
        }
    }

    protected void marshalVertexMap(TinkarOutput out) {
        try {
            out.writeInt(vertexMap.size());
            for (VertexDTO vertexDTO: vertexMap) {
                vertexDTO.marshal(out);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected static ImmutableIntObjectMap<ImmutableIntList>  unmarshalSuccessorMap(TinkarInput in, ImmutableList<VertexDTO> vertexMap) {
        if (LOCAL_MARSHAL_VERSION == in.getTinkerFormatVersion()) {
            int mapSize = in.getInt();
            MutableIntObjectMap<ImmutableIntList> successorMap = IntObjectMaps.mutable.ofInitialCapacity(mapSize);
            for (int i = 0; i < mapSize; i++) {
                int vertexSequence = in.getInt();
                int successorListSize = in.getInt();
                MutableIntList successorList = IntLists.mutable.empty();
                for (int j = 0; j < successorListSize; j++) {
                    successorList.add(in.getInt());
                }
                successorMap.put(vertexSequence, successorList.toImmutable());
            }
            return successorMap.toImmutable();
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + in.getTinkerFormatVersion());
        }
    }

    protected void marshalSuccessorMap(TinkarOutput out) {
        out.putInt(successorMap.size());
        successorMap.forEachKeyValue((int vertexSequence, ImmutableIntList successors) -> {
            out.putInt(vertexSequence);
            out.putInt(successors.size());
            successors.forEach(successorSequence -> out.putInt(successorSequence));
        });
    }
}
