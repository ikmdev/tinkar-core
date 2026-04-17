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
 * <p>
 * Each tool is a singleton service. The tool's managing controller declares
 * {@code ToolProvider.class} in its {@code serviceClasses()} and is discovered
 * as a {@link dev.ikm.tinkar.common.service.ServiceLifecycle} via {@code ServiceLoader}.
 * Consumers locate tools via
 * {@code ServiceLifecycleManager.get().getRunningServices(ToolProvider.class)}.
 * <p>
 * The SPI is intentionally minimal: {@link dev.ikm.tinkar.common.service.tool.ToolProvider}
 * with {@link dev.ikm.tinkar.common.service.tool.ToolProvider#describe()} and
 * {@link dev.ikm.tinkar.common.service.tool.ToolProvider#invoke(dev.ikm.tinkar.common.service.tool.ToolInvocation)},
 * plus supporting records for descriptor, parameters, invocation, and result.
 *
 * @since 1.127.2
 */
package dev.ikm.tinkar.common.service.tool;
