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


import java.io.File;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Service for loading data files during the DATA_LOAD phase.
 * <p>This service provides a unified mechanism for loading protobuf changeset files
 * into the data store. It is designed to be provider-agnostic - the same changeset
 * format works with RocksDB, SpinedArray, MVStore, and Ephemeral providers.
 * <p>The service supports loading multiple files in order and provides progress tracking
 * through TrackingCallable implementations.
 * <p>Example usage:
 * <pre>{@code
 * // During provider initialization (DATA_STORAGE phase):
 * DataLoadService loadService = DataLoadProvider.get();
 * loadService.addFile(new File("starter-data.pb.zip"));
 *
 * // DataLoadController will load files during DATA_LOAD phase
 * }</pre>
 */
public interface DataLoadService {

    /**
     * Adds a file to the load queue.
     * Files are loaded in the order they are added.
     *
     * @param file protobuf changeset file (.pb.zip or .zip)
     * @return this service for method chaining
     * @throws IllegalArgumentException if file is null or does not exist
     */
    DataLoadService addFile(File file);

    /**
     * Adds multiple files to the load queue.
     * Files are loaded in the order provided.
     *
     * @param files protobuf changeset files
     * @return this service for method chaining
     */
    DataLoadService addFiles(List<File> files);

    /**
     * Loads all queued files in order.
     * This method blocks until all files are loaded.
     * Uses LoadEntitiesFromProtobufFile which implements TrackingCallable
     * for progress reporting.
     *
     * @return summary of all entities loaded across all files
     * @throws Exception if loading fails
     */
    EntityCountSummary loadAll() throws Exception;

    /**
     * Loads all queued files asynchronously.
     *
     * @return future that completes when all files are loaded
     */
    Future<EntityCountSummary> loadAllAsync();

    /**
     * Returns the list of files queued for loading.
     *
     * @return immutable list of files
     */
    List<File> getQueuedFiles();

    /**
     * Clears the load queue without loading any files.
     */
    void clear();
}
