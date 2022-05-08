package org.hl7.tinkar.coordinate.view;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.common.binary.Decoder;
import org.hl7.tinkar.common.binary.DecoderInput;
import org.hl7.tinkar.common.binary.Encoder;
import org.hl7.tinkar.common.binary.EncoderOutput;
import org.hl7.tinkar.coordinate.ImmutableCoordinate;
import org.hl7.tinkar.coordinate.edit.EditCoordinate;
import org.hl7.tinkar.coordinate.edit.EditCoordinateRecord;
import org.hl7.tinkar.coordinate.language.LanguageCoordinate;
import org.hl7.tinkar.coordinate.language.LanguageCoordinateRecord;
import org.hl7.tinkar.coordinate.logic.LogicCoordinate;
import org.hl7.tinkar.coordinate.logic.LogicCoordinateRecord;
import org.hl7.tinkar.coordinate.navigation.NavigationCoordinate;
import org.hl7.tinkar.coordinate.navigation.NavigationCoordinateRecord;
import org.hl7.tinkar.coordinate.stamp.StampCoordinateRecord;

import java.util.List;

@RecordBuilder
public record ViewCoordinateRecord(StampCoordinateRecord stampCoordinate,
                                   ImmutableList<LanguageCoordinateRecord> languageCoordinateList,
                                   LogicCoordinateRecord logicCoordinate,
                                   NavigationCoordinateRecord navigationCoordinate,
                                   EditCoordinateRecord editCoordinate)
        implements ViewCoordinate, ImmutableCoordinate, ViewCoordinateRecordBuilder.With {

    public static ViewCoordinateRecord make(StampCoordinateRecord viewStampFilter,
                                            LanguageCoordinate languageCoordinate,
                                            LogicCoordinate logicCoordinate,
                                            NavigationCoordinate navigationCoordinate,
                                            EditCoordinate editCoordinate) {

        return new ViewCoordinateRecord(viewStampFilter,
                Lists.immutable.of(languageCoordinate.toLanguageCoordinateRecord()),
                logicCoordinate.toLogicCoordinateRecord(),
                navigationCoordinate.toNavigationCoordinateRecord(),
                editCoordinate.toEditCoordinateRecord());
    }

    public static ViewCoordinateRecord make(StampCoordinateRecord viewStampFilter,
                                            List<? extends LanguageCoordinate> languageCoordinates,
                                            LogicCoordinate logicCoordinate,
                                            NavigationCoordinate navigationCoordinate,
                                            EditCoordinate editCoordinate) {
        MutableList<LanguageCoordinateRecord> languageCoordinateRecords = Lists.mutable.empty();
        languageCoordinates.forEach(languageCoordinate -> languageCoordinateRecords.add(languageCoordinate.toLanguageCoordinateRecord()));
        return new ViewCoordinateRecord(viewStampFilter,
                languageCoordinateRecords.toImmutable(),
                logicCoordinate.toLogicCoordinateRecord(),
                navigationCoordinate.toNavigationCoordinateRecord(),
                editCoordinate.toEditCoordinateRecord());
    }

    @Decoder
    public static ViewCoordinateRecord decode(DecoderInput in) {
        switch (in.encodingFormatVersion()) {
            case MARSHAL_VERSION:
                StampCoordinateRecord stampCoordinateRecord = StampCoordinateRecord.decode(in);
                int languageCoordinateCount = in.readInt();
                MutableList<LanguageCoordinateRecord> languageCoordinateRecords = Lists.mutable.ofInitialCapacity(languageCoordinateCount);
                for (int i = 0; i < languageCoordinateCount; i++) {
                    languageCoordinateRecords.add(LanguageCoordinateRecord.decode(in));
                }
                LogicCoordinateRecord logicCoordinateRecord = LogicCoordinateRecord.decode(in);
                NavigationCoordinateRecord navigationCoordinateRecord = NavigationCoordinateRecord.decode(in);
                EditCoordinateRecord editCoordinateRecord = EditCoordinateRecord.decode(in);
                return new ViewCoordinateRecord(stampCoordinateRecord,
                        languageCoordinateRecords.toImmutable(),
                        logicCoordinateRecord,
                        navigationCoordinateRecord,
                        editCoordinateRecord);
            default:
                throw new UnsupportedOperationException("Unsupported version: " + in.encodingFormatVersion());
        }
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
    @Encoder
    public void encode(EncoderOutput out) {
        stampCoordinate.encode(out);
        out.writeInt(languageCoordinateList.size());
        for (LanguageCoordinateRecord languageCoordinateRecord : languageCoordinateList) {
            languageCoordinateRecord.encode(out);
        }
        logicCoordinate.encode(out);
        navigationCoordinate.encode(out);
        editCoordinate.encode(out);
    }
}
