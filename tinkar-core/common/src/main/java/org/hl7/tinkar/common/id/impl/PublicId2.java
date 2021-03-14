package org.hl7.tinkar.common.id.impl;

import org.hl7.tinkar.common.id.PublicId;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.LongConsumer;

public class PublicId2 extends PublicIdA {

    protected final long msb;
    protected final long lsb;
    protected final long msb2;
    protected final long lsb2;

    public PublicId2(UUID uuid, UUID uuid2) {
        this.msb = uuid.getMostSignificantBits();
        this.lsb = uuid.getLeastSignificantBits();
        this.msb2 = uuid2.getMostSignificantBits();
        this.lsb2 = uuid2.getLeastSignificantBits();
    }

    public PublicId2(long msb, long lsb, long msb2, long lsb2) {
        this.msb = msb;
        this.lsb = lsb;
        this.msb2 = msb2;
        this.lsb2 = lsb2;
    }

    @Override
    public int uuidCount() {
        return 2;
    }

    @Override
    public void forEach(LongConsumer consumer) {
        consumer.accept(msb);
        consumer.accept(lsb);
        consumer.accept(msb2);
        consumer.accept(lsb2);
    }

    @Override
    public UUID[] asUuidArray() {
        return new UUID[] { new UUID(msb, lsb), new UUID(msb2, lsb2)};
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o instanceof PublicId) {
            if (o instanceof PublicId1) {
                PublicId1 publicId1 = (PublicId1) o;
                return publicId1.equals(this);
            }
            if (o instanceof PublicId2) {
                PublicId2 publicId2 = (PublicId2) o;
                return msb == publicId2.msb && lsb == publicId2.lsb ||
                        msb == publicId2.msb2 && lsb == publicId2.lsb2 ||
                        msb2 == publicId2.msb && lsb2 == publicId2.lsb ||
                        msb2 == publicId2.msb2 && lsb2 == publicId2.lsb2 ;
            }
            if (o instanceof PublicId3) {
                PublicId3 publicId3 = (PublicId3) o;
                return msb == publicId3.msb && lsb == publicId3.lsb ||
                        msb == publicId3.msb2 && lsb == publicId3.lsb2 ||
                        msb == publicId3.msb3 && lsb == publicId3.lsb3 ||

                        msb2 == publicId3.msb && lsb2 == publicId3.lsb ||
                        msb2 == publicId3.msb2 && lsb2 == publicId3.lsb2 ||
                        msb2 == publicId3.msb3 && lsb2 == publicId3.lsb3;

            }
            PublicId publicId = (PublicId) o;
            UUID[] thisUuids = asUuidArray();
            return Arrays.stream(publicId.asUuidArray()).anyMatch(uuid -> {
                for (UUID thisUuid : thisUuids) {
                    if (uuid.equals(thisUuid)) {
                        return true;
                    }
                }
                return false;
            });
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new long[] {msb, lsb, msb2, lsb2});
    }

}
