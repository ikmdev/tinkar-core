package org.hl7.tinkar.entity.graph;

import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.Arrays;

public class JGraphUtil {
    public static class EntityVertexEdge extends DefaultEdge {
        @Override
        public int hashCode() {
            return getSource().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof EntityVertexEdge that) {
                return this.getSource().equals(that.getSource()) &&
                        this.getTarget().equals(that.getTarget());
            }
            return false;
        }
    }

    public static <V extends EntityVertex> Graph<V, DefaultEdge> toJGraph(DiGraphAbstract<V> diGraph) {
        Graph<V, DefaultEdge> directedGraph = new DefaultDirectedGraph<>(EntityVertexEdge.class);
        for (V vertex: diGraph.vertexMap) {
            if (vertex != null) {
                directedGraph.addVertex(vertex);
            }
        }
        for (V sourceVertex: diGraph.vertexMap) {
            if (sourceVertex != null) {
                ImmutableIntList successorList = diGraph.successorMap.get(sourceVertex.vertexIndex);
                if (successorList != null) {
                    for (int destinationIndex: successorList.toArray()) {
                        directedGraph.addEdge(sourceVertex, diGraph.vertex(destinationIndex));
                    }
                }
            }
        }
        return directedGraph;
    }
}
