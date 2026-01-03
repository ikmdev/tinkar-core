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

import dev.ikm.tinkar.common.service.PrimitiveDataSearchResult;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for search indexing and searching functionality.
 * <p>
 * Provides access to Lucene-based full-text search capabilities for Tinkar entities.
 * This service manages the lifecycle of the search index and provides methods for
 * indexing content and performing searches.
 * </p>
 */
public interface SearchService {

    /**
     * Indexes an object in the search index.
     * <p>
     * Currently supports SemanticEntity objects. Other object types are ignored.
     * </p>
     *
     * @param object the object to index
     */
    void index(Object object);

    /**
     * Commits any pending changes to the search index.
     *
     * @throws IOException if an error occurs during commit
     */
    void commit() throws IOException;

    /**
     * Performs a search query against the index.
     *
     * @param query the search query string
     * @param maxResultSize maximum number of results to return
     * @return array of search results
     * @throws Exception if an error occurs during search
     */
    PrimitiveDataSearchResult[] search(String query, int maxResultSize) throws Exception;

    /**
     * Recreates the entire Lucene index from scratch.
     * <p>
     * This is an expensive operation that should only be performed when necessary,
     * such as when the index is corrupted or when upgrading to a new index format.
     * </p>
     *
     * @return a CompletableFuture that completes when the index recreation is done
     */
    CompletableFuture<Void> recreateIndex();

    /**
     * Returns the name of this search service implementation.
     *
     * @return service name
     */
    String name();
}
