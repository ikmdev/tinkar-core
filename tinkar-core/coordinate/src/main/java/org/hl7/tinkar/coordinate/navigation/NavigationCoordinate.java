package org.hl7.tinkar.coordinate.navigation;

import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.primitive.ImmutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.component.Concept;
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


    static ImmutableIntSet defaultNavigationConceptIdentifierNids() {
        return IntSets.immutable.of(TinkarTerm.EL_PLUS_PLUS_INFERRED_FORM_ASSEMBLAGE.nid());
    }

    default UUID getNavigationCoordinateUuid() {
        return getNavigationCoordinateUuid(this);
    }

    //---------------------------

    ImmutableIntSet getNavigationConceptNids();

    default ImmutableSet<Concept> getNavigationIdentifierConcepts() {
        return IntSets.immutable.of(getNavigationConceptNids().toArray()).collect(nid -> Entity.getFast(nid));
    }

    NavigationCoordinateImmutable toNavigationCoordinateImmutable();

    default String toUserString() {
        StringBuilder sb = new StringBuilder("Navigators: ");
        for (int nid: getNavigationConceptNids().toArray()) {
            sb.append("\n     ").append(PrimitiveData.text(nid));
        }
        return sb.toString();
    }

}
