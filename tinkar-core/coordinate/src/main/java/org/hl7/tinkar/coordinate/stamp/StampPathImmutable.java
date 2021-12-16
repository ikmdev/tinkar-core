package org.hl7.tinkar.coordinate.stamp;

import com.google.auto.service.AutoService;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.MutableSet;
import org.hl7.tinkar.collection.ConcurrentReferenceHashMap;
import org.hl7.tinkar.common.binary.Decoder;
import org.hl7.tinkar.common.binary.DecoderInput;
import org.hl7.tinkar.common.binary.Encoder;
import org.hl7.tinkar.common.binary.EncoderOutput;
import org.hl7.tinkar.common.id.IntIds;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.coordinate.ImmutableCoordinate;
import org.hl7.tinkar.coordinate.PathService;
import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.TinkarTerm;

import java.util.Objects;

public final class StampPathImmutable implements StampPath, ImmutableCoordinate {

    private static final ConcurrentReferenceHashMap<Integer, StampPathImmutable> SINGLETONS =
            new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.STRONG,
                    ConcurrentReferenceHashMap.ReferenceType.WEAK);
    private final int pathConceptNid;
    private final ImmutableSet<StampPositionRecord> pathOrigins;

    private StampPathImmutable(ConceptFacade pathConcept, ImmutableSet<StampPositionRecord> pathOrigins) {
        this.pathConceptNid = pathConcept.nid();
        this.pathOrigins = pathOrigins;
    }

    private StampPathImmutable(int pathConceptNid, ImmutableSet<StampPositionRecord> pathOrigins) {
        this.pathConceptNid = pathConceptNid;
        this.pathOrigins = pathOrigins;
    }


    private StampPathImmutable(DecoderInput in) {
        this.pathConceptNid = in.readNid();
        int pathOriginsSize = in.readVarInt();
        MutableSet<StampPositionRecord> mutableOrigins = Sets.mutable.ofInitialCapacity(pathOriginsSize);
        for (int i = 0; i < pathOriginsSize; i++) {
            mutableOrigins.add(StampPositionRecord.make(in.readLong(), in.readNid()));
        }
        this.pathOrigins = mutableOrigins.toImmutable();
    }

    public static StampPathImmutable make(ConceptFacade pathConcept, ImmutableSet<StampPositionRecord> pathOrigins) {
        return make(pathConcept, pathOrigins);
    }

    public static StampPathImmutable make(int pathConceptNid, ImmutableSet<StampPositionRecord> pathOrigins) {
        if (pathConceptNid == TinkarTerm.UNINITIALIZED_COMPONENT.nid()) {
            return new StampPathImmutable(pathConceptNid, pathOrigins);
        }
        return SINGLETONS.computeIfAbsent(pathConceptNid,
                pathNid -> new StampPathImmutable(pathConceptNid, pathOrigins));
    }

    public static StampPathImmutable make(ConceptFacade pathConcept) {
        return make(pathConcept.nid());
    }

    public static StampPathImmutable make(int pathConceptNid) {
        if (pathConceptNid == TinkarTerm.UNINITIALIZED_COMPONENT.nid()) {
            return new StampPathImmutable(pathConceptNid, Sets.immutable.empty());
        }
        return SINGLETONS.computeIfAbsent(pathConceptNid,
                pathNid -> {
                    ImmutableSet<StampPositionRecord> pathOrigins = PathService.get().getPathOrigins(pathNid);
                    return new StampPathImmutable(pathNid, pathOrigins);
                });
    }

    @Decoder
    public static StampPathImmutable make(DecoderInput in) {
        switch (in.encodingFormatVersion()) {
            case MARSHAL_VERSION:
                StampPathImmutable stampPath = new StampPathImmutable(in);
                if (stampPath.pathConceptNid == TinkarTerm.UNINITIALIZED_COMPONENT.nid()) {
                    return stampPath;
                }
                return SINGLETONS.computeIfAbsent(stampPath.pathConceptNid(),
                        pathNid -> stampPath);
            default:
                throw new UnsupportedOperationException("Unsupported version: " + in.encodingFormatVersion());
        }
    }

    @Override
    public int pathConceptNid() {
        return this.pathConceptNid;
    }

    @Override
    public ImmutableSet<StampPositionRecord> getPathOrigins() {
        return this.pathOrigins;
    }

    public StampPathImmutable toStampPathImmutable() {
        return this;
    }

    public static final StampCoordinateRecord getStampFilter(StampPath stampPath) {
        return StampCoordinateRecord.make(StateSet.ACTIVE_AND_INACTIVE,
                StampPositionRecord.make(Long.MAX_VALUE, stampPath.pathConceptNid()),
                IntIds.set.empty());
    }

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        out.writeInt(this.pathConceptNid);
        out.writeVarInt(this.pathOrigins.size());
        for (StampPositionRecord stampPosition : this.pathOrigins) {
            stampPosition.encode(out);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(pathConceptNid(), getPathOrigins());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StampPathImmutable that)) return false;
        return pathConceptNid() == that.pathConceptNid() &&
                getPathOrigins().equals(that.getPathOrigins());
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("StampPathImmutable:{");
        sb.append(PrimitiveData.text(this.pathConceptNid));
        sb.append(" Origins: ").append(this.pathOrigins).append("}");
        return sb.toString();
    }

    @AutoService(CachingService.class)
    public static class CachingProvider implements CachingService {

        @Override
        public void reset() {
            SINGLETONS.clear();
        }
    }

}
