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
package dev.ikm.tinkar.common.service.plugin;

import dev.ikm.tinkar.common.service.PluggableService;
import dev.ikm.tinkar.common.service.PluginServiceLoader;
import dev.ikm.tinkar.common.service.plugin.internal.Layers;
import dev.ikm.tinkar.common.service.plugin.internal.PluginDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Entry point for activating the IKE plugin system.
 *
 * <p>Scans a configured plugin directory for JARs, materializes them as a
 * JPMS {@link ModuleLayer}, locates the {@code plugin-service-loader} JAR,
 * constructs its own {@link ModuleLayer} (parent = plugin layer), and
 * registers the resulting {@link PluginServiceLoader} with
 * {@link PluggableService}.
 *
 * <p>Call {@link #setPluginDirectory(Path)} once at application startup,
 * before anything that relies on {@code PluggableService.load(...)}.
 *
 * <h2>System properties</h2>
 *
 * <dl>
 *   <dt>{@value #PATH_KEY}</dt>
 *   <dd>Absolute path to the {@code plugin-service-loader} JAR. If set,
 *       overrides filesystem discovery.</dd>
 *   <dt>{@value #ARTIFACT_KEY}</dt>
 *   <dd>Filename prefix used when probing for the service-loader JAR
 *       (defaults to {@value #PLUGIN_SERVICE_LOADER_DIRECTORY}).</dd>
 * </dl>
 */
public class IkeServiceManager {
    private static final Logger LOG = LoggerFactory.getLogger(IkeServiceManager.class);

    /**
     * System property naming an explicit path to the {@code plugin-service-loader}
     * JAR. Overrides filesystem discovery.
     */
    public static final String PATH_KEY = "dev.ikm.tinkar.common.service.plugin.IkeServiceManager.PATH_KEY";

    /**
     * System property naming the artifact-id prefix used when searching for
     * the {@code plugin-service-loader} JAR on the filesystem.
     */
    public static final String ARTIFACT_KEY = "dev.ikm.tinkar.common.service.plugin.IkeServiceManager.ARTIFACT_KEY";

    private static final String PLUGIN_SERVICE_LOADER_DIRECTORY = "plugin-service-loader";

    private final Layers layers;

    private static final AtomicReference<IkeServiceManager> singletonReference = new AtomicReference<>();

    /**
     * Constructs the singleton manager and deploys the plugin-service-loader
     * layer above the plugin layer(s) produced by {@code Layers}.
     *
     * @param pluginsDirectories directories to scan for plugin JARs
     * @throws IllegalStateException if {@code IkeServiceManager} has already been initialized
     */
    private IkeServiceManager(Set<PluginDirectory> pluginsDirectories) {
        if (!IkeServiceManager.singletonReference.compareAndSet(null, this)) {
            throw new IllegalStateException("IkeServiceManager must only be set up once.");
        }
        this.layers = new Layers(pluginsDirectories);
        deployPluginServiceLoader(this.layers.getModuleLayers());
    }

    /**
     * Recursively searches {@code dirPath} for a file whose name starts with
     * {@code artifactKey} and ends with {@code .jar}.
     *
     * @param dirPath     directory to search
     * @param artifactKey expected filename prefix (typically the service-loader artifactId)
     * @return the absolute path of the first matching JAR, or empty if none found
     */
    public static Optional<String> findPluggableServiceLoaderJar(File dirPath, String artifactKey) {
        File[] filesList = dirPath.listFiles();
        if (filesList == null) {
            return Optional.empty();
        }
        for (File file : filesList) {
            if (file.isFile()) {
                if (file.getName().endsWith(".jar") && file.getName().startsWith(artifactKey)) {
                    return Optional.of(file.getAbsolutePath());
                }
            } else {
                Optional<String> optionalPluggableServiceLoaderJar = findPluggableServiceLoaderJar(file, artifactKey);
                if (optionalPluggableServiceLoaderJar.isPresent()) {
                    return optionalPluggableServiceLoaderJar;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves the plugin-service-loader JAR (using the {@value #PATH_KEY}
     * system property if set, otherwise probing the filesystem), constructs a
     * {@link ModuleLayer} containing only that JAR parented by {@code parentLayers},
     * and registers the resulting {@link PluginServiceLoader} with
     * {@link PluggableService}.
     *
     * <p>Called from {@link Layers} each time a plugin layer is rebuilt, so
     * service discovery stays current with the active plugin set.
     *
     * @param parentLayers parent {@link ModuleLayer}s for the service-loader layer
     *                     (typically boot + plugin)
     * @throws RuntimeException if no plugin-service-loader JAR can be located
     */
    public static void deployPluginServiceLoader(List<ModuleLayer> parentLayers) {
        if (System.getProperty(PATH_KEY) == null) {
            String artifactKey = System.getProperty(ARTIFACT_KEY, PLUGIN_SERVICE_LOADER_DIRECTORY);

            Path pluginServiceLoaderPath = resolvePluginServiceLoaderPath();

            findPluggableServiceLoaderJar(pluginServiceLoaderPath.toFile(),
                    artifactKey).ifPresentOrElse(pluggableServiceLoaderJar -> {
                System.setProperty(PATH_KEY, pluggableServiceLoaderJar);
                LOG.info("Found pluggable service loader jar: {}", pluggableServiceLoaderJar);
            }, () -> {
                throw new RuntimeException("""
                        No pluggable service loader found.
                        Ensure that PATH_KEY and ARTIFACT_KEY system properties are provided,
                        or that a pluggable service provider .jar file is provided at a discoverable location.
                        """);
            });
        }
        String pluginServiceLoaderPath = System.getProperty(PATH_KEY);

        ModuleLayer pluginServiceLoaderLayer = Layers.createModuleLayer(parentLayers,
                List.of(Path.of(pluginServiceLoaderPath)));
        ServiceLoader<PluginServiceLoader> pluggableServiceLoaderLoader =
                ServiceLoader.load(pluginServiceLoaderLayer, PluginServiceLoader.class);
        Optional<PluginServiceLoader> pluggableServiceLoaderOptional = pluggableServiceLoaderLoader.findFirst();
        pluggableServiceLoaderOptional.ifPresent(PluggableService::setPluggableServiceLoader);
    }

    /**
     * Resolves the directory containing the plugin-service-loader JAR by
     * probing, in order:
     *
     * <ol>
     *   <li>{@code <java.home>/plugin-service-loader/} — covers jpackage on
     *       macOS, Windows, and Linux as well as jlink launchers, since
     *       {@code java.home} points at the runtime root in all four cases:
     *       {@code Komet.app/Contents/runtime/Contents/Home} on macOS,
     *       {@code <InstallDir>\runtime} on Windows,
     *       {@code <install>/lib/runtime} on Linux,
     *       and the image root for a jlink launcher.</li>
     *   <li>{@code <parent-of-user.dir>/plugin-service-loader/} — jlink image
     *       launched from {@code bin/} on a developer workstation.</li>
     *   <li>{@code <user.dir>/target/plugin-service-loader/} — local Maven
     *       build.</li>
     * </ol>
     *
     * <p>If none of these exist, returns a reasonable default and logs every
     * attempted path so deployment misconfigurations surface in logs rather
     * than as opaque NPEs.
     *
     * @return the resolved directory, or a best-guess default that may not exist
     */
    private static Path resolvePluginServiceLoaderPath() {
        String javaHome = System.getProperty("java.home");
        String userDir = System.getProperty("user.dir");
        LOG.debug("Resolving plugin service loader path");
        LOG.debug("  java.home: {}", javaHome);
        LOG.debug("  user.dir: {}", userDir);
        LOG.debug("  jpackage.app-path: {}", System.getProperty("jpackage.app-path"));

        List<Path> candidates = pluginServiceLoaderCandidates(javaHome, userDir);

        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                LOG.info("Plugin Service Loader directory: {}", candidate.toAbsolutePath());
                return candidate;
            }
        }

        LOG.warn("Plugin service loader not found at any expected location. Tried:");
        for (Path candidate : candidates) {
            LOG.warn("  - {}", candidate.toAbsolutePath());
        }

        Path defaultPath = candidates.isEmpty()
                ? Path.of(PLUGIN_SERVICE_LOADER_DIRECTORY).toAbsolutePath()
                : candidates.get(0);
        LOG.warn("Using default path: {}", defaultPath.toAbsolutePath());
        return defaultPath;
    }

    /**
     * Computes the ordered list of candidate directories for the
     * plugin-service-loader, given {@code java.home} and {@code user.dir}
     * values. Pure function — visible for testing the cross-platform probe
     * order without mutating system properties.
     *
     * @param javaHome value of {@code java.home}, may be {@code null}/blank
     * @param userDir  value of {@code user.dir}, may be {@code null}
     * @return ordered list of candidate directories (never {@code null})
     */
    static List<Path> pluginServiceLoaderCandidates(String javaHome, String userDir) {
        List<Path> candidates = new ArrayList<>();
        if (javaHome != null && !javaHome.isBlank()) {
            candidates.add(Path.of(javaHome).resolve(PLUGIN_SERVICE_LOADER_DIRECTORY));
        }
        if (userDir != null && !userDir.isBlank()) {
            Path userDirPath = Path.of(userDir);
            Path parent = userDirPath.getParent();
            if (parent != null) {
                candidates.add(parent.resolve(PLUGIN_SERVICE_LOADER_DIRECTORY));
            }
            candidates.add(userDirPath.resolve("target").resolve(PLUGIN_SERVICE_LOADER_DIRECTORY));
        }
        return candidates;
    }

    /**
     * Initializes the plugin system, scanning {@code pluginDirectory} for
     * plugin JARs and activating the service-loader layer.
     *
     * <p>Must be called exactly once per JVM, before any code that relies on
     * {@link PluggableService#load(Class)} to see plugin-provided services.
     *
     * @param pluginDirectory directory containing plugin JARs
     * @throws IllegalStateException if the manager has already been initialized
     */
    public static void setPluginDirectory(Path pluginDirectory) {
        new IkeServiceManager(Set.of(new PluginDirectory("Standard plugins directory", pluginDirectory)));
    }

}
