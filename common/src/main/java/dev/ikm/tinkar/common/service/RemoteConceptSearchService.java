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

import java.util.List;
import java.util.UUID;

/**
 * Search contract for backends that can return rich, grouped/semantic results and
 * hydrate a full concept graph on demand, in addition to the flat {@link SearchService}
 * contract.
 *
 * <p>Discovered like any other lifecycle-managed service:
 * <pre>{@code
 * Optional<RemoteConceptSearchService> remote = ServiceLifecycleManager.get()
 *     .getRunningService(RemoteConceptSearchService.class);
 * if (remote.isPresent()) {
 *     // use remote.get().searchGrouped(...) / .searchFlat(...)
 * } else {
 *     // fall back to local search
 * }
 * }</pre>
 * Presence of the service in the returned {@code Optional} is itself the activity signal —
 * there is no separate {@code isActive()} check.
 */
public interface RemoteConceptSearchService {

    /**
     * Sort options mirroring common UI sort controls.
     */
    enum SortOption {
        TOP_COMPONENT,
        TOP_COMPONENT_ALPHA,
        SEMANTIC,
        SEMANTIC_ALPHA
    }

    /**
     * A single semantic match within a {@link GroupedResult}.
     *
     * @param highlightedText matched text with {@code <B>…</B>} markup
     * @param plainText       plain text without HTML markup
     * @param score           relevance score
     */
    record MatchingSemantic(String highlightedText, String plainText, float score) {}

    /**
     * A top-level (grouped) search result — one per matching concept.
     *
     * @param publicId           stable UUIDs identifying the concept
     * @param fullyQualifiedName FQN of the concept
     * @param active             whether the concept is currently active
     * @param topScore           highest relevance score among child semantics
     * @param matchingSemantics  child semantic matches
     */
    record GroupedResult(
            List<String> publicId,
            String fullyQualifiedName,
            boolean active,
            float topScore,
            List<MatchingSemantic> matchingSemantics) {}

    /**
     * A flat semantic search result (SEMANTIC sort modes) — one per matched semantic.
     *
     * @param publicId           stable UUIDs identifying the concept
     * @param fullyQualifiedName FQN of the concept
     * @param highlightedText    matched text with {@code <B>…</B>} markup
     * @param active             whether the concept is currently active
     * @param score              relevance score
     */
    record SemanticResult(
            List<String> publicId,
            String fullyQualifiedName,
            String highlightedText,
            boolean active,
            float score) {}

    /**
     * Performs a search returning grouped results (TOP_COMPONENT modes).
     */
    List<GroupedResult> searchGrouped(String query, int maxResults, SortOption sortOption);

    /**
     * Performs a search returning flat semantic results (SEMANTIC modes).
     */
    List<SemanticResult> searchFlat(String query, int maxResults, SortOption sortOption);

    /**
     * Fetches the full entity graph for a concept from the remote backend and loads it
     * into the local entity store, so subsequent local lookups (name, parents, axioms)
     * resolve without another round trip.
     *
     * @param publicIds the concept's public UUIDs (from a search result)
     * @return the local NID assigned to the concept after loading
     */
    int loadConceptWithSemantics(List<UUID> publicIds);
}
