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
 * Core service infrastructure for the Tinkar architecture.
 * <p>
 * This package provides the foundation for service discovery, lifecycle management, and plugin integration
 * in Tinkar applications. It enables a flexible, extensible architecture where services can be dynamically
 * discovered, configured, and managed through their complete lifecycle.
 * </p>
 *
 * <h2>Package Organization</h2>
 * <p>
 * This package is organized into several functional areas:
 * </p>
 * <ul>
 *   <li><b>Service Lifecycle Management</b> - Ordered startup and shutdown of application services</li>
 *   <li><b>Plugin System</b> - Dynamic loading of services from plugin modules</li>
 *   <li><b>Domain Services</b> - Core Tinkar services (data, executors, caching)</li>
 *   <li><b>Service Controllers</b> - Configuration and initialization of services</li>
 *   <li><b>Utilities</b> - Supporting classes for service management</li>
 * </ul>
 *
 * <h2>Service Lifecycle Management</h2>
 * <p>
 * The service lifecycle framework provides Maven-style phased startup and shutdown of services,
 * ensuring proper initialization order and clean resource management.
 * </p>
 *
 * <h3>Core Concepts</h3>
 * <dl>
 *   <dt>{@link dev.ikm.tinkar.common.service.ServiceLifecycle}</dt>
 *   <dd>Interface for services that participate in lifecycle management. Services implement
 *       {@code startup()} and {@code shutdown()} methods that are called at appropriate times.</dd>
 *
 *   <dt>{@link dev.ikm.tinkar.common.service.ServiceLifecyclePhase}</dt>
 *   <dd>Defines the standard lifecycle phases: INFRASTRUCTURE, DATA_STORAGE, INDEXING,
 *       CORE_SERVICES, APPLICATION_SERVICES, and PRESENTATION. Services start in phase order
 *       and shutdown in reverse order.</dd>
 *
 *   <dt>{@link dev.ikm.tinkar.common.service.LifecyclePhase}</dt>
 *   <dd>Annotation for declaratively specifying a service's lifecycle phase and sub-priority.</dd>
 *
 *   <dt>{@link dev.ikm.tinkar.common.service.ServiceLifecycleManager}</dt>
 *   <dd>Central manager that discovers, orders, and controls service startup and shutdown.
 *       Accessed via {@code ServiceLifecycleManager.get()}.</dd>
 * </dl>
 *
 * <h3>Quick Start - Implementing a Lifecycle-Managed Service</h3>
 * <pre>{@code
 * @LifecyclePhase(ServiceLifecyclePhase.DATA_STORAGE)
 * public class MyDatabaseService implements ServiceLifecycle {
 *
 *     private Connection connection;
 *
 *     @Override
 *     public void startup() throws Exception {
 *         LOG.info("Starting database service");
 *         connection = DriverManager.getConnection(DB_URL);
 *     }
 *
 *     @Override
 *     public void shutdown() {
 *         LOG.info("Shutting down database service");
 *         try {
 *             if (connection != null) {
 *                 connection.close();
 *             }
 *         } catch (SQLException e) {
 *             LOG.error("Error closing connection", e);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>
 * Register in {@code module-info.java}:
 * </p>
 * <pre>{@code
 * provides ServiceLifecycle with MyDatabaseService;
 * }</pre>
 *
 * <h3>Application Integration</h3>
 * <p>
 * In your application's startup sequence:
 * </p>
 * <pre>{@code
 * public class Application {
 *     public void init() {
 *         // 1. Initialize plugin system (if using plugins)
 *         PluginLoader.initialize();
 *
 *         // 2. Discover all lifecycle-managed services
 *         ServiceLifecycleManager.get().discoverServices();
 *
 *         // 3. (Optional) For mutually exclusive services, make selection
 *         ServiceLifecycleManager.get().selectServiceForGroup(
 *             "DATA_PROVIDER", SpinedArrayProvider.class);
 *
 *         // 4. Start all services in phase order
 *         ServiceLifecycleManager.get().startServices();
 *     }
 *
 *     public void shutdown() {
 *         // Shutdown all services in reverse order
 *         ServiceLifecycleManager.get().shutdownServices();
 *     }
 * }
 * }</pre>
 *
 * <h3>Mutual Exclusion Groups</h3>
 * <p>
 * When multiple implementations of a service exist and only ONE should be active
 * (e.g., choosing between different database providers), use mutual exclusion groups:
 * </p>
 * <pre>{@code
 * @LifecyclePhase(ServiceLifecyclePhase.DATA_STORAGE)
 * public class SpinedArrayProvider implements PrimitiveDataService, ServiceLifecycle {
 *
 *     @Override
 *     public String getMutualExclusionGroup() {
 *         return "DATA_PROVIDER";
 *     }
 *
 *     // ... lifecycle methods
 * }
 *
 * @LifecyclePhase(ServiceLifecyclePhase.DATA_STORAGE)
 * public class MVStoreProvider implements PrimitiveDataService, ServiceLifecycle {
 *
 *     @Override
 *     public String getMutualExclusionGroup() {
 *         return "DATA_PROVIDER";  // Same group = mutually exclusive
 *     }
 *
 *     // ... lifecycle methods
 * }
 * }</pre>
 *
 * <p>
 * Selection can be made programmatically, via user interaction, or command-line:
 * </p>
 * <pre>{@code
 * // Programmatic (e.g., in tests)
 * ServiceLifecycleManager.get().selectServiceForGroup(
 *     "DATA_PROVIDER", SpinedArrayProvider.class);
 *
 * // Command-line
 * java -Dservice.lifecycle.group.DATA_PROVIDER=SpinedArrayProvider -jar app.jar
 *
 * // User interaction (callback)
 * ServiceLifecycleManager.get().setServiceSelectionCallback((groupName, options) -> {
 *     // Show UI dialog, return user's choice
 *     return promptUser(groupName, options);
 * });
 * }</pre>
 *
 * <h3>Conditional Activation</h3>
 * <p>
 * Services can control whether they should activate during a particular startup:
 * </p>
 * <pre>{@code
 * public class OptionalFeatureService implements ServiceLifecycle {
 *
 *     @Override
 *     public boolean shouldActivate() {
 *         // Only activate if feature flag is enabled
 *         return Boolean.getBoolean("feature.optional.enabled");
 *     }
 *
 *     // ... lifecycle methods
 * }
 * }</pre>
 *
 * <h3>Command-Line Configuration</h3>
 * <p>
 * All lifecycle configuration can be overridden at runtime:
 * </p>
 * <pre>
 * # Override service phase
 * -Dservice.lifecycle.phase.MyService=INFRASTRUCTURE
 *
 * # Override sub-priority within phase (0-99)
 * -Dservice.lifecycle.subpriority.MyService=10
 *
 * # Select specific service for mutual exclusion group
 * -Dservice.lifecycle.group.DATA_PROVIDER=SpinedArrayProvider
 *
 * # Enable verbose logging
 * -Dservice.lifecycle.log.verbose=true
 * </pre>
 *
 * <h3>Lifecycle Phases in Detail</h3>
 * <dl>
 *   <dt>INFRASTRUCTURE (0-99)</dt>
 *   <dd>Thread pools, executors, logging, monitoring. Services that other services depend on.</dd>
 *
 *   <dt>DATA_STORAGE (100-199)</dt>
 *   <dd>Database connections, file systems, primitive data providers. Core data access layer.</dd>
 *
 *   <dt>INDEXING (200-299)</dt>
 *   <dd>Search indexes, caches that depend on data being available.</dd>
 *
 *   <dt>CORE_SERVICES (300-399)</dt>
 *   <dd>Business logic services that depend on data but not UI (EntityService, StampService).</dd>
 *
 *   <dt>APPLICATION_SERVICES (400-499)</dt>
 *   <dd>Higher-level application logic (reasoners, classifiers, validators).</dd>
 *
 *   <dt>PRESENTATION (500-599)</dt>
 *   <dd>UI services, window managers, anything requiring fully initialized data.</dd>
 * </dl>
 *
 * <h2>Plugin System</h2>
 * <p>
 * The plugin system enables dynamic loading of services from external JAR files using
 * Java's Module Layer mechanism.
 * </p>
 *
 * <h3>Core Plugin Classes</h3>
 * <dl>
 *   <dt>{@link dev.ikm.tinkar.common.service.PluggableService}</dt>
 *   <dd>Primary API for loading services. Transparently uses either standard {@link java.util.ServiceLoader}
 *       or plugin-enhanced loading if plugins are configured.</dd>
 *
 *   <dt>{@link dev.ikm.tinkar.common.service.PluginServiceLoader}</dt>
 *   <dd>Interface for plugin-aware service loaders. Implementations handle module layer creation
 *       and class loading from plugin JARs.</dd>
 * </dl>
 *
 * <h3>Using PluggableService</h3>
 * <pre>{@code
 * // Load all implementations of a service
 * ServiceLoader<MyService> loader = PluggableService.load(MyService.class);
 * for (MyService service : loader) {
 *     // Use service
 * }
 *
 * // Get the first (or only) implementation
 * MyService service = PluggableService.first(MyService.class);
 *
 * // Load a class by name (using plugin classloader if configured)
 * Class<?> clazz = PluggableService.forName("com.example.MyClass");
 * }</pre>
 *
 * <h3>Plugin Directory Structure</h3>
 * <pre>
 * plugins/
 * ├── my-plugin/
 * │   ├── my-plugin-1.0.0.jar
 * │   └── dependency-1.jar
 * ├── another-plugin/
 * │   └── another-plugin-2.0.0.jar
 * └── ...
 * </pre>
 *
 * <h2>Domain Services</h2>
 * <p>
 * Core Tinkar services provided by this package:
 * </p>
 *
 * <h3>Data Services</h3>
 * <dl>
 *   <dt>{@link dev.ikm.tinkar.common.service.PrimitiveData}</dt>
 *   <dd>Static accessor for the active primitive data service. Entry point for data operations.</dd>
 *
 *   <dt>{@link dev.ikm.tinkar.common.service.PrimitiveDataService}</dt>
 *   <dd>Interface for low-level data storage and retrieval. Implemented by providers like
 *       SpinedArrayProvider, MVStoreProvider.</dd>
 *
 *   <dt>{@link dev.ikm.tinkar.common.service.PrimitiveDataRepair}</dt>
 *   <dd>Interface for data repair operations (merge, erase) - extends PrimitiveDataService.</dd>
 *
 *   <dt>{@link dev.ikm.tinkar.common.service.PublicIdService}</dt>
 *   <dd>Service for resolving public identifiers to native IDs.</dd>
 *
 *   <dt>{@link dev.ikm.tinkar.common.service.NidGenerator}</dt>
 *   <dd>Interface for generating native identifiers (NIDs).</dd>
 * </dl>
 *
 * <h3>Executor Services</h3>
 * <dl>
 *   <dt>{@link dev.ikm.tinkar.common.service.TinkExecutor}</dt>
 *   <dd>Static accessor for thread pools and executors.</dd>
 *
 *   <dt>{@link dev.ikm.tinkar.common.service.ExecutorService}</dt>
 *   <dd>Interface providing various thread pools (blocking, non-blocking, ForkJoin, scheduled, IO).</dd>
 *
 *   <dt>{@link dev.ikm.tinkar.common.service.ExecutorController}</dt>
 *   <dd>Interface for controlling executor lifecycle (create, stop).</dd>
 *
 *   <dt>{@link dev.ikm.tinkar.common.service.TrackingCallable}</dt>
 *   <dd>Callable wrapper for tracking task execution.</dd>
 *
 *   <dt>{@link dev.ikm.tinkar.common.service.TrackingListener}</dt>
 *   <dd>Listener interface for monitoring tracked tasks.</dd>
 * </dl>
 *
 * <h3>Other Services</h3>
 * <dl>
 *   <dt>{@link dev.ikm.tinkar.common.service.CachingService}</dt>
 *   <dd>Interface for services that maintain caches that can be reset.</dd>
 *
 *   <dt>{@link dev.ikm.tinkar.common.service.DefaultDescriptionForNidService}</dt>
 *   <dd>Service for obtaining default text descriptions for concepts.</dd>
 * </dl>
 *
 * <h2>Service Controllers</h2>
 * <p>
 * Controllers manage the configuration and initialization of services, particularly data providers.
 * They bridge user/programmatic selection with service instantiation.
 * </p>
 *
 * <dl>
 *   <dt>{@link dev.ikm.tinkar.common.service.DataServiceController}</dt>
 *   <dd>Generic controller interface for data services. Provides methods for configuration,
 *       validation, startup, shutdown, and querying service state.</dd>
 *
 *   <dt>{@link dev.ikm.tinkar.common.service.LoadDataFromFileController}</dt>
 *   <dd>Specialized controller for loading data from files.</dd>
 *
 *   <dt>{@link dev.ikm.tinkar.common.service.DataServiceProperty}</dt>
 *   <dd>Represents a configurable property of a data service (e.g., directory path, file name).</dd>
 *
 *   <dt>{@link dev.ikm.tinkar.common.service.DataUriOption}</dt>
 *   <dd>Represents a selectable URI option for data sources.</dd>
 * </dl>
 *
 * <h3>Controller Usage Pattern</h3>
 * <pre>{@code
 * // 1. User selects "Open Existing" or "Create New"
 * ServiceLoader<DataServiceController> controllers =
 *     PluggableService.load(DataServiceController.class);
 *
 * // 2. Find appropriate controller
 * DataServiceController controller = controllers.stream()
 *     .filter(c -> c.controllerName().equals("Open SpinedArrayStore"))
 *     .findFirst()
 *     .orElseThrow();
 *
 * // 3. Configure controller
 * controller.setDataUriOption(new DataUriOption("My Database", dbUri));
 *
 * // 4. Start service (before lifecycle manager)
 * controller.start();
 *
 * // 5. Later, lifecycle manager discovers the now-active service
 * ServiceLifecycleManager.get().discoverServices();
 * ServiceLifecycleManager.get().startServices();
 * }</pre>
 *
 * <p>
 * <b>Note:</b> Controllers are used for <em>selection and initial configuration</em>.
 * Once a service is started via a controller, it becomes eligible for lifecycle management.
 * Controllers do not implement {@code ServiceLifecycle} - the services they create do.
 * </p>
 *
 * <h2>Configuration and Properties</h2>
 * <dl>
 *   <dt>{@link dev.ikm.tinkar.common.service.ServiceProperties}</dt>
 *   <dd>Global property store for service configuration. Properties persist for the application lifetime.</dd>
 *
 *   <dt>{@link dev.ikm.tinkar.common.service.ServiceKeys}</dt>
 *   <dd>Standard property keys (DATA_STORE_ROOT, JVM_UUID, CACHE_PERIOD_UUID).</dd>
 * </dl>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Set property
 * ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, new File("/path/to/data"));
 *
 * // Get property with default
 * File dataRoot = ServiceProperties.get(ServiceKeys.DATA_STORE_ROOT,
 *     new File("./default-data"));
 * }</pre>
 *
 * <h2>Utility Classes</h2>
 * <dl>
 *   <dt>{@link dev.ikm.tinkar.common.service.ServiceFactory}</dt>
 *   <dd>Marker interface for service factories.</dd>
 *
 *   <dt>{@link dev.ikm.tinkar.common.service.SaveState}</dt>
 *   <dd>Enum representing save states (SAVED, NOT_SAVED, UNKNOWN).</dd>
 *
 *   <dt>{@link dev.ikm.tinkar.common.service.DataActivity}</dt>
 *   <dd>Enum describing the type of data activity (SYNCHRONIZABLE_EDIT, DATA_REPAIR, etc.).</dd>
 *
 *   <dt>{@link dev.ikm.tinkar.common.service.NonExistentValue}</dt>
 *   <dd>Marker for non-existent values in data structures.</dd>
 *
 *   <dt>{@link dev.ikm.tinkar.common.service.SimpleIndeterminateTracker}</dt>
 *   <dd>Simple implementation of progress tracking for indeterminate operations.</dd>
 * </dl>
 *
 * <h2>Architecture Guidelines</h2>
 *
 * <h3>For Service Implementers</h3>
 * <ol>
 *   <li><b>Implement ServiceLifecycle</b> if your service needs ordered startup/shutdown</li>
 *   <li><b>Choose the appropriate phase</b> based on dependencies</li>
 *   <li><b>Use sub-priorities sparingly</b> - only when ordering within a phase matters</li>
 *   <li><b>Make startup idempotent</b> - it may be called multiple times in tests</li>
 *   <li><b>Never throw from shutdown()</b> - log errors instead</li>
 *   <li><b>Declare in module-info.java</b>:
 *       <pre>{@code provides ServiceLifecycle with MyService;}</pre>
 *   </li>
 * </ol>
 *
 * <h3>For Application Developers</h3>
 * <ol>
 *   <li><b>Initialize plugins early</b> before discovering services</li>
 *   <li><b>Discover once</b> - {@code discoverServices()} should be called once per application lifetime</li>
 *   <li><b>Handle mutual exclusion</b> - select services before calling {@code startServices()}</li>
 *   <li><b>Use shutdown hooks</b> to ensure {@code shutdownServices()} is called</li>
 *   <li><b>Consider restart requirements</b> - services cannot be restarted after shutdown</li>
 * </ol>
 *
 * <h3>For Plugin Developers</h3>
 * <ol>
 *   <li><b>Provide module-info.java</b> with proper {@code provides} declarations</li>
 *   <li><b>Document dependencies</b> clearly in your service documentation</li>
 *   <li><b>Test with lifecycle manager</b> to ensure proper phase placement</li>
 *   <li><b>Be mindful of classloader isolation</b> - avoid static state across module boundaries</li>
 * </ol>
 *
 * <h2>Testing Services</h2>
 * <pre>{@code
 * @BeforeEach
 * void setup() {
 *     // Clear any previous lifecycle state
 *     ServiceLifecycleManager.get().shutdownServices();
 *
 *     // Discover services
 *     ServiceLifecycleManager.get().discoverServices();
 *
 *     // Select specific implementations for tests
 *     ServiceLifecycleManager.get().selectServiceForGroup(
 *         "DATA_PROVIDER", InMemoryProvider.class);
 *
 *     // Start services
 *     ServiceLifecycleManager.get().startServices();
 * }
 *
 * @AfterEach
 * void teardown() {
 *     ServiceLifecycleManager.get().shutdownServices();
 * }
 * }</pre>
 *
 * <h2>Migration Guide</h2>
 * <p>
 * For existing services that use manual initialization:
 * </p>
 * <ol>
 *   <li>Implement {@link dev.ikm.tinkar.common.service.ServiceLifecycle}</li>
 *   <li>Move initialization code from constructor/static initializers to {@code startup()}</li>
 *   <li>Move cleanup code to {@code shutdown()}</li>
 *   <li>Add {@link dev.ikm.tinkar.common.service.LifecyclePhase} annotation</li>
 *   <li>Update {@code module-info.java} to provide {@code ServiceLifecycle}</li>
 *   <li>Remove manual {@code start()} / {@code stop()} calls from application code</li>
 *   <li>Test with {@code ServiceLifecycleManager}</li>
 * </ol>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * {@link dev.ikm.tinkar.common.service.ServiceLifecycleManager} methods are thread-safe and use
 * synchronization to prevent concurrent modifications. However, individual service {@code startup()}
 * and {@code shutdown()} methods are called sequentially, not concurrently.
 * </p>
 * <p>
 * Services should be designed to be thread-safe once started, but startup/shutdown themselves
 * do not need internal synchronization.
 * </p>
 *
 * <h2>Performance Considerations</h2>
 * <ul>
 *   <li><b>Discovery is fast</b> - service providers are loaded but not instantiated</li>
 *   <li><b>Startup is sequential</b> - services start one at a time in phase order</li>
 *   <li><b>Slow services block others</b> - keep {@code startup()} methods fast</li>
 *   <li><b>Use ExecutorService</b> for background initialization within {@code startup()}</li>
 * </ul>
 *
 * <h2>Troubleshooting</h2>
 * <dl>
 *   <dt>"Service not discovered"</dt>
 *   <dd>Check {@code module-info.java} has {@code provides ServiceLifecycle with YourService;}
 *       and that the module is on the module path.</dd>
 *
 *   <dt>"Wrong startup order"</dt>
 *   <dd>Verify {@code @LifecyclePhase} annotation or {@code getLifecyclePhase()} returns
 *       the correct phase. Use {@code -Dservice.lifecycle.log.verbose=true} to see full details.</dd>
 *
 *   <dt>"Service started twice"</dt>
 *   <dd>Ensure {@code discoverServices()} and {@code startServices()} are each called only once.
 *       Make {@code startup()} idempotent to handle multiple calls gracefully.</dd>
 *
 *   <dt>"Shutdown errors"</dt>
 *   <dd>Never throw exceptions from {@code shutdown()}. Log errors and continue cleanup.</dd>
 * </dl>
 *
 * <h2>See Also</h2>
 * <ul>
 *   <li>{@link java.util.ServiceLoader} - Java's standard service provider mechanism</li>
 *   <li>{@link java.lang.ModuleLayer} - Java's module layer system used by plugins</li>
 *   <li>Tinkar Architecture Documentation</li>
 * </ul>
 *
 * @since 1.0
 */
package dev.ikm.tinkar.common.service;