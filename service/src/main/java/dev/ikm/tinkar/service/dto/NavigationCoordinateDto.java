package dev.ikm.tinkar.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Settings for a saved NavigationCoordinate configuration.
 * A null premiseType defaults to INFERRED.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Navigation coordinate settings.")
public record NavigationCoordinateDto(

        @Schema(description = "Navigation premise type: STATED or INFERRED. Default: INFERRED.")
        PremiseType premiseType) {
}
