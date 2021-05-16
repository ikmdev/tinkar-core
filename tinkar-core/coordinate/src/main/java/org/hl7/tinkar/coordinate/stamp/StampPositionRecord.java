package org.hl7.tinkar.coordinate.stamp;


import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.set.ImmutableSet;
import org.hl7.tinkar.common.binary.Decoder;
import org.hl7.tinkar.common.binary.DecoderInput;
import org.hl7.tinkar.common.binary.Encoder;
import org.hl7.tinkar.common.binary.EncoderOutput;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.util.time.DateTimeUtil;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.coordinate.ImmutableCoordinate;
import org.hl7.tinkar.coordinate.stamp.calculator.PathProvider;
import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.entity.Entity;

import java.time.Instant;

@RecordBuilder
public record StampPositionRecord(long time, int pathForPositionNid)
        implements StampPosition, Comparable<StampPosition>, ImmutableCoordinate, StampPositionRecordBuilder.With {

    public static final int marshalVersion = 1;

    public static StampPositionRecord make(Instant time, ConceptFacade pathForPosition) {
        return new StampPositionRecord(DateTimeUtil.instantToEpochMs(time), pathForPosition.nid());
    }

    public static StampPositionRecord make(Instant time, int pathForPositionNid) {
        return new StampPositionRecord(DateTimeUtil.instantToEpochMs(time), pathForPositionNid);
    }

    public static StampPositionRecord make(long time, int pathForPositionNid) {
        return new StampPositionRecord(time, pathForPositionNid);
    }

    public static StampPositionRecord make(long time, ConceptFacade pathForPosition) {
        return new StampPositionRecord(time, pathForPosition.nid());
    }

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        out.writeInt(marshalVersion);
        out.writeLong(this.time);
        out.writeInt(this.pathForPositionNid);
    }

    @Decoder
    public static StampPositionRecord decode(DecoderInput in) {
        int objectMarshalVersion = in.readInt();
        switch (objectMarshalVersion) {
            case marshalVersion:
                return new StampPositionRecord(in.readLong(), in.readInt());
            default:
                throw new UnsupportedOperationException("Unsupported version: " + objectMarshalVersion);
        }
    }

    /**
     * Gets the time.
     *
     * @return the time
     */
    @Override
    public long time() {
        return this.time;
    }

    @Override
    public StampPositionRecord toStampPositionImmutable() {
        return this;
    }

    /**
     * Compare to.
     *
     * @param o the o
     * @return the int
     */
    @Override
    public int compareTo(StampPosition o) {
        final int comparison = Long.compare(this.time, o.time());

        if (comparison != 0) {
            return comparison;
        }

        return Integer.compare(this.pathForPositionNid, o.getPathForPositionNid());
    }


    @Override
    public int getPathForPositionNid() {
        return this.pathForPositionNid;
    }

    /**
     * Gets the stamp path concept nid.
     *
     * @return the stamp path concept nid
     */
    public Concept getPathForPositionConcept() {
        return Entity.getFast(this.pathForPositionNid);
    }

    @Override
    public StampPositionRecord withTime(long time) {
        return StampPositionRecordBuilder.With.super.withTime(time);
    }

    @Override
    public StampPositionRecord withPathForPositionNid(int pathForPositionNid) {
        return StampPositionRecordBuilder.With.super.withPathForPositionNid(pathForPositionNid);
    }

    /**
     * Equals.
     *
     * @param obj the obj
     * @return true, if successful
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (!(obj instanceof final StampPosition other)) {
            return false;
        }

        if (this.time != other.time()) {
            return false;
        }

        return this.pathForPositionNid == other.getPathForPositionNid();
    }

    /**
     * Hash code.
     *
     * @return the int
     */
    @Override
    public int hashCode() {
        int hash = 7;

        hash = 83 * hash + (int) (this.time ^ (this.time >>> 32));
        hash = 83 * hash + Integer.hashCode(this.pathForPositionNid);
        return hash;
    }

    /**
     * To string.
     *
     * @return the string
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append("StampPosition:{");

        if (this.time == Long.MAX_VALUE) {
            sb.append("latest");
        } else if (this.time == Long.MIN_VALUE) {
            sb.append("CANCELED");
        } else {
            sb.append(instant());
        }

        sb.append(" on '")
                .append(PrimitiveData.text(this.pathForPositionNid))
                .append("'}");
        return sb.toString();
    }

    public ImmutableSet<StampPositionRecord> getPathOrigins() {
        return PathProvider.getPathOrigins(this.pathForPositionNid);
    }
}
