package dev.ikm.tinkar.service.dto;

/**
 * Named presets for LanguageCoordinate configuration.
 * Each value maps directly to a {@code Coordinates.Language.*} factory method.
 * Null or absent preset defaults to {@code US_ENGLISH_REGULAR_NAME}.
 */
public enum LanguagePreset {

    /** US English, prefers regular name over FQN. Server default. */
    US_ENGLISH_REGULAR_NAME,

    /** US English, prefers fully qualified name over regular name. */
    US_ENGLISH_FULLY_QUALIFIED_NAME,

    /** GB English, prefers regular name over FQN. */
    GB_ENGLISH_PREFERRED_NAME,

    /** GB English, prefers fully qualified name over regular name. */
    GB_ENGLISH_FULLY_QUALIFIED_NAME,

    /** Language-agnostic, prefers regular name. */
    ANY_LANGUAGE_REGULAR_NAME,

    /** Language-agnostic, prefers fully qualified name. */
    ANY_LANGUAGE_FULLY_QUALIFIED_NAME,

    /** Language-agnostic, prefers definition descriptions. */
    ANY_LANGUAGE_DEFINITION,

    /** Spanish, prefers regular name over FQN. */
    SPANISH_PREFERRED_NAME,

    /** Spanish, prefers fully qualified name over regular name. */
    SPANISH_FULLY_QUALIFIED_NAME
}
