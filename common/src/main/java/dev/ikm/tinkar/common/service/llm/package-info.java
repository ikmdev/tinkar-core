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
 * in the {@link dev.ikm.tinkar.common.service.ServiceExclusionGroup#LLM_DRIVER}
 * mutual exclusion group.
 *
 * <h2>What this package contributes beyond the Tool SPI</h2>
 * The Tool SPI in {@link dev.ikm.tinkar.common.service.tool} covers
 * stateless request/response capabilities. LLM conversations add three
 * concerns that don't fit that shape:
 * <ol>
 *   <li><b>Session lifecycle</b> — multi-turn state (history, active STAMP
 *       coordinate, selected model) that outlives any single message</li>
 *   <li><b>Streaming events</b> — interleaved text chunks, tool-use
 *       notifications, and completion signals arriving from the model</li>
 *   <li><b>Session-open configuration</b> — model selection, tool filter,
 *       memory bounds — applied once per conversation</li>
 * </ol>
 * Tool dispatch is <em>not</em> in this package — the driver discovers
 * {@link dev.ikm.tinkar.common.service.tool.ToolProvider}s internally and
 * routes tool_use blocks emitted by the model.
 *
 * <h2>Types</h2>
 * <ul>
 *   <li>{@link dev.ikm.tinkar.common.service.llm.LlmDriver} — the SPI entry point</li>
 *   <li>{@link dev.ikm.tinkar.common.service.llm.LlmSession} — a stateful conversation</li>
 *   <li>{@link dev.ikm.tinkar.common.service.llm.LlmSessionConfig} — open-time configuration</li>
 *   <li>{@link dev.ikm.tinkar.common.service.llm.LlmEvent} — sealed event hierarchy
 *       (TextChunk, ToolUseRequested, ToolUseCompleted, Done, Error)</li>
 * </ul>
 *
 * <h2>Consumer example — running a conversation</h2>
 * <pre>{@code
 * LlmDriver driver = ServiceLifecycleManager.get()
 *         .getRunningService(LlmDriver.class)
 *         .orElseThrow(() -> new IllegalStateException("No LLM driver active"));
 *
 * try (LlmSession session = driver.open(LlmSessionConfig.of("claude-sonnet-4-5"))) {
 *
 *     StringBuilder assistantText = new StringBuilder();
 *     CountDownLatch done = new CountDownLatch(1);
 *
 *     session.send("Find concepts matching 'atrial fibrillation'.")
 *            .subscribe(new Flow.Subscriber<LlmEvent>() {
 *         private Flow.Subscription sub;
 *         public void onSubscribe(Flow.Subscription s) { sub = s; s.request(Long.MAX_VALUE); }
 *         public void onNext(LlmEvent event) {
 *             switch (event) {
 *                 case LlmEvent.TextChunk(String text)            -> assistantText.append(text);
 *                 case LlmEvent.ToolUseRequested(var name, var p) -> System.out.println("calling " + name);
 *                 case LlmEvent.ToolUseCompleted(var name, var r) -> System.out.println("result from " + name);
 *                 case LlmEvent.Done(var reason)                  -> done.countDown();
 *                 case LlmEvent.Error(var cause)                  -> { cause.printStackTrace(); done.countDown(); }
 *             }
 *         }
 *         public void onError(Throwable t)                     { t.printStackTrace(); done.countDown(); }
 *         public void onComplete()                             { done.countDown(); }
 *     });
 *
 *     done.await();
 *     System.out.println(assistantText);
 * }
 * }</pre>
 * The sealed hierarchy of {@link dev.ikm.tinkar.common.service.llm.LlmEvent}
 * enables exhaustive pattern matching — the compiler enforces that every
 * event kind is handled.
 *
 * <h2>Implementing a driver</h2>
 * Driver implementations follow the {@link dev.ikm.tinkar.common.service.ProviderController}
 * pattern with the {@link dev.ikm.tinkar.common.service.ServiceExclusionGroup#LLM_DRIVER}
 * exclusion group. An implementation:
 * <ul>
 *   <li>Builds its tool catalog at startup from
 *       {@code ServiceLifecycleManager.get().getRunningServices(ToolProvider.class)}</li>
 *   <li>Runs the vendor-specific wire protocol internally</li>
 *   <li>Translates between {@link dev.ikm.tinkar.common.service.llm.LlmEvent}s and vendor event formats</li>
 *   <li>Dispatches model-requested tool calls to the appropriate
 *       {@link dev.ikm.tinkar.common.service.tool.ToolProvider}</li>
 *   <li>Activates via {@link dev.ikm.tinkar.common.service.ServiceLifecycle#shouldActivate()}
 *       gated on API-key presence so absence of credentials does not break startup</li>
 * </ul>
 *
 * <h2>Threading and session ownership</h2>
 * <ul>
 *   <li>{@link dev.ikm.tinkar.common.service.llm.LlmDriver} itself is thread-safe</li>
 *   <li>An individual {@link dev.ikm.tinkar.common.service.llm.LlmSession} is NOT
 *       thread-safe; drive one session from one thread at a time, or use
 *       separate sessions for concurrent conversations</li>
 *   <li>Sessions are created and closed by the caller; the driver does not
 *       pool or retain them</li>
 * </ul>
 *
 * @since 1.127.2
 * @see dev.ikm.tinkar.common.service.tool.ToolProvider
 * @see dev.ikm.tinkar.common.service.ServiceExclusionGroup#LLM_DRIVER
 */
package dev.ikm.tinkar.common.service.llm;
