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

/**
 * A consumer-facing handle for running LLM conversations in-process.
 * <p>
 * A single {@code LlmDriver} is active at a time across the application,
 * selected via the {@code LLM_DRIVER}
 * {@link dev.ikm.tinkar.common.service.ServiceExclusionGroup}.
 * Implementations are vendor-specific (Anthropic, OpenAI, Ollama, etc.)
 * and ship as plugins.
 * <p>
 * Discovery:
 * <pre>{@code
 *   LlmDriver driver = ServiceLifecycleManager.get()
 *       .getRunningService(LlmDriver.class)
 *       .orElseThrow();
 * }</pre>
 * <p>
 * The driver is stateless with respect to conversations — each conversation
 * is an {@link LlmSession} the caller owns and closes. Tool dispatch is
 * handled internally by the driver by discovering
 * {@link dev.ikm.tinkar.common.service.tool.ToolProvider} implementations
 * and routing {@code tool_use} blocks emitted by the model.
 */
public interface LlmDriver {

    /**
     * Opens a new conversation session with the given configuration.
     * The caller is responsible for closing the returned session.
     *
     * @param config conversation configuration (model, coordinate, tool
     *               filter, etc.); never null
     * @return a new session ready to receive messages
     */
    LlmSession open(LlmSessionConfig config);
}
