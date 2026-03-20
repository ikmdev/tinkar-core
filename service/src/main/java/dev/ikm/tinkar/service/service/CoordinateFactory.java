package dev.ikm.tinkar.service.service;

import dev.ikm.tinkar.service.dto.CoordinateOverride;
import dev.ikm.tinkar.service.dto.LanguageCoordinateDto;
import dev.ikm.tinkar.service.dto.LanguagePreset;
import dev.ikm.tinkar.service.dto.NavigationCoordinateDto;
import dev.ikm.tinkar.service.dto.PremiseType;
import dev.ikm.tinkar.service.dto.StampCoordinateDto;
import dev.ikm.tinkar.common.id.IntIdList;
import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.common.id.IntIds;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.Coordinates;
import dev.ikm.tinkar.coordinate.language.LanguageCoordinateRecord;
import dev.ikm.tinkar.coordinate.navigation.NavigationCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StampPositionRecord;
import dev.ikm.tinkar.coordinate.stamp.StateSet;
import dev.ikm.tinkar.coordinate.view.ViewCoordinateRecord;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculatorWithCache;
import dev.ikm.tinkar.entity.EntityService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;

/**
 * Builds a {@link ViewCalculatorWithCache} from optional coordinate overrides.
 * Unspecified fields fall back to server defaults (same as Tier 1).
 */
@Slf4j
public class CoordinateFactory {

    /**
     * Returns the server-default calculator (same as Tier 1).
     */
    public static ViewCalculatorWithCache defaultCalculator() {
        return Calculators.View.Default();
    }

    /**
     * Builds a {@link StampCoordinateRecord} from a {@link StampCoordinateDto}.
     * Null fields fall back to server defaults.
     */
    public static StampCoordinateRecord buildStampCoordinate(StampCoordinateDto dto) {
        if (dto == null) {
            return (StampCoordinateRecord) Coordinates.Stamp.DevelopmentLatestActiveOnly();
        }
        StateSet allowedStates = resolveAllowedStates(dto.allowedStates());
        long positionTime = dto.positionTime() != null ? dto.positionTime() : Long.MAX_VALUE;
        int pathNid = resolvePathNid(dto.positionPathId());
        IntIdSet moduleNids = resolveModuleNids(dto.moduleIds());
        IntIdSet excludedModuleNids = resolveModuleNids(dto.excludedModuleIds());
        IntIdList modulePriorityNids = resolveModulePriorityNids(dto.modulePriorityIds());
        StampPositionRecord stampPosition = StampPositionRecord.make(positionTime, pathNid);
        return new StampCoordinateRecord(allowedStates, stampPosition, moduleNids, excludedModuleNids, modulePriorityNids);
    }

    /**
     * Builds a {@link NavigationCoordinateRecord} from a {@link NavigationCoordinateDto}.
     * A null dto or null premiseType defaults to INFERRED.
     */
    public static NavigationCoordinateRecord buildNavigationCoordinate(NavigationCoordinateDto dto) {
        if (dto == null) {
            return NavigationCoordinateRecord.makeInferred();
        }
        return resolveNavigation(dto.premiseType());
    }

    /**
     * Builds a {@link LanguageCoordinateRecord} from a {@link LanguageCoordinateDto}.
     * A null dto or null languagePreset defaults to US_ENGLISH_REGULAR_NAME.
     */
    public static LanguageCoordinateRecord buildLanguageCoordinate(LanguageCoordinateDto dto) {
        if (dto == null) {
            return Coordinates.Language.UsEnglishRegularName();
        }
        return resolveLanguage(dto.languagePreset());
    }

    /**
     * Builds a calculator from explicit stamp, language, and navigation coordinates.
     * Logic and edit coordinates use server defaults.
     */
    public static ViewCalculatorWithCache buildCalculator(StampCoordinateRecord stampCoordinate,
                                                          LanguageCoordinateRecord languageCoordinate,
                                                          NavigationCoordinateRecord navigationCoordinate) {
        ViewCoordinateRecord viewCoordinate = ViewCoordinateRecord.make(
                stampCoordinate,
                languageCoordinate,
                Coordinates.Logic.ElPlusPlus(),
                navigationCoordinate,
                Coordinates.Edit.Default());
        return ViewCalculatorWithCache.getCalculator(viewCoordinate);
    }

    /**
     * Builds a calculator from explicit stamp and navigation coordinates.
     * Language, logic, and edit coordinates use server defaults.
     */
    public static ViewCalculatorWithCache buildCalculator(StampCoordinateRecord stampCoordinate,
                                                          NavigationCoordinateRecord navigationCoordinate) {
        return buildCalculator(stampCoordinate, buildLanguageCoordinate(null), navigationCoordinate);
    }

