package org.hl7.tinkar.coordinate.view;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.binary.EncoderOutput;
import org.hl7.tinkar.coordinate.ImmutableCoordinate;
import org.hl7.tinkar.coordinate.language.LanguageCoordinate;
import org.hl7.tinkar.coordinate.language.LanguageCoordinateRecord;
import org.hl7.tinkar.coordinate.logic.LogicCoordinate;
import org.hl7.tinkar.coordinate.logic.LogicCoordinateRecord;
import org.hl7.tinkar.coordinate.navigation.NavigationCoordinate;
import org.hl7.tinkar.coordinate.navigation.NavigationCoordinateRecord;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import org.hl7.tinkar.coordinate.stamp.StampFilterRecord;
import org.hl7.tinkar.coordinate.stamp.StateSet;

@RecordBuilder
public record ViewCoordinateRecord(StampFilterRecord stampFilter,
                                   ImmutableList<LanguageCoordinateRecord> languageCoordinateList,
                                   LogicCoordinateRecord logicCoordinate,
                                   NavigationCoordinateRecord navigationCoordinate,
                                   StampCalculatorWithCache vertexCalculator)
        implements ViewCoordinate, ImmutableCoordinate, ViewCoordinateRecordBuilder.With {

    public static ViewCoordinateRecord make(StampFilterRecord viewStampFilter,
                                            LanguageCoordinate languageCoordinate,
                                            LogicCoordinate logicCoordinate,
                                            NavigationCoordinate navigationCoordinate,
                                            StateSet vertexStates) {

        StampFilterRecord vertexFilter = viewStampFilter.withAllowedStates(vertexStates);
        StampCalculatorWithCache vertexCalculator = StampCalculatorWithCache.getCalculator(vertexFilter);

        return new ViewCoordinateRecord(viewStampFilter,
                Lists.immutable.of(languageCoordinate.toLanguageCoordinateRecord()),
                logicCoordinate.toLogicCoordinateRecord(),
                navigationCoordinate.toNavigationCoordinateImmutable(),
                vertexCalculator);
    }

    @Override
    public ImmutableList<LanguageCoordinateRecord> languageList() {
        return languageCoordinateList;
    }

    @Override
    public LogicCoordinateRecord logic() {
        return logicCoordinate;
    }

    @Override
    public NavigationCoordinateRecord navigation() {
        return navigationCoordinate;
    }

    @Override
    public void encode(EncoderOutput out) {
        throw new UnsupportedOperationException();
    }
}
