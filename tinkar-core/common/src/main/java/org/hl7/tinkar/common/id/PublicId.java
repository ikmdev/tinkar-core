package org.hl7.tinkar.common.id;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.util.uuid.UuidUtil;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.LongConsumer;

public interface PublicId extends Comparable<PublicId> {

    static boolean equals(PublicId one, PublicId two) {
        if (one == two) {
            return true;
        }
        UUID[] oneUuids = one.asUuidArray();
        return Arrays.stream(two.asUuidArray()).anyMatch(twoUuid -> {
            for (UUID oneUuid : oneUuids) {
                if (twoUuid.equals(oneUuid)) {
                    return true;
                }
            }
            return false;
        });
    }

    UUID[] asUuidArray();

    default ImmutableList<UUID> asUuidList() {
        return Lists.immutable.of(asUuidArray());
    }

    int uuidCount();

    /**
     * Presents ordered list of longs, from the UUIDs in the order: msb, lsb, msb, lsb, ...
     *
     * @param consumer
     */
    void forEach(LongConsumer consumer);

    @Override
    default int compareTo(PublicId o) {
        if (this == o) {
            return 0;
        }
        UUID[] thisArray = asUuidArray();
        UUID[] thatArray = o.asUuidArray();
        Arrays.sort(thisArray);
        Arrays.sort(thatArray);
        return Arrays.compare(thisArray, thatArray);
    }

    default int publicIdHash() {
        return Arrays.hashCode(UuidUtil.asArray(asUuidArray()));

    }
}
