package dev.ikm.tinkar.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response object for descendant creation/removal operations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response for descendant operations")
public record DescendantOperationResponse(
        @Schema(description = "The parent concept ID") String parentConceptId,

        @Schema(description = "The descendant concept ID") String descendantConceptId,

        @Schema(description = "Description of the descendant concept") String descendantDescription,

        @Schema(description = "The operation performed (CREATED or REMOVED)") String operation,

        @Schema(description = "Whether the operation was successful") Boolean success,

        @Schema(description = "Error message if operation failed") String errorMessage) {

    /**
     * Factory method to create a successful response.
     */
    public static DescendantOperationResponse success(String parentConceptId, String descendantConceptId,
                                                       String descendantDescription, String operation) {
        return new DescendantOperationResponse(
                parentConceptId,
                descendantConceptId,
                descendantDescription,
                operation,
                true,
                null);
    }

    /**
     * Factory method to create an error response.
     */
    public static DescendantOperationResponse error(String parentConceptId, String descendantConceptId,
                                                     String errorMessage) {
        return new DescendantOperationResponse(
                parentConceptId,
                descendantConceptId,
                null,
                null,
                false,
                errorMessage);
    }
}