    /**
     * Builds a calculator from optional coordinate overrides.
     * Any null field in the override uses the server default value.
     *
     * @param override the coordinate overrides, or null for all defaults
     * @return a ViewCalculatorWithCache configured with the merged coordinates
     */
    public static ViewCalculatorWithCache buildCalculator(CoordinateOverride override) {
        if (override == null) {
            return defaultCalculator();
        }
        StampCoordinateDto stampDto = new StampCoordinateDto(
                override.allowedStates(), override.positionTime(), override.positionPathId(),
                override.moduleIds(), override.excludedModuleIds(), override.modulePriorityIds());
        NavigationCoordinateDto navDto = new NavigationCoordinateDto(override.premiseType());
        LanguageCoordinateDto langDto = override.languagePreset() != null
                ? new LanguageCoordinateDto(override.languagePreset()) : null;
        return buildCalculator(buildStampCoordinate(stampDto), buildLanguageCoordinate(langDto), buildNavigationCoordinate(navDto));
    }

    private static StateSet resolveAllowedStates(String allowedStates) {
        if (allowedStates == null || allowedStates.isBlank()) {
            return StateSet.ACTIVE_AND_INACTIVE;
        }
        return switch (allowedStates.toUpperCase()) {
            case "ACTIVE" -> StateSet.ACTIVE;
            case "INACTIVE" -> StateSet.INACTIVE;
            case "ACTIVE_AND_INACTIVE" -> StateSet.ACTIVE_AND_INACTIVE;
            default -> {
                log.warn("Unknown allowedStates value '{}', using default ACTIVE_AND_INACTIVE", allowedStates);
                yield StateSet.ACTIVE_AND_INACTIVE;
            }
        };
    }

    private static int resolvePathNid(String pathId) {
        if (pathId == null || pathId.isBlank()) {
            return Coordinates.Stamp.DevelopmentLatest().stampPosition().getPathForPositionNid();
        }
        try {
            PublicId publicId = PublicIds.of(UUID.fromString(pathId));
            return EntityService.get().nidForPublicId(publicId);
        } catch (Exception e) {
            log.warn("Failed to resolve path UUID '{}', using default development path: {}", pathId, e.getMessage());
            return Coordinates.Stamp.DevelopmentLatest().stampPosition().getPathForPositionNid();
        }
    }

    private static IntIdSet resolveModuleNids(List<String> moduleIds) {
        if (moduleIds == null || moduleIds.isEmpty()) {
            return IntIds.set.empty();
        }
        int[] nids = moduleIds.stream()
                .mapToInt(id -> {
                    try {
                        PublicId publicId = PublicIds.of(UUID.fromString(id));
                        return EntityService.get().nidForPublicId(publicId);
                    } catch (Exception e) {
                        log.warn("Failed to resolve module UUID '{}': {}", id, e.getMessage());
                        return Integer.MIN_VALUE;
                    }
                })
                .filter(nid -> nid != Integer.MIN_VALUE)
                .toArray();
        return IntIds.set.of(nids);
    }

    private static IntIdList resolveModulePriorityNids(List<String> modulePriorityIds) {
        if (modulePriorityIds == null || modulePriorityIds.isEmpty()) {
            return IntIds.list.empty();
        }
        int[] nids = modulePriorityIds.stream()
                .mapToInt(id -> {
                    try {
                        PublicId publicId = PublicIds.of(UUID.fromString(id));
                        return EntityService.get().nidForPublicId(publicId);
                    } catch (Exception e) {
                        log.warn("Failed to resolve module priority UUID '{}': {}", id, e.getMessage());
                        return Integer.MIN_VALUE;
                    }
                })
                .filter(nid -> nid != Integer.MIN_VALUE)
                .toArray();
        return IntIds.list.of(nids);
    }

    private static NavigationCoordinateRecord resolveNavigation(PremiseType premiseType) {
        if (premiseType == null) {
            return NavigationCoordinateRecord.makeInferred();
        }
        return switch (premiseType) {
            case STATED -> NavigationCoordinateRecord.makeStated();
            case INFERRED -> NavigationCoordinateRecord.makeInferred();
        };
    }

    private static LanguageCoordinateRecord resolveLanguage(LanguagePreset preset) {
        if (preset == null) {
            return Coordinates.Language.UsEnglishRegularName();
        }
        return switch (preset) {
            case US_ENGLISH_REGULAR_NAME -> Coordinates.Language.UsEnglishRegularName();
            case US_ENGLISH_FULLY_QUALIFIED_NAME -> Coordinates.Language.UsEnglishFullyQualifiedName();
            case GB_ENGLISH_PREFERRED_NAME -> Coordinates.Language.GbEnglishPreferredName();
            case GB_ENGLISH_FULLY_QUALIFIED_NAME -> Coordinates.Language.GbEnglishFullyQualifiedName();
            case ANY_LANGUAGE_REGULAR_NAME -> Coordinates.Language.AnyLanguageRegularName();
            case ANY_LANGUAGE_FULLY_QUALIFIED_NAME -> Coordinates.Language.AnyLanguageFullyQualifiedName();
            case ANY_LANGUAGE_DEFINITION -> Coordinates.Language.AnyLanguageDefinition();
            case SPANISH_PREFERRED_NAME -> Coordinates.Language.SpanishPreferredName();
            case SPANISH_FULLY_QUALIFIED_NAME -> Coordinates.Language.SpanishFullyQualifiedName();
        };
    }
}
