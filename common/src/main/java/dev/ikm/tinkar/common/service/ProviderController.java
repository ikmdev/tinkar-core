
package dev.ikm.tinkar.common.service;

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
 *   <li>Lifecycle phase and priority</li>
 *   <li>Mutual exclusion group (if applicable)</li>
 * </ul>
 * </p>
 *
 * @param <P> the provider type managed by this controller
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

    // ========== Protected Accessor ==========

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
}