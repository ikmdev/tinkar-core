package dev.ikm.tinkar.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response object for Tinkar search queries.
 * Designed to be compatible with both REST and gRPC implementations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response containing search query results")
public record TinkarSearchQueryResponse(
                @Schema(description = "The original search query", example = "diabetes") String query,

                @Schema(description = "Total number of results found", example = "42") Long totalCount,

                @Schema(description = "List of search results") List<SearchResult> results,

                @Schema(description = "Whether the search was successful", example = "true") Boolean success,

                @Schema(description = "Error message if search failed", example = "null") String errorMessage) {

        /**
         * Descriptions associated with a concept.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Concept descriptions")
        public record Descriptions(
                        @Schema(description = "Fully qualified name of the concept") String fullyQualifiedName,

                        @Schema(description = "Regular/preferred description text") String regularName,

                        @Schema(description = "Additional definition or description text") String definition) {
        }

        /**
         * STAMP version info for a concept.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "STAMP version information")
        public record Stamp(
                        @Schema(description = "Status concept public ID (UUID)") String statusPublicId,

                        @Schema(description = "Author concept public ID (UUID)") String authorPublicId,

                        @Schema(description = "Module concept public ID (UUID)") String modulePublicId,

                        @Schema(description = "Path concept public ID (UUID)") String pathPublicId,

                        @Schema(description = "Timestamp in epoch milliseconds") Long time) {
        }

        /**
         * Individual search result item matching TinkarSearchResult proto.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "A single search result")
        public record SearchResult(
                        @Schema(description = "Public ID (list of UUIDs)") List<String> publicId,

                        @Schema(description = "Descriptions associated with this concept") Descriptions descriptions,

                        @Schema(description = "STAMP version info") Stamp stamp) {
        }

        /**
         * Factory method to create a successful response.
         */
        public static TinkarSearchQueryResponse success(String query, List<SearchResult> results) {
                return new TinkarSearchQueryResponse(
                                query,
                                results != null ? (long) results.size() : 0L,
                                results,
                                true,
                                null);
        }

        /**
         * Factory method to create an error response.
         */
        public static TinkarSearchQueryResponse error(String query, String errorMessage) {
                return new TinkarSearchQueryResponse(
                                query,
                                0L,
                                null,
                                false,
                                errorMessage);
        }
}
