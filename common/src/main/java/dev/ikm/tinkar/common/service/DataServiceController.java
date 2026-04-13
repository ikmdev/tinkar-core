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


import dev.ikm.tinkar.common.validation.ValidationRecord;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.ImmutableMap;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Interface for controllers that manage user-selectable data service providers.
 * <p>This interface extends the capabilities of {@link ProviderController} by adding
 * UI-specific methods for data source selection, configuration, and validation.
 * It is intended for controllers that need to be selected by users through a UI
 * (e.g., "Open Rocks KB", "Load Ephemeral Store", etc.).
 *
 * <h2>Relationship with ProviderController</h2>
 * <p>Controllers implementing this interface should extend {@link ProviderController},
 * which provides the core lifecycle management and service discovery capabilities:
 * <ul>
 *   <li>{@link ProviderController#serviceClasses()} - declares what service interfaces the provider implements</li>
 *   <li>{@link ProviderController#provider()} - provides access to the provider instance</li>
 *   <li>Lifecycle management (startup, shutdown, cleanup)</li>
 * </ul>
 *
 * <h2>When to Use</h2>
 * <ul>
 *   <li><b>Use DataServiceController</b> for user-selectable data sources that need UI integration
 *       (e.g., RocksDB, MVStore, Ephemeral providers)</li>
 *   <li><b>Use ProviderController only</b> for automatic background services that don't need
 *       user selection (e.g., SearchProvider, ExecutorProvider)</li>
 * </ul>
 *
 * @param <P> the service interface type provided by this controller
 * @see ProviderController
 * @see ServiceLifecycle
 */
public interface DataServiceController<P> {

    /**
     * Returns the properties necessary to configure this service.
     *
     * @return an immutable map of configuration properties and their current values
     */
    default ImmutableMap<DataServiceProperty, String> providerProperties() {
        return Maps.immutable.empty();
    }

    /**
     * Retrieves the value of a data service property by key.
     *
     * @param propertyKey the property to look up
     * @return an {@link Optional} containing the property value, or empty if not set
     */
    default Optional<String> getDataServiceProperty(DataServiceProperty propertyKey) {
        return Optional.ofNullable(providerProperties().get(propertyKey));
    }

    /**
     * Sets a data service configuration property.
     *
     * @param key   the property key
     * @param value the property value
     * @throws UnsupportedOperationException if this controller does not support property modification
     */
    default void setDataServiceProperty(DataServiceProperty key, String value) {
        throw new UnsupportedOperationException();
    }

    /**
     * Validates a data service property value against the given target.
     *
     * @param dataServiceProperty the property to validate
     * @param value               the proposed value
     * @param target              the validation target context
     * @return an array of validation records, empty if validation passes
     */
    default ValidationRecord[] validate(DataServiceProperty dataServiceProperty, Object value, Object target) {
        return new ValidationRecord[] {};
    }

    /**
     * Returns the available data URI options for this provider, typically
     * discovered by scanning the user's Solor directory for valid data locations.
     *
     * @return a list of available data URI options
     */
    default List<DataUriOption> providerOptions() {
        List<DataUriOption> dataUriOptions = new ArrayList<>();
        File rootFolder = new File(System.getProperty("user.home"), "Solor");
        if (!rootFolder.exists()) {
            rootFolder.mkdirs();
        }
        for (File f : rootFolder.listFiles()) {
            if (f.isDirectory()) {
                String[] children = f.list((dir, name) -> isValidDataLocation(name));
                if (children.length != 0) {
                    dataUriOptions.add(new DataUriOption(f.getName(), f.toURI()));
                }
            }
        }
        return dataUriOptions;
    }

    /**
     * Sets the data URI option for the provider.
     * @param option the data URI option
     */
    void setDataUriOption(DataUriOption option);

    /**
     * Checks if a given name represents a valid data location for this provider.
     * @param name the name to check
     * @return true if valid, false otherwise
     */
    default boolean isValidDataLocation(String name) {
        return true;
    }

    /**
     * Returns the display name for this controller, used for user-facing identification
     * in menus and status displays.
     *
     * @return the human-readable controller name
     */
    String controllerName();

    /**
     * Returns whether the data service managed by this controller is currently running.
     *
     * @return {@code true} if the service is running, {@code false} otherwise
     */
    boolean running();

    /**
     * Starts the data service managed by this controller.
     */
    void start();

    /**
     * Stops the data service managed by this controller.
     */
    void stop();

    /**
     * Persists any in-memory state of the data service to durable storage.
     */
    void save();

    /**
     * Reloads the data service, re-reading configuration and reinitializing internal state.
     */
    void reload();

    // Note: serviceClasses() and provider() are inherited from ProviderController base class

    /**
     * Returns whether this controller is currently loading a new database from file.
     *
     * @return {@code true} if loading a new database from file, {@code false} otherwise
     */
    default boolean loading() {
        return false;
    }

}
