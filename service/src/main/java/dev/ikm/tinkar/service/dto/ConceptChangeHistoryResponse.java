package dev.ikm.tinkar.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response object for comprehensive concept change history queries.
 * Includes changes to the concept itself AND all attached semantics (comments, descriptions, etc.).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response containing comprehensive change history for a concept and its semantics")
public record ConceptChangeHistoryResponse(
        @Schema(description = "The concept public ID (UUID)") String conceptId,

        @Schema(description = "The concept description") String conceptDescription,

        @Schema(description = "Changes to the concept itself") List<VersionChange> conceptChanges,

        @Schema(description = "Changes to semantics attached to this concept") List<SemanticChangeHistory> semanticChanges,

        @Schema(description = "Total number of changes across concept and all semantics") Integer totalChanges,

        @Schema(description = "Whether the query was successful") Boolean success,

        @Schema(description = "Error message if query failed") String errorMessage) {

    /**
     * Change history for a single semantic.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Change history for a semantic")
    public record SemanticChangeHistory(
            @Schema(description = "Semantic public ID (UUID)") String semanticId,

            @Schema(description = "Pattern name (e.g., Comment Pattern, Description Pattern)") String patternName,

            @Schema(description = "Brief description of the semantic content") String summary,

            @Schema(description = "Version changes for this semantic") List<VersionChange> versionChanges) {
    }

    /**
     * A single version change at a specific STAMP.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "A version change at a specific STAMP")
    public record VersionChange(
            @Schema(description = "STAMP information for this version") StampInfo stamp,

            @Schema(description = "List of field changes in this version") List<FieldChange> fieldChanges) {
    }

    /**
     * STAMP information for a version.
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
     * A single field change showing prior and current values.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "A field change with prior and current values")
    public record FieldChange(
            @Schema(description = "Field name or description") String fieldName,

            @Schema(description = "Index of the field in the pattern") Integer fieldIndex,

            @Schema(description = "Prior value (null if new field)") String priorValue,

            @Schema(description = "Current value") String currentValue,

            @Schema(description = "Type of change: ADDED, MODIFIED, REMOVED") String changeType) {
    }

    /**
     * Factory method to create a successful response.
     */
    public static ConceptChangeHistoryResponse success(String conceptId, String conceptDescription,
                                                        List<VersionChange> conceptChanges,
                                                        List<SemanticChangeHistory> semanticChanges) {
        int totalChanges = (conceptChanges != null ? conceptChanges.size() : 0) +
                (semanticChanges != null ? semanticChanges.stream()
                        .mapToInt(s -> s.versionChanges() != null ? s.versionChanges().size() : 0)
                        .sum() : 0);

        return new ConceptChangeHistoryResponse(
                conceptId,
                conceptDescription,
                conceptChanges,
                semanticChanges,
                totalChanges,
                true,
                null);
    }

    /**
     * Factory method to create an error response.
     */
    public static ConceptChangeHistoryResponse error(String conceptId, String errorMessage) {
        return new ConceptChangeHistoryResponse(
                conceptId,
                null,
                null,
                null,
                0,
                false,
                errorMessage);
    }
}
