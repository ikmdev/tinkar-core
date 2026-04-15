package dev.ikm.tinkar.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response object for concept search queries with sort options.
 * Supports both grouped (TOP_COMPONENT) and flat (SEMANTIC) result structures.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response containing search query results with sort options")
public record ConceptSearchResponse(
        @Schema(description = "The original search query", example = "diabetes")
        String query,

        @Schema(description = "Total number of matching concepts", example = "42")
        Long totalCount,

        @Schema(description = "The sort option used", example = "TOP_COMPONENT")
        SearchSortOption sortBy,

        @Schema(description = "List of search results (flat structure for SEMANTIC modes)")
        List<SemanticSearchResult> results,

        @Schema(description = "List of grouped results (for TOP_COMPONENT modes)")
        List<GroupedSearchResult> groupedResults,

        @Schema(description = "Whether the search was successful", example = "true")
        Boolean success,

        @Schema(description = "Error message if search failed", example = "null")
        String errorMessage) {

    /**
     * Individual semantic search result with score and highlighted text.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "A single semantic search result")
    public record SemanticSearchResult(
            @Schema(description = "Public ID (list of UUIDs) of the concept")
            List<String> publicId,

            @Schema(description = "Fully qualified name of the concept")
            String fullyQualifiedName,

            @Schema(description = "Regular/preferred description")
            String regularName,

            @Schema(description = "The matched text with highlighting (HTML bold tags)")
            String highlightedText,

            @Schema(description = "The relevance score of this match", example = "0.95")
            Float score,

            @Schema(description = "Whether the concept is active")
            Boolean active) {
    }

    /**
     * Grouped search result containing a top-level concept and its matching semantics.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "A grouped search result by top-level component")
    public record GroupedSearchResult(
            @Schema(description = "Public ID (list of UUIDs) of the top-level concept")
            List<String> publicId,

            @Schema(description = "Fully qualified name of the top-level concept")
            String fullyQualifiedName,

            @Schema(description = "Whether the concept is active")
            Boolean active,

            @Schema(description = "The highest relevance score among matching semantics", example = "0.95")
            Float topScore,

            @Schema(description = "List of matching semantics for this concept")
            List<MatchingSemantic> matchingSemantics,

            @Schema(description = "The NID of the top-level concept entity")
            Integer conceptNid) {
    }

    /**
     * A matching semantic within a grouped result.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "A matching semantic within a concept group")
    public record MatchingSemantic(
            @Schema(description = "The matched text with highlighting (HTML bold tags)")
            String highlightedText,

            @Schema(description = "The plain text without highlighting")
            String plainText,

            @Schema(description = "The relevance score of this match", example = "0.95")
            Float score,

            @Schema(description = "The index of the matched field within the semantic")
            Integer fieldIndex,

            @Schema(description = "The NID of the matching semantic entity")
            Integer semanticNid) {
    }

    /**
     * Factory method to create a successful flat (semantic) response.
     */
    public static ConceptSearchResponse successFlat(String query, SearchSortOption sortBy,
            List<SemanticSearchResult> results) {
        return new ConceptSearchResponse(
                query,
                results != null ? (long) results.size() : 0L,
                sortBy,
                results,
                null,
                true,
                null);
    }

    /**
     * Factory method to create a successful grouped (top component) response.
     */
    public static ConceptSearchResponse successGrouped(String query, SearchSortOption sortBy,
            List<GroupedSearchResult> groupedResults, long totalSemanticCount) {
        return new ConceptSearchResponse(
                query,
                totalSemanticCount,
                sortBy,
                null,
                groupedResults,
                true,
                null);
    }

    /**
     * Factory method to create a successful empty response (no results, no error).
     * Used when the database has no starter data yet (fresh DB).
     */
    public static ConceptSearchResponse empty(String query) {
        return new ConceptSearchResponse(
                query,
                0L,
                null,
                List.of(),
                List.of(),
                true,
                null);
    }

    /**
     * Factory method to create an error response.
     */
    public static ConceptSearchResponse error(String query, String errorMessage) {
        return new ConceptSearchResponse(
                query,
                0L,
                null,
                null,
                null,
                false,
                errorMessage);
    }
}
