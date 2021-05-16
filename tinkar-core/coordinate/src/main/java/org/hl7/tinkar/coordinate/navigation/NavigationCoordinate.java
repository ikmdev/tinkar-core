package org.hl7.tinkar.coordinate.navigation;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.id.IntIdSet;
import org.hl7.tinkar.common.id.IntIds;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.coordinate.stamp.StateSet;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.terms.PatternFacade;
import org.hl7.tinkar.terms.PatternProxy;
import org.hl7.tinkar.terms.TinkarTerm;

import java.util.ArrayList;
import java.util.UUID;

/**
 * In mathematics, and more specifically in graph theory, a directed graph (or digraph)
 * is a graph that is made up of a set of vertices connected by edges, where the edges
 * have a direction associated with them.
 *
 * TODO change Graph NODE to Vertex everywhere since node is overloaded with JavaFx Node (which means something else)...
 */
public interface NavigationCoordinate {

    static UUID getNavigationCoordinateUuid(NavigationCoordinate navigationCoordinate) {
        ArrayList<UUID> uuidList = new ArrayList<>();
        for (int nid: navigationCoordinate.getNavigationConceptNids().toArray()) {
            Entity.provider().addSortedUuids(uuidList, nid);
        }
        return UUID.nameUUIDFromBytes(uuidList.toString().getBytes());
    }


    static IntIdSet defaultNavigationConceptIdentifierNids() {
        return IntIds.set.of(TinkarTerm.INFERRED_NAVIGATION.nid());
    }

    default UUID getNavigationCoordinateUuid() {
        return getNavigationCoordinateUuid(this);
    }

    //---------------------------

    IntIdSet getNavigationConceptNids();


    StateSet vertexStates();

    /**
     * Sort occurs first by the vertices sort pattern list, and then by the natural order of the concept description
     * as defined by the language coordinate. If the sort pattern list is empty, then the natural order
     * of the concept description as defined by the language coordinate.
     * @return true if vertices will be sorted.
     */
    boolean sortVertices();

    /**
     * Priority list of patterns used to sort vertices. If empty, and sortVertices() is true,
     * then the natural order of the concept description as defined by the language coordinate is used.
     * @return the priority list of patterns to use to sort the vertices.
     */
    IntIdList verticesSortPatternNidList();

    /**
     * Priority list of patterns used to sort vertices. If empty, and sortVertices() is true,
     * then the natural order of the concept description as defined by the language coordinate is used.
     * @return the priority list of patterns to use to sort the vertices.
     */
    default ImmutableList<PatternFacade> verticesSortPatternList() {
        return Lists.immutable.of(verticesSortPatternNidList().intStream()
                .mapToObj(nid -> (PatternFacade) PatternProxy.make(nid)).toArray(PatternFacade[]::new));
    }

    default ImmutableSet<Concept> getNavigationIdentifierConcepts() {
        return IntSets.immutable.of(getNavigationConceptNids().toArray()).collect(nid -> Entity.getFast(nid));
    }

    NavigationCoordinateRecord toNavigationCoordinateImmutable();

    default String toUserString() {
        StringBuilder sb = new StringBuilder("Navigators: ");
        for (int nid: getNavigationConceptNids().toArray()) {
            sb.append("\n     ").append(PrimitiveData.text(nid));
        }
        sb.append("\n\nVertex states:\n").append(vertexStates());
        if (sortVertices()) {
            sb.append("\n\nSort: \n");
            for (int patternNid: verticesSortPatternNidList().toArray()) {
                sb.append("  ");
                sb.append(PrimitiveData.text(patternNid));
                sb.append("\n");
            }
            sb.append("  natural order\n");
        } else {
            sb.append("\n\nSort: none\n");
        }
        return sb.toString();
    }

}
