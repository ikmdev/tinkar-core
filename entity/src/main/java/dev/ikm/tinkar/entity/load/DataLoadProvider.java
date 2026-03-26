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
package dev.ikm.tinkar.entity.load;

import dev.ikm.tinkar.common.service.*;
import dev.ikm.tinkar.common.service.EntityCountSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.Future;

/**
 * Provider implementation for data loading service.
 * Singleton instance manages the queue of files to load.
 * <p>This provider uses LoadEntitiesFromProtobufFile (which extends TrackingCallable)
 * to load each file, providing progress tracking capability.
 *
 * <p><b>Singleton Lifecycle</b></p>
 * <p>This class uses a singleton pattern that spans multiple service lifecycle phases:
 * <ol>
 *   <li><b>Creation (typically DATA_STORAGE phase):</b> First call to {@link #get()} creates
 *       the singleton instance. This usually happens when a data provider (RocksDB, SpinedArray, etc.)
 *       queues files during their initialization.</li>
 *   <li><b>Queuing (DATA_STORAGE through INDEXING phases):</b> Data providers and configuration
 *       services call {@link #addFile(File)} to queue files. The singleton persists across phases.</li>
 *   <li><b>Loading (DATA_LOAD phase):</b> DataLoadController calls {@link #loadAll()} to process
 *       all queued files. This happens after ENTITIES and INDEXING phases complete, ensuring
 *       all required services are available.</li>
 * </ol>
 * <p>The singleton is created on-demand but persists for the application lifetime, allowing
 * early queuing and late loading. This separation ensures files are loaded only after
 * SearchService and EntityProvider are ready.
 */
public class DataLoadProvider implements DataLoadService {

    private static final Logger LOG = LoggerFactory.getLogger(DataLoadProvider.class);
    private static final StableValue<DataLoadProvider> SINGLETON = StableValue.of();

    private final List<File> loadQueue = new ArrayList<>();
    private volatile boolean loadingAllowed = false;
    private final long creationTimestamp;

    /**
     * Gets the singleton instance, creating it on first access.
     * <p>     * This method is typically first called during DATA_STORAGE phase when data providers
     * queue import files. The same instance is reused throughout the application lifecycle.
     *
     * @return the singleton DataLoadProvider instance
     */
    public static DataLoadProvider get() {
        return SINGLETON.orElseSet(DataLoadProvider::new);
    }

    private DataLoadProvider() {
        this.creationTimestamp = System.currentTimeMillis();
        LOG.info("═══════════════════════════════════════════════════════════");
        LOG.info("DataLoadProvider singleton CREATED");
        LOG.info("  Thread: {}", Thread.currentThread().getName());
        LOG.info("  Phase: Likely DATA_STORAGE (files can now be queued)");
        LOG.info("  Loading: Will occur later in DATA_LOAD phase");
        LOG.info("═══════════════════════════════════════════════════════════");
    }

    /**
     * Enables loading. Called by DataLoadController when DATA_LOAD phase starts.
     * This is a defensive mechanism to prevent premature loading.
     */
    void enableLoading() {
        if (!loadingAllowed) {
            long elapsedMs = System.currentTimeMillis() - creationTimestamp;
            LOG.info("═══════════════════════════════════════════════════════════");
            LOG.info("DataLoadProvider loading ENABLED");
            LOG.info("  Time since creation: {} ms", elapsedMs);
            LOG.info("  Queued files: {}", loadQueue.size());
            LOG.info("  Ready to load in DATA_LOAD phase");
            LOG.info("═══════════════════════════════════════════════════════════");
            loadingAllowed = true;
        }
    }

    @Override
    public synchronized DataLoadService addFile(File file) {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("File does not exist: " + file);
        }
        loadQueue.add(file);
        LOG.info("Queued file #{} for DATA_LOAD phase: {}", loadQueue.size(), file.getName());
        LOG.debug("  Full path: {}", file.getAbsolutePath());
        LOG.debug("  Size: {} bytes", file.length());
        return this;
    }

    @Override
    public synchronized DataLoadService addFiles(List<File> files) {
        if (files != null) {
            files.forEach(this::addFile);
        }
        return this;
    }

    @Override
    public synchronized EntityCountSummary loadAll() throws Exception {
        // Defensive check: Ensure we're in DATA_LOAD phase
        if (!loadingAllowed) {
            throw new IllegalStateException(
                    "Cannot load files before DATA_LOAD phase. " +
                    "DataLoadController must call enableLoading() first. " +
                    "This indicates a lifecycle ordering problem.");
        }

        if (loadQueue.isEmpty()) {
            LOG.info("No files to load");
            return new EntityCountSummary(0, 0, 0, 0);
        }

        LOG.info("═══════════════════════════════════════════════════════════");
        LOG.info("Starting DATA_LOAD: Loading {} file(s) in order", loadQueue.size());
        LOG.info("═══════════════════════════════════════════════════════════");

        long totalConcepts = 0;
        long totalSemantics = 0;
        long totalPatterns = 0;
        long totalStamps = 0;

        for (int i = 0; i < loadQueue.size(); i++) {
            File file = loadQueue.get(i);
            LOG.info("Loading file {} of {}: {}", i + 1, loadQueue.size(), file.getName());

            // Use LoadDataFromFileController to load each file
            // This uses LoadEntitiesFromProtobufFile which implements TrackingCallable
            ServiceLoader<LoadDataFromFileController> controllerFinder =
                    PluggableService.load(LoadDataFromFileController.class);
            LoadDataFromFileController loader = controllerFinder.findFirst()
                    .orElseThrow(() -> new IllegalStateException("No LoadDataFromFileController found"));

            Future<?> loadFuture = loader.load(file);
            Object result = loadFuture.get(); // Block until complete

            if (result instanceof EntityCountSummary summary) {
                totalConcepts += summary.conceptCount();
                totalSemantics += summary.semanticCount();
                totalPatterns += summary.patternCount();
                totalStamps += summary.stampCount();
                LOG.info("Loaded file {}: {}", file.getName(), summary);
            }
        }

        EntityCountSummary totalSummary = new EntityCountSummary(
                totalConcepts, totalSemantics, totalPatterns, totalStamps);

        LOG.info("═══════════════════════════════════════════════════════════");
        LOG.info("DATA_LOAD Complete: {}", totalSummary);
        LOG.info("  Concepts:  {}", totalConcepts);
        LOG.info("  Semantics: {}", totalSemantics);
        LOG.info("  Patterns:  {}", totalPatterns);
        LOG.info("  Stamps:    {}", totalStamps);
        LOG.info("═══════════════════════════════════════════════════════════");
        return totalSummary;
    }

    @Override
    public Future<EntityCountSummary> loadAllAsync() {
        return TinkExecutor.ioThreadPool().submit(this::loadAll);
    }

    @Override
    public synchronized List<File> getQueuedFiles() {
        return Collections.unmodifiableList(new ArrayList<>(loadQueue));
    }

    @Override
    public synchronized void clear() {
        LOG.info("Clearing load queue ({} files)", loadQueue.size());
        loadQueue.clear();
    }
}
