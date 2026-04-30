package dev.ikm.tinkar.service.controller.graphrag;

import dev.ikm.tinkar.service.dto.ConceptSearchResponse;
import dev.ikm.tinkar.service.dto.SearchSortOption;
import dev.ikm.tinkar.service.dto.TinkarSearchQueryResponse;
import dev.ikm.tinkar.service.service.TinkarService;
import dev.ikm.tinkar.service.util.ProtoConversionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tier 1: Graph RAG Ready — REST controller.
 *
 * Simple, opinionated API for ML/RAG engineers and data scientists.
 * All responses are pre-resolved to human-readable form using server-side defaults.
 */
@RestController
@RequestMapping("/api/ike/graphrag")
@Tag(name = "IKE Graph RAG (Tier 1)", description = "Simple, opinionated API for ML/RAG engineers. Pre-resolved human-readable responses.")
public class GraphRAGRestController {

    private final TinkarService tinkarService;

    public GraphRAGRestController(TinkarService tinkarService) {
        this.tinkarService = tinkarService;
    }

    @Operation(summary = "Search Tinkar concepts", description = "Performs a search query for Tinkar concepts")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search completed successfully", content = @Content(schema = @Schema(implementation = TinkarSearchQueryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid query parameter")
    })
    @GetMapping("/search")
    public ResponseEntity<TinkarSearchQueryResponse> search(
            @Parameter(description = "Search query string", required = true, example = "chronic lung") @RequestParam String query) {
        return ResponseEntity.ok(ProtoConversionUtils.toDto(tinkarService.search(query)));
    }

    @Operation(summary = "Search for concepts", description = "Search for concepts based off a search term")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search completed successfully", content = @Content(schema = @Schema(implementation = TinkarSearchQueryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid query parameter")
    })
    @GetMapping("/concept-search")
    public ResponseEntity<TinkarSearchQueryResponse> conceptSearch(
            @Parameter(description = "Search query string", required = true, example = "chronic lung") @RequestParam String query,
            @Parameter(description = "Maximum number of results to return") @RequestParam(name = "maxResults", required = false) Integer maxResults) {
        return ResponseEntity.ok(ProtoConversionUtils.toDto(tinkarService.conceptSearch(query, maxResults)));
    }

    @Operation(summary = "Search for concepts with sort options", description = "Search for concepts with configurable sort options. " +
            "TOP_COMPONENT groups results by concept sorted by relevance score. " +
            "TOP_COMPONENT_ALPHA groups results by concept sorted alphabetically. " +
            "SEMANTIC shows individual matches sorted by relevance score. " +
            "SEMANTIC_ALPHA shows individual matches sorted alphabetically.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search completed successfully", content = @Content(schema = @Schema(implementation = ConceptSearchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid query or sort parameter")
    })
    @GetMapping("/concept-search-sorted")
    public ResponseEntity<ConceptSearchResponse> conceptSearchWithSort(
            @Parameter(description = "Search query string", required = true, example = "chronic lung") @RequestParam String query,
            @Parameter(description = "Maximum number of results to return") @RequestParam(name = "maxResults", required = false) Integer maxResults,
            @Parameter(description = "Sort option: TOP_COMPONENT (default), TOP_COMPONENT_ALPHA, SEMANTIC, or SEMANTIC_ALPHA", example = "TOP_COMPONENT") @RequestParam(name = "sortBy", required = false) SearchSortOption sortBy) {
        return ResponseEntity.ok(tinkarService.conceptSearchWithSort(query, maxResults, sortBy));
    }

    @Operation(summary = "Get entity by concept ID", description = "Look up an entity by the tinkar concept public ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Entity retrieved successfully", content = @Content(schema = @Schema(implementation = TinkarSearchQueryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid concept ID parameter")
    })
    @GetMapping("/entity")
    public ResponseEntity<TinkarSearchQueryResponse> getEntity(
            @Parameter(description = "Concept ID", required = true, example = "f5c39ec3-7256-3a03-b651-d17b623a30ec") @RequestParam("conceptId") String conceptId) {
        return ResponseEntity.ok(ProtoConversionUtils.toDto(tinkarService.getEntity(conceptId)));
    }

    @Operation(summary = "Get child concepts", description = "Look up child concepts for the tinkar concept public ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Child concepts retrieved successfully", content = @Content(schema = @Schema(implementation = TinkarSearchQueryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid concept ID parameter")
    })
    @GetMapping("/children")
    public ResponseEntity<TinkarSearchQueryResponse> getChildConcepts(
            @Parameter(description = "Concept ID", required = true, example = "f5c39ec3-7256-3a03-b651-d17b623a30ec") @RequestParam("conceptId") String conceptId) {
        return ResponseEntity.ok(ProtoConversionUtils.toDto(tinkarService.getChildConcepts(conceptId)));
    }

    @Operation(summary = "Get descendant concepts", description = "Look up descendant concepts for the tinkar concept public ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Descendant concepts retrieved successfully", content = @Content(schema = @Schema(implementation = TinkarSearchQueryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid concept ID parameter")
    })
    @GetMapping("/descendants")
    public ResponseEntity<TinkarSearchQueryResponse> getDescendantConcepts(
            @Parameter(description = "Concept ID", required = true, example = "f5c39ec3-7256-3a03-b651-d17b623a30ec") @RequestParam("conceptId") String conceptId) {
        return ResponseEntity.ok(ProtoConversionUtils.toDto(tinkarService.getDescendantConcepts(conceptId)));
    }

    @Operation(summary = "Get LIDR record concepts from test kit", description = "Look up LIDR record concepts for a test kit concept public ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "LIDR record concepts retrieved successfully", content = @Content(schema = @Schema(implementation = TinkarSearchQueryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid test kit concept ID parameter")
    })
    @GetMapping("/lidr-records")
    public ResponseEntity<TinkarSearchQueryResponse> getLIDRRecordConceptsFromTestKit(
            @Parameter(description = "Test kit concept ID", required = true, example = "f5c39ec3-7256-3a03-b651-d17b623a30ec") @RequestParam("testKitConceptId") String testKitConceptId) {
        return ResponseEntity.ok(ProtoConversionUtils.toDto(tinkarService.getLIDRRecordConceptsFromTestKit(testKitConceptId)));
    }

    @Operation(summary = "Get result conformance concepts from LIDR record", description = "Look up result conformances for a LIDR record concept public ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Result conformance concepts retrieved successfully", content = @Content(schema = @Schema(implementation = TinkarSearchQueryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid LIDR record concept ID parameter")
    })
    @GetMapping("/result-conformances")
    public ResponseEntity<TinkarSearchQueryResponse> getResultConformanceConceptsFromLIDRRecord(
            @Parameter(description = "LIDR record concept ID", required = true, example = "f5c39ec3-7256-3a03-b651-d17b623a30ec") @RequestParam("lidrRecordConceptId") String lidrRecordConceptId) {
        return ResponseEntity.ok(ProtoConversionUtils.toDto(tinkarService.getResultConformanceConceptsFromLIDRRecord(lidrRecordConceptId)));
    }

    @Operation(summary = "Get allowed result concepts from result conformance", description = "Look up allowed results for a result conformance concept public ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Allowed result concepts retrieved successfully", content = @Content(schema = @Schema(implementation = TinkarSearchQueryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid result conformance concept ID parameter")
    })
    @GetMapping("/allowed-results")
    public ResponseEntity<TinkarSearchQueryResponse> getAllowedResultConceptsFromResultConformance(
            @Parameter(description = "Result conformance concept ID", required = true, example = "f5c39ec3-7256-3a03-b651-d17b623a30ec") @RequestParam("resultConformanceConceptId") String resultConformanceConceptId) {
        return ResponseEntity.ok(ProtoConversionUtils.toDto(tinkarService.getAllowedResultConceptsFromResultConformance(resultConformanceConceptId)));
    }

    @Operation(summary = "Rebuild Lucene search index", description = "Rebuilds the Lucene search index.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Index rebuild started successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to start index rebuild")
    })
    @PostMapping("/rebuild-index")
    public ResponseEntity<String> rebuildSearchIndex() {
        String message = tinkarService.rebuildSearchIndex();
        return ResponseEntity.ok(message);
    }

}
