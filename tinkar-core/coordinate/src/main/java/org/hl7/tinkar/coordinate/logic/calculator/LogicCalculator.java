package org.hl7.tinkar.coordinate.logic.calculator;

import org.hl7.tinkar.coordinate.logic.LogicCoordinateRecord;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculator;
import org.hl7.tinkar.terms.EntityFacade;

public interface LogicCalculator {

    LogicCoordinateRecord logicCoordinateRecord();

    default boolean hasSufficientSet(EntityFacade entityFacade) {
        return hasSufficientSet(entityFacade.nid());
    }

    boolean hasSufficientSet(int nid);
}
