package dev.ikm.tinkar.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Settings for a saved StampCoordinate configuration.
 * Null fields mean "use server default".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Stamp coordinate settings. Null fields use server defaults.")
public record StampCoordinateDto(

        @Schema(description = "Allowed status states: ACTIVE, INACTIVE, or ACTIVE_AND_INACTIVE. Default: ACTIVE_AND_INACTIVE")
        String allowedStates,

        @Schema(description = "Position time as epoch milliseconds. Null means latest.")
        Long positionTime,

        @Schema(description = "UUID of the path concept (e.g., development path, master path). Null means development path.")
        String positionPathId,

        @Schema(description = "UUIDs of module concepts to include. Null or empty means no module filter.")
        List<String> moduleIds,

        @Schema(description = "UUIDs of module concepts to exclude. Null or empty means no exclusions.")
        List<String> excludedModuleIds,

        @Schema(description = "Ordered list of module concept UUIDs for priority when multiple versions compete. Null or empty means server default.")
        List<String> modulePriorityIds) {
}
