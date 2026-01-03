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
package dev.ikm.tinkar.provider.search;

import dev.ikm.tinkar.common.alert.AlertStreams;
import dev.ikm.tinkar.common.service.*;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.common.util.time.Stopwatch;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provider for Lucene-based search indexing and searching services.
 * <p>
 * This provider manages the lifecycle of the Indexer and Searcher components,
 * ensuring they are properly initialized during the INDEXING phase and cleanly
 * shutdown when the application terminates.
 * </p>
 */
public class SearchProvider implements SearchService {
    private static final Logger LOG = LoggerFactory.getLogger(SearchProvider.class);
    private static final File defaultDataDirectory = new File("target/lucene/");

    private static final StableValue<SearchProvider> SINGLETON = StableValue.of();

    private final Indexer indexer;
    private final Searcher searcher;
    private final Path indexPath;
    private final String name;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public static SearchProvider get() {
        return SINGLETON.orElseSet(SearchProvider::new);
    }

    /**
     * Private constructor - construction is managed by the Controller via {@link #get()}.
     * Use {@link #get()} to obtain the singleton instance managed by StableValue.
     */
    private SearchProvider() {
        // Get DATA_STORE_ROOT - it should be set by the data provider in DATA_STORAGE phase
        File datastoreRoot = ServiceProperties.get(ServiceKeys.DATA_STORE_ROOT)
                .map(obj -> {
                    LOG.info("DATA_STORE_ROOT retrieved from ServiceProperties: {}", obj);
                    return (File) obj;
                })
                .orElseThrow(() -> {
                    LOG.error("DATA_STORE_ROOT not found in ServiceProperties");
                    LOG.error("SearchProvider must start after DATA_STORAGE phase completes");
                    LOG.error("Check ServiceLifecyclePhase configuration and ensure data provider sets DATA_STORE_ROOT");
                    return new IllegalStateException(
                        "DATA_STORE_ROOT must be set before SearchProvider starts. " +
                        "Ensure a data provider has been initialized in the DATA_STORAGE phase.");
                });
        this(datastoreRoot);
    }

