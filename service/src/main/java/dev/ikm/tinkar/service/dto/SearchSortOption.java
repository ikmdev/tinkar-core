package dev.ikm.tinkar.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enum representing different sort options for search results.
 * Based on Komet's search sort options.
 */
@Schema(description = "Sort options for search results")
public enum SearchSortOption {
    /**
     * Groups results by top-level component (concept), sorted by relevance score.
     * Results with highest match scores appear first.
     */
    @Schema(description = "Group by top component, sorted by relevance score (highest first)")
    TOP_COMPONENT,

    /**
     * Groups results by top-level component (concept), sorted alphabetically.
     * Both the components and their child matches are sorted alphabetically.
     */
    @Schema(description = "Group by top component, sorted alphabetically")
    TOP_COMPONENT_ALPHA,

    /**
     * Shows individual matched description semantics, sorted by relevance score.
     * Results with highest match scores appear first.
     */
    @Schema(description = "Individual semantic matches, sorted by relevance score (highest first)")
    SEMANTIC,

    /**
     * Shows individual matched description semantics, sorted alphabetically.
     */
    @Schema(description = "Individual semantic matches, sorted alphabetically")
    SEMANTIC_ALPHA
}
