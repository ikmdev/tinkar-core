package dev.ikm.tinkar.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for a saved NavigationCoordinate. The {@code id} is derived deterministically
 * from the coordinate's content, so identical settings always produce the same ID.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A saved navigation coordinate with its content-derived UUID.")
public record SavedNavigationCoordinateResponse(

        @Schema(description = "Content-derived UUID of this navigation coordinate. Pass as navigationCoordinateId to /semantics-by-coordinate.",
                example = "4a58b23f-cd05-4b9b-b903-2345678901bc")
        String id,

        @Schema(description = "The stored navigation coordinate settings.")
        NavigationCoordinateDto settings,

        @Schema(description = "ISO-8601 timestamp when this coordinate was first saved.", example = "2026-02-27T12:00:00Z")
        String createdAt) {
}
