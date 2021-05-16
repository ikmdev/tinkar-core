package org.hl7.tinkar.coordinate.view.calculator;

import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculatorDelegate;
import org.hl7.tinkar.coordinate.navigation.calculator.NavigationCalculatorDelegate;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorDelegate;

public interface ViewCalculatorDelegate extends ViewCalculator, StampCalculatorDelegate, LanguageCalculatorDelegate, NavigationCalculatorDelegate {
    ViewCalculator viewCalculator();
}
