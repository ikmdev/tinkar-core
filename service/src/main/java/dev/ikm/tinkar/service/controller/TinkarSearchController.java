package dev.ikm.tinkar.service.controller;

import dev.ikm.tinkar.service.dto.ChangeHistoryResponse;
import dev.ikm.tinkar.service.dto.ConceptChangeHistoryResponse;
import dev.ikm.tinkar.service.dto.ConceptSearchResponse;
import dev.ikm.tinkar.service.dto.ConceptSemanticsResponse;
import dev.ikm.tinkar.service.dto.DescendantOperationResponse;
import dev.ikm.tinkar.service.dto.SearchSortOption;
import dev.ikm.tinkar.service.dto.TinkarSearchQueryResponse;
import dev.ikm.tinkar.service.dto.TinkarSearchQueryResponse.Descriptions;
import dev.ikm.tinkar.service.dto.TinkarSearchQueryResponse.SearchResult;
import dev.ikm.tinkar.service.dto.TinkarSearchQueryResponse.Stamp;
import dev.ikm.tinkar.service.proto.TinkarConceptDescriptions;
import dev.ikm.tinkar.service.proto.TinkarSearchResult;
import dev.ikm.tinkar.service.service.TinkarService;
import dev.ikm.tinkar.schema.StampVersion;
import dev.ikm.tinkar.service.controller.graphrag.GraphRAGRestController;
import dev.ikm.tinkar.service.controller.knowledgegraph.KnowledgeGraphRestController;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @deprecated Use {@link GraphRAGRestController} (Tier 1)
 *             and {@link KnowledgeGraphRestController} (Tier 2) instead.
 */
@Deprecated
@RestController
@RequestMapping("/api/tinkar")
@Tag(name = "Tinkar Search (Deprecated)", description = "DEPRECATED — Use IKE Graph RAG (Tier 1) or IKE Knowledge Graph (Tier 2) endpoints instead.")
public class TinkarSearchController {

        private final TinkarService tinkarService;

        public TinkarSearchController(TinkarService tinkarService) {
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

                return ResponseEntity.ok(toDto(tinkarService.search(query)));
        }

