package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.common.id.PublicId;

import java.util.UUID;
import java.util.function.LongConsumer;

public interface IdentifierData extends PublicId {

    long mostSignificantBits();
    long leastSignificantBits();
    long[] additionalUuidLongs();
    int nid();

    @Override
    default UUID[] asUuidArray() {
        UUID[] uuidArray = new UUID[uuidCount()];
        uuidArray[0] = new UUID(mostSignificantBits(), leastSignificantBits());
        long[] additionalUuidLongs = additionalUuidLongs();
        if (additionalUuidLongs != null) {
            for (int i = 1; i < uuidArray.length; i++) {
                uuidArray[i] = new UUID(additionalUuidLongs[((i - 1) * 2)], additionalUuidLongs[((i - 1) * 2) + 1]);
            }
        }
        return uuidArray;
    }

    @Override
    default int uuidCount() {
        if (additionalUuidLongs() != null) {
            return (additionalUuidLongs().length / 2) + 1;
        }
        return 1;
    }

    @Override
    default void forEach(LongConsumer consumer) {
        consumer.accept(mostSignificantBits());
        consumer.accept(leastSignificantBits());
        if (additionalUuidLongs() != null) {
            for (long uuidPart : additionalUuidLongs()) {
                consumer.accept(uuidPart);
            }
        }
    }

    default PublicId publicId() {
        return this;
    }
}
