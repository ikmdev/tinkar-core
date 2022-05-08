package org.hl7.tinkar.component.graph;

import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.map.primitive.ImmutableIntObjectMap;

public interface DiGraph<V extends Vertex> extends Graph<V> {

    /**
     * A graph can have multiple roots.
     *
     * @return The roots of a directed graph.
     */
    ImmutableList<V> roots();

    /**
     * A directed graph can have multiple predecessors.
     *
     * @param vertex to get the predecessors for
     * @return The predecessors of the provided vertex. Empty list if a root node.
     */
    ImmutableList<V> predecessors(V vertex);

    ImmutableIntObjectMap<ImmutableIntList> predecessorMap();


    @Override
    default ImmutableIntList successors(int vertexIndex) {
        return successorMap().get(vertexIndex);
    }

}
