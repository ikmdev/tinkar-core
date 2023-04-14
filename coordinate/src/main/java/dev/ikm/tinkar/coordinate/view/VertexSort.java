package dev.ikm.tinkar.coordinate.view;


import org.eclipse.collections.api.collection.ImmutableCollection;
import org.eclipse.collections.api.list.ImmutableList;
import dev.ikm.tinkar.common.binary.Encodable;
import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculator;
import dev.ikm.tinkar.coordinate.navigation.calculator.Edge;
import dev.ikm.tinkar.coordinate.navigation.calculator.NavigationCalculator;
import dev.ikm.tinkar.entity.Entity;

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
