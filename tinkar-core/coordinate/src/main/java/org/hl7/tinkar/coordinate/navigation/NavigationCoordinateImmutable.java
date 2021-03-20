package org.hl7.tinkar.coordinate.navigation;


import java.util.Objects;

import com.google.auto.service.AutoService;
import org.eclipse.collections.api.set.primitive.ImmutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.hl7.tinkar.collection.ConcurrentReferenceHashMap;
import org.hl7.tinkar.common.binary.Decoder;
import org.hl7.tinkar.common.binary.DecoderInput;
import org.hl7.tinkar.common.binary.Encoder;
import org.hl7.tinkar.common.binary.EncoderOutput;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.coordinate.Coordinates;
import org.hl7.tinkar.coordinate.ImmutableCoordinate;
import org.hl7.tinkar.coordinate.logic.LogicCoordinate;
import org.hl7.tinkar.coordinate.logic.PremiseType;

//This class is not treated as a service, however, it needs the annotation, so that the reset() gets fired at appropriate times.
@AutoService(CachingService.class)
public final class NavigationCoordinateImmutable implements NavigationCoordinate, ImmutableCoordinate, CachingService {

    private static final ConcurrentReferenceHashMap<NavigationCoordinateImmutable, NavigationCoordinateImmutable> SINGLETONS =
            new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.WEAK,
                    ConcurrentReferenceHashMap.ReferenceType.WEAK);

    private static final int marshalVersion = 5;

    private final ImmutableIntSet navigationConceptNids;

    private NavigationCoordinateImmutable() {
        // No arg constructor for HK2 managed instance
        // This instance just enables reset functionality...
        this.navigationConceptNids = null;
     }

    @Override
    public void reset() {
        SINGLETONS.clear();
    }

    /**
     *
     * @param navigationConceptNids
     */
    private NavigationCoordinateImmutable(ImmutableIntSet navigationConceptNids) {
        this.navigationConceptNids = navigationConceptNids;
    }
    public static NavigationCoordinateImmutable make(ImmutableIntSet digraphConceptNids) {
        return SINGLETONS.computeIfAbsent(new NavigationCoordinateImmutable(digraphConceptNids),
                digraphCoordinateImmutable -> digraphCoordinateImmutable);
    }

    public static NavigationCoordinateImmutable makeInferred() {
        throw new UnsupportedOperationException();
//        return SINGLETONS.computeIfAbsent(new NavigationCoordinateImmutable(
//                        IntSets.immutable.of(TinkarTerm.EL_PLUS_PLUS_INFERRED_ASSEMBLAGE.nid())),
//                digraphCoordinateImmutable -> digraphCoordinateImmutable);
    }

    public static NavigationCoordinateImmutable makeStated() {
        throw new UnsupportedOperationException();
//        return SINGLETONS.computeIfAbsent(new NavigationCoordinateImmutable(
//                        IntSets.immutable.of(TinkarTerm.EL_PLUS_PLUS_STATED_ASSEMBLAGE.nid())),
//                digraphCoordinateImmutable -> digraphCoordinateImmutable);
    }

    public static NavigationCoordinateImmutable makeInferred(LogicCoordinate logicCoordinate) {
        return SINGLETONS.computeIfAbsent(new NavigationCoordinateImmutable(
                        IntSets.immutable.of(logicCoordinate.getInferredAssemblageNid())),
                digraphCoordinateImmutable -> digraphCoordinateImmutable);
    }

    public static NavigationCoordinateImmutable makeStated(LogicCoordinate logicCoordinate) {
        return SINGLETONS.computeIfAbsent(new NavigationCoordinateImmutable(
                        IntSets.immutable.of(logicCoordinate.getStatedAssemblageNid())),
                digraphCoordinateImmutable -> digraphCoordinateImmutable);
    }

    private NavigationCoordinateImmutable(DecoderInput in, int objectMarshalVersion) {
        PremiseType.valueOf(in.readString());
        this.navigationConceptNids = IntSets.immutable.of(in.readNidArray());
    }

    public static NavigationCoordinateImmutable make(PremiseType premiseType) {
        if (premiseType == PremiseType.INFERRED) {
            return makeInferred(Coordinates.Logic.ElPlusPlus());
        }
         return makeStated(Coordinates.Logic.ElPlusPlus());
     }

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        out.writeNidArray(this.navigationConceptNids.toArray());
    }

    @Decoder
    public static NavigationCoordinateImmutable decode(DecoderInput in) {
        int objectMarshalVersion = in.encodingFormatVersion();
        switch (objectMarshalVersion) {
            case marshalVersion:
                return SINGLETONS.computeIfAbsent(new NavigationCoordinateImmutable(in, objectMarshalVersion),
                        digraphCoordinateImmutable -> digraphCoordinateImmutable);
            default:
                throw new UnsupportedOperationException("Unsupported version: " + objectMarshalVersion);
        }
    }

    @Override
    public ImmutableIntSet getNavigationConceptNids() {
        return this.navigationConceptNids;
    }

    @Override
    public NavigationCoordinateImmutable toNavigationCoordinateImmutable() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NavigationCoordinateImmutable that)) return false;
        return getNavigationConceptNids().equals(that.getNavigationConceptNids());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getNavigationConceptNids());
    }

    @Override
    public String toString() {
        return "NavigationCoordinateImmutable{" +
                "navigationConcepts=" + navigationConceptNids +
                '}';
    }


}
