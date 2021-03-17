package org.hl7.tinkar.lombok.dto.graph;

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

public record GraphDTO(ImmutableList<VertexDTO> vertexMap,
                       ImmutableIntObjectMap<ImmutableIntList> successorMap)
        implements Graph<VertexDTO>, GraphDefaults {

    private static final int LOCAL_MARSHAL_VERSION = 3;


}
