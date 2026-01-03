
package dev.ikm.tinkar.common.service;

import org.eclipse.collections.api.list.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Abstract base class for provider lifecycle controllers.
 * <p>
 * This class handles the common boilerplate for managing singleton provider instances
 * that integrate with both {@link ServiceLifecycle} and domain-specific controller interfaces.
 * </p>
 * <p>
 * Subclasses need only implement a few abstract methods to specify:
 * <ul>
 *   <li>How to create the provider instance</li>
 *   <li>How to start the provider</li>
 *   <li>How to stop the provider</li>
 *   <li>What service interface(s) the provider implements</li>
 *   <li>Lifecycle phase and priority</li>
 *   <li>Mutual exclusion group (if applicable)</li>
 * </ul>
 * </p>
 *
 * <h2>Type Parameter Relationship</h2>
 * <p>
 * The generic type parameter {@code P} represents the <b>concrete provider implementation class</b>
 * (e.g., {@code SearchProvider}, {@code RocksProvider}). This provider class must implement
 * ALL service interfaces returned by {@link #serviceClasses()}.
 * </p>
 * <p>
 * <b>Contract:</b> For each {@code Class<?>} in {@code serviceClasses()}, the provider type {@code P}
 * must be assignable to that class. The compiler cannot enforce this relationship due to type erasure,
 * so implementers must ensure correctness.
 * </p>
 * <p>
 * Example:
 * <pre>{@code
 * // SearchProvider implements SearchService
 * public class Controller extends ProviderController<SearchProvider> {
 *     @Override
 *     public ImmutableList<Class<?>> serviceClasses() {
 *         return Lists.immutable.of(SearchService.class);
 *     }
 *     // SearchProvider MUST implement SearchService
 * }
 *
 * // RocksProvider implements PrimitiveDataService
 * public class Controller extends ProviderController<RocksProvider> {
 *     @Override
 *     public ImmutableList<Class<?>> serviceClasses() {
 *         return Lists.immutable.of(PrimitiveDataService.class);
 *     }
 *     // RocksProvider MUST implement PrimitiveDataService
 * }
 *
 * // Hypothetical multi-service provider
 * // MultiProvider implements both SearchService and IndexService
 * public class Controller extends ProviderController<MultiProvider> {
 *     @Override
 *     public ImmutableList<Class<?>> serviceClasses() {
 *         return Lists.immutable.of(SearchService.class, IndexService.class);
 *     }
 *     // MultiProvider MUST implement BOTH SearchService AND IndexService
 * }
 * }</pre>
 * </p>
 *
 * @param <P> the concrete provider implementation class that implements the service interface(s)
 *           declared in {@link #serviceClasses()}
 *
 * @see ServiceLifecycle
 * @see ExecutorController
 * @see DataServiceController
 */
public abstract class ProviderController<P> implements ServiceLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(ProviderController.class);

    private final AtomicReference<P> providerRef = new AtomicReference<>();
    protected final AtomicReference<DataUriOption> dataUriOptionRef = new AtomicReference<>();

    public void setDataUriOption(DataUriOption option) {
        this.dataUriOptionRef.set(option);
    }

    // ========== ServiceLifecycle Implementation ==========

    @Override
    public final void startup()  {
        providerRef.updateAndGet(existing -> {
            if (existing != null) {
                LOG.debug("{} already started", getProviderName());
                return existing;
            }

            try {
                P provider = createProvider();
                initializeProvider(provider);
                startProvider(provider);
                LOG.info("{} started successfully", getProviderName());
                return provider;
            } catch (Exception e) {
                LOG.error("Failed to start {}", getProviderName(), e);
                throw new RuntimeException("Failed to start " + getProviderName(), e);
            }
        });
    }

    @Override
    public final void shutdown() {
        providerRef.updateAndGet(existing -> {
            if (existing != null) {
                try {
                    stopProvider(existing);
                    cleanupProvider(existing);
                    LOG.info("{} stopped successfully", getProviderName());
                } catch (Exception e) {
                    LOG.error("Error stopping {}", getProviderName(), e);
                }
            }
            return null;
        });
    }

    // ========== Abstract Methods (Required) ==========

    /**
     * Creates a new instance of the provider.
     * <p>
     * Called during {@link #startup()} when no provider instance exists.
     * </p>
     *
     * @return a new provider instance
     * @throws Exception if provider creation fails
     */
    protected abstract P createProvider() throws Exception;

    /**
     * Starts the provider's services.
     * <p>
     * Called after {@link #createProvider()} and {@link #initializeProvider(Object)}.
     * This is where you should start thread pools, open connections, etc.
     * </p>
     *
     * @param provider the provider to start
     * @throws Exception if startup fails
     */
    protected abstract void startProvider(P provider) throws Exception;

    /**
     * Stops the provider's services.
     * <p>
     * Called during {@link #shutdown()}. This is where you should stop thread pools,
     * close connections, flush buffers, etc.
     * </p>
     *
     * @param provider the provider to stop
     * @throws Exception if shutdown fails (will be logged but not rethrown)
     */
    protected abstract void stopProvider(P provider) throws Exception;

    /**
     * Returns the display name of the provider for logging.
     *
     * @return provider name (e.g., "ExecutorProvider", "SpinedArrayProvider")
     */
    protected abstract String getProviderName();

    /**
     * Returns the list of service interface classes that this provider implements.
     * <p>
     * This enables consistent service discovery across all providers, whether they are
     * user-selectable data sources or automatic background services. Providers can implement
     * multiple service interfaces, giving flexibility for evolution and composition.
     * </p>
     *
     * <h3>Implementation Contract</h3>
     * <p>
     * <b>CRITICAL:</b> The provider type {@code P} (the generic parameter of {@code ProviderController<P>})
     * MUST implement ALL service interfaces returned by this method. This is a runtime contract
     * that cannot be enforced by the compiler due to type erasure.
     * </p>
     * <p>
     * For example, if {@code ProviderController<SearchProvider>} returns
     * {@code Lists.immutable.of(SearchService.class)}, then {@code SearchProvider} must
     * implement {@code SearchService}.
     * </p>
     *
     * <h3>Discovery</h3>
     * <p>
     * Other services discover this provider by calling:
     * <pre>{@code
     * SearchService service = ServiceLifecycleManager.get()
     *     .getRunningService(SearchService.class)
     *     .orElseThrow();
     * }</pre>
     * The lifecycle manager will iterate through all {@code ProviderController} instances,
     * check their {@code serviceClasses()}, and return the provider if it declares the
     * requested service interface.
     * </p>
     *
     * <h3>Examples</h3>
     * <pre>{@code
     * // Single service interface
     * public class SearchProvider implements SearchService { ... }
     * public class Controller extends ProviderController<SearchProvider> {
     *     public ImmutableList<Class<?>> serviceClasses() {
     *         return Lists.immutable.of(SearchService.class);
     *     }
     * }
     *
     * // Single service interface (data provider)
     * public class RocksProvider implements PrimitiveDataService { ... }
     * public class Controller extends ProviderController<RocksProvider> {
     *     public ImmutableList<Class<?>> serviceClasses() {
     *         return Lists.immutable.of(PrimitiveDataService.class);
     *     }
     * }
     *
     * // Multiple service interfaces
     * public class MultiProvider implements SearchService, IndexService { ... }
     * public class Controller extends ProviderController<MultiProvider> {
     *     public ImmutableList<Class<?>> serviceClasses() {
     *         return Lists.immutable.of(
     *             SearchService.class,
     *             IndexService.class
     *         );
     *     }
     * }
     * }</pre>
     *
     * @return immutable list of service interface classes that the provider type {@code P} implements;
     *         must not be null or empty
     */
    public abstract ImmutableList<Class<?>> serviceClasses();

    // ========== Hook Methods (Optional Override) ==========

    /**
     * Initializes the provider before starting.
     * <p>
     * Override this to perform initialization that must happen after construction
     * but before startup. Default implementation does nothing.
     * </p>
     *
     * @param provider the provider to initialize
     * @throws Exception if initialization fails
     */
    protected void initializeProvider(P provider) throws Exception {
        // Override if needed
    }

    /**
     * Cleans up resources after stopping the provider.
     * <p>
     * Override this to perform cleanup that must happen after shutdown.
     * Default implementation does nothing.
     * </p>
     *
     * @param provider the provider to clean up
     * @throws Exception if cleanup fails
     */
    protected void cleanupProvider(P provider) throws Exception {
        // Override if needed
    }

    // ========== ServiceLifecycle Defaults ==========

    @Override
    public ServiceLifecyclePhase getLifecyclePhase() {
        return ServiceLifecyclePhase.APPLICATION_SERVICES;
    }

    @Override
    public int getSubPriority() {
        return 50; // Default middle priority
    }

    @Override
    public Optional<ServiceExclusionGroup> getMutualExclusionGroup() {
        return Optional.empty(); // Override if provider is mutually exclusive
    }

    // ========== Provider Access ==========

    /**
     * Gets the current provider instance for external consumption.
     * <p>
     * This is the public API for accessing the provider service. Other services should
     * discover and access this controller's provider through this method.
     * </p>
     * <p>
     * The returned provider instance {@code P} implements all service interfaces declared
     * by {@link #serviceClasses()}. Callers typically discover this controller via
     * {@link ServiceLifecycleManager#getRunningService(Class)} and then access the provider
     * through this method.
     * </p>
     * <p>
     * Example usage:
     * <pre>{@code
     * // Discovery via ServiceLifecycleManager
     * SearchService searchService = ServiceLifecycleManager.get()
     *     .getRunningService(SearchService.class)
     *     .orElseThrow();
     *
     * // The lifecycle manager internally does:
     * // 1. Finds ProviderController with SearchService.class in serviceClasses()
     * // 2. Calls controller.provider() to get the SearchProvider instance
     * // 3. Returns it cast to SearchService
     * }</pre>
     * </p>
     *
     * @return the provider instance that implements the service interface(s) declared
     *         by {@link #serviceClasses()}
     * @throws IllegalStateException if provider is not started
     */
    public final P provider() {
        return requireProvider();
    }

    /**
     * Gets the current provider instance.
     * <p>
     * May return null if the provider has not been started or has been shut down.
     * </p>
     *
     * @return the provider instance, or null if not available
     */
    protected final P getProvider() {
        return providerRef.get();
    }

    /**
     * Gets the current provider instance, throwing an exception if not available.
     * <p>
     * Use this when the provider must be present for the operation to succeed.
     * </p>
     *
     * @return the provider instance
     * @throws IllegalStateException if provider is not started
     */
    protected final P requireProvider() {
        P provider = providerRef.get();
        if (provider == null) {
            throw new IllegalStateException(
                    getProviderName() + " not started. Ensure ServiceLifecycleManager has started services.");
        }
        return provider;
    }

    /**
     * Gets the current provider instance, or creates and starts one if not available.
     * <p>
     * <b>Use with caution:</b> This bypasses normal lifecycle management.
     * Prefer using {@link #requireProvider()} or waiting for {@link #startup()} to be called.
     * </p>
     *
     * @return the provider instance
     * @throws RuntimeException if provider creation or startup fails
     */
    protected final P getOrCreateProvider() {
        return providerRef.updateAndGet(existing -> {
            if (existing != null) {
                return existing;
            }
            try {
                startup();
                return providerRef.get();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create " + getProviderName(), e);
            }
        });
    }

    /**
     * Returns a user-friendly string representation for UI display.
     * <p>
     * For DataServiceControllers, this returns the controller name.
     * Otherwise, it returns the provider name.
     * </p>
     *
     * @return user-friendly display name
     */
    @Override
    public String toString() {
        if (this instanceof DataServiceController<?> dsc) {
            return dsc.controllerName();
        }
        return getProviderName();
    }
}