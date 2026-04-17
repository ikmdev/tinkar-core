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
package dev.ikm.tinkar.common.service.plugin.internal;

import java.nio.file.Path;

/**
 * A named filesystem directory that {@code Layers} scans for plugin JARs
 * <em>once at startup</em>. The scan is not ongoing — there is no
 * {@link java.nio.file.WatchService}, no hot-reload, and no lifecycle events.
 * Plugin changes require an application restart.
 *
 * @param name      human-readable label for diagnostics (e.g. "Standard plugins directory")
 * @param directory path to the directory containing plugin JAR files
 */
public record PluginDirectory(String name, Path directory) {
}
