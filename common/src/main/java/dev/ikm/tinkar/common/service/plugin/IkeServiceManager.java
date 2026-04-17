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
     *   <li>jpackage app layout — {@code <Contents>/runtime/Contents/Home/plugin-service-loader/}
     *       (detected via {@code jpackage.app-path})</li>
     *   <li>jlink image layout — sibling of {@code bin/}, i.e.
     *       {@code <image-root>/plugin-service-loader/}</li>
     *   <li>Local Maven build — {@code <user.dir>/target/plugin-service-loader/}</li>
     * </ol>
     *
     * <p>If none of these exist, returns a reasonable default (image-root
     * sibling) and logs the attempted paths so deployment misconfigurations
     * surface in logs rather than as opaque NPEs.
     *
     * @return the resolved directory, or a best-guess default that may not exist
     */
    private static Path resolvePluginServiceLoaderPath() {
        LOG.debug("Resolving plugin service loader path");
        LOG.debug("  user.dir: {}", System.getProperty("user.dir"));
        LOG.debug("  jpackage.app-path: {}", System.getProperty("jpackage.app-path"));

        // 1. jpackage-installed application
        String jpackageAppPath = System.getProperty("jpackage.app-path");
        if (jpackageAppPath != null) {
            LOG.info("Detected jpackage.app-path for plugin service loader: {}", jpackageAppPath);
            Path appPath = Path.of(jpackageAppPath);

            // jpackage.app-path points to .../Contents/MacOS/<executable-name>.
            // Navigate up to Contents, then into runtime/Contents/Home/plugin-service-loader.
            Path macosDir = appPath.getParent();
            if (macosDir != null) {
                Path contentsDir = macosDir.getParent();
                if (contentsDir != null) {
                    Path pluginServiceLoaderPath = contentsDir.resolve("runtime")
                            .resolve("Contents")
                            .resolve("Home")
                            .resolve(PLUGIN_SERVICE_LOADER_DIRECTORY);

                    LOG.info("Checking jpackage plugin service loader path: {}", pluginServiceLoaderPath.toAbsolutePath());
                    LOG.info("  Path exists: {}", Files.exists(pluginServiceLoaderPath));

                    if (pluginServiceLoaderPath.toFile().exists()) {
                        LOG.info("Plugin Service Loader directory: {}", pluginServiceLoaderPath.toAbsolutePath());
                        return pluginServiceLoaderPath;
                    }
                }
            }
        }

        // 2. jlink image — launched from bin/, image root is parent of user.dir
        Path userDir = Path.of(System.getProperty("user.dir"));
        Path parent = userDir.getParent();

        if (parent != null) {
            Path jlinkImagePath = parent.resolve(PLUGIN_SERVICE_LOADER_DIRECTORY);
            if (jlinkImagePath.toFile().exists()) {
                LOG.info("Plugin Service Loader directory: {}", jlinkImagePath.toAbsolutePath());
                return jlinkImagePath;
            }
        }

        // 3. Local Maven build — target/plugin-service-loader/
        Path localPluginServiceLoaderPath = userDir.resolve("target").resolve(PLUGIN_SERVICE_LOADER_DIRECTORY);
        if (localPluginServiceLoaderPath.toFile().exists()) {
            LOG.info("Plugin Service Loader directory: {}", localPluginServiceLoaderPath.toAbsolutePath());
            return localPluginServiceLoaderPath;
        }

        // None found — log attempted paths before returning a best-guess default.
        LOG.warn("Plugin service loader not found at any expected location. Tried:");
        if (jpackageAppPath != null) {
            LOG.warn("  - jpackage: <Contents>/runtime/Contents/Home/{}", PLUGIN_SERVICE_LOADER_DIRECTORY);
        }
        if (parent != null) {
            LOG.warn("  - Jlink image: {}", parent.resolve(PLUGIN_SERVICE_LOADER_DIRECTORY).toAbsolutePath());
        }
        LOG.warn("  - Local build: {}", localPluginServiceLoaderPath.toAbsolutePath());

        Path defaultPath = parent != null ? parent.resolve(PLUGIN_SERVICE_LOADER_DIRECTORY) :
                userDir.resolve(PLUGIN_SERVICE_LOADER_DIRECTORY);
        LOG.warn("Using default path: {}", defaultPath.toAbsolutePath());
        return defaultPath;
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
