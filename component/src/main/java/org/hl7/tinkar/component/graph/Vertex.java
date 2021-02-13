package org.hl7.tinkar.component.graph;

import org.eclipse.collections.api.RichIterable;
import org.hl7.tinkar.common.util.id.VertexId;
import org.hl7.tinkar.component.Concept;

import java.util.Optional;

public interface Vertex {

    /**
     *
     * @return universally unique identifier for this vertex
     */
    VertexId vertexId();

    /**
     * The index of this vertex within its graph. The index is locally
     * unique within a graph, but not across graphs, or different versions of the same graph.
     * Vertex index is not used in equality or hash calculations.
     * @return the vertex index.
     */
    int vertexIndex();

    /**
     * Concept that represents the meaning of this vertex.
     * @return
     */
    Concept meaning();

    /**
     *
     * @param propertyConcept
     * @param <T> Type of the property object
     * @return An optional object that is associated with the properly concept.
     */
    <T extends Object> Optional<T> property(Concept propertyConcept);


    /**
     *
     * @param propertyConcept
     * @param <T> Type of the property object
     * @return A possibly null object that is associated with the properly concept.
     */
    <T extends Object> T propertyFast(Concept propertyConcept);

    /**
     *
     * @return keys for the populated properties.
     */
    <C extends Concept> RichIterable<C> propertyKeys();

}
