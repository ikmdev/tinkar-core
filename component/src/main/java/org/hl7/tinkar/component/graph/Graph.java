package org.hl7.tinkar.component.graph;

import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.ImmutableIntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;

import java.util.UUID;

public interface Graph<V extends Vertex> {

    /**
     *
     * @param vertexId a universally unique identifier for a vertex
     * @return the vertex associated with the identifier
     */
    V vertex(UUID vertexId);

    V vertex(int vertexSequence);

    ImmutableList<V> vertexMap();

    ImmutableIntObjectMap<ImmutableIntList> successorMap();

    /**
     *
     * @param vertex a vertex to retrieve the successors of
     * @return the successors for the provided vertex
     */
    ImmutableList<V> successors(V vertex);

}
