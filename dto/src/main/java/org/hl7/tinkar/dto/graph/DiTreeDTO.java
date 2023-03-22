package org.hl7.tinkar.dto.graph;


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
import org.hl7.tinkar.component.graph.GraphAdaptorFactory;
import org.hl7.tinkar.dto.binary.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

public record DiTreeDTO(VertexDTO root,
                        ImmutableIntIntMap predecessorMap,
                        ImmutableList<VertexDTO> vertexMap,
                        ImmutableIntObjectMap<ImmutableIntList> successorMap)
        implements DiTree<VertexDTO>, GraphDefaults, Marshalable {

    @Unmarshaler
    public static DiTreeDTO make(TinkarInput in) {
        try {
            if (LOCAL_MARSHAL_VERSION == in.getTinkerFormatVersion()) {
                ImmutableList<VertexDTO> vertexMap = GraphDefaults.unmarshalVertexMap(in);
                ImmutableIntObjectMap<ImmutableIntList> successorMap = GraphDefaults.unmarshalSuccessorMap(in, vertexMap);
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

    public static Builder builder(VertexDTO root) {
        return new Builder(root);
    }

    @Override
    public <A> A adapt(GraphAdaptorFactory<A> adaptorFactory) {
        throw new UnsupportedOperationException("Adaptors are ephemeral, and are not transfer objects");
    }

    @Override
    public Optional<VertexDTO> predecessor(VertexDTO vertex) {
        return Optional.ofNullable(vertexMap().get(predecessorMap.get(vertex.vertexIndex())));
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
            if (!successorMap.containsKey(parent.vertexIndex())) {
                successorMap.put(parent.vertexIndex(), IntLists.mutable.empty());
            }
            successorMap.get(parent.vertexIndex()).add(child.vertexIndex());
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
