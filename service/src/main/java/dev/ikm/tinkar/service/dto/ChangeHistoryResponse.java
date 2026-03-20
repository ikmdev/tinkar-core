package dev.ikm.tinkar.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response object for entity change history queries.
 * Represents the chronology of changes made to an entity across different STAMP versions.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response containing change history for an entity")
public record ChangeHistoryResponse(
        @Schema(description = "The entity public ID (UUID)") String entityId,

        @Schema(description = "The entity description") String entityDescription,

        @Schema(description = "Total number of version changes") Integer totalVersions,

        @Schema(description = "List of version changes in chronological order") List<VersionChange> versionChanges,

        @Schema(description = "Whether the query was successful") Boolean success,

        @Schema(description = "Error message if query failed") String errorMessage) {

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
    public static ChangeHistoryResponse success(String entityId, String entityDescription,
                                                 List<VersionChange> versionChanges) {
        return new ChangeHistoryResponse(
                entityId,
                entityDescription,
                versionChanges != null ? versionChanges.size() : 0,
                versionChanges,
                true,
                null);
    }

    /**
     * Factory method to create an error response.
     */
    public static ChangeHistoryResponse error(String entityId, String errorMessage) {
        return new ChangeHistoryResponse(
                entityId,
                null,
                0,
                null,
                false,
                errorMessage);
    }
}
