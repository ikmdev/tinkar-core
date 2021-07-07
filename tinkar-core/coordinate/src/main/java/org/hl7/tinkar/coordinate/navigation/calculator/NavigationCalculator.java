package org.hl7.tinkar.coordinate.navigation.calculator;

import org.hl7.tinkar.common.id.IdList;
import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.id.IntIdSet;
import org.hl7.tinkar.common.id.IntIds;
import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculatorDelegate;
import org.hl7.tinkar.coordinate.navigation.NavigationCoordinateRecord;
import org.hl7.tinkar.coordinate.stamp.StateSet;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorDelegate;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.PatternFacade;

public interface NavigationCalculator extends StampCalculatorDelegate, LanguageCalculatorDelegate {

    boolean sortVertices();

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
    default IntIdList descendentsOf(ConceptFacade concept) {
        return descendentsOf(concept.nid());
    }
    default IntIdList descendentsOf(int conceptNid) {
        if (sortVertices()) {
            return sortedDescendentsOf(conceptNid);
        }
        return IntIds.list.of(unsortedDescendentsOf(conceptNid).toArray());
    }

    default IntIdList ancestorsOf(ConceptFacade concept) {
        return descendentsOf(concept.nid());
    }
    default IntIdList ancestorsOf(int conceptNid) {
        if (sortVertices()) {
            return sortedAncestorsOf(conceptNid);
        }
        return IntIds.list.of(unsortedAncestorsOf(conceptNid).toArray());
    }

    default IntIdList kindOf(ConceptFacade concept) {
        return childrenOf(concept.nid());
    }
    default IntIdList kindOf(int conceptNid) {
        if (sortVertices()) {
            return kindOf(conceptNid);
        }
        return kindOf(conceptNid);
    }

    default IntIdSet kindOfSet(ConceptFacade concept) {
        return kindOfSet(concept.nid());
    }
    IntIdSet kindOfSet(int conceptNid);

    default IntIdList childrenOf(ConceptFacade concept) {
        return childrenOf(concept.nid());
    }
    default IntIdList childrenOf(int conceptNid) {
        if (sortVertices()) {
            return sortedChildrenOf(conceptNid);
        }
        return unsortedChildrenOf(conceptNid);
    }

    default IntIdList sortedParentsOf(ConceptFacade concept) {
        return sortedParentsOf(concept.nid());
    }
    IntIdList sortedParentsOf(int conceptNid);

    default IntIdList sortedChildrenOf(ConceptFacade concept) {
        return sortedChildrenOf(concept.nid());
    }
    IntIdList sortedChildrenOf(int conceptNid);

    default IntIdList sortedDescendentsOf(ConceptFacade concept) {
        return sortedDescendentsOf(concept.nid());
    }
    IntIdList sortedDescendentsOf(int conceptNid);

    default IntIdList sortedAncestorsOf(ConceptFacade concept) {
        return sortedAncestorsOf(concept.nid());
    }
    IntIdList sortedAncestorsOf(int conceptNid);

    default IntIdSet unsortedAncestorsOf(ConceptFacade concept) {
        return unsortedAncestorsOf(concept.nid());
    }
    IntIdSet unsortedAncestorsOf(int conceptNid);

    default IntIdList unsortedChildrenOf(ConceptFacade concept) {
        return unsortedChildrenOf(concept.nid());
    }
    IntIdList unsortedChildrenOf(int conceptNid);

    default IntIdSet unsortedDescendentsOf(ConceptFacade concept) {
        return unsortedDescendentsOf(concept.nid());
    }
    IntIdSet unsortedDescendentsOf(int conceptNid);

    default IntIdList unsortedParentsOf(ConceptFacade concept) {
        return unsortedParentsOf(concept.nid());
    }
    IntIdList unsortedParentsOf(int conceptNid);

    IntIdList toSortedList(IntIdList inputList);

    default IntIdList toSortedList(IntIdSet inputSet) {
        // TODO add pattern sort to implementation...
        return toSortedList(IntIds.list.of(inputSet.toArray()));
    }

    NavigationCoordinateRecord navigationCoordinate();


    default IntIdList unsortedParentsOf(ConceptFacade concept, PatternFacade patternFacade) {
        return unsortedParentsOf(concept.nid(), patternFacade.nid());
    }
    IntIdList unsortedParentsOf(int conceptNid, int patternNid);


}
