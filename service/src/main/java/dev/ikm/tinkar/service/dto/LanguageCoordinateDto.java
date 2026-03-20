package dev.ikm.tinkar.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Settings for a saved LanguageCoordinate configuration.
 * A null languagePreset defaults to US_ENGLISH_REGULAR_NAME.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Language coordinate settings.")
public record LanguageCoordinateDto(

        @Schema(description = "Language preset. Default: US_ENGLISH_REGULAR_NAME.")
        LanguagePreset languagePreset) {
}
