package org.hl7.tinkar.component.graph;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.map.primitive.ImmutableIntObjectMap;

import java.util.UUID;

public interface Graph<V extends Vertex> {

    /**
     * @param vertexId a universally unique identifier for a vertex
     * @return the vertex associated with the identifier
     */
    V vertex(UUID vertexId);

    V vertex(int vertexSequence);

    ImmutableList<V> vertexMap();

    ImmutableIntObjectMap<ImmutableIntList> successorMap();

    default ImmutableList<V> descendents(V logicVertex) {
        MutableList<V> descendentList = Lists.mutable.empty();
        recursiveAddSuccessors(logicVertex, descendentList);
        return descendentList.toImmutable();
    }

    default void recursiveAddSuccessors(V logicVertex, MutableList<V> descendentList) {
        ImmutableList<V> successorList = successors(logicVertex);
        descendentList.addAllIterable(successorList);
        for (V successor : successorList) {
            recursiveAddSuccessors(successor, descendentList);
        }
    }

    /**
     * @param vertex a vertex to retrieve the successors of
     * @return the successors for the provided vertex
     */
    ImmutableList<V> successors(V vertex);

}
