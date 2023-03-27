package dev.ikm.tinkar.dto.graph;


import dev.ikm.tinkar.dto.binary.*;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.ImmutableIntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import dev.ikm.tinkar.component.graph.DiGraph;
import dev.ikm.tinkar.component.graph.GraphAdaptorFactory;
import dev.ikm.tinkar.dto.binary.*;

public record DiGraphDTO(ImmutableIntList rootSequences,
                         ImmutableIntObjectMap<ImmutableIntList> predecessorMap,
                         ImmutableList<VertexDTO> vertexMap,
                         ImmutableIntObjectMap<ImmutableIntList> successorMap)
        implements DiGraph<VertexDTO>, GraphDefaults, Marshalable {

    private static final int LOCAL_MARSHAL_VERSION = 3;

    @Override
    public VertexType vertexType(VertexDTO vertex) {
        throw new UnsupportedOperationException();
    }

    @Unmarshaler
    public static DiGraphDTO make(TinkarInput in) {
        if (LOCAL_MARSHAL_VERSION == in.getTinkerFormatVersion()) {
            ImmutableList<VertexDTO> vertexMap = GraphDefaults.unmarshalVertexMap(in);
            ImmutableIntObjectMap<ImmutableIntList> successorMap = GraphDefaults.unmarshalSuccessorMap(in, vertexMap);

            int rootCount = in.getInt();
            MutableIntList roots = IntLists.mutable.empty();
            for (int i = 0; i < rootCount; i++) {
                roots.add(in.getInt());
            }
            int predecessorMapSize = in.getInt();
            MutableIntObjectMap<ImmutableIntList> predecessorMap = IntObjectMaps.mutable.ofInitialCapacity(predecessorMapSize);
            for (int i = 0; i < predecessorMapSize; i++) {
                int vertexIndex = in.getInt();
                int predecessorCount = in.getInt();
                MutableIntList predecessorList = IntLists.mutable.empty();
                for (int j = 0; j < predecessorCount; j++) {
                    predecessorList.add(in.getInt());
                }
                predecessorMap.put(vertexIndex, predecessorList.toImmutable());
            }
            return new DiGraphDTO(roots.toImmutable(),
                    predecessorMap.toImmutable(),
                    vertexMap,
                    successorMap);
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + marshalVersion);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public <A> A adapt(GraphAdaptorFactory<A> adaptorFactory) {
        throw new UnsupportedOperationException("Adaptors are ephemeral, and are not transfer objects");
    }

    @Override
    public ImmutableList<VertexDTO> roots() {
        MutableList<VertexDTO> roots = Lists.mutable.ofInitialCapacity(rootSequences.size());
        rootSequences.forEach(rootSequence -> roots.add(vertex(rootSequence)));
        return roots.toImmutable();
    }

    @Override
    public ImmutableList<VertexDTO> predecessors(VertexDTO vertex) {
        ImmutableIntList predecessorIntList = predecessorMap.getIfAbsent(vertex.vertexIndex(), () -> IntLists.immutable.empty());
        if (predecessorIntList.isEmpty()) {
            return Lists.immutable.empty();
        }
        MutableList<VertexDTO> predecessorList = Lists.mutable.ofInitialCapacity(predecessorIntList.size());
        predecessorIntList.forEach(vertexSequence -> predecessorList.add(vertexMap().get(vertexSequence)));
        return predecessorList.toImmutable();
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        marshalVertexMap(out);
        marshalSuccessorMap(out);
        out.putInt(rootSequences.size());
        rootSequences.forEach(root -> out.putInt(root));

        out.putInt(predecessorMap.size());
        predecessorMap.forEachKeyValue((vertex, predecessorList) -> {
            out.putInt(vertex);
            out.putInt(predecessorList.size());
            predecessorList.forEach(predecessorSequence -> out.putInt(predecessorSequence));
        });
    }

    public static class Builder {
        private final MutableList<VertexDTO> vertexMap = Lists.mutable.empty();
        private final MutableIntObjectMap<MutableIntList> successorMap = IntObjectMaps.mutable.empty();
        private final MutableIntObjectMap<MutableIntList> predecessorMap = IntObjectMaps.mutable.empty();
        ;
        private final MutableIntList roots = IntLists.mutable.empty();

        protected Builder() {
        }

        public Builder addRoot(VertexDTO root) {
            vertexMap.add(root.vertexIndex(), root);
            roots.add(root.vertexIndex());
            return this;
        }

        public Builder add(VertexDTO child, VertexDTO parent) {
            vertexMap.add(child.vertexIndex(), child);
            successorMap.getIfAbsentPut(parent.vertexIndex(), IntLists.mutable.empty()).add(child.vertexIndex());
            predecessorMap.getIfAbsentPut(child.vertexIndex(), IntLists.mutable.empty()).add(parent.vertexIndex());
            return this;
        }

        public DiGraphDTO build() {

            MutableIntObjectMap<ImmutableIntList> intermediateSuccessorMap = IntObjectMaps.mutable.ofInitialCapacity(successorMap.size());
            successorMap.forEachKeyValue((vertex, successorList) -> intermediateSuccessorMap.put(vertex, successorList.toImmutable()));

            MutableIntObjectMap<ImmutableIntList> intermediatePredecessorMap = IntObjectMaps.mutable.ofInitialCapacity(predecessorMap.size());
            predecessorMap.forEachKeyValue((vertex, predecessorList) -> intermediatePredecessorMap.put(vertex, predecessorList.toImmutable()));

            return new DiGraphDTO(roots.toImmutable(),
                    intermediatePredecessorMap.toImmutable(),
                    vertexMap.toImmutable(),
                    intermediateSuccessorMap.toImmutable());
        }
    }
}
