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
 * Tool Service Provider Interface (SPI).
 * <p>
 * {@link dev.ikm.tinkar.common.service.tool.ToolProvider} is the single
 * interface a tool implementation satisfies. Tools are discovered through
 * the standard {@link dev.ikm.tinkar.common.service.ProviderController}
 * pattern and invoked by LLM drivers or other tool consumers.
 *
 * <h2>What the SPI contributes</h2>
 * The Tool SPI is the adapter between LLM-neutral typed services (e.g.
 * {@code EntityService}, {@code StampService}) and LLM-callable
 * capabilities. It carries the metadata (name, parameters schema,
 * description) the LLM needs to choose and invoke a capability, plus
 * a uniform invocation shape.
 * <p>
 * Discovery, lifecycle, activation, mutual exclusion — those are handled
 * by the existing {@link dev.ikm.tinkar.common.service.ProviderController}
 * and {@link dev.ikm.tinkar.common.service.ServiceLifecycleManager}
 * machinery. The Tool SPI adds no new discovery mechanism.
 *
 * <h2>Types</h2>
 * <ul>
 *   <li>{@link dev.ikm.tinkar.common.service.tool.ToolProvider} — the SPI interface</li>
 *   <li>{@link dev.ikm.tinkar.common.service.tool.ToolDescriptor} — tool metadata</li>
 *   <li>{@link dev.ikm.tinkar.common.service.tool.ToolParameter} — one parameter</li>
 *   <li>{@link dev.ikm.tinkar.common.service.tool.ToolSchema} — JSON-Schema-shaped type description</li>
 *   <li>{@link dev.ikm.tinkar.common.service.tool.ToolInvocation} — parameters + context for a call</li>
 *   <li>{@link dev.ikm.tinkar.common.service.tool.ToolContext} — per-call context</li>
 *   <li>{@link dev.ikm.tinkar.common.service.tool.ToolResult} — invocation outcome</li>
 * </ul>
 *
 * <h2>Implementing a tool — minimal example</h2>
 * <pre>{@code
 * public class GetConceptTool extends ProviderController<GetConceptTool>
 *         implements ToolProvider {
 *
 *     @Override protected GetConceptTool createProvider()               { return this; }
 *     @Override protected void startProvider(GetConceptTool p)          { }
 *     @Override protected void stopProvider(GetConceptTool p)           { }
 *     @Override protected String getProviderName()                      { return describe().name(); }
 *     @Override public ImmutableList<Class<?>> serviceClasses() {
 *         return Lists.immutable.of(ToolProvider.class);
 *     }
 *
 *     @Override public ToolDescriptor describe() {
 *         return new ToolDescriptor(
 *             "concept.get",
 *             "Retrieve a concept's default description by NID or UUID.",
 *             Lists.immutable.of(
 *                 ToolParameter.requiredParam("identifier", ToolSchema.string(),
 *                     "Native ID (NID) integer or UUID string of the concept.")
 *             ),
 *             Optional.of("The concept's FQN/preferred term plus semantic counts."));
 *     }
 *
 *     @Override public ToolResult invoke(ToolInvocation inv) {
 *         String id = inv.getString("identifier").orElse(null);
 *         if (id == null) return ToolResult.errorResult("identifier is required");
 *         // ... look up, format ...
 *         return ToolResult.text("Concept: ...");
 *     }
 * }
 * }</pre>
 * Declare it in {@code module-info.java}:
 * <pre>{@code
 * provides dev.ikm.tinkar.common.service.ServiceLifecycle with
 *         com.example.tools.GetConceptTool;
 * }</pre>
 *
 * <h2>Discovering tools</h2>
 * A tool consumer (typically the {@link dev.ikm.tinkar.common.service.llm.LlmDriver})
 * builds its catalog via:
 * <pre>{@code
 * Collection<ToolProvider> tools = ServiceLifecycleManager.get()
 *         .getRunningServices(ToolProvider.class);
 * Map<String, ToolProvider> byName = tools.stream()
 *         .collect(Collectors.toMap(t -> t.describe().name(), Function.identity()));
 * }</pre>
 *
 * <h2>Naming conventions</h2>
 * Tool names use dotted-namespace form {@code <domain>.<operation>}:
 * <ul>
 *   <li>{@code concept.get}, {@code concept.search}</li>
 *   <li>{@code navigation.parents}, {@code navigation.children}</li>
 *   <li>{@code axiom.stated}, {@code axiom.inferred}</li>
 *   <li>{@code stamp.setCoordinate}</li>
 * </ul>
 * The {@link dev.ikm.tinkar.common.service.llm.LlmDriver} fails fast on
 * name collisions when building its tool catalog at startup.
 *
 * <h2>Contracts summary</h2>
 * <ul>
 *   <li>{@link dev.ikm.tinkar.common.service.tool.ToolProvider#invoke(dev.ikm.tinkar.common.service.tool.ToolInvocation)}
 *       MUST be thread-safe; singletons are invoked concurrently from multiple sessions</li>
 *   <li>Tool-level errors are returned as {@link dev.ikm.tinkar.common.service.tool.ToolResult#errorResult(String)},
 *       not thrown</li>
 *   <li>Per-invocation state lives in {@link dev.ikm.tinkar.common.service.tool.ToolInvocation};
 *       instance fields carry only shared, thread-safe state</li>
 *   <li>State persistence: use preferences for user preferences, the
 *       primitive data service for larger data</li>
 * </ul>
 *
 * @since 1.127.2
 * @see dev.ikm.tinkar.common.service.ProviderController
 * @see dev.ikm.tinkar.common.service.ServiceLifecycleManager#getRunningServices(Class)
 * @see dev.ikm.tinkar.common.service.llm.LlmDriver
 */
package dev.ikm.tinkar.common.service.tool;