    private SearchProvider(File datastoreRoot) {
        Stopwatch stopwatch = new Stopwatch();
        LOG.info("Opening " + this.getClass().getSimpleName());

        this.name = datastoreRoot.getName();

        Path indexPath = Path.of(datastoreRoot.getPath(), "lucene");
        boolean indexExists = Files.exists(indexPath);

        LOG.info("Index path: " + indexPath.toAbsolutePath());
        this.indexPath = indexPath;

        Indexer tempIndexer;
        try {
            tempIndexer = new Indexer(indexPath);
        } catch (IllegalArgumentException ex) {
            // If Indexer Codec does not match, then delete and rebuild with new Codec
            LOG.warn("Index codec mismatch, deleting and recreating index");
            try {
                FileUtil.recursiveDelete(indexPath.toFile());
                indexExists = false;
                tempIndexer = new Indexer(indexPath);
            } catch (IOException e) {
                throw new RuntimeException("Failed to recreate index after codec mismatch", e);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize indexer", e);
        }
        this.indexer = tempIndexer;

        try {
            this.searcher = new Searcher();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize searcher", e);
        }

        // Check if we need to recreate the index (data exists but index doesn't)
        // Look for any data provider directories (rocks, spinedarrays, mvstore.dat, etc.)
        boolean dataExists = checkDataExists(datastoreRoot);

        if (dataExists && !indexExists) {
            LOG.info("Database exists but Lucene index is missing, scheduling index recreation");
            try {
                this.recreateIndex().get();
                LOG.info("Lucene index recreation completed successfully");
            } catch (Exception e) {
                LOG.error("Failed to recreate Lucene index", e);
            }
        } else if (!dataExists) {
            LOG.info("No existing database found - Lucene index will be created as data is added");
        } else {
            LOG.info("Lucene index already exists");
        }

        stopwatch.stop();
        LOG.info("Opened " + this.getClass().getSimpleName() + " in: " + stopwatch.durationString());
    }

    @Override
    public void index(Object object) {
        if (closed.get()) {
            LOG.debug("SearchProvider is closed, skipping index operation");
            return;
        }
        indexer.index(object);
    }

    @Override
    public void commit() throws IOException {
        if (closed.get()) {
            LOG.debug("SearchProvider is closed, skipping commit");
            return;
        }
        indexer.commit();
    }

    @Override
    public PrimitiveDataSearchResult[] search(String query, int maxResultSize) throws Exception {
        LOG.debug("SearchProvider.search() called with query='{}', maxResultSize={}", query, maxResultSize);
        if (closed.get()) {
            LOG.error("SearchProvider is closed, cannot perform search");
            throw new IllegalStateException("SearchProvider is closed");
        }
        PrimitiveDataSearchResult[] results = searcher.search(query, maxResultSize);
        LOG.debug("SearchProvider.search() returning {} results", results != null ? results.length : 0);
        return results;
    }

    @Override
    public CompletableFuture<Void> recreateIndex() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return TinkExecutor.ioThreadPool().submit(new RecreateIndex(this.indexer)).get();
            } catch (InterruptedException | ExecutionException ex) {
                AlertStreams.dispatchToRoot(new CompletionException("Error encountered while creating Lucene indexes. " +
                        "Search and Type Ahead Suggestions may not function as expected.", ex));
            }
            return null;
        }, TinkExecutor.ioThreadPool());
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * Checks if data exists in the datastore root by looking for known data provider directories/files.
     * This is provider-agnostic and works with RocksDB, MVStore, SpinedArray, etc.
     *
     * @param datastoreRoot the root directory to check
     * @return true if data exists, false otherwise
     */
    private boolean checkDataExists(File datastoreRoot) {
        if (!datastoreRoot.exists()) {
            return false;
        }

        // Check for common data provider indicators
        File[] indicators = {
            new File(datastoreRoot, "rocks"),           // RocksDB
            new File(datastoreRoot, "mvstore.dat"),     // MVStore
            new File(datastoreRoot, "nidToByteArrayMap"), // SpinedArray
            new File(datastoreRoot, "nidToPatternNidMap") // SpinedArray
        };

        for (File indicator : indicators) {
            if (indicator.exists()) {
                LOG.debug("Found data indicator: {}", indicator.getName());
                return true;
            }
        }

        // Also check if the directory has any significant content (not just lucene folder)
        File[] files = datastoreRoot.listFiles();
        if (files != null) {
            for (File file : files) {
                // Ignore lucene directory and hidden files
                if (!file.getName().equals("lucene") && !file.getName().startsWith(".")) {
                    if (file.isDirectory() && file.list() != null && file.list().length > 0) {
                        LOG.debug("Found non-empty directory: {}", file.getName());
                        return true;
                    } else if (file.isFile() && file.length() > 0) {
                        LOG.debug("Found non-empty file: {}", file.getName());
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) {
            LOG.info("SearchProvider already closed");
            return;
        }

        LOG.info("Closing SearchProvider...");

        try {
            indexer.commit();
        } catch (IOException e) {
            LOG.warn("Error committing index during close", e);
        }

        try {
            searcher.close();
        } catch (IOException e) {
            LOG.warn("Error closing Searcher", e);
        }

        try {
            indexer.close();
        } catch (IOException e) {
            LOG.warn("Error closing Indexer", e);
        }

        LOG.info("SearchProvider closed.");
    }

    /**
     * Controller for SearchProvider lifecycle management.
     * <p>
     * Manages the lifecycle of search services during the INDEXING phase,
     * which occurs after DATA_STORAGE but before CORE_SERVICES.
     * This is NOT a user-selectable data source - it runs automatically.
     * </p>
     *
     * <h3>Type Relationship</h3>
     * <p>
     * This controller extends {@code ProviderController<SearchProvider>} where {@code SearchProvider}
     * is the concrete implementation class. The {@link #serviceClasses()} method returns
     * {@code SearchService.class}, which {@code SearchProvider} implements. This establishes
     * the contract that {@code SearchProvider implements SearchService}.
     * </p>
     *
     * <h3>Service Discovery</h3>
     * <p>
     * Other services discover SearchProvider by requesting SearchService:
     * <pre>{@code
     * SearchService searchService = ServiceLifecycleManager.get()
     *     .getRunningService(SearchService.class)
     *     .orElseThrow();
     * }</pre>
     * </p>
     */
    public static class Controller extends ProviderController<SearchProvider> {

        @Override
        protected SearchProvider createProvider() throws Exception {
            return SearchProvider.get();
        }

        @Override
        protected void startProvider(SearchProvider provider) {
            // Provider starts itself during get()
        }

        @Override
        protected void stopProvider(SearchProvider provider) {
            try {
                provider.close();
            } catch (IOException e) {
                throw new RuntimeException("Failed to close SearchProvider", e);
            }
        }

        @Override
        protected void cleanupProvider(SearchProvider provider) throws Exception {
            // close() already handles commit and cleanup
        }

        @Override
        protected String getProviderName() {
            return "SearchProvider";
        }

        @Override
        public ImmutableList<Class<?>> serviceClasses() {
            // SearchProvider (the generic type parameter P) implements SearchService
            // This establishes the contract: ProviderController<SearchProvider> provides SearchService
            return Lists.immutable.of(SearchService.class);
        }

        @Override
        public ServiceLifecyclePhase getLifecyclePhase() {
            return ServiceLifecyclePhase.INDEXING;
        }

        @Override
        public Optional<ServiceExclusionGroup> getMutualExclusionGroup() {
            return Optional.empty();
        }

        @Override
        public Optional<Object> getService() {
            return Optional.of(requireProvider());
        }

        @Override
        public int getSubPriority() {
            return 10; // Start early in INDEXING phase
        }
    }
}
