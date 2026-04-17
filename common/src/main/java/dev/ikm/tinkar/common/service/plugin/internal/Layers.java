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

import dev.ikm.tinkar.common.service.plugin.IkeServiceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Boot-time infrastructure that discovers plugin JARs on the filesystem and
 * materializes them as a JPMS {@link ModuleLayer} parented by the boot layer.
 *
 * <p>Current behavior: all plugin JARs from all configured directories are
 * resolved into a single {@link ModuleLayer}, sharing one class loader.
 * Collision handling is diagnostic (logs + exceptions from
 * {@link Configuration#resolve}), not OSGi-style versioned isolation.
 *
 * <p>Conflict detection (split packages, duplicate artifact IDs at different
 * versions) is tracked as follow-on work; see IKE-Network/ike-issues#149.
 */
public class Layers {
    private static final Logger LOG = LoggerFactory.getLogger(Layers.class);
    private static final Pattern PLUGIN_ARTIFACT_PATTERN =
            Pattern.compile("(.*?)\\-(\\d[\\d+\\-_A-Za-z\\.]*?)\\.(jar|zip|tar|tar\\.gz)");

    /** Temporary-directory prefix for unpacked plugins. */
    public static final String TINKAR_PLUGINS_TEMP_DIR = "tinkar-plugins";

    /** Layer name reported for the JPMS boot layer. */
    public static final String BOOT_LAYER = "boot-layer";

    /** Layer name reported for the plugin layer. */
    public static final String PLUGIN_LAYER = "plugin-layer";

    private static final List<ModuleLayer> PLUGIN_PARENT_LAYER_AS_LIST = List.of(ModuleLayer.boot());

    /** The actual module layers by name. */
    private final CopyOnWriteArraySet<PluginNameAndModuleLayer> moduleLayers = new CopyOnWriteArraySet<>();
    private final PluginNameAndModuleLayer bootLayer;

    /**
     * Temporary directory where all plug-ins will be copied to. Modules will
     * be sourced from there, allowing removal of plug-ins by deleting their
     * original directory.
     */
    private final Path pluginsWorkingDir;

    /** All configured directories potentially containing plug-ins. */
    private final Set<PluginDirectory> pluginsDirectories;

    /**
     * Creates a new {@code Layers} manager, scanning each supplied directory
     * for plugin JARs and constructing a {@link ModuleLayer} from what it finds.
     *
     * @param pluginsDirectories directories to scan
     * @throws RuntimeException if a working temporary directory cannot be created
     */
    public Layers(Set<PluginDirectory> pluginsDirectories) {
        this.bootLayer = new PluginNameAndModuleLayer(BOOT_LAYER, ModuleLayer.boot());
        this.moduleLayers.add(bootLayer);
        this.pluginsDirectories = Collections.unmodifiableSet(pluginsDirectories);

        try {
            this.pluginsWorkingDir = Files.createTempDirectory(TINKAR_PLUGINS_TEMP_DIR);

            if (!pluginsDirectories.isEmpty()) {
                for (PluginDirectory pluginDirectory : pluginsDirectories) {
                    handlePluginComponent(pluginDirectory);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the {@link ModuleLayer}s constructed so far, in insertion order,
     * starting with the boot layer.
     *
     * @return the list of {@link ModuleLayer}s currently tracked
     */
    public List<ModuleLayer> getModuleLayers() {
        return moduleLayers.stream().map(PluginNameAndModuleLayer::moduleLayer).toList();
    }

    /**
     * Scans the given {@link PluginDirectory} for plugin JARs and appends
     * a new {@link ModuleLayer} to this manager's layer list if any are found.
     * Re-deploys the {@link dev.ikm.tinkar.common.service.PluginServiceLoader}
     * against the refreshed layer list so dynamic service discovery stays
     * current.
     *
     * @param pluginDirectory the directory to scan
     * @throws IOException if the directory cannot be read
     */
    private void handlePluginComponent(PluginDirectory pluginDirectory) throws IOException {
        LOG.info("Processing plugin directory: {}", pluginDirectory.directory().toAbsolutePath());

        // Reset to boot layer only; plugin layer is rebuilt below.
        Layers.this.moduleLayers.clear();
        Layers.this.moduleLayers.add(bootLayer);
        List<Path> pluginPathEntries = getPluginPathEntries(pluginDirectory);

        LOG.info("Found {} plugin JAR files in directory", pluginPathEntries.size());
        for (Path pluginPath : pluginPathEntries) {
            LOG.info("  - Plugin JAR: {}", pluginPath.toAbsolutePath());
        }

        if (pluginPathEntries.isEmpty()) {
            LOG.warn("No plugin JARs found in: {}", pluginDirectory.directory().toAbsolutePath());
            return;
        }

        ModuleLayer pluginModuleLayer = createModuleLayer(PLUGIN_PARENT_LAYER_AS_LIST, pluginPathEntries);
        PluginNameAndModuleLayer pluginNameAndModuleLayer =
                new PluginNameAndModuleLayer(pluginDirectory.name(), pluginModuleLayer);
        moduleLayers.add(pluginNameAndModuleLayer);

        LOG.info("Created plugin module layer '{}' with {} modules:",
                pluginDirectory.name(), pluginModuleLayer.modules().size());
        for (Module module : pluginModuleLayer.modules()) {
            LOG.info("  - Module: {} (descriptor: {})",
                    module.getName(),
                    module.getDescriptor() != null ? module.getDescriptor().toNameAndVersion() : "none");
        }

        // Refresh the dedicated plugin-service-loader layer so ServiceLoader
        // sees providers in the newly-created plugin layer.
        IkeServiceManager.deployPluginServiceLoader(
                moduleLayers.stream().map(PluginNameAndModuleLayer::moduleLayer).toList());
    }

    private static List<Path> getPluginPathEntries(PluginDirectory pluginDirectory) {
        return getPluginPathEntries(pluginDirectory.directory().toFile(), new ArrayList<>());
    }

    private static List<Path> getPluginPathEntries(File directory, List<Path> pluginPathEntries) {
        File[] files = directory.listFiles();
        if (files == null) {
            LOG.warn("Cannot list files in directory: {}", directory);
            return pluginPathEntries;
        }

        for (File jarFile : files) {
            if (jarFile.getName().endsWith(".jar")) {
                pluginPathEntries.add(jarFile.toPath());
                LOG.debug("Found plugin JAR: {}", jarFile.getName());
            } else if (jarFile.isDirectory()) {
                getPluginPathEntries(jarFile, pluginPathEntries);
            }
        }
        return pluginPathEntries;
    }

    /**
     * Derives a plugin name from the JAR filename, matching
     * {@code <artifactId>-<version>.<ext>}.
     *
     * <p>Currently returns only the artifact ID as the plugin key, intentionally
     * collapsing different versions of the same artifact to the same name.
     * Version-aware keying is tracked with conflict detection (issue #149).
     *
     * @param pluginDirectory the containing plugin watch directory (retained for future version-aware keys)
     * @param path                 the path of the plugin artifact
     * @return the artifact ID, or empty if the filename does not match the expected pattern
     */
    private Optional<String> pluginName(PluginDirectory pluginDirectory, Path path) {
        Matcher matcher = PLUGIN_ARTIFACT_PATTERN.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String pluginArtifactId = matcher.group(1);
        return Optional.of(pluginArtifactId);
    }

    /**
     * Creates a {@link ModuleLayer} containing the modules discovered at the
     * given module-path entries, parented by the supplied layers. Logs the
     * discovered modules, resolved modules, and provided services for
     * diagnostic visibility.
     *
     * @param parentLayers       the parent {@link ModuleLayer}s; the new layer
     *                           is reachable from these
     * @param modulePathEntries  JARs or module directories to resolve
     * @return the newly created {@link ModuleLayer}, or {@code null} if no
     *         modules are discovered at the given paths
     * @throws RuntimeException if resolution fails; the exception message lists
     *                          parent-layer modules to aid diagnosis
     */
    public static ModuleLayer createModuleLayer(List<ModuleLayer> parentLayers, List<Path> modulePathEntries) {
        LOG.info("Creating module layer from {} path entries", modulePathEntries.size());
        for (Path entry : modulePathEntries) {
            LOG.info("  Module path entry: {}", entry);
        }

        ClassLoader scl = ClassLoader.getSystemClassLoader();

        ModuleFinder finder = ModuleFinder.of(modulePathEntries.toArray(Path[]::new));

        Set<String> roots = finder.findAll()
                .stream()
                .map(m -> m.descriptor().name())
                .collect(Collectors.toSet());

        LOG.info("ModuleFinder discovered {} modules:", roots.size());
        for (String moduleName : roots) {
            LOG.info("  - Module: {}", moduleName);
        }

        if (roots.isEmpty()) {
            LOG.error("No modules found in the provided paths! Check if JARs have valid module-info.class");
            return null;
        }

        try {
            Configuration appConfig = Configuration.resolve(
                    finder,
                    parentLayers.stream().map(ModuleLayer::configuration).collect(Collectors.toList()),
                    ModuleFinder.of(),
                    roots);

            LOG.info("Module configuration resolved with {} modules", appConfig.modules().size());
            for (var resolvedModule : appConfig.modules()) {
                LOG.info("  - Resolved module: {}", resolvedModule.name());
                resolvedModule.reference().descriptor().provides().forEach(provides ->
                        LOG.info("    Provides service: {} with implementations: {}",
                                provides.service(),
                                String.join(", ", provides.providers())));
            }

            ModuleLayer layer = ModuleLayer.defineModulesWithOneLoader(appConfig, parentLayers, scl).layer();
            LOG.info("Created module layer with {} modules", layer.modules().size());

            return layer;
        } catch (Exception e) {
            LOG.error("Failed to create module layer", e);
            LOG.error("Available modules in parent layers:");
            for (ModuleLayer parentLayer : parentLayers) {
                LOG.error("  Parent layer has {} modules:", parentLayer.modules().size());
                for (Module m : parentLayer.modules()) {
                    LOG.error("    - {}", m.getName());
                }
            }
            throw new RuntimeException("Failed to create module layer for plugins", e);
        }
    }

    /**
     * Copies a plugin artifact into the given target directory, preserving the
     * filename. Supports {@code .jar} today; archive formats ({@code .zip},
     * {@code .tar}, {@code .tar.gz}) are reserved for future work.
     *
     * @param pluginArtifact path of the source artifact to copy
     * @param targetDir      directory to copy into (created if absent)
     * @return a single-element list containing {@code targetDir}
     * @throws UnsupportedOperationException if the file extension is not {@code .jar}
     * @throws RuntimeException              if the copy fails
     */
    private List<Path> copyPluginArtifact(Path pluginArtifact, Path targetDir) {
        String fileName = pluginArtifact.getFileName().toString();
        if (fileName.endsWith(".jar")) {
            Path dest = targetDir.resolve(fileName);
            try {
                Files.createDirectories(dest.getParent());
                Files.copy(pluginArtifact, dest, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if (fileName.endsWith(".zip")) {
            throw new UnsupportedOperationException("Can't handle .zip");
        } else if (fileName.endsWith(".tar")) {
            throw new UnsupportedOperationException("Can't handle .tar");
        } else if (fileName.endsWith(".tar.gz")) {
            throw new UnsupportedOperationException("Can't handle .tar.gz");
        }

        return List.of(targetDir);
    }
}
