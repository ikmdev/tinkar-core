package dev.ikm.tinkar.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Summary of entity counts from an import or export operation")
public record EntityCountSummaryResponse(
        @Schema(description = "Number of concepts") Long conceptsCount,
        @Schema(description = "Number of semantics") Long semanticsCount,
        @Schema(description = "Number of patterns") Long patternsCount,
        @Schema(description = "Number of stamps") Long stampsCount,
        @Schema(description = "Total entity count") Long totalCount,
        @Schema(description = "Whether the operation was successful") Boolean success,
        @Schema(description = "Error message if operation failed") String errorMessage) {

    public static EntityCountSummaryResponse success(long concepts, long semantics,
            long patterns, long stamps) {
        long total = concepts + semantics + patterns + stamps;
        return new EntityCountSummaryResponse(concepts, semantics, patterns, stamps,
                total, true, null);
    }

    public static EntityCountSummaryResponse error(String errorMessage) {
        return new EntityCountSummaryResponse(null, null, null, null, null, false, errorMessage);
    }
}
