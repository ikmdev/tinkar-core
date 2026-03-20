package dev.ikm.tinkar.service.dto;

/**
 * Response returned when saving or retrieving a saved LanguageCoordinate.
 *
 * @param id        Content-derived UUID; pass as {@code languageCoordinateId} to query endpoints.
 * @param settings  The language coordinate settings that were saved.
 * @param createdAt ISO-8601 timestamp of when the coordinate was first saved.
 */
public record SavedLanguageCoordinateResponse(String id, LanguageCoordinateDto settings, String createdAt) {
}
