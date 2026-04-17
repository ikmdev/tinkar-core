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
package dev.ikm.tinkar.plugin.service.loader;

import dev.ikm.tinkar.common.service.PluginServiceLoader;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.MutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Default {@link PluginServiceLoader} implementation. Delegates to
 * {@link ServiceLoader#load(ModuleLayer, Class)} against the {@link ModuleLayer}
 * that contains this class, so discovery spans the plugin layer (parent) and
 * the boot layer (grandparent).
 *
 * <p>This class must live in its own module, loaded into its own
 * {@link ModuleLayer} at runtime — it cannot be on the application's boot
 * modulepath. {@code IkeServiceManager} wires up the layer hierarchy during
 * startup.
 */
public class IkePluginServiceLoader implements PluginServiceLoader {
    private static final Logger LOG = LoggerFactory.getLogger(IkePluginServiceLoader.class);
    private final ReentrantLock lock = new ReentrantLock();
    private volatile ImmutableSet<ClassLoader> classLoaders;

    /**
     * Creates a new {@code IkePluginServiceLoader}. Instances are constructed
     * reflectively by {@link ServiceLoader} during plugin layer activation.
     */
    public IkePluginServiceLoader() {
    }

    /**
     * {@inheritDoc}
     *
     * <p>Resolves the {@link ModuleLayer} that contains this class, calls
     * {@link Module#addUses(Class)} on that module so dynamic discovery is
     * legal, then returns a {@link ServiceLoader} bound to the layer. Logs
     * every discovered implementation for diagnostic traceability.
     *
     * @param service the service class to load providers for
     * @param <S> the service type
     * @return a {@link ServiceLoader} that discovers providers in this layer
     *         and its ancestors
     */
    @Override
    public <S> ServiceLoader<S> loader(Class<S> service) {
        ensureUses(service);
        LOG.debug("Loading services for: {}", service.getName());
        ModuleLayer layer = IkePluginServiceLoader.class.getModule().getLayer();
        LOG.debug("Service loader using module layer with {} modules", layer.modules().size());

        ServiceLoader<S> serviceLoader = ServiceLoader.load(layer, service);

        int serviceCount = 0;
        for (S svc : serviceLoader) {
            serviceCount++;
            LOG.info("Discovered service implementation: {} for service: {}",
                    svc.getClass().getName(), service.getName());
        }

        if (serviceCount == 0) {
            LOG.warn("No implementations found for service: {} in layer with modules: {}",
                    service.getName(),
                    layer.modules().stream().map(Module::getName).collect(Collectors.joining(", ")));
        } else {
            LOG.info("Total {} implementation(s) found for service: {}", serviceCount, service.getName());
        }

        // Return a fresh loader since iteration above consumed it for logging.
        return ServiceLoader.load(layer, service);
    }

    /**
     * Adds the given service to the {@code uses} clause of this module if not
     * already present. Required because the plugin service architecture
     * discovers service interfaces dynamically at runtime — the static
     * {@code uses} declarations in {@code module-info} cannot anticipate every
     * service a plugin might provide.
     *
     * @param service the service class to register a dynamic {@code uses} for
     * @return {@code true} if {@code addUses} was invoked;
     *         {@code false} if the module already declared use of {@code service}
     */
    public boolean ensureUses(Class<?> service) {
        if (!this.getClass().getModule().canUse(service)) {
            this.getClass().getModule().addUses(service);
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Walks the class loaders of every module in this layer's parents
     * (lazily initialized and cached) and returns the first {@link Class}
     * found for {@code className}. Falls through to
     * {@link ClassNotFoundException} if no parent layer contains the class.
     *
     * @param className the fully qualified class name
     * @return the resolved {@link Class}
     * @throws ClassNotFoundException if no parent class loader can resolve the name
     */
    @Override
    public Class<?> forName(String className) throws ClassNotFoundException {
        if (classLoaders == null) {
            lock.lock();
            try {
                if (classLoaders == null) {
                    MutableSet<ClassLoader> classLoaderSet = Sets.mutable.empty();
                    classLoaderSet.add(IkePluginServiceLoader.class.getClassLoader());
                    for (ModuleLayer moduleLayer : this.getClass().getModule().getLayer().parents()) {
                        for (Module module : moduleLayer.modules()) {
                            if (module.getClassLoader() != null) {
                                classLoaderSet.add(module.getClassLoader());
                            }
                        }
                    }
                    classLoaders = classLoaderSet.toImmutableSet();
                }
            } finally {
                lock.unlock();
            }
        }
        for (ClassLoader classLoader : classLoaders) {
            try {
                return Class.forName(className, true, classLoader);
            } catch (ClassNotFoundException e) {
                // Try the next loader.
            }
        }
        throw new ClassNotFoundException(className);
    }
}
