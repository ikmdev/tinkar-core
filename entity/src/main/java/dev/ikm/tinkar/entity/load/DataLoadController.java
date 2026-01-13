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
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;

/**
 * Singleton service for loading protobuf changeset files during DATA_LOAD phase.
 * <p>
 * This controller provides a unified, provider-agnostic mechanism for loading
 * data files. It runs after all infrastructure (DATA_STORAGE, ENTITIES, INDEXING)
 * is ready, ensuring SearchService and EntityProvider are available.
 * </p>
 * <p>
 * The controller automatically:
 * <ul>
 *   <li>Collects files from all ChangeSetImportConfiguration services</li>
 *   <li>Loads files queued by data providers during DATA_STORAGE phase</li>
 *   <li>Loads all files in order using LoadEntitiesFromProtobufFile (TrackingCallable)</li>
 *   <li>Saves the datastore after loading completes</li>
 * </ul>
 * </p>
 * <p>
 * Usage example for data providers:
 * <pre>{@code
 * // During provider initialization (DATA_STORAGE phase):
 * DataLoadService loadService = DataLoadProvider.get();
 * loadService.addFile(new File("starter-data.pb.zip"));
 *
 * // DataLoadController will automatically load during DATA_LOAD phase
 * }</pre>
 * </p>
 */
public class DataLoadController extends ProviderController<DataLoadProvider>
        implements ServiceLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(DataLoadController.class);

    @Override
    protected DataLoadProvider createProvider() {
        return DataLoadProvider.get();
    }

    @Override
    protected void startProvider(DataLoadProvider provider) throws Exception {
        LOG.info("DataLoadController entering DATA_LOAD phase");

        // Collect changeset files from all ChangeSetImportConfiguration services
        collectChangeSetImports(provider);

        // Enable loading now that we're in DATA_LOAD phase (defensive check)
        provider.enableLoading();

        // Load all queued files
        if (!provider.getQueuedFiles().isEmpty()) {
            LOG.info("DataLoadController: {} file(s) queued, beginning load", provider.getQueuedFiles().size());
            EntityCountSummary summary = provider.loadAll();
            LOG.info("DataLoadController load completed: {}", summary);

            // Save after loading
            LOG.info("Saving datastore after loading...");
            PrimitiveData.save();
            LOG.info("Datastore saved successfully");

            // Recreate Lucene index after data import
            recreateLuceneIndexAfterLoad();
        } else {
            LOG.info("DataLoadController: No files queued for loading");
        }
    }

    /**
     * Recreates the Lucene index after data has been loaded.
     * <p>
     * This ensures the search index is updated with all newly imported entities.
     * The index recreation is performed asynchronously via PrimitiveData service
     * and we wait for it to complete to ensure search is functional before proceeding.
     * </p>
     */
    private void recreateLuceneIndexAfterLoad() {
        LOG.info("═══════════════════════════════════════════════════════════");
        LOG.info("Recreating Lucene index after data import...");
        LOG.info("═══════════════════════════════════════════════════════════");

        try {
            // Use PrimitiveData.get() to access the data provider's recreateLuceneIndex() method
            // This delegates to the underlying search service without requiring direct dependency
            CompletableFuture<Void> indexFuture = PrimitiveData.get().recreateLuceneIndex();

            LOG.info("Index recreation started, waiting for completion...");
            indexFuture.get(); // Block until index recreation completes

            LOG.info("═══════════════════════════════════════════════════════════");
            LOG.info("Lucene index recreation completed successfully");
            LOG.info("Search functionality is now available");
            LOG.info("═══════════════════════════════════════════════════════════");
        } catch (Exception e) {
            LOG.error("═══════════════════════════════════════════════════════════");
            LOG.error("Failed to recreate Lucene index after data load", e);
            LOG.error("Search functionality may not work correctly");
            LOG.error("You may need to manually recreate the index");
            LOG.error("═══════════════════════════════════════════════════════════");
        }
    }

    /**
     * Collects changeset files from all services implementing ChangeSetImportConfiguration.
     */
    private void collectChangeSetImports(DataLoadProvider provider) {
        ServiceLoader<ChangeSetImportConfiguration> configLoader =
                PluggableService.load(ChangeSetImportConfiguration.class);

        configLoader.stream().forEach(configProvider -> {
            try {
                ChangeSetImportConfiguration config = configProvider.get();
                List<File> files = config.getChangeSetFilesToImport();
                if (files != null && !files.isEmpty()) {
                    LOG.info("Adding {} changeset file(s) from {}",
                            files.size(), config.getClass().getSimpleName());
                    provider.addFiles(files);
                }
            } catch (Exception e) {
                LOG.error("Error collecting changeset imports from {}: {}",
                        configProvider.type().getName(), e.getMessage(), e);
            }
        });
    }

    @Override
    protected void stopProvider(DataLoadProvider provider) {
        // Nothing to stop - provider is stateless after loading
    }

    @Override
    protected String getProviderName() {
        return "DataLoadController";
    }

    @Override
    public ImmutableList<Class<?>> serviceClasses() {
        return Lists.immutable.of(DataLoadService.class);
    }

    @Override
    public ServiceLifecyclePhase getLifecyclePhase() {
        return ServiceLifecyclePhase.DATA_LOAD;
    }

    @Override
    public int getSubPriority() {
        return 10; // Start early in DATA_LOAD phase
    }

    @Override
    public Optional<ServiceExclusionGroup> getMutualExclusionGroup() {
        return Optional.empty();
    }
}
