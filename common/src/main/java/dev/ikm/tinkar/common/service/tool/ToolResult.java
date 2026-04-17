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

import java.util.Map;
import java.util.Optional;

/**
 * The outcome of a tool invocation.
 * <p>
 * {@code textOutput} is what the LLM sees directly. {@code structuredData}
 * is an optional machine-readable payload for non-LLM consumers (UI
 * rendering, programmatic callers, tests).
 * <p>
 * Tool-level errors (bad parameters, entity not found, etc.) should be
 * returned as a result with {@link #error()} true and a descriptive
 * {@code textOutput}; they should NOT be thrown. Throw only for
 * framework-level failures the LLM cannot meaningfully handle.
 *
 * @param textOutput LLM-facing text (required, may be empty string)
 * @param structuredData optional structured payload for non-LLM consumers
 * @param error whether this result represents a tool-level error
 */
public record ToolResult(
        String textOutput,
        Optional<Map<String, Object>> structuredData,
        boolean error) {

    /**
     * Canonical constructor requiring non-null {@code textOutput} and
     * normalizing a null {@code structuredData} to {@link Optional#empty()}.
     *
     * @throws IllegalArgumentException if {@code textOutput} is null
     */
    public ToolResult {
        if (textOutput == null) {
            throw new IllegalArgumentException("textOutput required (use empty string if truly empty)");
        }
        if (structuredData == null) {
            structuredData = Optional.empty();
        }
    }

    /**
     * Factory for a successful text-only result.
     *
     * @param output LLM-facing text output
     * @return a non-error result with no structured data
     */
    public static ToolResult text(String output) {
        return new ToolResult(output, Optional.empty(), false);
    }

    /**
     * Factory for a tool-level error result. Callers should use this
     * rather than throwing to signal bad parameters, not-found errors,
     * or similar recoverable failures.
     *
     * @param message LLM-facing error description
     * @return an error result with the given message
     */
    public static ToolResult errorResult(String message) {
        return new ToolResult(message, Optional.empty(), true);
    }

    /**
     * Factory for a successful result carrying both LLM text and a
     * structured payload for non-LLM consumers.
     *
     * @param text LLM-facing text output
     * @param structured structured data for non-LLM consumers
     * @return a non-error result with both text and structured data
     */
    public static ToolResult of(String text, Map<String, Object> structured) {
        return new ToolResult(text, Optional.of(structured), false);
    }
}
