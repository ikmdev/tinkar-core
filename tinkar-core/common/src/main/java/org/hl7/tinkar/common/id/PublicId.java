package org.hl7.tinkar.common.id;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.util.uuid.UuidUtil;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.LongConsumer;

import static org.hl7.tinkar.common.id.IdCollection.TO_STRING_LIMIT;

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

    int uuidCount();

    default boolean contains(UUID uuid) {
        return (asUuidList().contains(uuid));
    }

    default ImmutableList<UUID> asUuidList() {
        return Lists.immutable.of(asUuidArray());
    }

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

    default String idString() {
        return idString(asUuidArray());
    }

    static String idString(UUID[] uuids) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < uuids.length && i <= TO_STRING_LIMIT; i++) {
            sb.append("\"");
            sb.append(uuids[i].toString());
            sb.append("\"");
            sb.append(", ");
            if (i == TO_STRING_LIMIT) {
                sb.append("..., ");
            }
        }
        sb.delete(sb.length() - 2, sb.length() - 1);
        sb.deleteCharAt(sb.length() - 1);
        sb.append("]");
        return sb.toString();
    }
}
