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
import dev.ikm.tinkar.common.util.Validator;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import org.eclipse.collections.api.factory.primitive.LongLists;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.api.list.primitive.MutableLongList;

import java.util.UUID;

/**
 * Utility class to simplify writing identifier data to entity records.
 */
public record PublicIdentifierRecord(long mostSignificantBits,
                                     long leastSignificantBits,
                                     ImmutableLongList additionalUuidLongs) {

    public PublicIdentifierRecord {
        Validator.notZero(mostSignificantBits);
        Validator.notZero(leastSignificantBits);
    }
    /**
     * The record header for a public id: every UUID, the one listed first in the most and least
     * significant bits and the rest in the additional longs. The split is how the record is laid
     * out, nothing more: the UUID in the leading bits is no more the component's than the others.
     * Code that writes a record header from a public id uses this rather than splitting the UUIDs
     * itself.
     *
     * @param publicId the public id
     * @return the header holding every one of its UUIDs
     */
    public static PublicIdentifierRecord make(PublicId publicId) {
        UUID[] uuids = publicId.asUuidArray();
        UUID head = uuids[0]; // first-uuid: the record layout's head/rest split; the rest follow

        if (uuids.length > 1) {
            MutableLongList additionalUuidLongs = LongLists.mutable.empty();
            for (int i = 1; i < uuids.length; i++) {
                additionalUuidLongs.add(uuids[i].getMostSignificantBits());
                additionalUuidLongs.add(uuids[i].getLeastSignificantBits());
            }
            return new PublicIdentifierRecord(head.getMostSignificantBits(), head.getLeastSignificantBits(), additionalUuidLongs.toImmutable());
        }
        return new PublicIdentifierRecord(head.getMostSignificantBits(), head.getLeastSignificantBits(), null);
    }
}
