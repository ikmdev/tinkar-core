package org.hl7.tinkar.coordinate.navigation.calculator;

import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculatorDelegate;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorDelegate;

public interface NavigationCalculatorDelegate extends NavigationCalculator, LanguageCalculatorDelegate, StampCalculatorDelegate {

    NavigationCalculator navigationCalculator();

    @Override
    default boolean sortVertices() {
        return navigationCalculator().sortVertices();
    }

    @Override
    default IntIdList unsortedParents(int conceptNid) {
        return navigationCalculator().unsortedParents(conceptNid);
    }

    @Override
    default IntIdList unsortedChildren(int conceptNid) {
        return navigationCalculator().unsortedChildren(conceptNid);
    }

    @Override
    default IntIdList sortedParents(int conceptNid) {
        return navigationCalculator().sortedParents(conceptNid);
    }

    @Override
    default IntIdList sortedChildren(int conceptNid) {
        return navigationCalculator().sortedChildren(conceptNid);
    }

}
