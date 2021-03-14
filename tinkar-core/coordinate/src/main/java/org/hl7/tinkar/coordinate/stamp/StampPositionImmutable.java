package org.hl7.tinkar.coordinate.stamp;


import org.eclipse.collections.api.set.ImmutableSet;
import org.hl7.tinkar.collection.ConcurrentReferenceHashMap;
import org.hl7.tinkar.common.binary.Decoder;
import org.hl7.tinkar.common.binary.DecoderInput;
import org.hl7.tinkar.common.binary.Encoder;
import org.hl7.tinkar.common.binary.EncoderOutput;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.coordinate.language.DefaultDescriptionText;
import org.hl7.tinkar.coordinate.ImmutableCoordinate;
import org.hl7.tinkar.entity.Entity;

//This class is not treated as a service, however, it needs the annotation, so that the reset() gets fired at appropriate times.
// No arg constructor for Service Provider Interface (SPI) generated instance is required
//@TODO Service annotation
public final class StampPositionImmutable
        implements StampPosition, Comparable<StampPosition>, ImmutableCoordinate, CachingService {

    private static final ConcurrentReferenceHashMap<StampPositionImmutable, StampPositionImmutable> SINGLETONS =
            new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.WEAK,
                                             ConcurrentReferenceHashMap.ReferenceType.WEAK);



    public static final int marshalVersion = 1;

    /** The time. */
    private final long time;

    private final int pathForPositionNid;

    private transient StampPathImmutable stampPath;

    private StampPositionImmutable() {
        // No arg constructor for Service Provider Interface (SPI) generated instance
        // This instance just enables reset functionality...
        this.time    = Long.MIN_VALUE;
        this.pathForPositionNid = Integer.MAX_VALUE;
    }
    
    @Override
    public void reset() {
        SINGLETONS.clear();
    }

    /**
     * Instantiates a new stamp position impl.
     *
     * @param time the time
     * @param pathForPositionNid the path nid
     */
    private StampPositionImmutable(long time, int pathForPositionNid) {
        this.time    = time;
        this.pathForPositionNid = pathForPositionNid;
    }

    private StampPositionImmutable(long time, Concept pathForPosition) {
        this.time    = time;
        this.pathForPositionNid = Entity.provider().nidForComponent(pathForPosition);
    }

    public static StampPositionImmutable make(long time, int pathForPositionNid) {
        return SINGLETONS.computeIfAbsent(new StampPositionImmutable(time, pathForPositionNid), stampPositionImmutable -> stampPositionImmutable);
    }

    public static StampPositionImmutable make(long time, Concept pathForPosition) {
        return SINGLETONS.computeIfAbsent(new StampPositionImmutable(time, pathForPosition), stampPositionImmutable -> stampPositionImmutable);
    }

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        out.writeInt(marshalVersion);
        out.writeLong(this.time);
        out.writeInt(this.pathForPositionNid);
    }

    @Decoder
    public static StampPositionImmutable decode(DecoderInput in) {
        int objectMarshalVersion = in.readInt();
        switch (objectMarshalVersion) {
            case marshalVersion:
                return new StampPositionImmutable(in.readLong(), in.readInt());
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
    public StampPositionImmutable toStampPositionImmutable() {
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
                .append(DefaultDescriptionText.get(this.pathForPositionNid))
                .append("' path}");
        return sb.toString();
    }

    public ImmutableSet<StampPositionImmutable> getPathOrigins() {
        if (this.stampPath == null) {
            this.stampPath = StampPathImmutable.make(getPathForPositionNid());
        }
        return this.stampPath.getPathOrigins();
    }
}
