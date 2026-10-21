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
package dev.ikm.tinkar.entity.boundary;

import dev.ikm.tinkar.common.id.PublicId;

/**
 * One way a commit crosses the boundary between knowledge, ephemeral and instance data
 * (IKE-Network/ike-issues#1313).
 *
 * @param rule   which rule it breaks
 * @param source the public id of the component whose version breaks it
 * @param target the public id of the component it leads to, or {@code null} for a rule about the source alone
 * @param detail what was found, in words
 */
public record BoundaryViolation(Rule rule, PublicId source, PublicId target, String detail) {

    /** The rules of the boundary. */
    public enum Rule {
        /** Knowledge references ephemeral or instance data. */
        KNOWLEDGE_REFERENCES_DATA,
        /** Ephemeral data references instance data, or instance data references ephemeral data. */
        CROSSES_KINDS,
        /** A later version is written under a module of another kind than the component's first. */
        BIRTH_IS_FINAL,
        /** An element of an ephemeral or instance pattern is written under a module of another kind. */
        ELEMENT_OUTSIDE_ITS_PATTERN,
        /** A module is a kind of both the ephemeral and the instance parent. */
        MODULE_UNDER_BOTH_PARENTS
    }

    @Override
    public String toString() {
        return rule.name().toLowerCase().replace('_', ' ') + ": " + source.idString()
                + (target == null ? "" : " -> " + target.idString()) + " (" + detail + ")";
    }
}
