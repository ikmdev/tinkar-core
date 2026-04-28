package dev.ikm.tinkar.service.controller.knowledgegraph;

import dev.ikm.tinkar.service.dto.ChangeHistoryResponse;
import dev.ikm.tinkar.service.dto.*;
import dev.ikm.tinkar.service.dto.CoordinateOverride;
import dev.ikm.tinkar.service.dto.DescendantOperationResponse;
import dev.ikm.tinkar.service.dto.PremiseType;
import dev.ikm.tinkar.service.dto.SavedLanguageCoordinateResponse;
import dev.ikm.tinkar.service.dto.SavedNavigationCoordinateResponse;
import dev.ikm.tinkar.service.dto.TinkarSearchQueryResponse;
import dev.ikm.tinkar.service.dto.TinkarSearchQueryResponse.Descriptions;
import dev.ikm.tinkar.service.dto.TinkarSearchQueryResponse.SearchResult;
import dev.ikm.tinkar.service.dto.TinkarSearchQueryResponse.Stamp;
import dev.ikm.tinkar.service.proto.TinkarSearchResult;
import dev.ikm.tinkar.service.service.CoordinateFactory;
import dev.ikm.tinkar.service.service.CoordinateStoreService;
import dev.ikm.tinkar.service.service.TinkarService;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculatorWithCache;
import dev.ikm.tinkar.schema.StampVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Tier 2: Concept-Aware (Knowledge Graph) — REST controller.
 *
 * Exposes the concept-oriented structure with semantic patterns, STAMP info,
 * version history, and write operations. Target audience: analytics engineers,
 * knowledge graph practitioners.
 *
 * Read endpoints accept optional coordinate override parameters to control
 * STAMP filtering and navigation mode. Omitted parameters use server defaults.
 */
@RestController
@RequestMapping("/api/ike/knowledgegraph")
@Tag(name = "IKE Knowledge Graph (Tier 2)", description = "Concept-aware API exposing STAMP coordinates, semantic patterns, and version history.")
public class KnowledgeGraphRestController {

    private final TinkarService tinkarService;
    private final CoordinateStoreService coordinateStoreService;

    public KnowledgeGraphRestController(TinkarService tinkarService, CoordinateStoreService coordinateStoreService) {
        this.tinkarService = tinkarService;
        this.coordinateStoreService = coordinateStoreService;
    }

