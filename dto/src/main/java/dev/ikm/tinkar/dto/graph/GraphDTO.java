package dev.ikm.tinkar.dto.graph;

import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.map.primitive.ImmutableIntObjectMap;
import dev.ikm.tinkar.component.graph.Graph;
import dev.ikm.tinkar.component.graph.GraphAdaptorFactory;

public record GraphDTO(ImmutableList<VertexDTO> vertexMap,
                       ImmutableIntObjectMap<ImmutableIntList> successorMap)
        implements Graph<VertexDTO>, GraphDefaults {

    private static final int LOCAL_MARSHAL_VERSION = 3;

    @Override
    public <A> A adapt(GraphAdaptorFactory<A> adaptorFactory) {
        throw new UnsupportedOperationException("Adaptors are ephemeral, and are not transfer objects");
    }

    @Override
    public ImmutableIntList successors(int vertexIndex) {
        return successorMap.get(vertexIndex);
    }

}
