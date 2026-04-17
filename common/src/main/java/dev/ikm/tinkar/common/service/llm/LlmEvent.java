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

import dev.ikm.tinkar.common.service.tool.ToolResult;

import java.util.Map;

/**
 * An event emitted during a single {@link LlmSession#send(String)}
 * interaction. Subscribers consume these as they arrive to show streaming
 * text, tool-use indicators, completion, or errors.
 * <p>
 * The hierarchy is sealed — pattern matching is exhaustive, and adding
 * a new event kind is a source-compatible breaking change that forces
 * every consumer to update:
 * <pre>{@code
 * switch (event) {
 *     case LlmEvent.TextChunk(String text)            -> ui.append(text);
 *     case LlmEvent.ToolUseRequested(var name, var p) -> ui.showCallingTool(name);
 *     case LlmEvent.ToolUseCompleted(var name, var r) -> ui.showToolResult(name, r);
 *     case LlmEvent.Done(var reason)                  -> ui.markTurnComplete();
 *     case LlmEvent.Error(var cause)                  -> ui.showError(cause);
 * }
 * }</pre>
 *
 * @see LlmSession#send(String)
 */
public sealed interface LlmEvent {

    /**
     * Partial assistant text as it streams from the model.
     *
     * @param text newly arrived text chunk (may be a single token or several)
     */
    record TextChunk(String text) implements LlmEvent {}

    /**
     * The model has requested invocation of a tool. The driver is handling
     * dispatch; this event is informational (e.g. for UI indicators that
     * a tool is being called).
     *
     * @param toolName the name of the requested tool
     * @param parameters parameter values the model supplied
     */
    record ToolUseRequested(String toolName, Map<String, Object> parameters) implements LlmEvent {}

    /**
     * A tool invocation requested by the model has completed.
     *
     * @param toolName the name of the invoked tool
     * @param result the tool's result (may be an error result)
     */
    record ToolUseCompleted(String toolName, ToolResult result) implements LlmEvent {}

    /**
     * The model has finished its response turn. The session is ready to
     * receive another {@link LlmSession#send(String)} call.
     *
     * @param stopReason vendor-specific reason the turn ended (e.g.
     *                   {@code "end_turn"}, {@code "max_tokens"},
     *                   {@code "tool_use"})
     */
    record Done(String stopReason) implements LlmEvent {}

    /**
     * An error occurred. The session should be considered unusable after
     * this event; callers should close it and open a new one if they wish
     * to continue.
     *
     * @param cause the underlying exception
     */
    record Error(Throwable cause) implements LlmEvent {}
}
