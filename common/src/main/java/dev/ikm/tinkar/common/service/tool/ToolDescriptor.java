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

import org.eclipse.collections.api.list.ImmutableList;

import java.util.Optional;

/**
 * Describes a tool to an LLM or other consumer.
 * <p>
 * Returned by {@link ToolProvider#describe()} and consumed by tool
 * catalogers (typically an {@link dev.ikm.tinkar.common.service.llm.LlmDriver})
 * at startup to build the set of capabilities presented to the model.
 * <p>
 * The {@code name} must be unique within the active tool catalog; use
 * the dotted-namespace convention {@code <domain>.<operation>}
 * ({@code "concept.get"}, {@code "navigation.parents"},
 * {@code "axiom.inferred"}). Name collisions are detected at LLM driver
 * startup and cause fail-fast.
 * <p>
 * The {@code description} is presented to the LLM and should explain
 * when to use the tool, any constraints on its use, and the shape of
 * the result. Write it for an LLM audience — the model chooses among
 * tools based on these descriptions, so clarity matters.
 *
 * @param name unique namespaced identifier (e.g. {@code "concept.get"})
 * @param description human-readable guidance for the LLM
 * @param parameters ordered list of input parameters (may be empty)
 * @param resultDescription optional guidance on what the result will contain
 * @see ToolProvider#describe()
 * @see ToolParameter
 */
public record ToolDescriptor(
        String name,
        String description,
        ImmutableList<ToolParameter> parameters,
        Optional<String> resultDescription) {

    /**
     * Canonical constructor validating required fields. Null
     * {@code resultDescription} is normalized to {@link Optional#empty()}.
     *
     * @throws IllegalArgumentException if {@code name} is null or blank,
     *         {@code description} is null or blank, or {@code parameters} is null
     */
    public ToolDescriptor {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tool name required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("tool description required");
        }
        if (parameters == null) {
            throw new IllegalArgumentException("parameters list required (may be empty)");
        }
        if (resultDescription == null) {
            resultDescription = Optional.empty();
        }
    }
}
