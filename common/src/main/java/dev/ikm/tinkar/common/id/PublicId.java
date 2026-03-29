/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.common.id;

import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.api.list.primitive.MutableLongList;
import org.eclipse.collections.impl.factory.primitive.LongLists;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.LongConsumer;

import static dev.ikm.tinkar.common.id.IdCollection.TO_STRING_LIMIT;

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

    default UUID[] asUuidArray() {
        UUID[] uuids = new UUID[uuidCount()];
        long[] longs = new long[uuidCount() * 2];
        int[] index = {0};
        forEach(l -> longs[index[0]++] = l);
        for (int i = 0; i < uuids.length; i++) {
            uuids[i] = new UUID(longs[i * 2], longs[i * 2 + 1]);
        }
        return uuids;
    }

    int uuidCount();

    default boolean contains(UUID uuid) {
        return (asUuidList().contains(uuid));
    }

    ImmutableList<UUID> asUuidList();

    default ImmutableLongList additionalUuidLongs() {
        int count = uuidCount();
        if (count <= 1) {
            return null;
        }
        long[] longs = new long[count * 2];
        int[] index = {0};
        forEach(l -> longs[index[0]++] = l);
        MutableLongList additionalLongs = LongLists.mutable.withInitialCapacity((count - 1) * 2);
        for (int i = 2; i < longs.length; i++) {
            additionalLongs.add(longs[i]);
        }
        return additionalLongs.toImmutable();
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
