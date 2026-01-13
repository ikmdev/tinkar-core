
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

/**
 * Defines the lifecycle phases for service startup and shutdown.
 * <p>
 * Services start in the order defined by this enum (top to bottom),
 * and shutdown in reverse order. This phase-based approach makes it
 * clear where services fit in the startup sequence without requiring
 * knowledge of numeric priority values.
 * </p>
 * <p>
 * Modeled after Maven's build lifecycle phases, this provides a
 * clear contract for service initialization order.
 * </p>
 * <p>
 * Services within the same phase may start in any order unless a
 * sub-priority is specified. Sub-priorities are integers from 0-99,
 * with lower values starting first.
 * </p>
 *
 * <h3>Command-Line Overrides</h3>
 * <p>
 * Service phases and sub-priorities can be overridden via system properties:
 * </p>
 * <ul>
 *   <li>{@code -Dservice.lifecycle.phase.MyServiceClass=INFRASTRUCTURE}</li>
 *   <li>{@code -Dservice.lifecycle.subpriority.MyServiceClass=10}</li>
 * </ul>
 * <p>
 * Use the simple class name (without package) for the property.
 * </p>
 */
public enum ServiceLifecyclePhase {

    /**
     * Infrastructure phase - executors, thread pools, core utilities.
     * <p>
     * Examples: ExecutorProvider, logging configuration, monitoring
     * </p>
     * <p>Base value: 0</p>
     */
    INFRASTRUCTURE(0),

    /**
     * Configuration phase - user selections, provider choices, settings.
     * <p>
     * This phase allows for interactive or programmatic configuration after
     * infrastructure is ready but before data services start. Group selections
     * for mutual exclusion groups are resolved during this phase.
     * </p>
     * <p>
     * Examples: Configuration UI, provider selection dialogs, settings managers
     * </p>
     * <p>Base value: 100</p>
     */
    CONFIGURATION(100),

    /**
     * Data storage initialization - database connections, file systems.
     * <p>
     * Examples: SpinedArrayProvider, MVStore, RocksDB initialization
     * </p>
     * <p>Base value: 200</p>
     */
    DATA_STORAGE(200),

    /**
     * Entity services - entity management, identity resolution, stamp management.
     * <p>
     * Services that provide access to entities, public IDs, descriptions, and stamps.
     * These services depend on data storage but are needed before indexing.
     * </p>
     * <p>
     * Examples: EntityService, PublicIdService, StampService, DefaultDescriptionForNidService
     * </p>
     * <p>Base value: 300</p>
     */
    ENTITIES(300),

    /**
     * Indexing services - search indexes, caches that depend on data storage.
     * <p>
     * Examples: Lucene indexer, cache managers
     * </p>
     * <p>Base value: 400</p>
     */
    INDEXING(400),

    /**
     * Data loading phase - bulk import operations, changeset processing.
     * <p>
     * This phase occurs after all data infrastructure (storage, entities, indexing)
     * is fully operational. Use this phase for heavyweight data loading operations
     * that write entities and require full service availability.
     * </p>
     * <p>
     * The DataLoadController singleton runs in this phase and provides a consistent
     * mechanism for loading protobuf changesets. All data providers (RocksDB, SpinedArray,
     * MVStore, Ephemeral) can configure the DataLoadController with files to import.
     * </p>
     * <p>
     * Examples: Initial data imports via DataLoadController, changeset loading,
     * database population from protobuf files
     * </p>
     * <p>Base value: 500</p>
     */
    DATA_LOAD(500),

    /**
     * Core business services - services that depend on data but not UI.
     * <p>
     * Examples: StampService, ConceptService, EntityService
     * </p>
     * <p>Base value: 600</p>
     */
    CORE_SERVICES(600),

    /**
     * Application services - higher-level business logic.
     * <p>
     * Examples: ReasonerService, ClassifierService
     * </p>
     * <p>Base value: 700</p>
     */
    APPLICATION_SERVICES(700),

    /**
     * UI and presentation services - anything requiring data to be fully loaded.
     * <p>
     * Examples: UI controllers, window managers, event dispatchers
     * </p>
     * <p>Base value: 800</p>
     */
    PRESENTATION(800);

    private final int baseValue;

    ServiceLifecyclePhase(int baseValue) {
        this.baseValue = baseValue;
    }

    /**
     * Returns the base numeric value for this phase.
     * <p>
     * This value is used internally for ordering. Services can specify
     * sub-priorities (0-99) which are added to this base value.
     * </p>
     *
     * @return base value for this phase
     */
    public int getBaseValue() {
        return baseValue;
    }
}