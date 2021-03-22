package org.hl7.tinkar.coordinate.stamp;

import java.util.Objects;

import com.google.auto.service.AutoService;
import org.hl7.tinkar.collection.ConcurrentReferenceHashMap;
import org.hl7.tinkar.common.binary.Decoder;
import org.hl7.tinkar.common.binary.DecoderInput;
import org.hl7.tinkar.common.binary.Encoder;
import org.hl7.tinkar.common.binary.EncoderOutput;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.entity.DefaultDescriptionText;
import org.hl7.tinkar.coordinate.ImmutableCoordinate;

//This class is not treated as a service, however, it needs the annotation, so that the reset() gets fired at appropriate times.
@AutoService(CachingService.class)
public class StampBranchImmutable implements StampBranch, ImmutableCoordinate, CachingService {

    private static final ConcurrentReferenceHashMap<Integer, StampBranchImmutable> SINGLETONS =
            new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.STRONG,
                    ConcurrentReferenceHashMap.ReferenceType.WEAK);

    private static final int marshalVersion = 1;

    private final int branchConceptNid;

    private final long branchOriginTime;

    private StampBranchImmutable() {
        // No arg constructor for HK2 managed instance
        // This instance just enables reset functionality...
        this.branchConceptNid = Integer.MAX_VALUE;
        this.branchOriginTime = Long.MIN_VALUE;
    }
    
    @Override
    public void reset() {
        SINGLETONS.clear();
    }

    private StampBranchImmutable(int branchConceptNid, long branchOriginTime) {
        this.branchConceptNid = branchConceptNid;
        this.branchOriginTime = branchOriginTime;
    }

    private StampBranchImmutable(DecoderInput in) {
        this.branchConceptNid = in.readNid();
        this.branchOriginTime = in.readLong();
    }

    public static StampBranchImmutable make(int pathConceptNid, long branchOriginTime) {
        return SINGLETONS.computeIfAbsent(pathConceptNid,
                pathNid -> new StampBranchImmutable(pathConceptNid, branchOriginTime));
    }

    @Override
    public long getBranchOriginTime() {
        return branchOriginTime;
    }

    @Decoder
    public static StampBranchImmutable decode(DecoderInput in) {
        int objectMarshalVersion = in.encodingFormatVersion();
        switch (objectMarshalVersion) {
            case marshalVersion:
                StampBranchImmutable stampBranch = new StampBranchImmutable(in);
                return SINGLETONS.computeIfAbsent(stampBranch.getPathOfBranchNid(),
                        branchConceptNid -> stampBranch);
            default:
                throw new UnsupportedOperationException("Unsupported version: " + objectMarshalVersion);
        }
    }

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        out.writeNid(this.branchConceptNid);
        out.writeLong(this.branchOriginTime);
    }

    @Override
    public StampBranchImmutable toStampBranchImmutable() {
        return this;
    }

    public int getPathOfBranchNid() {
        return this.branchConceptNid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StampBranch that)) return false;
        return getPathOfBranchNid() == that.getPathOfBranchNid() &&
                getBranchOriginTime() == that.getBranchOriginTime();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPathOfBranchNid(), getBranchOriginTime());
    }

    /**
     * To string.
     *
     * @return the string
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append("StampBranchImmutable:{ At date/time ");

        if (this.branchOriginTime == Long.MAX_VALUE) {
            sb.append("latest");
        } else if (this.branchOriginTime == Long.MIN_VALUE) {
            sb.append("CANCELED");
        } else {
            sb.append(getTimeAsInstant());
        }

        sb.append(" start branch for '")
                .append(DefaultDescriptionText.get(this.branchConceptNid))
                .append("'}");
        return sb.toString();
    }

    public String toUserString() {
        final StringBuilder sb = new StringBuilder("At date/time ");

        if (this.branchOriginTime == Long.MAX_VALUE) {
            sb.append("latest");
        } else if (this.branchOriginTime == Long.MIN_VALUE) {
            sb.append("CANCELED");
        } else {
            sb.append(getTimeAsInstant());
        }

        sb.append(" start branch for '")
                .append(DefaultDescriptionText.get(this.branchConceptNid))
                .append("'}");
        return sb.toString();
    }
}
