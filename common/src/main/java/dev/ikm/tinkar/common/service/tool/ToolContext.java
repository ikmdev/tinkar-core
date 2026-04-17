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
package dev.ikm.tinkar.common.service.tool;

import java.util.Optional;
import java.util.UUID;

/**
 * Per-invocation context passed alongside parameters. Carries information
 * the tool may need that doesn't belong in LLM-visible parameters: STAMP
 * coordinate, session identifier, caller identity.
 * <p>
 * The context is created by the LLM driver (or other tool caller) and
 * supplied on each invocation. Tools should not cache context between calls.
 *
 * @param sessionId identifier of the calling session, if any
 * @param stampCoordinate active STAMP coordinate for read operations, if
 *                        supplied; typed as {@link Object} to avoid a
 *                        dependency from this module on the entity/coordinate
 *                        modules (the concrete type is
 *                        {@code StampCoordinateRecord} when present)
 * @param callerId identifier of the caller for audit logging, if supplied
 */
public record ToolContext(
        Optional<UUID> sessionId,
        Optional<Object> stampCoordinate,
        Optional<String> callerId) {

    /**
     * Canonical constructor normalizing null components to
     * {@link Optional#empty()}.
     */
    public ToolContext {
        if (sessionId == null) {
            sessionId = Optional.empty();
        }
        if (stampCoordinate == null) {
            stampCoordinate = Optional.empty();
        }
        if (callerId == null) {
            callerId = Optional.empty();
        }
    }

    /**
     * Returns a context with all fields empty, for invocations that don't
     * carry identifying information.
     *
     * @return an empty context
     */
    public static ToolContext empty() {
        return new ToolContext(Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Returns a context scoped only to the given session identifier.
     *
     * @param sessionId session identifier to attach
     * @return a context with session set, other fields empty
     */
    public static ToolContext forSession(UUID sessionId) {
        return new ToolContext(Optional.of(sessionId), Optional.empty(), Optional.empty());
    }
}
