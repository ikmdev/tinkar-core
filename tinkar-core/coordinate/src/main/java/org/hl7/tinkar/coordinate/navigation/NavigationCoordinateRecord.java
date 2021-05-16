package org.hl7.tinkar.coordinate.navigation;


import java.util.Objects;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.hl7.tinkar.common.binary.Decoder;
import org.hl7.tinkar.common.binary.DecoderInput;
import org.hl7.tinkar.common.binary.Encoder;
import org.hl7.tinkar.common.binary.EncoderOutput;
import org.hl7.tinkar.common.id.*;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.coordinate.Coordinates;
import org.hl7.tinkar.coordinate.ImmutableCoordinate;
import org.hl7.tinkar.coordinate.logic.LogicCoordinate;
import org.hl7.tinkar.coordinate.logic.PremiseType;
import org.hl7.tinkar.coordinate.stamp.StateSet;
import org.hl7.tinkar.terms.TinkarTerm;

@RecordBuilder
public record NavigationCoordinateRecord(IntIdSet navigationConceptNids,
                                         StateSet vertexStates,
                                         boolean sortVertices,
                                         IntIdList verticesSortPatternNidList)
        implements NavigationCoordinate, ImmutableCoordinate, NavigationCoordinateRecordBuilder.With {

    private static final int marshalVersion = 5;

    @Override
    public IntIdList verticesSortPatternNidList() {
        return this.verticesSortPatternNidList;
    }

    @Override
    public boolean sortVertices() {
        return this.sortVertices;
    }

    public static NavigationCoordinateRecord make(IntIdSet digraphConceptNids) {
        return new NavigationCoordinateRecord(digraphConceptNids, StateSet.ACTIVE_ONLY, true, IntIds.list.empty());
    }

    public static NavigationCoordinateRecord make(IntIdSet digraphConceptNids, boolean sortVertices) {
        return new NavigationCoordinateRecord(digraphConceptNids, StateSet.ACTIVE_ONLY, sortVertices, IntIds.list.empty());
    }

    public static NavigationCoordinateRecord makeInferred() {
        return new NavigationCoordinateRecord(
                IntIds.set.of(TinkarTerm.INFERRED_NAVIGATION_PATTERN.nid()),
                StateSet.ACTIVE_ONLY, true, IntIds.list.empty());
    }

    public static NavigationCoordinateRecord makeStated() {
        return new NavigationCoordinateRecord(
                IntIds.set.of(TinkarTerm.STATED_NAVIGATION_PATTERN.nid()),
                StateSet.ACTIVE_ONLY, true, IntIds.list.empty());
    }

    public static NavigationCoordinateRecord makeInferred(LogicCoordinate logicCoordinate) {
        return new NavigationCoordinateRecord(
                IntIds.set.of(logicCoordinate.inferredAxiomsPatternNid()),
                StateSet.ACTIVE_ONLY, true, IntIds.list.empty());
    }

    public static NavigationCoordinateRecord makeStated(LogicCoordinate logicCoordinate) {
        return new NavigationCoordinateRecord(
                IntIds.set.of(logicCoordinate.statedAxiomsPatternNid()),
                StateSet.ACTIVE_ONLY, true, IntIds.list.empty());
    }

    public static NavigationCoordinateRecord make(PremiseType premiseType) {
        if (premiseType == PremiseType.INFERRED) {
            return makeInferred(Coordinates.Logic.ElPlusPlus());
        }
         return makeStated(Coordinates.Logic.ElPlusPlus());
     }

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        out.writeNidArray(this.navigationConceptNids.toArray());
        vertexStates.encode(out);
        out.writeBoolean(this.sortVertices);
        out.writeNidArray(this.verticesSortPatternNidList.toArray());
    }

    @Decoder
    public static NavigationCoordinateRecord decode(DecoderInput in) {
        int objectMarshalVersion = in.encodingFormatVersion();
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
    public IntIdSet getNavigationConceptNids() {
        return this.navigationConceptNids;
    }

    @Override
    public NavigationCoordinateRecord toNavigationCoordinateImmutable() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NavigationCoordinateRecord that)) return false;
        return getNavigationConceptNids().equals(that.getNavigationConceptNids());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getNavigationConceptNids());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (int nid: navigationConceptNids.toArray()) {
            sb.append(PrimitiveData.text(nid));
            sb.append(", ");
        }
        sb.delete(sb.length()-2, sb.length()-1);
        sb.append("]");
        return "NavigationCoordinateImmutable{" +
                "navigationConcepts=" + sb.toString() +
                '}';
    }
}
