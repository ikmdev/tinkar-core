package org.hl7.tinkar.coordinate.navigation;


import io.soabase.recordbuilder.core.RecordBuilder;
import org.hl7.tinkar.common.binary.Decoder;
import org.hl7.tinkar.common.binary.DecoderInput;
import org.hl7.tinkar.common.binary.Encoder;
import org.hl7.tinkar.common.binary.EncoderOutput;
import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.id.IntIdSet;
import org.hl7.tinkar.common.id.IntIds;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.coordinate.Coordinates;
import org.hl7.tinkar.coordinate.ImmutableCoordinate;
import org.hl7.tinkar.coordinate.logic.LogicCoordinate;
import org.hl7.tinkar.coordinate.logic.PremiseType;
import org.hl7.tinkar.coordinate.stamp.StateSet;
import org.hl7.tinkar.terms.TinkarTerm;

import java.util.Objects;

@RecordBuilder
public record NavigationCoordinateRecord(IntIdSet navigationPatternNids,
                                         StateSet vertexStates,
                                         boolean sortVertices,
                                         IntIdList verticesSortPatternNidList)
        implements NavigationCoordinate, ImmutableCoordinate, NavigationCoordinateRecordBuilder.With {

    private static final int marshalVersion = 5;

    public static NavigationCoordinateRecord make(IntIdSet navigationPatternNids) {
        return new NavigationCoordinateRecord(navigationPatternNids, StateSet.ACTIVE, true, IntIds.list.empty());
    }

    public static NavigationCoordinateRecord make(IntIdSet navigationPatternNids,
                                                  StateSet vertexStates,
                                                  boolean sortVertices,
                                                  IntIdList verticesSortPatternNidList) {
        return new NavigationCoordinateRecord(navigationPatternNids, vertexStates,
                sortVertices, verticesSortPatternNidList);
    }

    public static NavigationCoordinateRecord makeInferred() {
        return new NavigationCoordinateRecord(
                IntIds.set.of(TinkarTerm.INFERRED_NAVIGATION_PATTERN.nid()),
                StateSet.ACTIVE_AND_INACTIVE, true, IntIds.list.empty());
    }

    public static NavigationCoordinateRecord makeStated() {
        return new NavigationCoordinateRecord(
                IntIds.set.of(TinkarTerm.STATED_NAVIGATION_PATTERN.nid()),
                StateSet.ACTIVE_AND_INACTIVE, true, IntIds.list.empty());
    }

    public static NavigationCoordinateRecord make(PremiseType premiseType) {
        if (premiseType == PremiseType.INFERRED) {
            return makeInferred(Coordinates.Logic.ElPlusPlus());
        }
        return makeStated(Coordinates.Logic.ElPlusPlus());
    }

    public static NavigationCoordinateRecord makeInferred(LogicCoordinate logicCoordinate) {
        return new NavigationCoordinateRecord(
                IntIds.set.of(logicCoordinate.inferredAxiomsPatternNid()),
                StateSet.ACTIVE_AND_INACTIVE, true, IntIds.list.empty());
    }

    public static NavigationCoordinateRecord makeStated(LogicCoordinate logicCoordinate) {
        return new NavigationCoordinateRecord(
                IntIds.set.of(logicCoordinate.statedAxiomsPatternNid()),
                StateSet.ACTIVE_AND_INACTIVE, true, IntIds.list.empty());
    }

    @Decoder
    public static NavigationCoordinateRecord decode(DecoderInput in) {
        int objectMarshalVersion = in.readInt();
        switch (objectMarshalVersion) {
            case marshalVersion:
                return new NavigationCoordinateRecord(IntIds.set.of(in.readNidArray()),
                        StateSet.decode(in),
                        in.readBoolean(), IntIds.list.of(in.readNidArray()));
            default:
                throw new UnsupportedOperationException("Unsupported version: " + objectMarshalVersion);
        }
    }

    @Override
    public IntIdList verticesSortPatternNidList() {
        return this.verticesSortPatternNidList;
    }

    @Override
    public NavigationCoordinateRecord toNavigationCoordinateRecord() {
        return this;
    }

    @Override
    public boolean sortVertices() {
        return this.sortVertices;
    }

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        out.writeInt(marshalVersion);
        out.writeNidArray(this.navigationPatternNids.toArray());
        vertexStates.encode(out);
        out.writeBoolean(this.sortVertices);
        out.writeNidArray(this.verticesSortPatternNidList.toArray());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NavigationCoordinateRecord that)) return false;
        return navigationPatternNids().equals(that.navigationPatternNids());
    }

    @Override
    public int hashCode() {
        return Objects.hash(navigationPatternNids());
    }

    @Override
    public String toString() {
        /*
        IntIdSet navigationPatternNids,
                                         StateSet vertexStates,
                                         boolean sortVertices,
                                         IntIdList verticesSortPatternNidList
         */
        StringBuilder sb = new StringBuilder("NavigationCoordinateRecord{");

        sb.append("navigationConcepts=[");
        for (int nid : navigationPatternNids.toArray()) {
            sb.append(PrimitiveData.text(nid));
            sb.append(", ");
        }
        sb.delete(sb.length() - 2, sb.length() - 1);
        sb.append("], ");
        sb.append("vertexStates=").append(vertexStates);
        sb.append(", sortVertices=").append(sortVertices);
        sb.append(", verticesSortPatternList=[");
        for (int nid : verticesSortPatternNidList.toArray()) {
            sb.append(PrimitiveData.text(nid));
            sb.append(", ");
        }
        if (verticesSortPatternNidList.notEmpty()) {
            sb.delete(sb.length() - 2, sb.length() - 1);
        }
        sb.append("]}");

        return sb.toString();
    }
}
