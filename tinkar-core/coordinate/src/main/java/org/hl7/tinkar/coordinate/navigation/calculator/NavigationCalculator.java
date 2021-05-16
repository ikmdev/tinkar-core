package org.hl7.tinkar.coordinate.navigation.calculator;

import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculatorDelegate;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorDelegate;
import org.hl7.tinkar.terms.ConceptFacade;

public interface NavigationCalculator extends StampCalculatorDelegate, LanguageCalculatorDelegate {

    boolean sortVertices();

    default IntIdList parents(ConceptFacade concept) {
        return parents(concept.nid());
    }
    default IntIdList parents(int conceptNid) {
        if (sortVertices()) {
            return sortedParents(conceptNid);
        }
        return unsortedParents(conceptNid);
    }
    default IntIdList children(ConceptFacade concept) {
        return children(concept.nid());
    }
    default IntIdList children(int conceptNid) {
        if (sortVertices()) {
            return sortedChildren(conceptNid);
        }
        return unsortedChildren(conceptNid);
    }

    default IntIdList unsortedParents(ConceptFacade concept) {
        return sortedParents(concept.nid());
    }
    IntIdList unsortedParents(int conceptNid);

    default IntIdList unsortedChildren(ConceptFacade concept) {
        return sortedChildren(concept.nid());
    }
    IntIdList unsortedChildren(int conceptNid);


    default IntIdList sortedParents(ConceptFacade concept) {
        return sortedParents(concept.nid());
    }
    IntIdList sortedParents(int conceptNid);

    default IntIdList sortedChildren(ConceptFacade concept) {
        return sortedChildren(concept.nid());
    }
    IntIdList sortedChildren(int conceptNid);
}