    @Operation(summary = "Get all semantics for a concept",
            description = "Retrieves all semantics (comments, descriptions, axioms, etc.) attached to a concept. " +
                    "Supports optional coordinate overrides for STAMP filtering and navigation mode.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Semantics retrieved successfully", content = @Content(schema = @Schema(implementation = ConceptSemanticsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid concept ID parameter")
    })
    @GetMapping("/semantics")
    public ResponseEntity<ConceptSemanticsResponse> inspectConcept(
            @Parameter(description = "Concept ID (UUID)", required = true, example = "9fc3832b-a5f8-5504-ba16-7551976841dc") @RequestParam("conceptId") String conceptId,
            @Parameter(description = "Allowed states: ACTIVE, INACTIVE, or ACTIVE_AND_INACTIVE") @RequestParam(required = false) String allowedStates,
            @Parameter(description = "Position time as epoch milliseconds (null = latest)") @RequestParam(required = false) Long positionTime,
            @Parameter(description = "UUID of the path concept") @RequestParam(required = false) String positionPath,
            @Parameter(description = "UUIDs of module concepts to include") @RequestParam(required = false) List<String> modules,
            @Parameter(description = "UUIDs of module concepts to exclude") @RequestParam(required = false) List<String> excludedModules,
            @Parameter(description = "Ordered UUIDs of module concepts for priority") @RequestParam(required = false) List<String> modulePriority,
            @Parameter(description = "Navigation premise type: STATED or INFERRED") @RequestParam(required = false) PremiseType premiseType,
            @Parameter(description = "Language coordinate preset controlling description type and dialect preference") @RequestParam(required = false) LanguagePreset languagePreset) {
        ViewCalculatorWithCache calc = buildCalculator(allowedStates, positionTime, positionPath, modules, excludedModules, modulePriority, premiseType, languagePreset);
        return ResponseEntity.ok(tinkarService.inspectConcept(conceptId, calc));
    }

    @Operation(summary = "Get comments for a concept",
            description = "Retrieves all comment semantics attached to a concept. " +
                    "Supports optional coordinate overrides for STAMP filtering and navigation mode.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Comments retrieved successfully", content = @Content(schema = @Schema(implementation = ConceptSemanticsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid concept ID parameter")
    })
    @GetMapping("/comments")
    public ResponseEntity<ConceptSemanticsResponse> getConceptComments(
            @Parameter(description = "Concept ID (UUID)", required = true, example = "9fc3832b-a5f8-5504-ba16-7551976841dc") @RequestParam("conceptId") String conceptId,
            @Parameter(description = "Allowed states: ACTIVE, INACTIVE, or ACTIVE_AND_INACTIVE") @RequestParam(required = false) String allowedStates,
            @Parameter(description = "Position time as epoch milliseconds (null = latest)") @RequestParam(required = false) Long positionTime,
            @Parameter(description = "UUID of the path concept") @RequestParam(required = false) String positionPath,
            @Parameter(description = "UUIDs of module concepts to include") @RequestParam(required = false) List<String> modules,
            @Parameter(description = "UUIDs of module concepts to exclude") @RequestParam(required = false) List<String> excludedModules,
            @Parameter(description = "Ordered UUIDs of module concepts for priority") @RequestParam(required = false) List<String> modulePriority,
            @Parameter(description = "Navigation premise type: STATED or INFERRED") @RequestParam(required = false) PremiseType premiseType) {
        ViewCalculatorWithCache calc = buildCalculator(allowedStates, positionTime, positionPath, modules, excludedModules, modulePriority, premiseType, null);
        return ResponseEntity.ok(tinkarService.getConceptComments(conceptId, calc));
    }

    @Operation(summary = "Get change history for an entity",
            description = "Retrieves the complete change history for an entity, showing all STAMP versions and field modifications. " +
                    "Supports optional coordinate overrides for STAMP filtering.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Change history retrieved successfully", content = @Content(schema = @Schema(implementation = ChangeHistoryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid entity ID parameter")
    })
    @GetMapping("/change-history")
    public ResponseEntity<ChangeHistoryResponse> getChangeHistory(
            @Parameter(description = "Entity ID (UUID)", required = true, example = "f5c39ec3-7256-3a03-b651-d17b623a30ec") @RequestParam("entityId") String entityId,
            @Parameter(description = "Allowed states: ACTIVE, INACTIVE, or ACTIVE_AND_INACTIVE") @RequestParam(required = false) String allowedStates,
            @Parameter(description = "Position time as epoch milliseconds (null = latest)") @RequestParam(required = false) Long positionTime,
            @Parameter(description = "UUID of the path concept") @RequestParam(required = false) String positionPath,
            @Parameter(description = "UUIDs of module concepts to include") @RequestParam(required = false) List<String> modules,
            @Parameter(description = "UUIDs of module concepts to exclude") @RequestParam(required = false) List<String> excludedModules,
            @Parameter(description = "Ordered UUIDs of module concepts for priority") @RequestParam(required = false) List<String> modulePriority,
            @Parameter(description = "Navigation premise type: STATED or INFERRED") @RequestParam(required = false) PremiseType premiseType) {
        ViewCalculatorWithCache calc = buildCalculator(allowedStates, positionTime, positionPath, modules, excludedModules, modulePriority, premiseType, null);
        return ResponseEntity.ok(tinkarService.getChangeHistory(entityId, calc));
    }

    @Operation(summary = "Get comprehensive change history for a concept",
            description = "Retrieves the full change history for a concept INCLUDING all attached semantics. " +
                    "Supports optional coordinate overrides for STAMP filtering.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Change history retrieved successfully", content = @Content(schema = @Schema(implementation = ConceptChangeHistoryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid concept ID parameter")
    })
    @GetMapping("/concept-change-history")
    public ResponseEntity<ConceptChangeHistoryResponse> getConceptChangeHistory(
            @Parameter(description = "Concept ID (UUID)", required = true, example = "9fc3832b-a5f8-5504-ba16-7551976841dc") @RequestParam("conceptId") String conceptId,
            @Parameter(description = "Allowed states: ACTIVE, INACTIVE, or ACTIVE_AND_INACTIVE") @RequestParam(required = false) String allowedStates,
            @Parameter(description = "Position time as epoch milliseconds (null = latest)") @RequestParam(required = false) Long positionTime,
            @Parameter(description = "UUID of the path concept") @RequestParam(required = false) String positionPath,
            @Parameter(description = "UUIDs of module concepts to include") @RequestParam(required = false) List<String> modules,
            @Parameter(description = "UUIDs of module concepts to exclude") @RequestParam(required = false) List<String> excludedModules,
            @Parameter(description = "Ordered UUIDs of module concepts for priority") @RequestParam(required = false) List<String> modulePriority,
            @Parameter(description = "Navigation premise type: STATED or INFERRED") @RequestParam(required = false) PremiseType premiseType) {
        ViewCalculatorWithCache calc = buildCalculator(allowedStates, positionTime, positionPath, modules, excludedModules, modulePriority, premiseType, null);
        return ResponseEntity.ok(tinkarService.getConceptChangeHistory(conceptId, calc));
    }

    @Operation(summary = "Get direct child concepts",
            description = "Retrieves direct child concepts using coordinate-aware navigation. " +
                    "The premiseType parameter controls whether STATED (authored) or INFERRED (classified) IS-A relationships are used.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Child concepts retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid concept ID parameter")
    })
    @GetMapping("/children")
    public ResponseEntity<TinkarSearchQueryResponse> getChildConcepts(
            @Parameter(description = "Concept ID (UUID)", required = true, example = "f6978e15-e169-58c2-a93d-eac1511974da") @RequestParam("conceptId") String conceptId,
            @Parameter(description = "Allowed states: ACTIVE, INACTIVE, or ACTIVE_AND_INACTIVE") @RequestParam(required = false) String allowedStates,
            @Parameter(description = "Position time as epoch milliseconds (null = latest)") @RequestParam(required = false) Long positionTime,
            @Parameter(description = "UUID of the path concept") @RequestParam(required = false) String positionPath,
            @Parameter(description = "UUIDs of module concepts to include") @RequestParam(required = false) List<String> modules,
            @Parameter(description = "UUIDs of module concepts to exclude") @RequestParam(required = false) List<String> excludedModules,
            @Parameter(description = "Ordered UUIDs of module concepts for priority") @RequestParam(required = false) List<String> modulePriority,
            @Parameter(description = "Navigation premise type: STATED or INFERRED") @RequestParam(required = false) PremiseType premiseType) {
        ViewCalculatorWithCache calc = buildCalculator(allowedStates, positionTime, positionPath, modules, excludedModules, modulePriority, premiseType, null);
        return ResponseEntity.ok(toDto(tinkarService.getChildConcepts(conceptId, calc)));
    }

    @Operation(summary = "Get all descendant concepts",
            description = "Retrieves all descendant concepts (full subtree) using coordinate-aware navigation. " +
                    "The premiseType parameter controls whether STATED (authored) or INFERRED (classified) IS-A relationships are used.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Descendant concepts retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid concept ID parameter")
    })
    @GetMapping("/descendants")
    public ResponseEntity<TinkarSearchQueryResponse> getDescendantConcepts(
            @Parameter(description = "Concept ID (UUID)", required = true, example = "f6978e15-e169-58c2-a93d-eac1511974da") @RequestParam("conceptId") String conceptId,
            @Parameter(description = "Allowed states: ACTIVE, INACTIVE, or ACTIVE_AND_INACTIVE") @RequestParam(required = false) String allowedStates,
            @Parameter(description = "Position time as epoch milliseconds (null = latest)") @RequestParam(required = false) Long positionTime,
            @Parameter(description = "UUID of the path concept") @RequestParam(required = false) String positionPath,
            @Parameter(description = "UUIDs of module concepts to include") @RequestParam(required = false) List<String> modules,
            @Parameter(description = "UUIDs of module concepts to exclude") @RequestParam(required = false) List<String> excludedModules,
            @Parameter(description = "Ordered UUIDs of module concepts for priority") @RequestParam(required = false) List<String> modulePriority,
            @Parameter(description = "Navigation premise type: STATED or INFERRED") @RequestParam(required = false) PremiseType premiseType) {
        ViewCalculatorWithCache calc = buildCalculator(allowedStates, positionTime, positionPath, modules, excludedModules, modulePriority, premiseType, null);
        return ResponseEntity.ok(toDto(tinkarService.getDescendantConcepts(conceptId, calc)));
    }

