package dev.ikm.tinkar.coordinate.view;

import dev.ikm.tinkar.coordinate.Coordinates;
import dev.ikm.tinkar.coordinate.ImmutableCoordinate;
import dev.ikm.tinkar.coordinate.edit.EditCoordinate;
import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import dev.ikm.tinkar.coordinate.edit.EditCoordinateRecord;
import dev.ikm.tinkar.coordinate.language.LanguageCoordinate;
import dev.ikm.tinkar.coordinate.language.LanguageCoordinateRecord;
import dev.ikm.tinkar.coordinate.logic.LogicCoordinate;
import dev.ikm.tinkar.coordinate.logic.LogicCoordinateRecord;
import dev.ikm.tinkar.coordinate.navigation.NavigationCoordinate;
import dev.ikm.tinkar.coordinate.navigation.NavigationCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord;

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
        switch (Encodable.checkVersion(in)) {
            default:
                StampCoordinateRecord stampCoordinateRecord = StampCoordinateRecord.decode(in);
                int languageCoordinateCount = in.readInt();
                MutableList<LanguageCoordinateRecord> languageCoordinateRecords = Lists.mutable.ofInitialCapacity(languageCoordinateCount);
                for (int i = 0; i < languageCoordinateCount; i++) {
                    languageCoordinateRecords.add(LanguageCoordinateRecord.decode(in));
                }
                LogicCoordinateRecord logicCoordinateRecord = LogicCoordinateRecord.decode(in);
                NavigationCoordinateRecord navigationCoordinateRecord = NavigationCoordinateRecord.decode(in);
                if (in.encodingFormatVersion() > FIRST_VERSION) {
                    EditCoordinateRecord editCoordinateRecord = EditCoordinateRecord.decode(in);
                    return new ViewCoordinateRecord(stampCoordinateRecord,
                            languageCoordinateRecords.toImmutable(),
                            logicCoordinateRecord,
                            navigationCoordinateRecord,
                            editCoordinateRecord);
                }
                return new ViewCoordinateRecord(stampCoordinateRecord,
                        languageCoordinateRecords.toImmutable(),
                        logicCoordinateRecord,
                        navigationCoordinateRecord,
                        Coordinates.Edit.Default());
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
