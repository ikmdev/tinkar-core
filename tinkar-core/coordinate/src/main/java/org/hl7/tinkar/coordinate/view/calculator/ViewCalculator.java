package org.hl7.tinkar.coordinate.view.calculator;

import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculatorDelegate;
import org.hl7.tinkar.coordinate.logic.calculator.LogicCalculatorDelegate;
import org.hl7.tinkar.coordinate.navigation.calculator.NavigationCalculatorDelegate;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorDelegate;
import org.hl7.tinkar.coordinate.view.ViewCoordinateRecord;

public interface ViewCalculator extends StampCalculatorDelegate, LanguageCalculatorDelegate, NavigationCalculatorDelegate, LogicCalculatorDelegate {

    ViewCoordinateRecord viewCoordinateRecord();

}
