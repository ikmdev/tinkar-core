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
package dev.ikm.tinkar.common.service.llm;

import dev.ikm.tinkar.common.service.tool.ToolDescriptor;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;

/**
 * Configuration for opening an {@link LlmSession}.
 * <p>
 * Only {@code model} is required; every other field has sensible defaults
 * or is genuinely optional.
 *
 * @param model vendor-specific model identifier (e.g. "claude-sonnet-4-5")
 * @param initialCoordinate STAMP coordinate to start the session with;
 *                          tools may update via a tool call. Typed as
 *                          {@link Object} to avoid a dependency from this
 *                          module on entity/coordinate modules
 *                          ({@code StampCoordinateRecord} when present)
 * @param systemPromptAddition text appended to the driver's default system
 *                             prompt
 * @param toolFilter filter limiting which tools are exposed to the model;
 *                   default (empty) means all discovered tools
 * @param maxMemoryTurns maximum conversation turns to retain in memory
 *                      before summarization or truncation; default
 *                      driver-dependent
 */
public record LlmSessionConfig(
        String model,
        Optional<Object> initialCoordinate,
        Optional<String> systemPromptAddition,
        Optional<Predicate<ToolDescriptor>> toolFilter,
        OptionalInt maxMemoryTurns) {

    /**
     * Canonical constructor validating required fields and normalizing null
     * {@link Optional}s to empty.
     *
     * @throws IllegalArgumentException if {@code model} is null or blank
     */
    public LlmSessionConfig {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model required");
        }
        if (initialCoordinate == null) {
            initialCoordinate = Optional.empty();
        }
        if (systemPromptAddition == null) {
            systemPromptAddition = Optional.empty();
        }
        if (toolFilter == null) {
            toolFilter = Optional.empty();
        }
        if (maxMemoryTurns == null) {
            maxMemoryTurns = OptionalInt.empty();
        }
    }

    /**
     * Factory for a minimal configuration with just a model identifier and
     * all other options at their defaults.
     *
     * @param model vendor-specific model identifier
     * @return a minimal configuration
     */
    public static LlmSessionConfig of(String model) {
        return new LlmSessionConfig(model, Optional.empty(), Optional.empty(),
                Optional.empty(), OptionalInt.empty());
    }
}
