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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manages the lifecycle of services implementing {@link ServiceLifecycle}.
 * <p>
 * This manager discovers services via {@link PluggableService} during initialization,
 * orders them by phase and sub-priority, and manages their startup and shutdown
 * in the correct sequence.
 * </p>
 * <p>
 * Services are started in priority order (low to high), and shutdown in
 * reverse priority order.
 * </p>
 *
 * <h3>Lifecycle States</h3>
 * <ul>
 *   <li><b>UNINITIALIZED</b> - Initial state, no services discovered yet</li>
 *   <li><b>DISCOVERED</b> - Services have been discovered but not started</li>
 *   <li><b>STARTING</b> - Currently starting services</li>
 *   <li><b>RUNNING</b> - All services started successfully</li>
 *   <li><b>SHUTTING_DOWN</b> - Currently shutting down services</li>
 *   <li><b>SHUTDOWN</b> - All services shutdown</li>
 * </ul>
 *
 * <h3>Command-Line Configuration</h3>
 * <pre>
 * # Override specific service phases/priorities
 * -Dservice.lifecycle.phase.MyService=INFRASTRUCTURE
 * -Dservice.lifecycle.subpriority.MyService=10
 *
 * # Select a specific service for a mutual exclusion group
 * -Dservice.lifecycle.group.EXECUTOR_PROVIDER=ExecutorProviderLifecycle
 *
 * # Enable verbose logging
 * -Dservice.lifecycle.log.verbose=true
 * </pre>
 */