        @Operation(summary = "Search for concepts", description = "Search for concepts based off a search term")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Search completed successfully", content = @Content(schema = @Schema(implementation = TinkarSearchQueryResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid query parameter")
        })
        @GetMapping("/conceptSearch")
        public ResponseEntity<TinkarSearchQueryResponse> conceptSearch(
                        @Parameter(description = "Search query string", required = true, example = "chronic lung") @RequestParam String query,
                        @Parameter(description = "Maximum number of results to return", required = false) @RequestParam(name = "maxResults", required = false) Integer maxResults) {

                return ResponseEntity.ok(toDto(tinkarService.conceptSearch(query, maxResults)));
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
        @GetMapping("/conceptSearchWithSort")
        public ResponseEntity<ConceptSearchResponse> conceptSearchWithSort(
                        @Parameter(description = "Search query string", required = true, example = "chronic lung") @RequestParam String query,
                        @Parameter(description = "Maximum number of results to return", required = false) @RequestParam(name = "maxResults", required = false) Integer maxResults,
                        @Parameter(description = "Sort option: TOP_COMPONENT (default), TOP_COMPONENT_ALPHA, SEMANTIC, or SEMANTIC_ALPHA", required = false, example = "TOP_COMPONENT") @RequestParam(name = "sortBy", required = false) SearchSortOption sortBy) {

                return ResponseEntity.ok(tinkarService.conceptSearchWithSort(query, maxResults, sortBy));
        }

        @Operation(summary = "Get entity by concept ID", description = "Look up an entity by the tinkar concept public ID")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Entity retrieved successfully", content = @Content(schema = @Schema(implementation = TinkarSearchQueryResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid concept ID parameter")
        })
        @GetMapping("/conceptId")
        public ResponseEntity<TinkarSearchQueryResponse> getEntity(
                        @Parameter(description = "Concept ID", required = true, example = "f5c39ec3-7256-3a03-b651-d17b623a30ec") @RequestParam("conceptId") String conceptId) {

                return ResponseEntity.ok(toDto(tinkarService.getEntity(conceptId)));
        }

        @Operation(summary = "Get child concepts", description = "Look up child concepts for the tinkar concept public ID")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Child concepts retrieved successfully", content = @Content(schema = @Schema(implementation = TinkarSearchQueryResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid concept ID parameter")
        })
        @GetMapping("/children/conceptId")
        public ResponseEntity<TinkarSearchQueryResponse> getTinkarChildConcepts(
                        @Parameter(description = "Concept ID", required = true, example = "f5c39ec3-7256-3a03-b651-d17b623a30ec") @RequestParam("conceptId") String conceptId) {

                return ResponseEntity.ok(toDto(tinkarService.getChildConcepts(conceptId)));
        }

        @Operation(summary = "Get descendant concepts", description = "Look up descendant concepts for the tinkar concept public ID")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Descendant concepts retrieved successfully", content = @Content(schema = @Schema(implementation = TinkarSearchQueryResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid concept ID parameter")
        })
        @GetMapping("/descendants/conceptId")
        public ResponseEntity<TinkarSearchQueryResponse> getTinkarDescendantConcepts(
                        @Parameter(description = "Concept ID", required = true, example = "f5c39ec3-7256-3a03-b651-d17b623a30ec") @RequestParam("conceptId") String conceptId) {

                return ResponseEntity.ok(toDto(tinkarService.getDescendantConcepts(conceptId)));
        }

        @Operation(summary = "Get LIDR record concepts from test kit", description = "Look up lidr record concepts for a test kit concept public ID")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "LIDR record concepts retrieved successfully", content = @Content(schema = @Schema(implementation = TinkarSearchQueryResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid test kit concept ID parameter")
        })
        @GetMapping("/lidr-records/testKitConceptId")
        public ResponseEntity<TinkarSearchQueryResponse> getLIDRRecordConceptsFromTestKit(
                        @Parameter(description = "Test kit concept ID", required = true, example = "f5c39ec3-7256-3a03-b651-d17b623a30ec") @RequestParam("testKitConceptId") String testKitConceptId) {

                return ResponseEntity.ok(toDto(tinkarService.getLIDRRecordConceptsFromTestKit(testKitConceptId)));
        }

        @Operation(summary = "Get result conformance concepts from LIDR record", description = "Look up result conformances for a LIDR record concept public ID")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Result conformance concepts retrieved successfully", content = @Content(schema = @Schema(implementation = TinkarSearchQueryResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid LIDR record concept ID parameter")
        })
        @GetMapping("/result-conformances/lidrRecordConceptId")
        public ResponseEntity<TinkarSearchQueryResponse> getResultConformanceConceptsFromLIDRRecord(
                        @Parameter(description = "LIDR record concept ID", required = true, example = "f5c39ec3-7256-3a03-b651-d17b623a30ec") @RequestParam("lidrRecordConceptId") String lidrRecordConceptId) {

                return ResponseEntity.ok(toDto(tinkarService.getResultConformanceConceptsFromLIDRRecord(lidrRecordConceptId)));
        }

        @Operation(summary = "Get allowed result concepts from result conformance", description = "Look up allowed results for a result conformance concept public ID")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Allowed result concepts retrieved successfully", content = @Content(schema = @Schema(implementation = TinkarSearchQueryResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid result conformance concept ID parameter")
        })
        @GetMapping("/allowed-results/resultConformanceConceptId")
        public ResponseEntity<TinkarSearchQueryResponse> getAllowedResultConceptsFromResultConformance(
                        @Parameter(description = "Result conformance concept ID", required = true, example = "f5c39ec3-7256-3a03-b651-d17b623a30ec") @RequestParam("resultConformanceConceptId") String resultConformanceConceptId) {

                return ResponseEntity.ok(toDto(tinkarService.getAllowedResultConceptsFromResultConformance(
                                resultConformanceConceptId)));
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

        @Operation(summary = "Get change history for an entity", description = "Retrieves the complete change history for an entity, showing all STAMP versions and field modifications. This demonstrates IKE-Flow change tracking capabilities.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Change history retrieved successfully", content = @Content(schema = @Schema(implementation = ChangeHistoryResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid entity ID parameter")
        })
        @GetMapping("/change-history")
        public ResponseEntity<ChangeHistoryResponse> getChangeHistory(
                        @Parameter(description = "Entity ID (UUID)", required = true, example = "f5c39ec3-7256-3a03-b651-d17b623a30ec") @RequestParam("entityId") String entityId) {

                return ResponseEntity.ok(tinkarService.getChangeHistory(entityId));
        }

        @Operation(summary = "Create a sample change (comment)", description = "Creates a new comment semantic attached to the specified concept to demonstrate change tracking. Returns the change history for the newly created semantic.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Change created successfully", content = @Content(schema = @Schema(implementation = ChangeHistoryResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid concept ID or comment parameter")
        })
        @PostMapping("/create-change")
        public ResponseEntity<ChangeHistoryResponse> createSampleChange(
                        @Parameter(description = "Concept ID to attach the comment to (UUID)", required = true, example = "f5c39ec3-7256-3a03-b651-d17b623a30ec") @RequestParam("conceptId") String conceptId,
                        @Parameter(description = "Comment text to add", required = true, example = "This is a sample comment for demonstration") @RequestParam("comment") String comment) {

                return ResponseEntity.ok(tinkarService.createSampleChange(conceptId, comment));
        }

        @Operation(summary = "Get comments for a concept", description = "Retrieves all comment semantics attached to a concept. Use this to view all comments added via the create-change endpoint.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Comments retrieved successfully", content = @Content(schema = @Schema(implementation = ConceptSemanticsResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid concept ID parameter")
        })
        @GetMapping("/comments")
        public ResponseEntity<ConceptSemanticsResponse> getConceptComments(
                        @Parameter(description = "Concept ID (UUID)", required = true, example = "9fc3832b-a5f8-5504-ba16-7551976841dc") @RequestParam("conceptId") String conceptId) {

                return ResponseEntity.ok(tinkarService.getConceptComments(conceptId));
        }

        @Operation(summary = "Get all semantics for a concept", description = "Retrieves all semantics (comments, descriptions, axioms, etc.) attached to a concept.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Semantics retrieved successfully", content = @Content(schema = @Schema(implementation = ConceptSemanticsResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid concept ID parameter")
        })
        @GetMapping("/semantics")
        public ResponseEntity<ConceptSemanticsResponse> inspectConcept(
                        @Parameter(description = "Concept ID (UUID)", required = true, example = "9fc3832b-a5f8-5504-ba16-7551976841dc") @RequestParam("conceptId") String conceptId) {

                return ResponseEntity.ok(tinkarService.inspectConcept(conceptId));
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

        @Operation(summary = "Get comprehensive change history for a concept", description = "Retrieves the full change history for a concept INCLUDING all attached semantics (comments, descriptions, axioms, etc.). Use this to see all changes made to a concept and its associated data.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Change history retrieved successfully", content = @Content(schema = @Schema(implementation = ConceptChangeHistoryResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid concept ID parameter")
        })
        @GetMapping("/concept-change-history")
        public ResponseEntity<ConceptChangeHistoryResponse> getConceptChangeHistory(
                        @Parameter(description = "Concept ID (UUID)", required = true, example = "9fc3832b-a5f8-5504-ba16-7551976841dc") @RequestParam("conceptId") String conceptId) {

                return ResponseEntity.ok(tinkarService.getConceptChangeHistory(conceptId));
        }

        @Operation(summary = "Save pending changes", description = "Saves all pending changes to persistent storage (disk). Changes made via create-change are held in memory until this endpoint is called. This supports a review workflow where changes can be reviewed via concept-change-history before being permanently saved.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Save operation completed"),
                        @ApiResponse(responseCode = "500", description = "Failed to save changes")
        })
        @PostMapping("/save-changes")
        public ResponseEntity<String> saveChanges() {
                String message = tinkarService.saveChanges();
                return ResponseEntity.ok(message);
        }

        @Operation(summary = "Discard pending changes", description = "Discards all pending changes that have not been saved to disk. After calling this, restart the server to reload data from the last saved state.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Discard operation completed"),
                        @ApiResponse(responseCode = "500", description = "Failed to discard changes")
        })
        @PostMapping("/discard-changes")
        public ResponseEntity<String> discardChanges() {
                String message = tinkarService.discardChanges();
                return ResponseEntity.ok(message);
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

        @Operation(summary = "Remove a descendant from a concept", description = "Removes the IS-A relationship between a parent concept and a descendant concept. The descendant will no longer appear as a child of the parent.")
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

                Descriptions descriptions = toDescriptionsDto(proto.getDescriptions());
                Stamp stamp = toStampDto(proto.getStamp());

                return new SearchResult(publicIds, descriptions, stamp);
        }

        private Descriptions toDescriptionsDto(TinkarConceptDescriptions proto) {
                return new Descriptions(
                                proto.getFullyQualifiedName(),
                                proto.getRegularName(),
                                proto.getDefinition());
        }

        private Stamp toStampDto(StampVersion proto) {
                String statusPublicId = proto.hasStatusPublicId() && !proto.getStatusPublicId().getUuidsList().isEmpty()
                                ? proto.getStatusPublicId().getUuids(0)
                                : null;
                String authorPublicId = proto.hasAuthorPublicId() && !proto.getAuthorPublicId().getUuidsList().isEmpty()
                                ? proto.getAuthorPublicId().getUuids(0)
                                : null;
                String modulePublicId = proto.hasModulePublicId() && !proto.getModulePublicId().getUuidsList().isEmpty()
                                ? proto.getModulePublicId().getUuids(0)
                                : null;
                String pathPublicId = proto.hasPathPublicId() && !proto.getPathPublicId().getUuidsList().isEmpty()
                                ? proto.getPathPublicId().getUuids(0)
                                : null;

                return new Stamp(
                                statusPublicId,
                                authorPublicId,
                                modulePublicId,
                                pathPublicId,
                                proto.getTime());
        }
}
