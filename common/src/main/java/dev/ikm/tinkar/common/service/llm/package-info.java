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

/**
 * LLM Driver interfaces.
 * <p>
 * {@link dev.ikm.tinkar.common.service.llm.LlmDriver} is the entry point
 * for in-process LLM conversations. Implementations are vendor-specific
 * (Anthropic Claude, OpenAI, Ollama, etc.) and ship as plugins declared
 * in the {@code LLM_DRIVER}
 * {@link dev.ikm.tinkar.common.service.ServiceExclusionGroup}.
 * <p>
 * The LLM driver is a consumer-facing service — Komet code that wants to
 * chat with an LLM opens an {@link dev.ikm.tinkar.common.service.llm.LlmSession}
 * through the driver. Tool dispatch is handled internally by the driver
 * using the Tool SPI in {@link dev.ikm.tinkar.common.service.tool}.
 *
 * @since 1.127.2
 */
package dev.ikm.tinkar.common.service.llm;
