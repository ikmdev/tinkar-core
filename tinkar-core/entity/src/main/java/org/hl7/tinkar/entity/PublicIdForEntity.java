package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.hl7.tinkar.common.id.PublicId;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.LongConsumer;

public class PublicIdForEntity implements PublicId {

    protected long mostSignificantBits;

    protected long leastSignificantBits;

    protected long[] additionalUuidLongs;

    @Override
    public int hashCode() {
        return Arrays.hashCode(additionalUuidLongs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o instanceof PublicId publicId) {
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
    public UUID[] asUuidArray() {
        UUID[] uuidArray = new UUID[uuidCount()];
        uuidArray[0] = new UUID(mostSignificantBits, leastSignificantBits);
        if (additionalUuidLongs != null) {
            for (int i = 1; i < uuidArray.length; i++) {
                uuidArray[i] = new UUID(additionalUuidLongs[((i - 1) * 2)], additionalUuidLongs[((i - 1) * 2) + 1]);
            }
        }
        return uuidArray;
    }

    @Override
    public int uuidCount() {
        if (additionalUuidLongs != null) {
            return (additionalUuidLongs.length / 2) + 1;
        }
        return 1;
    }

    @Override
    public void forEach(LongConsumer consumer) {
        consumer.accept(mostSignificantBits);
        consumer.accept(leastSignificantBits);
        if (additionalUuidLongs != null) {
            for (long uuidPart : additionalUuidLongs) {
                consumer.accept(uuidPart);
            }
        }
    }

    protected void fillId(ByteBuf readBuf) {
        this.mostSignificantBits = readBuf.readLong();
        this.leastSignificantBits = readBuf.readLong();
        int additionalUuidLongsCount = readBuf.readInt();
        if (additionalUuidLongsCount == 0) {
            additionalUuidLongs = null;
        } else {
            additionalUuidLongs = new long[additionalUuidLongsCount];
            for (int i = 0; i < additionalUuidLongsCount; i++) {
                additionalUuidLongs[i] = readBuf.readLong();
            }
        }
    }

    protected void writeId(ByteBuf writeBuf) {
        writeBuf.writeLong(this.mostSignificantBits);
        writeBuf.writeLong(this.leastSignificantBits);
        if (additionalUuidLongs == null) {
            writeBuf.writeInt(0);
        } else {
            writeBuf.writeInt(additionalUuidLongs.length);
            for (long additionalIdLong : additionalUuidLongs) {
                writeBuf.writeLong(additionalIdLong);
            }
        }


    }
}