    @Operation(summary = "Create a sample change (comment)", description = "Creates a new comment semantic attached to the specified concept.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Change created successfully", content = @Content(schema = @Schema(implementation = ChangeHistoryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid concept ID or comment parameter")
    })
    @PostMapping("/changes")
    public ResponseEntity<ChangeHistoryResponse> createSampleChange(
            @Parameter(description = "Concept ID (UUID)", required = true, example = "f5c39ec3-7256-3a03-b651-d17b623a30ec") @RequestParam("conceptId") String conceptId,
            @Parameter(description = "Comment text to add", required = true, example = "This is a sample comment") @RequestParam("comment") String comment) {
        return ResponseEntity.ok(tinkarService.createSampleChange(conceptId, comment));
    }

    @Operation(summary = "Save pending changes", description = "Saves all pending changes to persistent storage (disk).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Save operation completed"),
            @ApiResponse(responseCode = "500", description = "Failed to save changes")
    })
    @PostMapping("/save")
    public ResponseEntity<String> saveChanges() {
        return ResponseEntity.ok(tinkarService.saveChanges());
    }

    @Operation(summary = "Discard pending changes", description = "Discards all pending changes that have not been saved to disk.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Discard operation completed"),
            @ApiResponse(responseCode = "500", description = "Failed to discard changes")
    })
    @PostMapping("/discard")
    public ResponseEntity<String> discardChanges() {
        return ResponseEntity.ok(tinkarService.discardChanges());
    }

    @Operation(summary = "Add a descendant to a concept", description = "Creates a new IS-A relationship making an existing concept a descendant (child) of the specified parent concept.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Descendant added successfully", content = @Content(schema = @Schema(implementation = DescendantOperationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid concept ID parameter")
    })
    @PostMapping("/descendants")
    public ResponseEntity<DescendantOperationResponse> addDescendant(
            @Parameter(description = "Parent concept ID (UUID)", required = true, example = "f6978e15-e169-58c2-a93d-eac1511974da") @RequestParam("parentConceptId") String parentConceptId,
            @Parameter(description = "Concept ID to add as descendant (UUID)", required = true, example = "9fc3832b-a5f8-5504-ba16-7551976841dc") @RequestParam("descendantConceptId") String descendantConceptId) {
        return ResponseEntity.ok(tinkarService.addDescendant(parentConceptId, descendantConceptId));
    }

    @Operation(summary = "Create a new concept and add as descendant", description = "Creates a new concept with the specified name and establishes an IS-A relationship with the parent concept.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "New concept created and added as descendant successfully", content = @Content(schema = @Schema(implementation = DescendantOperationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid concept ID or name parameter")
    })
    @PostMapping("/descendants/create")
    public ResponseEntity<DescendantOperationResponse> createAndAddDescendant(
            @Parameter(description = "Parent concept ID (UUID)", required = true, example = "f6978e15-e169-58c2-a93d-eac1511974da") @RequestParam("parentConceptId") String parentConceptId,
            @Parameter(description = "Name for the new concept", required = true, example = "New Medical Condition") @RequestParam("conceptName") String conceptName) {
        return ResponseEntity.ok(tinkarService.createAndAddDescendant(parentConceptId, conceptName));
    }

    @Operation(summary = "Remove a descendant from a concept", description = "Removes the IS-A relationship between a parent concept and a descendant concept.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Descendant removed successfully", content = @Content(schema = @Schema(implementation = DescendantOperationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid concept ID parameter")
    })
    @DeleteMapping("/descendants")
    public ResponseEntity<DescendantOperationResponse> removeDescendant(
            @Parameter(description = "Parent concept ID (UUID)", required = true, example = "f6978e15-e169-58c2-a93d-eac1511974da") @RequestParam("parentConceptId") String parentConceptId,
            @Parameter(description = "Descendant concept ID to remove (UUID)", required = true, example = "9fc3832b-a5f8-5504-ba16-7551976841dc") @RequestParam("descendantConceptId") String descendantConceptId) {
        return ResponseEntity.ok(tinkarService.removeDescendant(parentConceptId, descendantConceptId));
    }

    @Operation(summary = "Save a stamp coordinate",
            description = "Saves a StampCoordinate to the dataset and returns its content-derived UUID. " +
                    "Identical settings always produce the same UUID (idempotent). " +
                    "The returned ID can be passed to /semantics-by-coordinate as stampCoordinateId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Stamp coordinate saved successfully", content = @Content(schema = @Schema(implementation = SavedStampCoordinateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body")
    })
    @PostMapping("/coordinates/stamp")
    public ResponseEntity<SavedStampCoordinateResponse> saveStampCoordinate(@RequestBody StampCoordinateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(coordinateStoreService.saveStamp(dto));
    }

    @Operation(summary = "List all saved stamp coordinates",
            description = "Returns all StampCoordinates that have been saved to the loaded dataset.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stamp coordinates retrieved successfully", content = @Content(schema = @Schema(implementation = SavedStampCoordinateResponse.class)))
    })
    @GetMapping("/coordinates/stamp")
    public ResponseEntity<List<SavedStampCoordinateResponse>> listStampCoordinates() {
        return ResponseEntity.ok(coordinateStoreService.findAllStamp());
    }

    @Operation(summary = "Save a navigation coordinate",
            description = "Saves a NavigationCoordinate to the dataset and returns its content-derived UUID. " +
                    "Identical settings always produce the same UUID (idempotent). " +
                    "The returned ID can be passed to /semantics-by-coordinate as navigationCoordinateId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Navigation coordinate saved successfully", content = @Content(schema = @Schema(implementation = SavedNavigationCoordinateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body")
    })
    @PostMapping("/coordinates/navigation")
    public ResponseEntity<SavedNavigationCoordinateResponse> saveNavigationCoordinate(@RequestBody NavigationCoordinateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(coordinateStoreService.saveNavigation(dto));
    }

    @Operation(summary = "List all saved navigation coordinates",
            description = "Returns all NavigationCoordinates that have been saved to the loaded dataset.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Navigation coordinates retrieved successfully", content = @Content(schema = @Schema(implementation = SavedNavigationCoordinateResponse.class)))
    })
    @GetMapping("/coordinates/navigation")
    public ResponseEntity<List<SavedNavigationCoordinateResponse>> listNavigationCoordinates() {
        return ResponseEntity.ok(coordinateStoreService.findAllNavigation());
    }

    @Operation(summary = "Save a language coordinate",
            description = "Saves a LanguageCoordinate to the dataset and returns its content-derived UUID. " +
                    "Identical settings always produce the same UUID (idempotent). " +
                    "The returned ID can be passed to /semantics-by-coordinate as languageCoordinateId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Language coordinate saved successfully", content = @Content(schema = @Schema(implementation = SavedLanguageCoordinateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body")
    })
    @PostMapping("/coordinates/language")
    public ResponseEntity<SavedLanguageCoordinateResponse> saveLanguageCoordinate(@RequestBody LanguageCoordinateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(coordinateStoreService.saveLanguage(dto));
    }

    @Operation(summary = "List all saved language coordinates",
            description = "Returns all LanguageCoordinates that have been saved to the loaded dataset.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Language coordinates retrieved successfully", content = @Content(schema = @Schema(implementation = SavedLanguageCoordinateResponse.class)))
    })
    @GetMapping("/coordinates/language")
    public ResponseEntity<List<SavedLanguageCoordinateResponse>> listLanguageCoordinates() {
        return ResponseEntity.ok(coordinateStoreService.findAllLanguage());
    }

    @Operation(summary = "Get semantics for a concept using saved coordinates",
            description = "Retrieves all semantics for a concept using previously saved coordinate configurations. " +
                    "All coordinate IDs are optional; omitted values use server defaults. " +
                    "Save coordinates via POST /coordinates/stamp, /coordinates/navigation, and /coordinates/language.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Semantics retrieved successfully", content = @Content(schema = @Schema(implementation = ConceptSemanticsResponse.class))),
            @ApiResponse(responseCode = "404", description = "No coordinate found with the given ID"),
            @ApiResponse(responseCode = "400", description = "Invalid conceptId or coordinate ID parameter")
    })
    @GetMapping("/semantics-by-coordinate")
    public ResponseEntity<ConceptSemanticsResponse> getSemanticsWithCoordinate(
            @Parameter(description = "Concept ID (UUID)", required = true, example = "9fc3832b-a5f8-5504-ba16-7551976841dc") @RequestParam("conceptId") String conceptId,
            @Parameter(description = "Saved stamp coordinate ID (UUID returned by POST /coordinates/stamp)", example = "3f47a12e-bc94-4b8a-a8f2-1234567890ab") @RequestParam(required = false) String stampCoordinateId,
            @Parameter(description = "Saved navigation coordinate ID (UUID returned by POST /coordinates/navigation)", example = "4a58b23f-cd05-4b9b-b903-2345678901bc") @RequestParam(required = false) String navigationCoordinateId,
            @Parameter(description = "Saved language coordinate ID (UUID returned by POST /coordinates/language)", example = "5b69c34g-de16-4b0c-c014-3456789012cd") @RequestParam(required = false) String languageCoordinateId) {
        dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord stampCoord = resolveStampCoordinate(stampCoordinateId);
        dev.ikm.tinkar.coordinate.language.LanguageCoordinateRecord langCoord = resolveLanguageCoordinate(languageCoordinateId);
        dev.ikm.tinkar.coordinate.navigation.NavigationCoordinateRecord navCoord = resolveNavigationCoordinate(navigationCoordinateId);
        ViewCalculatorWithCache calc = CoordinateFactory.buildCalculator(stampCoord, langCoord, navCoord);
        return ResponseEntity.ok(tinkarService.inspectConcept(conceptId, calc));
    }

    @Operation(summary = "Load full entity graph for a concept",
            description = "Returns the complete binary entity graph (concept + semantics + patterns + stamps + " +
                    "navigation neighbors + STAMP_PATTERN) as serialized protobuf bytes " +
                    "(Content-Type: application/x-protobuf). Deserialize as TinkarConceptEntityResponse " +
                    "and load entities into a local entity store to power the concept detail view, " +
                    "Hierarchy tab, and History tab.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Entity graph returned as protobuf bytes"),
            @ApiResponse(responseCode = "400", description = "Invalid concept ID parameter")
    })
    @GetMapping("/entity-graph")
    public ResponseEntity<byte[]> loadConceptEntityGraph(
            @Parameter(description = "Concept ID (UUID)", required = true, example = "9fc3832b-a5f8-5504-ba16-7551976841dc") @RequestParam("conceptId") String conceptId) {

        dev.ikm.tinkar.service.proto.TinkarConceptEntityResponse response = tinkarService.loadConceptEntityGraph(conceptId);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "application/x-protobuf");
        return new ResponseEntity<>(response.toByteArray(), headers, HttpStatus.OK);
    }

    private dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord resolveStampCoordinate(String stampCoordinateId) {
        if (stampCoordinateId == null) {
            return CoordinateFactory.buildStampCoordinate(null);
        }
        SavedStampCoordinateResponse saved = coordinateStoreService.findStampById(stampCoordinateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No stamp coordinate found with id: " + stampCoordinateId));
        return CoordinateFactory.buildStampCoordinate(saved.settings());
    }

    private dev.ikm.tinkar.coordinate.navigation.NavigationCoordinateRecord resolveNavigationCoordinate(String navigationCoordinateId) {
        if (navigationCoordinateId == null) {
            return CoordinateFactory.buildNavigationCoordinate(null);
        }
        SavedNavigationCoordinateResponse saved = coordinateStoreService.findNavigationById(navigationCoordinateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No navigation coordinate found with id: " + navigationCoordinateId));
        return CoordinateFactory.buildNavigationCoordinate(saved.settings());
    }

    private dev.ikm.tinkar.coordinate.language.LanguageCoordinateRecord resolveLanguageCoordinate(String languageCoordinateId) {
        if (languageCoordinateId == null) {
            return CoordinateFactory.buildLanguageCoordinate(null);
        }
        SavedLanguageCoordinateResponse saved = coordinateStoreService.findLanguageById(languageCoordinateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No language coordinate found with id: " + languageCoordinateId));
        return CoordinateFactory.buildLanguageCoordinate(saved.settings());
    }

    private ViewCalculatorWithCache buildCalculator(String allowedStates, Long positionTime,
            String positionPath, List<String> modules, List<String> excludedModules,
            List<String> modulePriority, PremiseType premiseType,
            LanguagePreset languagePreset) {
        if (allowedStates == null && positionTime == null && positionPath == null
                && (modules == null || modules.isEmpty())
                && (excludedModules == null || excludedModules.isEmpty())
                && (modulePriority == null || modulePriority.isEmpty())
                && premiseType == null && languagePreset == null) {
            return CoordinateFactory.defaultCalculator();
        }
        CoordinateOverride override = new CoordinateOverride(
                allowedStates, positionTime, positionPath, modules, excludedModules, modulePriority, premiseType, languagePreset);
        return CoordinateFactory.buildCalculator(override);
    }

    private TinkarSearchQueryResponse toDto(dev.ikm.tinkar.service.proto.TinkarSearchQueryResponse proto) {
        List<SearchResult> results = proto.getResultsList().stream()
                .map(this::toSearchResultDto)
                .toList();
        return new TinkarSearchQueryResponse(
                proto.getQuery(),
                proto.getTotalCount(),
                results,
                proto.getSuccess(),
                proto.getErrorMessage().isEmpty() ? null : proto.getErrorMessage());
    }

    private SearchResult toSearchResultDto(TinkarSearchResult proto) {
        List<String> publicIds = proto.getPublicId().getUuidsList();
        Descriptions descriptions = new Descriptions(
                proto.getDescriptions().getFullyQualifiedName(),
                proto.getDescriptions().getRegularName(),
                proto.getDescriptions().getDefinition());
        StampVersion stampProto = proto.getStamp();
        Stamp stamp = new Stamp(
                stampProto.hasStatusPublicId() && !stampProto.getStatusPublicId().getUuidsList().isEmpty()
                        ? stampProto.getStatusPublicId().getUuids(0) : null,
                stampProto.hasAuthorPublicId() && !stampProto.getAuthorPublicId().getUuidsList().isEmpty()
                        ? stampProto.getAuthorPublicId().getUuids(0) : null,
                stampProto.hasModulePublicId() && !stampProto.getModulePublicId().getUuidsList().isEmpty()
                        ? stampProto.getModulePublicId().getUuids(0) : null,
                stampProto.hasPathPublicId() && !stampProto.getPathPublicId().getUuidsList().isEmpty()
                        ? stampProto.getPathPublicId().getUuids(0) : null,
                stampProto.getTime());
        return new SearchResult(publicIds, descriptions, stamp);
    }
}
