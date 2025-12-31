
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

/**
 * Defines lifecycle management hooks for services loaded via PluggableService.
 * <p>
 * Services implementing this interface will have their startup and shutdown
 * methods invoked by the ServiceLifecycleManager in phase order.
 * </p>
 * <p>
 * Services are discovered once during application initialization and cannot
 * be dynamically added after startup begins. This ensures consistent lifecycle
 * management and prevents reference management issues.
 * </p>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @LifecyclePhase(ServiceLifecyclePhase.DATA_STORAGE)
 * public class MyDataService implements ServiceLifecycle {
 *
 *     @Override
 *     public void startup() throws Exception {
 *         LOG.info("Starting MyDataService");
 *         // Initialize database connection
 *     }
 *
 *     @Override
 *     public void shutdown() {
 *         LOG.info("Shutting down MyDataService");
 *         // Close database connection
 *     }
 * }
 * }</pre>
 *
 * <h3>Mutual Exclusion Example</h3>
 * <pre>{@code
 * @LifecyclePhase(ServiceLifecyclePhase.DATA_STORAGE)
 * public class SpinedArrayProvider implements PrimitiveDataService, ServiceLifecycle {
 *
 *     @Override
 *     public String getMutualExclusionGroup() {
 *         return "DATA_PROVIDER";
 *     }
 *
 *     // Only ONE service with group "DATA_PROVIDER" will be started
 * }
 * }</pre>
 *
 * <h3>Static Discovery Facade</h3>
 * <pre>{@code
 * // Simple facade for common use cases
 * ServiceLifecycle.discover();
 * ServiceLifecycle.start();
 * ServiceLifecycle.shutdown();
 * }</pre>
 *
 * <h3>Phase Override via Command Line</h3>
 * <pre>
 * java -Dservice.lifecycle.phase.MyDataService=INFRASTRUCTURE \
 *      -Dservice.lifecycle.subpriority.MyDataService=20 \
 *      -Dservice.lifecycle.group.DATA_PROVIDER=SpinedArrayProvider \
 *      -jar myapp.jar
 * </pre>
 */
public interface ServiceLifecycle {

    /**
     * Invoked during service startup phase.
     * <p>
     * Services should initialize resources, establish connections, and prepare
     * for operation. This method should be fast and non-blocking where possible.
     * </p>
     * <p>
     * If this method throws an exception, the entire application startup will
     * fail and no further services will be started.
     * </p>
     *
     * @throws Exception if startup fails - will be logged and rethrown as RuntimeException
     */
    void startup() throws Exception;

    /**
     * Invoked during service shutdown phase.
     * <p>
     * Services should release resources, close connections, flush buffers,
     * and perform cleanup. This method must not throw exceptions - any errors
     * should be logged internally.
     * </p>
     * <p>
     * All services will be shut down even if some fail. Exceptions are logged
     * but do not prevent other services from shutting down.
     * </p>
     */
    void shutdown();

    /**
     * Returns the lifecycle phase for this service.
     * <p>
     * This method is used if no {@link LifecyclePhase} annotation is present.
     * The annotation takes precedence if both are specified.
     * </p>
     * <p>
     * Can be overridden via system property:
     * {@code -Dservice.lifecycle.phase.ClassName=PHASE_NAME}
     * </p>
     *
     * @return the lifecycle phase (default: APPLICATION_SERVICES)
     */
    default ServiceLifecyclePhase getLifecyclePhase() {
        return ServiceLifecyclePhase.APPLICATION_SERVICES;
    }

    /**
     * Returns the sub-priority within the lifecycle phase.
     * <p>
     * Services with lower sub-priority values start first within their phase.
     * Valid range is 0-99, with 50 as the default.
     * </p>
     * <p>
     * Most services should use the default. Only specify a sub-priority if
     * you have dependencies within the same phase.
     * </p>
     * <p>
     * Can be overridden via system property:
     * {@code -Dservice.lifecycle.subpriority.ClassName=10}
     * </p>
     *
     * @return sub-priority within phase (0-99, default: 50)
     */
    default int getSubPriority() {
        return 50;
    }

    /**
     * Determines whether this service should be activated during startup.
     * <p>
     * This method is called AFTER discovery but BEFORE startup. If it returns false,
     * the service is skipped during this startup cycle.
     * </p>
     * <p>
     * Use cases:
     * <ul>
     *   <li>Feature-flag controlled services</li>
     *   <li>Platform-specific services (desktop vs web)</li>
     *   <li>Test-only services</li>
     *   <li>Conditionally enabled features</li>
     * </ul>
     * </p>
     *
     * @return true if this service should start, false to skip
     */
    default boolean shouldActivate() {
        return true;
    }

    /**
     * Returns a group identifier for mutually exclusive services.
     * <p>
     * Services returning the same non-null group will be treated as mutually exclusive -
     * only ONE service from each group will be started. The selection can be made via:
     * <ul>
     *   <li>System property: {@code -Dservice.lifecycle.group.DATA_PROVIDER=SpinedArrayProvider}</li>
     *   <li>Programmatic selection via {@link ServiceLifecycleManager#selectServiceForGroup}</li>
     *   <li>User selection callback</li>
     * </ul>
     * </p>
     * <p>
     * Common group names:
     * <ul>
     *   <li>DATA_PROVIDER - for database/storage providers</li>
     *   <li>SEARCH_ENGINE - for search implementations</li>
     *   <li>CACHE_PROVIDER - for caching implementations</li>
     * </ul>
     * </p>
     *
     * @return exclusion group if this service is mutually exclusive with others, 
     *         or {@code Optional.empty()} if this service can run alongside any other service
     * @see ServiceExclusionGroup
     */
    default Optional<ServiceExclusionGroup> getMutualExclusionGroup() {
        return Optional.empty();
    }

    // Static facade methods for simplified access

    /**
     * Discovers all lifecycle-managed services.
     * <p>
     * This is a convenience method equivalent to:
     * {@code ServiceLifecycleManager.get().discoverServices()}
     * </p>
     */
    static void discover() {
        ServiceLifecycleManager.get().discoverServices();
    }

    /**
     * Starts all discovered services in phase order.
     * <p>
     * This is a convenience method equivalent to:
     * {@code ServiceLifecycleManager.get().startServices()}
     * </p>
     */
    static void start() {
        ServiceLifecycleManager.get().startServices();
    }
}