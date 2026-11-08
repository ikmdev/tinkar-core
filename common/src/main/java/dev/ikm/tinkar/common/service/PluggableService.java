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
package dev.ikm.tinkar.common.service;

import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PluggablePluggableServiceLoader is a utility class used to load service providers using the {@link ServiceLoader} mechanism.
 */
public class PluggableService<S> {
    private static AtomicReference<PluginServiceLoader> pluggableServiceAtomic = new AtomicReference<>();
    private static ConcurrentHashMap<Class, Object> cachedServices = new ConcurrentHashMap();

    /**
     * Loads a PluggableService object for the given service class.
     *
     * @param service the class representing the service to be loaded
     * @param <S>     the type of the service to be loaded
     * @return a PluggableService object for the given service class
     * @throws RuntimeException if there is an error accessing the load method or with the reflection
     */
    public static <S> ServiceLoader<S> load(Class<S> service) {
        if (pluggableServiceAtomic.get() == null) {
            if (!PluggableService.class.getModule().canUse(service)) {
                PluggableService.class.getModule().addUses(service);
            }
            return ServiceLoader.load(service);
        }
        try {
            return pluggableServiceAtomic.get().loader(service);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets the service loader for the PluggablePluggableServiceLoader class.
     *
     * @param pluginServiceLoader the service loader object to be set
     * @throws RuntimeException if the load method cannot be accessed or if there is a problem with the reflection
     */
    public static void setPluggableServiceLoader(PluginServiceLoader pluginServiceLoader) {
        pluggableServiceAtomic.set(pluginServiceLoader);
    }

    /**
     * A simplified method to load when there should only be one and only one instance of a service.
     * @param service
     * @return
     * @param <S>
     */
    public static <S> S first(Class<S> service) {
        return (S) cachedServices.computeIfAbsent(service, serviceClass -> {
            ServiceLoader<?> loader = PluggableService.load(service);
            if (loader.stream().count() > 1) {
                throw new IllegalStateException("More than one pluggable service loaded for: " + service);
            }
            return loader.findFirst().get();
        });
     }

    /**
     * The class of a name, through the plugin class loader the pluggable service loader provides.
     * A name no class has is looked up in the {@link RenamedClasses} providers, so a name stored
     * before its class was renamed still resolves.
     *
     * @param className the binary name
     * @return the class
     * @throws ClassNotFoundException when no class has the name, now or after a rename
     */
     public static Class<?> forName(String className) throws ClassNotFoundException {
         try {
             return resolve(className);
         } catch (ClassNotFoundException missing) {
             // A name stored before its class was renamed, as a saved layout keeps a factory's name.
             for (RenamedClasses renamed : load(RenamedClasses.class)) {
                 Optional<String> current = renamed.current(className);
                 if (current.isPresent() && !current.get().equals(className)) {
                     return resolve(current.get());
                 }
             }
             throw missing;
         }
     }

     private static Class<?> resolve(String className) throws ClassNotFoundException {
         if (pluggableServiceAtomic.get() == null) {
             return Class.forName(className);
         }
         return pluggableServiceAtomic.get().forName(className);
     }
}
