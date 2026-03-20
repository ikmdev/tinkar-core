package dev.ikm.tinkar.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response object for semantics attached to a concept.
 * Used to list comments, descriptions, and other semantics associated with a concept.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response containing semantics attached to a concept")
public record ConceptSemanticsResponse(
        @Schema(description = "The concept public ID (UUID)") String conceptId,

        @Schema(description = "The concept description") String conceptDescription,

        @Schema(description = "Total number of semantics found") Integer totalCount,

        @Schema(description = "List of semantics attached to this concept") List<SemanticInfo> semantics,

        @Schema(description = "Whether the query was successful") Boolean success,

        @Schema(description = "Error message if query failed") String errorMessage) {

    /**
     * Information about a single semantic attached to a concept.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Information about a semantic")
    public record SemanticInfo(
            @Schema(description = "Semantic public ID (UUID)") String semanticId,

            @Schema(description = "Pattern name (e.g., Comment Pattern, Description Pattern)") String patternName,

            @Schema(description = "Field values of the semantic") List<FieldValue> fields,

            @Schema(description = "STAMP information") StampInfo stamp) {
    }

    /**
     * A field value within a semantic.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "A field value in a semantic")
    public record FieldValue(
            @Schema(description = "Field index") Integer index,

            @Schema(description = "Field value as string") String value) {
    }

    /**
     * STAMP information for a semantic version.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "STAMP version information")
    public record StampInfo(
            @Schema(description = "Status (e.g., Active, Inactive)") String status,

            @Schema(description = "Author name") String author,

            @Schema(description = "Module name") String module,

            @Schema(description = "Path name") String path,

            @Schema(description = "Timestamp in epoch milliseconds") Long time,

            @Schema(description = "Human-readable timestamp") String formattedTime) {
    }

    /**
     * Factory method to create a successful response.
     */
    public static ConceptSemanticsResponse success(String conceptId, String conceptDescription,
                                                    List<SemanticInfo> semantics) {
        return new ConceptSemanticsResponse(
                conceptId,
                conceptDescription,
                semantics != null ? semantics.size() : 0,
                semantics,
                true,
                null);
    }

    /**
     * Factory method to create an error response.
     */
    public static ConceptSemanticsResponse error(String conceptId, String errorMessage) {
        return new ConceptSemanticsResponse(
                conceptId,
                null,
                0,
                null,
                false,
                errorMessage);
    }
}
