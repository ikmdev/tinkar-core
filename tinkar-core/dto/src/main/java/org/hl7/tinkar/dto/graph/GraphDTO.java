package org.hl7.tinkar.dto.graph;

import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.map.primitive.ImmutableIntObjectMap;
import org.hl7.tinkar.component.graph.Graph;

public record GraphDTO(ImmutableList<VertexDTO> vertexMap,
                       ImmutableIntObjectMap<ImmutableIntList> successorMap)
        implements Graph<VertexDTO>, GraphDefaults {

    private static final int LOCAL_MARSHAL_VERSION = 3;


}
