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
package dev.ikm.tinkar.entity.changeset;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.schema.VertexUUID;

import java.util.UUID;

/**
 * UUIDs between the component model and the protobuf schema: the one place a public id or a
 * vertex UUID is encoded or decoded.
 *
 * <p>Format version 2 writes every UUID as two longs, most significant bits first
 * ({@code PublicId.uuid_bits}, {@code VertexUUID}'s two bit fields). Format version 1 wrote
 * them as text, which is still read; nothing writes text any more.
 */
public final class SchemaIds {

    private SchemaIds() {
    }

    /** A public id in the schema: two longs per UUID, in the public id's UUID order. */
    public static dev.ikm.tinkar.schema.PublicId toSchema(PublicId publicId) {
        if (publicId.uuidCount() == 0) {
            throw new IllegalArgumentException("A public id with no UUIDs cannot be written");
        }
        dev.ikm.tinkar.schema.PublicId.Builder builder = dev.ikm.tinkar.schema.PublicId.newBuilder();
        // msb, lsb, msb, lsb, ...: the order uuid_bits holds them in.
        publicId.forEach(builder::addUuidBits);
        return builder.build();
    }

    /** UUIDs as a public id in the schema, in the order given. */
    public static dev.ikm.tinkar.schema.PublicId toSchema(UUID... uuids) {
        return toSchema(PublicIds.of(uuids));
    }

    /**
     * A schema public id's UUIDs: from its longs, or, for format version 1, its text. A public
     * id that carries longs is read from them alone.
     *
     * @throws IllegalArgumentException if it carries no UUIDs, or an odd number of longs
     */
    public static UUID[] uuids(dev.ikm.tinkar.schema.PublicId pbPublicId) {
        int longs = pbPublicId.getUuidBitsCount();
        if (longs > 0) {
            if (longs % 2 != 0) {
                throw new IllegalArgumentException("A public id carries " + longs
                        + " longs; a UUID takes two: " + pbPublicId);
            }
            UUID[] uuids = new UUID[longs / 2];
            for (int i = 0; i < uuids.length; i++) {
                uuids[i] = new UUID(pbPublicId.getUuidBits(2 * i), pbPublicId.getUuidBits(2 * i + 1));
            }
            return uuids;
        }
        if (pbPublicId.getUuidsCount() > 0) {
            UUID[] uuids = new UUID[pbPublicId.getUuidsCount()];
            for (int i = 0; i < uuids.length; i++) {
                uuids[i] = UUID.fromString(pbPublicId.getUuids(i));
            }
            return uuids;
        }
        throw new IllegalArgumentException("A public id carries no UUIDs");
    }

    /** A schema public id as a public id. */
    public static PublicId toPublicId(dev.ikm.tinkar.schema.PublicId pbPublicId) {
        return PublicIds.of(uuids(pbPublicId));
    }

    /** Whether a schema public id carries a UUID, as longs or text. */
    public static boolean hasUuids(dev.ikm.tinkar.schema.PublicId pbPublicId) {
        return pbPublicId != null && (pbPublicId.getUuidBitsCount() > 0 || pbPublicId.getUuidsCount() > 0);
    }

    /** A vertex UUID in the schema, as two longs. */
    public static VertexUUID toSchemaVertex(UUID uuid) {
        return VertexUUID.newBuilder()
                .setMostSignificantBits(uuid.getMostSignificantBits())
                .setLeastSignificantBits(uuid.getLeastSignificantBits())
                .build();
    }

    /**
     * A schema vertex UUID: from its text when it has some (format version 1), else from its
     * longs.
     */
    public static UUID toUuid(VertexUUID vertexUuid) {
        if (!vertexUuid.getUuid().isEmpty()) {
            return UUID.fromString(vertexUuid.getUuid());
        }
        return new UUID(vertexUuid.getMostSignificantBits(), vertexUuid.getLeastSignificantBits());
    }
}
