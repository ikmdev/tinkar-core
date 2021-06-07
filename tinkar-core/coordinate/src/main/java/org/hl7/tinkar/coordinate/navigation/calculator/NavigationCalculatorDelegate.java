package org.hl7.tinkar.coordinate.navigation.calculator;

import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.id.IntIdSet;
import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculatorDelegate;
import org.hl7.tinkar.coordinate.stamp.StateSet;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculator;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorDelegate;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;

public interface NavigationCalculatorDelegate extends NavigationCalculator, LanguageCalculatorDelegate, StampCalculatorDelegate {

    NavigationCalculator navigationCalculator();

    @Override
    default IntIdList toSortedList(IntIdList inputList) {
        return navigationCalculator().toSortedList(inputList);
    }

    @Override
    default StampCalculatorWithCache vertexStampCalculator() {
        return navigationCalculator().vertexStampCalculator();
    }

    @Override
    default StateSet allowedVertexStates() {
        return navigationCalculator().allowedVertexStates();
    }

    @Override
    default IntIdSet kindOfSet(int conceptNid) {
        return navigationCalculator().kindOfSet(conceptNid);
    }

    @Override
    default IntIdList sortedDescendentsOf(int conceptNid) {
        return navigationCalculator().sortedDescendentsOf(conceptNid);
    }

    @Override
    default IntIdSet unsortedDescendentsOf(int conceptNid) {
        return navigationCalculator().unsortedDescendentsOf(conceptNid);
    }

    @Override
    default IntIdList sortedAncestorsOf(int conceptNid) {
        return navigationCalculator().sortedAncestorsOf(conceptNid);
    }

    @Override
    default IntIdSet unsortedAncestorsOf(int conceptNid) {
        return navigationCalculator().unsortedAncestorsOf(conceptNid);
    }

    @Override
    default StampCalculator stampCalculator() {
        return navigationCalculator().stampCalculator();
    }

    @Override
    default boolean sortVertices() {
        return navigationCalculator().sortVertices();
    }

    @Override
    default IntIdList unsortedParentsOf(int conceptNid) {
        return navigationCalculator().unsortedParentsOf(conceptNid);
    }

    @Override
    default IntIdList unsortedChildrenOf(int conceptNid) {
        return navigationCalculator().unsortedChildrenOf(conceptNid);
    }

    @Override
    default IntIdList sortedParentsOf(int conceptNid) {
        return navigationCalculator().sortedParentsOf(conceptNid);
    }

    @Override
    default IntIdList sortedChildrenOf(int conceptNid) {
        return navigationCalculator().sortedChildrenOf(conceptNid);
    }

}
