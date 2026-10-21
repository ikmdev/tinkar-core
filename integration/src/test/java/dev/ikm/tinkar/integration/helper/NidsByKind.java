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
package dev.ikm.tinkar.integration.helper;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.EntityService;

import java.util.UUID;

/**
 * The nid of a component a test makes, minted under its kind if the store does not know it yet,
 * as the entity layer mints one. A store that keys nids by pattern refuses a mint with no pattern
 * in scope, where the sequential store minted any UUID (IKE-Network/ike-issues#1313); a known
 * component's nid is returned whatever kind is named.
 */
public final class NidsByKind {

    private NidsByKind() {
    }

    /** A concept's nid, minted under the concept pattern. */
    public static long concept(UUID... uuids) {
        return EntityService.get().nidForConcept(PublicIds.of(uuids));
    }

    /** A concept's nid, minted under the concept pattern. */
    public static long concept(PublicId id) {
        return EntityService.get().nidForConcept(id);
    }

    /** A pattern's nid, minted under the pattern-of-patterns. */
    public static long pattern(UUID... uuids) {
        return EntityService.get().nidForPattern(PublicIds.of(uuids));
    }

    /** A pattern's nid, minted under the pattern-of-patterns. */
    public static long pattern(PublicId id) {
        return EntityService.get().nidForPattern(id);
    }

    /** A STAMP's nid, minted under the stamp pattern. */
    public static long stamp(UUID... uuids) {
        return EntityService.get().nidForStamp(PublicIds.of(uuids));
    }

    /** A STAMP's nid, minted under the stamp pattern. */
    public static long stamp(PublicId id) {
        return EntityService.get().nidForStamp(id);
    }

    /** A semantic's nid, minted under its pattern, itself minted as a pattern if new. */
    public static long semantic(UUID pattern, UUID semantic) {
        return EntityService.get().nidForSemantic(PublicIds.of(pattern), PublicIds.of(semantic));
    }

    /**
     * The nid of a component whose kind a test does not model: its own when the store knows it,
     * else one minted under the concept pattern. For tests of record shapes and their
     * transforms, where a nid need only name the public id.
     */
    public static long any(PublicId id) {
        return PrimitiveData.get().hasPublicId(id) ? PrimitiveData.nid(id) : concept(id);
    }
}
