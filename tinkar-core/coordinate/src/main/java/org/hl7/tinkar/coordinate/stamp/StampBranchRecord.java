package org.hl7.tinkar.coordinate.stamp;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.hl7.tinkar.common.binary.Decoder;
import org.hl7.tinkar.common.binary.DecoderInput;
import org.hl7.tinkar.common.binary.Encoder;
import org.hl7.tinkar.common.binary.EncoderOutput;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.util.time.DateTimeUtil;
import org.hl7.tinkar.coordinate.ImmutableCoordinate;

import java.time.Instant;
import java.util.Objects;

@RecordBuilder
public record StampBranchRecord(int branchConceptNid, long branchOriginTime)
        implements StampBranch, ImmutableCoordinate, StampBranchRecordBuilder.With {

    @Decoder
    public static StampBranchRecord decode(DecoderInput in) {
        switch (in.encodingFormatVersion()) {
            case MARSHAL_VERSION:
                return make(in.readNid(), in.readLong());
            default:
                throw new UnsupportedOperationException("Unsupported version: " + in.encodingFormatVersion());
        }
    }

    public static StampBranchRecord make(int pathConceptNid, long branchOriginTime) {
        return new StampBranchRecord(pathConceptNid, branchOriginTime);
    }

    public static StampBranchRecord make(int pathConceptNid, Instant branchOriginInstant) {
        return new StampBranchRecord(pathConceptNid, DateTimeUtil.instantToEpochMs(branchOriginInstant));
    }

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        out.writeNid(this.branchConceptNid);
        out.writeLong(this.branchOriginTime);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StampBranch that)) return false;
        return getPathOfBranchNid() == that.getPathOfBranchNid() &&
                getBranchOriginTime() == that.getBranchOriginTime();
    }

    @Override
    public long getBranchOriginTime() {
        return branchOriginTime;
    }

    public int getPathOfBranchNid() {
        return this.branchConceptNid;
    }

    @Override
    public StampBranchRecord toStampBranchRecord() {
        return this;
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
                .append(PrimitiveData.text(this.branchConceptNid))
                .append("'}");
        return sb.toString();
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
                .append(PrimitiveData.text(this.branchConceptNid))
                .append("'}");
        return sb.toString();
    }
}
