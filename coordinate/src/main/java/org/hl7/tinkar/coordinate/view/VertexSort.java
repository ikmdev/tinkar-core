package org.hl7.tinkar.coordinate.view;


import org.eclipse.collections.api.collection.ImmutableCollection;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.binary.Encodable;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculator;
import org.hl7.tinkar.coordinate.navigation.calculator.Edge;
import org.hl7.tinkar.coordinate.navigation.calculator.NavigationCalculator;
import org.hl7.tinkar.entity.Entity;

import java.util.UUID;

public interface VertexSort extends Encodable {

    /**
     * @return a unique identifier for this sort method
     */
    UUID getVertexSortUUID();

    String getVertexSortName();

    default String getVertexLabel(Concept vertexConcept, LanguageCalculator languageCalculator) {
        return getVertexLabel(Entity.nid(vertexConcept), languageCalculator);
    }

    String getVertexLabel(int vertexConceptNid, LanguageCalculator languageCalculator);

    /**
     * Sort the vertex concept nids with respect to settings from the
     * digraphCoordinate where appropriate.
     *
     * @param vertexConceptNids
     * @param navigationCalculator
     * @return sorted vertexConceptNids
     */
    int[] sortVertexes(int[] vertexConceptNids, NavigationCalculator navigationCalculator);

    ImmutableList<Edge> sortEdges(ImmutableCollection<Edge> edges, NavigationCalculator navigationCalculator);
}
