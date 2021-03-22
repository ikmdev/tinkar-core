package org.hl7.tinkar.coordinate.stamp;

import java.util.Objects;

import com.google.auto.service.AutoService;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.hl7.tinkar.collection.ConcurrentReferenceHashMap;
import org.hl7.tinkar.common.binary.Decoder;
import org.hl7.tinkar.common.binary.DecoderInput;
import org.hl7.tinkar.common.binary.Encoder;
import org.hl7.tinkar.common.binary.EncoderOutput;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.entity.DefaultDescriptionText;
import org.hl7.tinkar.coordinate.ImmutableCoordinate;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.terms.TinkarTerm;

//This class is not treated as a service, however, it needs the annotation, so that the reset() gets fired at appropriate times.
@AutoService(CachingService.class)
public final class StampPathImmutable implements StampPath, ImmutableCoordinate, CachingService {

    private static final ConcurrentReferenceHashMap<Integer, StampPathImmutable> SINGLETONS =
            new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.STRONG,
                    ConcurrentReferenceHashMap.ReferenceType.WEAK);

    private static final int marshalVersion = 1;

    private final int pathConceptNid;

    private final ImmutableSet<StampPositionImmutable> pathOrigins;

    private StampPathImmutable() {
        // No arg constructor for HK2 managed instance
        // This instance just enables reset functionality...
        this.pathConceptNid = Integer.MAX_VALUE;
        this.pathOrigins = null;
    }
    
    @Override
    public void reset() {
        SINGLETONS.clear();
    }

    private StampPathImmutable(Concept pathConcept, ImmutableSet<StampPositionImmutable> pathOrigins) {
        this.pathConceptNid = Entity.provider().nidForComponent(pathConcept);
        this.pathOrigins = pathOrigins;
    }


    private StampPathImmutable(int pathConceptNid, ImmutableSet<StampPositionImmutable> pathOrigins) {
        this.pathConceptNid = pathConceptNid;
        this.pathOrigins = pathOrigins;
    }

    private StampPathImmutable(DecoderInput in) {
        this.pathConceptNid = in.readNid();
        int pathOriginsSize = in.readVarInt();
        MutableSet<StampPositionImmutable> mutableOrigins = Sets.mutable.ofInitialCapacity(pathOriginsSize);
        for (int i = 0; i < pathOriginsSize; i++) {
            mutableOrigins.add(StampPositionImmutable.make(in.readLong(), in.readNid()));
        }
        this.pathOrigins = mutableOrigins.toImmutable();
    }

    public static StampPathImmutable make(Concept pathConcept, ImmutableSet<StampPositionImmutable> pathOrigins) {
        return make(pathConcept, pathOrigins);
    }

    public static StampPathImmutable make(int pathConceptNid, ImmutableSet<StampPositionImmutable> pathOrigins) {
        if (pathConceptNid == TinkarTerm.UNINITIALIZED_COMPONENT.nid()) {
            return new StampPathImmutable(pathConceptNid, pathOrigins);
        }
        return SINGLETONS.computeIfAbsent(pathConceptNid,
                pathNid -> new StampPathImmutable(pathConceptNid, pathOrigins));
    }


    public static StampPathImmutable make(Concept pathConcept) {
        return make(Entity.provider().nidForComponent(pathConcept));
    }
    public static StampPathImmutable make(int pathConceptNid) {
        if (pathConceptNid == TinkarTerm.UNINITIALIZED_COMPONENT.nid()) {
            return new StampPathImmutable(pathConceptNid, Sets.immutable.empty());
        }
        return SINGLETONS.computeIfAbsent(pathConceptNid,
                pathNid -> {
                    ImmutableSet<StampPositionImmutable> pathOrigins = PathProvider.getPathOrigins(pathNid);
                    return new StampPathImmutable(pathNid, pathOrigins);
                });
    }

    @Decoder
    public static StampPathImmutable make(DecoderInput in) {
        int objectMarshalVersion = in.encodingFormatVersion();
        switch (objectMarshalVersion) {
            case marshalVersion:
                StampPathImmutable stampPath = new StampPathImmutable(in);
                if (stampPath.pathConceptNid == TinkarTerm.UNINITIALIZED_COMPONENT.nid()) {
                    return stampPath;
                }
                return SINGLETONS.computeIfAbsent(stampPath.pathConceptNid(),
                        pathNid -> stampPath);
            default:
                throw new UnsupportedOperationException("Unsupported version: " + objectMarshalVersion);
        }
    }

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        out.writeInt(this.pathConceptNid);
        out.writeVarInt(this.pathOrigins.size());
        for (StampPositionImmutable stampPosition: this.pathOrigins) {
            stampPosition.encode(out);
        }
    }

    public StampPathImmutable toStampPathImmutable() {
        return this;
    }

    @Override
    public int pathConceptNid() {
        return this.pathConceptNid;
    }

    @Override
    public ImmutableSet<StampPositionImmutable> getPathOrigins() {
        return this.pathOrigins;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StampPathImmutable that)) return false;
        return pathConceptNid() == that.pathConceptNid() &&
                getPathOrigins().equals(that.getPathOrigins());
    }

    @Override
    public int hashCode() {
        return Objects.hash(pathConceptNid(), getPathOrigins());
    }


    public static final StampFilterImmutable getStampFilter(StampPath stampPath) {
        return StampFilterImmutable.make(StateSet.ACTIVE_AND_INACTIVE,
                StampPositionImmutable.make(Long.MAX_VALUE, stampPath.pathConcept()),
                IntSets.immutable.empty());
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("StampPathImmutable:{");
        sb.append(DefaultDescriptionText.get(this.pathConceptNid));
        sb.append(" Origins: ").append(this.pathOrigins).append("}");
        return sb.toString();
    }

}
