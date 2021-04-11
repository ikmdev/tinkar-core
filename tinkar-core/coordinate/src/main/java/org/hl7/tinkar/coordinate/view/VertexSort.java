package org.hl7.tinkar.coordinate.view;


import java.util.UUID;

import org.hl7.tinkar.common.binary.Encodable;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.coordinate.manifold.ManifoldCoordinateImmutable;
import org.hl7.tinkar.coordinate.language.LanguageCoordinate;
import org.hl7.tinkar.coordinate.stamp.StampFilter;
import org.hl7.tinkar.entity.Entity;

public interface VertexSort extends Encodable {

    /**
     *
     * @return a unique identifier for this sort method
     */
    UUID getVertexSortUUID();

    String getVertexSortName();

    String getVertexLabel(int vertexConceptNid, LanguageCoordinate languageCoordinate, StampFilter stampFilter);

    default String getVertexLabel(Concept vertexConcept, LanguageCoordinate languageCoordinate, StampFilter stampFilter) {
        return getVertexLabel(Entity.provider().nidForComponent(vertexConcept), languageCoordinate, stampFilter);
    }

    /**
     * Sort the vertex concept nids with respect to settings from the
     * digraphCoordinate where appropriate.
     * @param vertexConceptNids
     * @param view
     * @return sorted vertexConceptNids
     */
    int[] sortVertexes(int[] vertexConceptNids, View view);
}
