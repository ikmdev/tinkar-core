package org.hl7.tinkar.common.id;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.LongConsumer;

public interface PublicId {

    UUID[] asUuidArray();

    default ImmutableList<UUID> asUuidList() {
        return Lists.immutable.of(asUuidArray());
    }

    int uuidCount();

    /**
     * Presents ordered list of longs, from the UUIDs in the order: msb, lsb, msb, lsb, ...
     * @param consumer
     */
    void forEach(LongConsumer consumer);

    static boolean equals(PublicId one, PublicId two) {
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
}
