package org.hl7.tinkar.lombok.dto.graph;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;
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
import org.hl7.tinkar.lombok.dto.binary.*;
import org.hl7.tinkar.component.graph.DiTree;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Optional;

@Value
@Accessors(fluent = true)
public class DiTreeDTO extends GraphDTO implements DiTree<VertexDTO>, Marshalable {

    private static final int LOCAL_MARSHAL_VERSION = 3;

    @NonNull
    VertexDTO root;

    @NonNull
    ImmutableIntIntMap predecessorMap;

     public DiTreeDTO(@NonNull VertexDTO root,
                     @NonNull ImmutableIntIntMap predecessorMap,
                     @NonNull ImmutableList<VertexDTO> vertexMap,
                     @NonNull ImmutableIntObjectMap<ImmutableIntList> successorMap) {
        super(vertexMap, successorMap);
        this.root = root;
        this.predecessorMap = predecessorMap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiTreeDTO diTreeDTO)) return false;
        if (!super.equals(o)) return false;
        return root.equals(diTreeDTO.root) && predecessorMap.equals(diTreeDTO.predecessorMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), root, predecessorMap);
    }

    @Override
    public Optional<VertexDTO> predecessor(VertexDTO vertex) {
        return Optional.ofNullable(vertexMap().get(predecessorMap.get(vertex.vertexIndex())));
    }

    @Unmarshaler
    public static DiTreeDTO make(TinkarInput in) {
        try {
            if (LOCAL_MARSHAL_VERSION == in.getTinkerFormatVersion()) {
                ImmutableList<VertexDTO> vertexMap = GraphDTO.unmarshalVertexMap(in);
                ImmutableIntObjectMap<ImmutableIntList> successorMap = GraphDTO.unmarshalSuccessorMap(in, vertexMap);
                VertexDTO root = vertexMap.get(in.getInt());
                int predecessorMapSize = in.readInt();
                MutableIntIntMap predecessorMap = IntIntMaps.mutable.ofInitialCapacity(predecessorMapSize);
                for (int i = 0; i < predecessorMapSize; i++) {
                    predecessorMap.put(in.getInt(), in.getInt());
                }
                return new DiTreeDTO(root, predecessorMap.toImmutable(), vertexMap, successorMap);
            } else {
                throw new UnsupportedOperationException("Unsupported version: " + marshalVersion);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        marshalVertexMap(out);
        marshalSuccessorMap(out);

        out.putInt(root.vertexIndex());
        out.putInt(predecessorMap.size());
        predecessorMap.forEachKeyValue((vertex, predecessor) -> {
            out.putInt(vertex);
            out.putInt(predecessor);
        });
    }
    public static Builder builder(VertexDTO root) {
         return new Builder(root);
    }
    public static class Builder {
         private final MutableList<VertexDTO> vertexMap = Lists.mutable.empty();
         private final MutableIntObjectMap<MutableIntList> successorMap = IntObjectMaps.mutable.empty();
         private final MutableIntIntMap predecessorMap = IntIntMaps.mutable.empty();
         private final VertexDTO root;

        private Builder(VertexDTO root) {
            this.root = root;
            vertexMap.add(this.root.vertexIndex(), this.root);
        }

        public Builder add(VertexDTO child, VertexDTO parent) {
            vertexMap.add(child.vertexIndex(), child);
            successorMap.getIfAbsent(parent.vertexIndex(), () -> IntLists.mutable.empty()).add(child.vertexIndex());
            predecessorMap.put(child.vertexIndex(), parent.vertexIndex());
            return this;
        }

        public DiTreeDTO build() {

            MutableIntObjectMap<ImmutableIntList> intermediateSuccessorMap = IntObjectMaps.mutable.ofInitialCapacity(successorMap.size());
            successorMap.forEachKeyValue((vertex, successorList) -> intermediateSuccessorMap.put(vertex, successorList.toImmutable()));

            return new DiTreeDTO(root,
                    predecessorMap.toImmutable(),
                    vertexMap.toImmutable(),
                    intermediateSuccessorMap.toImmutable());
        }
    }

}