public class ServiceLifecycleManager {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceLifecycleManager.class);
    private static final ServiceLifecycleManager INSTANCE = new ServiceLifecycleManager();

    private static final String PHASE_OVERRIDE_PREFIX = "service.lifecycle.phase.";
    private static final String SUBPRIORITY_OVERRIDE_PREFIX = "service.lifecycle.subpriority.";
    private static final String GROUP_SELECTION_PREFIX = "service.lifecycle.group.";
    private static final String VERBOSE_LOGGING_PROPERTY = "service.lifecycle.log.verbose";

    /**
     * Internal lifecycle states for the manager itself.
     */
    public enum State {
        UNINITIALIZED, DISCOVERED, PREPARED, STARTING, RUNNING, SHUTTING_DOWN, SHUTDOWN
    }

    private final Map<Class<?>, ServiceLifecycle> discoveredServices = new ConcurrentHashMap<>();
    private final Map<Class<?>, ServiceLifecycle> activeServices = new ConcurrentHashMap<>();
    private final Map<Class<?>, ServicePriority> servicePriorities = new ConcurrentHashMap<>();
    private final Map<ServiceExclusionGroup, List<ServiceInfo>> mutualExclusionGroups = new ConcurrentHashMap<>();
    private final Map<ServiceExclusionGroup, Class<?>> groupSelections = new ConcurrentHashMap<>();
    private Function<GroupSelectionContext, Class<?>> groupSelectionCallback;

    private State state = State.UNINITIALIZED;
    private final boolean verboseLogging;

    /**
     * Package-private class to hold phase and sub-priority information.
     * Visible for debugging but not part of public API.
     */
    static class ServicePriority {
        final ServiceLifecyclePhase phase;
        final int subPriority;
        final int effectivePriority;
        final String source; // Where the priority came from (annotation, interface, override)

        ServicePriority(ServiceLifecyclePhase phase, int subPriority, String source) {
            this.phase = phase;
            this.subPriority = subPriority;
            this.effectivePriority = phase.getBaseValue() + subPriority;
            this.source = source;
        }
    }

    /**
     * Package-private class to hold service information for mutual exclusion groups.
     * Visible for debugging but not part of public API.
     */
    static class ServiceInfo {
        final Class<?> serviceClass;
        final ServiceLifecycle service;
        final ServicePriority priority;

        ServiceInfo(Class<?> serviceClass, ServiceLifecycle service, ServicePriority priority) {
            this.serviceClass = serviceClass;
            this.service = service;
            this.priority = priority;
        }
    }

    /**
     * Context provided to group selection callback.
     */
    public static class GroupSelectionContext {
        private final ServiceExclusionGroup group;
        private final List<Class<?>> candidates;

        GroupSelectionContext(ServiceExclusionGroup group, List<Class<?>> candidates) {
            this.group = group;
            this.candidates = Collections.unmodifiableList(candidates);
        }

        public ServiceExclusionGroup getGroup() {
            return group;
        }

        public String getGroupName() {
            return group.getGroupName();
        }

        public List<Class<?>> getCandidates() {
            return candidates;
        }
    }

    private ServiceLifecycleManager() {
        verboseLogging = Boolean.getBoolean(VERBOSE_LOGGING_PROPERTY);
    }

    public static ServiceLifecycleManager get() {
        return INSTANCE;
    }

    /**
     * Sets a callback function for selecting services from mutual exclusion groups.
     * <p>
     * This callback is invoked when multiple services belong to the same group and
     * no command-line selection or programmatic selection has been made.
     * </p>
     *
     * @param callback function to select from group candidates, or null to use default
     */
    public void setGroupSelectionCallback(Function<GroupSelectionContext, Class<?>> callback) {
        this.groupSelectionCallback = callback;
    }

    /**
     * Programmatically selects a specific service for a mutual exclusion group.
     * <p>
     * This method must be called BEFORE {@link #startServices()}.
     * </p>
     *
     * @param group the mutual exclusion group
     * @param serviceClass the service class to activate for this group
     * @throws IllegalStateException if called after services have been started
     */
    public void selectServiceForGroup(ServiceExclusionGroup group, Class<?> serviceClass) {
        if (state == State.RUNNING) {
            throw new IllegalStateException("Cannot select group services after startup");
        }
        groupSelections.put(group, serviceClass);
        LOG.info("Programmatically selected {} for group {}", serviceClass.getSimpleName(), group.getGroupName());
    }

    /**
     * Discovers and registers all services implementing ServiceLifecycle.
     * <p>
     * This method should be called early in application initialization,
     * before calling startServices(). It can only be called once - subsequent
     * calls will log a warning and return.
     * </p>
     * <p>
     * Services are discovered from:
     * <ul>
     *   <li>ServiceLoader mechanism via PluggableService</li>
     *   <li>All loaded plugin modules</li>
     * </ul>
     * </p>
     *
     * @throws IllegalStateException if called after services have been started
     */
    public synchronized void discoverServices() {
        if (state == State.DISCOVERED) {
            LOG.warn("Services already discovered, ignoring duplicate call");
            return;
        }

        if (state != State.UNINITIALIZED) {
            throw new IllegalStateException(
                    "Cannot discover services in state: " + state);
        }

        LOG.info("═══════════════════════════════════════════════════════════");
        LOG.info("Service Lifecycle Discovery Starting");
        LOG.info("═══════════════════════════════════════════════════════════");

        long startTime = System.currentTimeMillis();

        ServiceLoader<ServiceLifecycle> serviceLoader = PluggableService.load(ServiceLifecycle.class);
        List<ServiceLoader.Provider<ServiceLifecycle>> providers =
                serviceLoader.stream().collect(Collectors.toList());

        LOG.info("Found {} ServiceLifecycle provider(s)", providers.size());

        int successCount = 0;
        int failureCount = 0;

        for (ServiceLoader.Provider<ServiceLifecycle> provider : providers) {
            try {
                ServiceLifecycle service = provider.get();
                Class<?> serviceClass = service.getClass();

                discoveredServices.put(serviceClass, service);
                ServicePriority priority = determinePriority(service);
                servicePriorities.put(serviceClass, priority);

                // Track mutual exclusion groups
                service.getMutualExclusionGroup().ifPresent(group -> {
                    mutualExclusionGroups
                            .computeIfAbsent(group, k -> new ArrayList<>())
                            .add(new ServiceInfo(serviceClass, service, priority));
                });

                successCount++;

                if (verboseLogging) {
                    String groupInfo = service.getMutualExclusionGroup()
                            .map(g -> ", group=" + g.getGroupName())
                            .orElse("");
                    LOG.debug("Discovered: {} [phase={}, sub={}, effective={}, source={}{}]",
                            serviceClass.getSimpleName(),
                            priority.phase.name(),
                            priority.subPriority,
                            priority.effectivePriority,
                            priority.source,
                            groupInfo);
                }

            } catch (Exception e) {
                failureCount++;
                LOG.error("Failed to load service from provider: " + provider.type(), e);
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        state = State.DISCOVERED;

        LOG.info("───────────────────────────────────────────────────────────");
        LOG.info("Discovery Summary:");
        LOG.info("  Success: {} services", successCount);
        if (failureCount > 0) {
            LOG.warn("  Failed:  {} services", failureCount);
        }
        if (!mutualExclusionGroups.isEmpty()) {
            LOG.info("  Mutual exclusion groups: {}", mutualExclusionGroups.size());
        }
        LOG.info("  Duration: {} ms", duration);
        LOG.info("═══════════════════════════════════════════════════════════");

        if (successCount > 0) {
            logMutualExclusionGroups();
        }
    }

    /**
     * Prepares for startup by resolving group selections and applying activation filters.
     * <p>
     * This must be called after discovery and after any programmatic selections have been made,
     * but before startServices(). It can be called multiple times - subsequent calls are no-ops.
     * </p>
     * <p>
     * Note: startServices() automatically calls this method if not already called.
     * </p>
     */
    public synchronized void prepareStartup() {
        if (state == State.PREPARED) {
            LOG.debug("Startup already prepared, ignoring duplicate call");
            return;
        }

        if (state != State.DISCOVERED) {
            throw new IllegalStateException(
                    "Cannot prepare startup in state: " + state + ". Must call discoverServices() first.");
        }

        resolveGroupSelections();
        applyActivationFilters();
        logStartupPlan();
        state = State.PREPARED;
    }

    /**
     * Logs information about mutual exclusion groups.
     */
    private void logMutualExclusionGroups() {
        if (mutualExclusionGroups.isEmpty()) {
            return;
        }

        LOG.info("");
        LOG.info("═══════════════════════════════════════════════════════════");
        LOG.info("Mutual Exclusion Groups:");
        LOG.info("═══════════════════════════════════════════════════════════");

        mutualExclusionGroups.forEach((group, services) -> {
            LOG.info("");
            LOG.info("Group: {}", group.getGroupName());
            LOG.info("───────────────────────────────────────────────────────────");
            services.forEach(info ->
                    LOG.info("  • {} (phase={}, sub={})",
                            info.serviceClass.getSimpleName(),
                            info.priority.phase.name(),
                            info.priority.subPriority));
        });

        LOG.info("═══════════════════════════════════════════════════════════");
        LOG.info("");
    }

    /**
     * Resolves which services should be activated from mutual exclusion groups.
     */
    private void resolveGroupSelections() {
        for (Map.Entry<ServiceExclusionGroup, List<ServiceInfo>> entry : mutualExclusionGroups.entrySet()) {
            ServiceExclusionGroup group = entry.getKey();
            List<ServiceInfo> services = entry.getValue();

            if (services.size() == 1) {
                // Only one service in group - auto-select it
                Class<?> selected = services.get(0).serviceClass;
                groupSelections.put(group, selected);
                LOG.info("Auto-selected {} for group {} (only candidate)",
                        selected.getSimpleName(), group.getGroupName());
                continue;
            }

            // Check for command-line override
            String propertyKey = GROUP_SELECTION_PREFIX + group.getGroupName();
            String selectedName = System.getProperty(propertyKey);

            if (selectedName != null) {
                Optional<ServiceInfo> found = services.stream()
                        .filter(info -> info.serviceClass.getSimpleName().equals(selectedName))
                        .findFirst();

                if (found.isPresent()) {
                    groupSelections.put(group, found.get().serviceClass);
                    LOG.info("Command-line selected {} for group {}", selectedName, group.getGroupName());
                    continue;
                } else {
                    LOG.warn("Command-line selection '{}' not found in group {}. Available: {}",
                            selectedName, group.getGroupName(),
                            services.stream()
                                    .map(info -> info.serviceClass.getSimpleName())
                                    .collect(Collectors.joining(", ")));
                }
            }

            // Check for programmatic selection
            if (groupSelections.containsKey(group)) {
                LOG.info("Using programmatic selection for group {}", group.getGroupName());
                continue;
            }

            // Use callback if provided
            if (groupSelectionCallback != null) {
                List<Class<?>> candidates = services.stream()
                        .map(info -> info.serviceClass)
                        .collect(Collectors.toList());

                Class<?> selected = groupSelectionCallback.apply(
                        new GroupSelectionContext(group, candidates));

                if (selected != null) {
                    groupSelections.put(group, selected);
                    LOG.info("Callback selected {} for group {}",
                            selected.getSimpleName(), group.getGroupName());
                    continue;
                }
            }

            // Default: select first by priority
            ServiceInfo defaultSelection = services.stream()
                    .min(Comparator.comparingInt(info -> info.priority.effectivePriority))
                    .orElseThrow();

            groupSelections.put(group, defaultSelection.serviceClass);
            LOG.warn("No selection specified for group {}. Defaulting to {} (lowest priority)",
                    group.getGroupName(), defaultSelection.serviceClass.getSimpleName());
        }
    }

    /**
     * Applies activation filters (shouldActivate() and group membership).
     */
    private void applyActivationFilters() {
        LOG.info("");
        LOG.info("═══════════════════════════════════════════════════════════");
        LOG.info("Applying Activation Filters:");
        LOG.info("═══════════════════════════════════════════════════════════");

        int activatedCount = 0;
        int skippedCount = 0;

        for (Map.Entry<Class<?>, ServiceLifecycle> entry : discoveredServices.entrySet()) {
            Class<?> serviceClass = entry.getKey();
            ServiceLifecycle service = entry.getValue();
            String serviceName = serviceClass.getSimpleName();

            // Check mutual exclusion group
            Optional<ServiceExclusionGroup> groupOpt = service.getMutualExclusionGroup();
            if (groupOpt.isPresent()) {
                ServiceExclusionGroup group = groupOpt.get();
                Class<?> selectedForGroup = groupSelections.get(group);
                if (!serviceClass.equals(selectedForGroup)) {
                    LOG.info("  ⊗ {} - excluded from group {}", serviceName, group.getGroupName());
                    skippedCount++;
                    continue;
                }
            }

            // Check shouldActivate()
            if (!service.shouldActivate()) {
                LOG.info("  ⊗ {} - shouldActivate() returned false", serviceName);
                skippedCount++;
                continue;
            }

            // Service is active
            activeServices.put(serviceClass, service);
            activatedCount++;

            if (verboseLogging) {
                LOG.debug("  ✓ {} - activated", serviceName);
            }
        }

        LOG.info("───────────────────────────────────────────────────────────");
        LOG.info("Activation Summary:");
        LOG.info("  Activated: {}", activatedCount);
        LOG.info("  Skipped: {}", skippedCount);
        LOG.info("═══════════════════════════════════════════════════════════");
        LOG.info("");
    }

    /**
     * Logs the planned startup order before execution.
     */
    private void logStartupPlan() {
        if (activeServices.isEmpty()) {
            LOG.warn("No services to start");
            return;
        }

        LOG.info("");
        LOG.info("═══════════════════════════════════════════════════════════");
        LOG.info("Planned Startup Order:");
        LOG.info("═══════════════════════════════════════════════════════════");

        List<Map.Entry<Class<?>, ServiceLifecycle>> sortedServices =
                getSortedActiveServices();

        ServiceLifecyclePhase currentPhase = null;
        int phaseCount = 1;

        for (Map.Entry<Class<?>, ServiceLifecycle> entry : sortedServices) {
            Class<?> serviceClass = entry.getKey();
            ServicePriority priority = servicePriorities.get(serviceClass);

            if (currentPhase != priority.phase) {
                currentPhase = priority.phase;
                LOG.info("");
                LOG.info("Phase {}: {} (base={})",
                        phaseCount++, currentPhase.name(), currentPhase.getBaseValue());
                LOG.info("───────────────────────────────────────────────────────────");
            }

            String overrideMarker = priority.source.equals("override") ? " [OVERRIDDEN]" : "";
            LOG.info("  • {} (sub={}, effective={}){}",
                    serviceClass.getSimpleName(),
                    priority.subPriority,
                    priority.effectivePriority,
                    overrideMarker);
        }

        LOG.info("═══════════════════════════════════════════════════════════");
        LOG.info("");
    }

    /**
     * Determines the priority for a service, checking command-line overrides first,
     * then annotation, then interface methods.
     */
    private ServicePriority determinePriority(ServiceLifecycle service) {
        Class<?> serviceClass = service.getClass();
        String className = serviceClass.getSimpleName();

        // Check for command-line override first
        String phaseOverride = System.getProperty(PHASE_OVERRIDE_PREFIX + className);
        String subPriorityOverride = System.getProperty(SUBPRIORITY_OVERRIDE_PREFIX + className);

        if (phaseOverride != null || subPriorityOverride != null) {
            ServiceLifecyclePhase phase = phaseOverride != null ?
                    ServiceLifecyclePhase.valueOf(phaseOverride) :
                    getPhaseFromAnnotationOrInterface(service);

            int subPriority = subPriorityOverride != null ?
                    Integer.parseInt(subPriorityOverride) :
                    getSubPriorityFromAnnotationOrInterface(service);

            validateSubPriority(subPriority, className);

            LOG.info("Command-line override applied to {}: phase={}, sub={}",
                    className, phase.name(), subPriority);

            return new ServicePriority(phase, subPriority, "override");
        }

        // Check for annotation
        LifecyclePhase annotation = serviceClass.getAnnotation(LifecyclePhase.class);
        if (annotation != null) {
            validateSubPriority(annotation.subPriority(), className);
            return new ServicePriority(
                    annotation.value(),
                    annotation.subPriority(),
                    "annotation");
        }

        // Use interface defaults
        ServiceLifecyclePhase phase = service.getLifecyclePhase();
        int subPriority = service.getSubPriority();
        validateSubPriority(subPriority, className);

        return new ServicePriority(phase, subPriority, "interface");
    }

    private ServiceLifecyclePhase getPhaseFromAnnotationOrInterface(ServiceLifecycle service) {
        LifecyclePhase annotation = service.getClass().getAnnotation(LifecyclePhase.class);
        return annotation != null ? annotation.value() : service.getLifecyclePhase();
    }

    private int getSubPriorityFromAnnotationOrInterface(ServiceLifecycle service) {
        LifecyclePhase annotation = service.getClass().getAnnotation(LifecyclePhase.class);
        return annotation != null ? annotation.subPriority() : service.getSubPriority();
    }

    private void validateSubPriority(int subPriority, String serviceName) {
        if (subPriority < 0 || subPriority >= 100) {
            throw new IllegalArgumentException(
                    "Sub-priority for " + serviceName + " must be 0-99, was: " + subPriority);
        }
    }

    /**
     * Starts all discovered services in priority order.
     * <p>
     * Services with lower effective priority values start first. Services are started
     * sequentially. If any service fails to start, the entire startup process fails
     * and no further services are started.
     * </p>
     *
     * @throws RuntimeException if any service fails to start
     * @throws IllegalStateException if services have not been discovered
     */
    public synchronized void startServices() {
        if (state != State.DISCOVERED && state != State.PREPARED) {
            throw new IllegalStateException(
                    "Cannot start services in state: " + state +
                            ". Call discoverServices() first.");
        }

        // Automatically prepare startup (resolve selections, apply filters) before starting
        // This happens after discovery and after any GUI/programmatic selections have been made
        if (state == State.DISCOVERED) {
            prepareStartup();
        }

        if (activeServices.isEmpty()) {
            LOG.warn("No active services to start");
            state = State.RUNNING;
            return;
        }

        state = State.STARTING;

        LOG.info("");
        LOG.info("═══════════════════════════════════════════════════════════");
        LOG.info("Service Lifecycle Startup Beginning");
        LOG.info("═══════════════════════════════════════════════════════════");

        long overallStartTime = System.currentTimeMillis();
        List<Map.Entry<Class<?>, ServiceLifecycle>> sortedServices = getSortedActiveServices();

        ServiceLifecyclePhase currentPhase = null;
        long phaseStartTime = 0;
        int phaseServiceCount = 0;

        for (Map.Entry<Class<?>, ServiceLifecycle> entry : sortedServices) {
            Class<?> serviceClass = entry.getKey();
            ServiceLifecycle service = entry.getValue();
            ServicePriority priority = servicePriorities.get(serviceClass);

            // Log phase transition
            if (currentPhase != priority.phase) {
                if (currentPhase != null) {
                    long phaseDuration = System.currentTimeMillis() - phaseStartTime;
                    LOG.info("Phase {} complete: {} services in {} ms",
                            currentPhase.name(), phaseServiceCount, phaseDuration);
                    LOG.info("───────────────────────────────────────────────────────────");
                }
                currentPhase = priority.phase;
                phaseStartTime = System.currentTimeMillis();
                phaseServiceCount = 0;
                LOG.info("");
                LOG.info("Starting Phase: {}", currentPhase.name());
            }

            startService(service, serviceClass, priority);
            phaseServiceCount++;
        }

        // Log final phase completion
        if (currentPhase != null) {
            long phaseDuration = System.currentTimeMillis() - phaseStartTime;
            LOG.info("Phase {} complete: {} services in {} ms",
                    currentPhase.name(), phaseServiceCount, phaseDuration);
        }

        long overallDuration = System.currentTimeMillis() - overallStartTime;

        state = State.RUNNING;

        LOG.info("═══════════════════════════════════════════════════════════");
        LOG.info("Service Lifecycle Startup Complete");
        LOG.info("  Total services: {}", activeServices.size());
        LOG.info("  Total duration: {} ms", overallDuration);
        LOG.info("═══════════════════════════════════════════════════════════");
        LOG.info("");
    }

    private void startService(ServiceLifecycle service, Class<?> serviceClass,
                              ServicePriority priority) {
        String serviceName = serviceClass.getSimpleName();

        try {
            long startTime = System.currentTimeMillis();

            if (verboseLogging) {
                LOG.debug("  Starting: {}", serviceName);
            }

            service.startup();

            long duration = System.currentTimeMillis() - startTime;

            LOG.info("  ✓ {} ({} ms)", serviceName, duration);

        } catch (Exception e) {
            LOG.error("═══════════════════════════════════════════════════════════");
            LOG.error("✗ STARTUP FAILED: {}", serviceName);
            LOG.error("  Phase: {}", priority.phase.name());
            LOG.error("  Sub-priority: {}", priority.subPriority);
            LOG.error("═══════════════════════════════════════════════════════════");
            LOG.error("Error: ", e);

            state = State.DISCOVERED; // Reset to allow retry
            throw new RuntimeException("Service startup failed: " + serviceName, e);
        }
    }

    /**
     * Shuts down all managed services in reverse priority order.
     * <p>
     * Services with higher priority values shutdown first. All services
     * are shut down even if some fail - failures are logged but not rethrown.
     * </p>
     */
    public synchronized void shutdownServices() {
        if (state != State.RUNNING) {
            LOG.warn("Cannot shutdown services in state: {}. Ignoring.", state);
            return;
        }

        state = State.SHUTTING_DOWN;

        LOG.info("");
        LOG.info("═══════════════════════════════════════════════════════════");
        LOG.info("Service Lifecycle Shutdown Beginning");
        LOG.info("═══════════════════════════════════════════════════════════");

        long overallStartTime = System.currentTimeMillis();

        // Reverse order for shutdown
        List<Map.Entry<Class<?>, ServiceLifecycle>> sortedServices = getSortedActiveServices();
        Collections.reverse(sortedServices);

        List<String> failedServices = new ArrayList<>();
        ServiceLifecyclePhase currentPhase = null;
        long phaseStartTime = 0;
        int phaseServiceCount = 0;

        for (Map.Entry<Class<?>, ServiceLifecycle> entry : sortedServices) {
            Class<?> serviceClass = entry.getKey();
            ServiceLifecycle service = entry.getValue();
            ServicePriority priority = servicePriorities.get(serviceClass);

            // Log phase transition
            if (currentPhase != priority.phase) {
                if (currentPhase != null) {
                    long phaseDuration = System.currentTimeMillis() - phaseStartTime;
                    LOG.info("Phase {} shutdown: {} services in {} ms",
                            currentPhase.name(), phaseServiceCount, phaseDuration);
                    LOG.info("───────────────────────────────────────────────────────────");
                }
                currentPhase = priority.phase;
                phaseStartTime = System.currentTimeMillis();
                phaseServiceCount = 0;
                LOG.info("");
                LOG.info("Shutting down Phase: {}", currentPhase.name());
            }

            String serviceName = serviceClass.getSimpleName();

            try {
                long startTime = System.currentTimeMillis();

                if (verboseLogging) {
                    LOG.debug("  Shutting down: {}", serviceName);
                }

                service.shutdown();

                long duration = System.currentTimeMillis() - startTime;
                LOG.info("  ✓ {} ({} ms)", serviceName, duration);

            } catch (Exception e) {
                LOG.error("  ✗ {} - Error during shutdown", serviceName, e);
                failedServices.add(serviceName);
            }

            phaseServiceCount++;
        }

        // Log final phase completion
        if (currentPhase != null) {
            long phaseDuration = System.currentTimeMillis() - phaseStartTime;
            LOG.info("Phase {} shutdown: {} services in {} ms",
                    currentPhase.name(), phaseServiceCount, phaseDuration);
        }

        long overallDuration = System.currentTimeMillis() - overallStartTime;

        state = State.SHUTDOWN;

        LOG.info("═══════════════════════════════════════════════════════════");
        if (failedServices.isEmpty()) {
            LOG.info("Service Lifecycle Shutdown Complete - All services shutdown cleanly");
        } else {
            LOG.warn("Service Lifecycle Shutdown Complete - {} service(s) had errors:",
                    failedServices.size());
            failedServices.forEach(name -> LOG.warn("  • {}", name));
        }
        LOG.info("  Total services: {}", activeServices.size());
        LOG.info("  Total duration: {} ms", overallDuration);
        LOG.info("═══════════════════════════════════════════════════════════");
        LOG.info("");
    }

    /**
     * Returns active services sorted by effective priority.
     */
    private List<Map.Entry<Class<?>, ServiceLifecycle>> getSortedActiveServices() {
        return activeServices.entrySet().stream()
                .sorted(Comparator.comparingInt(entry ->
                        servicePriorities.get(entry.getKey()).effectivePriority))
                .collect(Collectors.toList());
    }

    /**
     * Returns the current lifecycle state.
     */
    public State getState() {
        return state;
    }

    /**
     * Returns whether services have been started.
     */
    public boolean isRunning() {
        return state == State.RUNNING;
    }

    /**
     * Returns an immutable view of discovered service classes.
     */
    public Set<Class<?>> getDiscoveredServiceClasses() {
        return Collections.unmodifiableSet(discoveredServices.keySet());
    }

    /**
     * Returns an immutable view of active (started) service classes.
     */
    public Set<Class<?>> getActiveServiceClasses() {
        return Collections.unmodifiableSet(activeServices.keySet());
    }

    /**
     * Returns the number of active services.
     */
    public int getActiveServiceCount() {
        return activeServices.size();
    }

    /**
     * Returns the number of discovered services (before activation filtering).
     */
    public int getDiscoveredServiceCount() {
        return discoveredServices.size();
    }

    /**
     * Returns whether services have been discovered.
     */
    public boolean isDiscovered() {
        return state != State.UNINITIALIZED;
    }

    /**
     * Returns an immutable collection of all discovered service instances.
     * This is useful for GUI components that need to present service options
     * before services are started.
     */
    public Collection<ServiceLifecycle> getAllServices() {
        return Collections.unmodifiableCollection(discoveredServices.values());
    }

    /**
     * Returns an immutable view of mutual exclusion groups.
     */
    public Map<String, List<String>> getMutualExclusionGroups() {
        return mutualExclusionGroups.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        entry -> entry.getKey().getGroupName(),
                        entry -> entry.getValue().stream()
                                .map(info -> info.serviceClass.getSimpleName())
                                .collect(Collectors.toList())
                ));
    }

    /**
     * Retrieves a running service by type.
     * <p>
     * Searches through active services for one that provides a service instance
     * matching the requested type. The search strategy is:
     * </p>
     * <ol>
     *   <li>First checks {@link ProviderController} instances via {@link ProviderController#serviceClasses()}
     *       and {@link ProviderController#provider()} - the preferred, type-safe approach</li>
     *   <li>Falls back to checking {@link ServiceLifecycle#getService()} for non-ProviderController services</li>
     * </ol>
     * <p>
     * This method should only be called after services have been started
     * (state == RUNNING). If called earlier, it will return empty and log a warning.
     * </p>
     * <p>
     * Example usage:
     * <pre>{@code
     * // Discover SearchService (provided by SearchProvider via ProviderController)
     * SearchService searchService = ServiceLifecycleManager.get()
     *     .getRunningService(SearchService.class)
     *     .orElseThrow(() -> new IllegalStateException("SearchService not available"));
     *
     * // Discover PrimitiveDataService (provided by RocksProvider or other data provider)
     * PrimitiveDataService dataService = ServiceLifecycleManager.get()
     *     .getRunningService(PrimitiveDataService.class)
     *     .orElseThrow();
     * }</pre>
     * </p>
     *
     * @param serviceType the type of service to retrieve
     * @param <T> the service type
     * @return Optional containing the service instance if found and running
     * @see ProviderController#serviceClasses()
     * @see ProviderController#provider()
     */
    public <T> Optional<T> getRunningService(Class<T> serviceType) {
        if (state != State.RUNNING) {
            LOG.warn("getRunningService() called in state {} - services may not be fully initialized", state);
            return Optional.empty();
        }

        // Check ProviderControllers first (more specific via serviceClasses())
        for (ServiceLifecycle lifecycle : activeServices.values()) {
            if (lifecycle instanceof ProviderController<?> controller) {
                // Check if any of the service classes matches the requested type
                for (Class<?> serviceClass : controller.serviceClasses()) {
                    if (serviceType.isAssignableFrom(serviceClass)) {
                        try {
                            Object service = controller.provider();
                            if (serviceType.isInstance(service)) {
                                return Optional.of(serviceType.cast(service));
                            }
                        } catch (IllegalStateException e) {
                            LOG.debug("Provider not available from controller: {}", controller.getClass().getSimpleName());
                        }
                        break; // Found matching service class, no need to check others for this controller
                    }
                }
            }
        }

        // Fallback to generic getService() check for non-ProviderController services
        for (ServiceLifecycle lifecycle : activeServices.values()) {
            Optional<Object> serviceOpt = lifecycle.getService();
            if (serviceOpt.isPresent()) {
                Object service = serviceOpt.get();
                if (serviceType.isInstance(service)) {
                    return Optional.of(serviceType.cast(service));
                }
            }
        }

        return Optional.empty();
    }
}