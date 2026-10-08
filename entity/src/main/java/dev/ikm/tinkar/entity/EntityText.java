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

import dev.ikm.tinkar.common.service.DiagnosticText;

/**
 * An entity or one of its versions as text for a diagnostic message, such as the message of an
 * exception ({@code IKE-Network/ike-issues#1189}).
 *
 * <p>{@link Entity#entityToString()} is not that form: it writes the nid of the entity, and the
 * text of every version with the nids its fields hold. Here the entity is identified as
 * {@link DiagnosticText#component(int)} identifies a component — by its description and UUID,
 * and by its nid only when the store has no public id for it — and nothing else about it is
 * written.
 *
 * <p>No method here throws, because the entity in such a message is often the malformed one the
 * message is about.
 */
public final class EntityText {

    private EntityText() {
    }

    /**
     * The kind of an entity and its identification.
     *
     * @param entity the entity; may be null
     * @return the entity as {@code ConceptRecord: Chronic lung disease (UUID 0b6f…)}; a note in
     *         place of the entity when it cannot be written
     */
    public static String diagnostic(Entity<?> entity) {
        if (entity == null) {
            return "no entity";
        }
        try {
            return entity.getClass().getSimpleName() + ": " + DiagnosticText.component(entity.nid());
        } catch (RuntimeException malformed) {
            return "an entity that cannot be written (" + malformed.getClass().getSimpleName() + ")";
        }
    }

    /**
     * The kind of a version, the identification of its entity, and the identification of its
     * stamp.
     *
     * @param version the version; may be null
     * @return the version as {@code ConceptVersionRecord of Chronic lung disease (UUID 0b6f…),
     *         stamp UUID 5e2c…}; a note in place of the version when it cannot be written
     */
    public static String diagnostic(EntityVersion version) {
        if (version == null) {
            return "no version";
        }
        try {
            return version.getClass().getSimpleName() + " of " + DiagnosticText.component(version.nid())
                    + ", stamp " + DiagnosticText.component(version.stampNid());
        } catch (RuntimeException malformed) {
            return "a version that cannot be written (" + malformed.getClass().getSimpleName() + ")";
        }
    }
}
