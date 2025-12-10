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
package dev.ikm.tinkar.common.id.impl;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.impl.factory.primitive.LongLists;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.LongConsumer;

public class PublicIdN implements PublicId {

    private final ImmutableLongList uuidParts;

    public PublicIdN(UUID... uuids) {
        uuidParts = UuidUtil.asImmutableLongList(uuids);
    }

    public PublicIdN(long... uuidParts) {
        this.uuidParts = LongLists.immutable.of(uuidParts);
    }

    public PublicIdN(ImmutableLongList uuidParts) {
        this.uuidParts = uuidParts;
    }

    @Override
    public int uuidCount() {
        return uuidParts.size()/2;
    }

    @Override
    public UUID[] asUuidArray() {
        return asUuidList().toArray(new UUID[uuidCount()]);
    }

    @Override
    public ImmutableList<UUID> asUuidList() {
        return UuidUtil.toList(uuidParts);
    }

    @Override
    public void forEach(LongConsumer consumer) {
        uuidParts.forEach(uuidPart -> consumer.accept(uuidPart));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o instanceof PublicId publicId) {
            UUID[] thisUuids = asUuidArray();
            return Arrays.stream(publicId.asUuidArray()).anyMatch(uuid -> {
                for (UUID thisUuid: thisUuids) {
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
        return Arrays.hashCode(uuidParts.toArray());
    }

}
