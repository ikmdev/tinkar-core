package dev.ikm.tinkar.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Results from running the reasoner classification pipeline")
public record ReasonerResultsResponse(
        @Schema(description = "Number of concepts in the classification set") Integer classifiedConceptCount,
        @Schema(description = "Number of concepts with inferred changes") Integer inferredChangesCount,
        @Schema(description = "Number of concepts with navigation changes") Integer navigationChangesCount,
        @Schema(description = "Number of equivalent concept sets detected") Integer equivalentSetsCount,
        @Schema(description = "Number of concepts with cycles detected") Integer cyclesCount,
        @Schema(description = "Number of orphaned concepts detected") Integer orphansCount,
        @Schema(description = "Duration of the reasoner pipeline in milliseconds") Long durationMs,
        @Schema(description = "Whether the operation was successful") Boolean success,
        @Schema(description = "Error message if operation failed") String errorMessage) {

    public static ReasonerResultsResponse success(int classifiedConceptCount,
            int inferredChangesCount, int navigationChangesCount,
            int equivalentSetsCount, int cyclesCount, int orphansCount, long durationMs) {
        return new ReasonerResultsResponse(classifiedConceptCount, inferredChangesCount,
                navigationChangesCount, equivalentSetsCount, cyclesCount, orphansCount,
                durationMs, true, null);
    }

    public static ReasonerResultsResponse error(String errorMessage) {
        return new ReasonerResultsResponse(null, null, null, null, null, null, null,
                false, errorMessage);
    }
}
