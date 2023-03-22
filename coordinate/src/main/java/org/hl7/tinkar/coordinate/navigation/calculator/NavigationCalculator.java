package org.hl7.tinkar.coordinate.navigation.calculator;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.id.IntIdSet;
import org.hl7.tinkar.common.id.IntIds;
import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculatorDelegate;
import org.hl7.tinkar.coordinate.navigation.NavigationCoordinateRecord;
import org.hl7.tinkar.coordinate.stamp.StateSet;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorDelegate;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.EntityFacade;
import org.hl7.tinkar.terms.PatternFacade;
import org.hl7.tinkar.terms.TinkarTerm;

public interface NavigationCalculator extends StampCalculatorDelegate, LanguageCalculatorDelegate {

    StampCalculatorWithCache vertexStampCalculator();

    StateSet allowedVertexStates();

    default IntIdList parentsOf(ConceptFacade concept) {
        return parentsOf(concept.nid());
    }

    default IntIdList parentsOf(int conceptNid) {
        if (sortVertices()) {
            return sortedParentsOf(conceptNid);
        }
        return unsortedParentsOf(conceptNid);
    }

    boolean sortVertices();

    IntIdList sortedParentsOf(int conceptNid);

    IntIdList unsortedParentsOf(int conceptNid);

    default IntIdSet descendentsOf(ConceptFacade concept) {
        return descendentsOf(concept.nid());
    }

    IntIdSet descendentsOf(int conceptNid);

    default IntIdSet ancestorsOf(ConceptFacade concept) {
        return descendentsOf(concept.nid());
    }

    IntIdSet ancestorsOf(int conceptNid);

    default IntIdSet kindOf(ConceptFacade concept) {
        return kindOf(concept.nid());
    }

    IntIdSet kindOf(int conceptNid);

    default ImmutableList<Edge> parentEdges(ConceptFacade concept) {
        return childEdges(concept.nid());
    }

    default ImmutableList<Edge> childEdges(int conceptNid) {
        if (sortVertices()) {
            return sortedChildEdges(conceptNid);
        }
        return unsortedChildEdges(conceptNid);
    }

    ImmutableList<Edge> sortedChildEdges(int conceptNid);

    ImmutableList<Edge> unsortedChildEdges(int conceptNid);

    default ImmutableList<Edge> parentEdges(int conceptNid) {
        if (sortVertices()) {
            return sortedParentEdges(conceptNid);
        }
        return unsortedParentEdges(conceptNid);
    }

    ImmutableList<Edge> sortedParentEdges(int conceptNid);

    ImmutableList<Edge> unsortedParentEdges(int conceptNid);

    default ImmutableList<Edge> sortedParentEdges(ConceptFacade concept) {
        return sortedParentEdges(concept.nid());
    }

    default ImmutableList<Edge> unsortedParentEdges(ConceptFacade concept) {
        return unsortedParentEdges(concept.nid());
    }

    default ImmutableList<Edge> childEdges(ConceptFacade concept) {
        return childEdges(concept.nid());
    }

    default ImmutableList<Edge> sortedChildEdges(ConceptFacade concept) {
        return sortedChildEdges(concept.nid());
    }

    default ImmutableList<Edge> unsortedChildEdges(ConceptFacade concept) {
        return unsortedChildEdges(concept.nid());
    }

    default IntIdList childrenOf(ConceptFacade concept) {
        return childrenOf(concept.nid());
    }

    default IntIdList childrenOf(int conceptNid) {
        if (sortVertices()) {
            return sortedChildrenOf(conceptNid);
        }
        return unsortedChildrenOf(conceptNid);
    }

    IntIdList sortedChildrenOf(int conceptNid);

    IntIdList unsortedChildrenOf(int conceptNid);

    default IntIdList sortedParentsOf(ConceptFacade concept) {
        return sortedParentsOf(concept.nid());
    }

    default IntIdList sortedChildrenOf(ConceptFacade concept) {
        return sortedChildrenOf(concept.nid());
    }

    default IntIdList unsortedChildrenOf(ConceptFacade concept) {
        return unsortedChildrenOf(concept.nid());
    }

    default IntIdList unsortedParentsOf(ConceptFacade concept) {
        return unsortedParentsOf(concept.nid());
    }

    default IntIdList toSortedList(IntIdSet inputSet) {
        // TODO add pattern sort to implementation...
        return toSortedList(IntIds.list.of(inputSet.toArray()));
    }

    IntIdList toSortedList(IntIdList inputList);

    NavigationCoordinateRecord navigationCoordinate();


    default IntIdList unsortedParentsOf(ConceptFacade concept, PatternFacade patternFacade) {
        return unsortedParentsOf(concept.nid(), patternFacade.nid());
    }

    IntIdList unsortedParentsOf(int conceptNid, int patternNid);


    default boolean isMultiparent(EntityFacade facade) {
        return isMultiparent(facade.nid());
    }

    default boolean isMultiparent(int conceptNid) {
        if (conceptNid == -1
                || conceptNid == TinkarTerm.UNINITIALIZED_COMPONENT.nid()) {
            return false;
        }
        return parentsOf(conceptNid).size() > 1;
    }

}
