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

/**
 * A capability that can be invoked by an LLM driver or other tool consumer.
 * <p>
 * Implementations are discovered through the standard
 * {@link dev.ikm.tinkar.common.service.ProviderController} pattern: a tool
 * class (often the same class) is managed by a Controller that declares
 * {@code ToolProvider.class} in its {@code serviceClasses()} and is
 * discovered as a {@link dev.ikm.tinkar.common.service.ServiceLifecycle}
 * via {@code ServiceLoader}.
 * <p>
 * <b>Thread safety.</b> {@link #invoke(ToolInvocation)} MUST be thread-safe.
 * Tool providers are singletons and may be invoked concurrently from multiple
 * LLM sessions. Per-invocation state belongs in the {@link ToolInvocation}
 * argument, not in instance fields.
 * <p>
 * <b>Naming.</b> Tool names should be namespaced (e.g. {@code "concept.get"},
 * {@code "navigation.parents"}). The LLM driver fails fast on name collisions
 * when it builds its tool catalog at startup.
 * <p>
 * <b>Error handling.</b> Tool-level errors (invalid parameters, entity not
 * found, etc.) should be returned as a {@link ToolResult} with
 * {@link ToolResult#error()} {@code true} and a descriptive
 * {@link ToolResult#textOutput()}. Throw only for framework-level failures
 * the LLM cannot meaningfully handle.
 */
public interface ToolProvider {

    /**
     * Returns metadata describing this tool: name, parameters schema, and
     * human-readable documentation. Called by tool consumers (typically an
     * LLM driver) at startup to build the available tool catalog presented
     * to models.
     *
     * @return non-null descriptor
     */
    ToolDescriptor describe();

    /**
     * Invokes the tool with the given parameters and context.
     * <p>
     * Implementations should validate parameters, perform the work, and
     * return a {@link ToolResult} with textual output suitable for the LLM.
     * Tool-level errors should be returned as results with
     * {@link ToolResult#error()} true; throw only for framework-level
     * failures.
     *
     * @param invocation parameters and context for this invocation; never null
     * @return non-null result
     */
    ToolResult invoke(ToolInvocation invocation);
}
