package dev.ikm.tinkar.coordinate.view.calculator;

import dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculatorDelegate;
import dev.ikm.tinkar.coordinate.navigation.calculator.NavigationCalculatorDelegate;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorDelegate;

public interface ViewCalculatorDelegate extends ViewCalculator, StampCalculatorDelegate, LanguageCalculatorDelegate, NavigationCalculatorDelegate {
    ViewCalculator viewCalculator();
}
