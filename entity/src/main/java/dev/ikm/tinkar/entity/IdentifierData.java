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
package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.iterator.LongIterator;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;

import java.util.UUID;
import java.util.function.LongConsumer;

public interface IdentifierData extends PublicId {

    long mostSignificantBits();
    long leastSignificantBits();
    ImmutableLongList additionalUuidLongs();
    int nid();

    @Override
    default UUID[] asUuidArray() {
        return asUuidList().toArray(new UUID[uuidCount()]);
    }

    @Override
    default ImmutableList<UUID> asUuidList() {
        MutableList<UUID> uuidList = Lists.mutable.withInitialCapacity(uuidCount());
        uuidList.add(new UUID(mostSignificantBits(), leastSignificantBits()));
        if (additionalUuidLongs() != null) {
            LongIterator iterator = additionalUuidLongs().longIterator();
            while (iterator.hasNext()) {
                uuidList.add(new UUID(iterator.next(), iterator.next()));
            }
        }
        return uuidList.toImmutable();
    }

    @Override
    default int uuidCount() {
        if (additionalUuidLongs() != null) {
            return (additionalUuidLongs().size() / 2) + 1;
        }
        return 1;
    }

    @Override
    default void forEach(LongConsumer consumer) {
        consumer.accept(mostSignificantBits());
        consumer.accept(leastSignificantBits());
        if (additionalUuidLongs() != null) {
            additionalUuidLongs().forEach(each -> consumer.accept(each));
        }
    }

    default PublicId publicId() {
        return PublicIds.of(asUuidList());
    }
}
