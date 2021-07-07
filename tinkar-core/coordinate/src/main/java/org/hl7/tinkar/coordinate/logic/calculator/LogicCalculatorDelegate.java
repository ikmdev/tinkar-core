package org.hl7.tinkar.coordinate.logic.calculator;

import org.hl7.tinkar.coordinate.logic.LogicCoordinateRecord;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculator;

public interface LogicCalculatorDelegate extends LogicCalculator {
    LogicCalculator logicCalculator();

    @Override
    default boolean hasSufficientSet(int nid) {
        return logicCalculator().hasSufficientSet(nid);
    }

    @Override
    default LogicCoordinateRecord logicCoordinateRecord() {
        return logicCalculator().logicCoordinateRecord();
    }
}
