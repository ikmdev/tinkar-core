package dev.ikm.tinkar.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for a saved StampCoordinate. The {@code id} is derived deterministically
 * from the coordinate's content, so identical settings always produce the same ID.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A saved stamp coordinate with its content-derived UUID.")
public record SavedStampCoordinateResponse(

        @Schema(description = "Content-derived UUID of this stamp coordinate. Pass as stampCoordinateId to /semantics-by-coordinate.",
                example = "3f47a12e-bc94-4b8a-a8f2-1234567890ab")
        String id,

        @Schema(description = "The stored stamp coordinate settings.")
        StampCoordinateDto settings,

        @Schema(description = "ISO-8601 timestamp when this coordinate was first saved.", example = "2026-02-27T12:00:00Z")
        String createdAt) {
}
