package org.hl7.tinkar.coordinate.view;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.common.binary.EncoderOutput;
import org.hl7.tinkar.coordinate.ImmutableCoordinate;
import org.hl7.tinkar.coordinate.language.LanguageCoordinate;
import org.hl7.tinkar.coordinate.language.LanguageCoordinateRecord;
import org.hl7.tinkar.coordinate.logic.LogicCoordinate;
import org.hl7.tinkar.coordinate.logic.LogicCoordinateRecord;
import org.hl7.tinkar.coordinate.navigation.NavigationCoordinate;
import org.hl7.tinkar.coordinate.navigation.NavigationCoordinateRecord;
import org.hl7.tinkar.coordinate.stamp.StampCoordinateRecord;
import org.hl7.tinkar.coordinate.stamp.StateSet;

import java.util.List;

@RecordBuilder
public record ViewCoordinateRecord(StampCoordinateRecord stampCoordinate,
                                   ImmutableList<LanguageCoordinateRecord> languageCoordinateList,
                                   LogicCoordinateRecord logicCoordinate,
                                   NavigationCoordinateRecord navigationCoordinate)
        implements ViewCoordinate, ImmutableCoordinate, ViewCoordinateRecordBuilder.With {

    public static ViewCoordinateRecord make(StampCoordinateRecord viewStampFilter,
                                            LanguageCoordinate languageCoordinate,
                                            LogicCoordinate logicCoordinate,
                                            NavigationCoordinate navigationCoordinate) {

        return new ViewCoordinateRecord(viewStampFilter,
                Lists.immutable.of(languageCoordinate.toLanguageCoordinateRecord()),
                logicCoordinate.toLogicCoordinateRecord(),
                navigationCoordinate.toNavigationCoordinateRecord());
    }
    public static ViewCoordinateRecord make(StampCoordinateRecord viewStampFilter,
                                            List<? extends LanguageCoordinate> languageCoordinates,
                                            LogicCoordinate logicCoordinate,
                                            NavigationCoordinate navigationCoordinate) {

        MutableList<LanguageCoordinateRecord> languageCoordinateRecords = Lists.mutable.empty();
        languageCoordinates.forEach(languageCoordinate -> languageCoordinateRecords.add(languageCoordinate.toLanguageCoordinateRecord()));
        return new ViewCoordinateRecord(viewStampFilter,
                languageCoordinateRecords.toImmutable(),
                logicCoordinate.toLogicCoordinateRecord(),
                navigationCoordinate.toNavigationCoordinateRecord());
    }

    @Override
    public ViewCoordinateRecord toViewCoordinateRecord() {
        return this;
    }

    @Override
    public Iterable<LanguageCoordinateRecord> languageCoordinateIterable() {
        return languageCoordinateList();
    }

    @Override
    public void encode(EncoderOutput out) {
        throw new UnsupportedOperationException();
    }
}
