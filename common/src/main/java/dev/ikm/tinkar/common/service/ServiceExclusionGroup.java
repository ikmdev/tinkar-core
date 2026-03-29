
package dev.ikm.tinkar.common.service;

/**
 * Defines mutually exclusive service groups for the service lifecycle system.
 * <p>Services belonging to the same exclusion group are mutually exclusive - only
 * ONE service from each group will be started during application initialization.
 * <p>This enum provides type safety and clear documentation of all architectural
 * service boundaries in the system.
 *
 * <p><b>Selection Priority</b></p>
 * <ol>
 *   <li>Command-line system property: {@code -Dservice.lifecycle.group.EXECUTOR_PROVIDER=KometExecutorProviderLifecycle}</li>
 *   <li>Programmatic selection via {@link ServiceLifecycleManager#selectServiceForGroup}</li>
 *   <li>User selection callback</li>
 *   <li>Automatic selection based on {@link ServiceLifecycle#shouldActivate()}</li>
 *   <li>First service by priority order (fallback)</li>
 * </ol>
 *
 * <p><b>Adding New Groups</b></p>
 * <p>If your plugin requires a new exclusion group, add it to this enum with
 * clear documentation. This ensures type safety and prevents conflicts.
 */
public enum ServiceExclusionGroup {

    /**
     * Executor/thread pool providers.
     * <p>     * Mutually exclusive executor implementations:
     * <ul>
     *   <li><b>ExecutorProvider</b> - Headless/logging-based execution (testing, servers)</li>
     *   <li><b>KometExecutorProvider</b> - JavaFX-aware execution with UI integration</li>
     * </ul>
     * <p>     * Only one executor provider should be active. The choice is typically determined
     * by whether JavaFX is available and initialized.
     */
    EXECUTOR_PROVIDER,

    /**
     * Data storage/persistence providers.
     * <p>     * Mutually exclusive storage implementations:
     * <ul>
     *   <li><b>SpinedArrayProvider</b> - Memory-based storage with persistence</li>
     *   <li><b>MVStoreProvider</b> - H2 MVStore-based storage</li>
     *   <li><b>RocksDBProvider</b> - RocksDB-based storage</li>
     *   <li><b>EphemeralProvider</b> - In-memory only (testing)</li>
     * </ul>
     * <p>     * Only one data storage provider should be active. Selection is typically
     * based on performance requirements, memory constraints, and persistence needs.
     */
    DATA_PROVIDER,

    /**
     * Search/indexing engine providers.
     * <p>     * Mutually exclusive search implementations:
     * <ul>
     *   <li><b>LuceneSearchProvider</b> - Apache Lucene-based full-text search</li>
     *   <li><b>SimpleSearchProvider</b> - Basic in-memory search (testing)</li>
     * </ul>
     * <p>     * Only one search provider should be active. Selection is typically based
     * on search complexity requirements and performance needs.
     */
    SEARCH_ENGINE,

    /**
     * Caching strategy providers.
     * <p>     * Mutually exclusive caching implementations:
     * <ul>
     *   <li><b>CaffeineProvider</b> - Caffeine-based caching</li>
     *   <li><b>NoCacheProvider</b> - No caching (testing, debugging)</li>
     * </ul>
     */
    CACHE_PROVIDER;

    /**
     * Returns the group name for use in system properties and logging.
     * <p>     * System property format: {@code -Dservice.lifecycle.group.<groupName>=<ServiceClassName>}
     *
     * @return the group name (same as enum constant name)
     */
    public String getGroupName() {
        return name();
    }
}