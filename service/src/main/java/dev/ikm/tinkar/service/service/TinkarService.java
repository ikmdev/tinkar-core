package dev.ikm.tinkar.service.service;

import dev.ikm.tinkar.service.dto.ChangeHistoryResponse;
import dev.ikm.tinkar.service.dto.ConceptChangeHistoryResponse;
import dev.ikm.tinkar.service.dto.ConceptSearchResponse;
import dev.ikm.tinkar.service.dto.ConceptSemanticsResponse;
import dev.ikm.tinkar.service.dto.DescendantOperationResponse;
import dev.ikm.tinkar.service.dto.EntityCountSummaryResponse;
import dev.ikm.tinkar.service.dto.ReasonerResultsResponse;
import dev.ikm.tinkar.service.dto.SearchSortOption;
import dev.ikm.tinkar.service.proto.TinkarConceptSemanticsResponse;
import dev.ikm.tinkar.service.proto.TinkarSearchQueryResponse;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculatorWithCache;

import java.io.File;
import java.util.List;

public interface TinkarService {
    TinkarSearchQueryResponse search(String query);

    TinkarSearchQueryResponse conceptSearch(String query, Integer maxResults);

    /**
     * Searches for concepts with configurable sort options.
     * Supports both grouped (by top component) and flat (by semantic match) result structures.
     *
     * Sort options:
     * - TOP_COMPONENT: Groups results by concept, sorted by relevance score (highest first)
     * - TOP_COMPONENT_ALPHA: Groups results by concept, sorted alphabetically
     * - SEMANTIC: Flat list of semantic matches, sorted by relevance score (highest first)
     * - SEMANTIC_ALPHA: Flat list of semantic matches, sorted alphabetically
     *
     * @param query The search query string
     * @param maxResults Maximum number of results to return
     * @param sortBy The sort option to use (defaults to TOP_COMPONENT if null)
     * @return ConceptSearchResponse containing search results in the requested format
     */
    ConceptSearchResponse conceptSearchWithSort(String query, Integer maxResults, SearchSortOption sortBy);

    TinkarSearchQueryResponse getEntity(String conceptId);

    TinkarSearchQueryResponse getChildConcepts(String conceptId);

    /**
     * Gets direct child concepts using the specified view calculator for coordinate resolution.
     * The navigation coordinate (premise type) controls whether stated or inferred relationships are used.
     * @param conceptId The public ID (UUID) of the parent concept
     * @param viewCalculator The view calculator with coordinate overrides
     * @return TinkarSearchQueryResponse containing child concepts
     */
    TinkarSearchQueryResponse getChildConcepts(String conceptId, ViewCalculatorWithCache viewCalculator);

    TinkarSearchQueryResponse getDescendantConcepts(String conceptId);

    /**
     * Gets all descendant concepts (full subtree) using the specified view calculator for coordinate resolution.
     * The navigation coordinate (premise type) controls whether stated or inferred relationships are used.
     * @param conceptId The public ID (UUID) of the parent concept
     * @param viewCalculator The view calculator with coordinate overrides
     * @return TinkarSearchQueryResponse containing descendant concepts
     */
    TinkarSearchQueryResponse getDescendantConcepts(String conceptId, ViewCalculatorWithCache viewCalculator);

    TinkarSearchQueryResponse getLIDRRecordConceptsFromTestKit(String conceptId);

    TinkarSearchQueryResponse getResultConformanceConceptsFromLIDRRecord(String conceptId);

    TinkarSearchQueryResponse getAllowedResultConceptsFromResultConformance(String conceptId);

    /**
     * Rebuilds the Lucene search index. This operation is asynchronous and may take some time.
     * @return A message indicating that the rebuild process has started
     */
    String rebuildSearchIndex();

    /**
     * Gets the change history for an entity, showing all version changes and field modifications.
     * This demonstrates IKE-Flow change tracking capabilities.
     * @param entityId The public ID (UUID) of the entity
     * @return ChangeHistoryResponse containing the chronology of changes
     */
    ChangeHistoryResponse getChangeHistory(String entityId);

    /**
     * Gets the change history for an entity using the specified view calculator for coordinate resolution.
     * @param entityId The public ID (UUID) of the entity
     * @param viewCalculator The view calculator with coordinate overrides
     * @return ChangeHistoryResponse containing the chronology of changes
     */
    ChangeHistoryResponse getChangeHistory(String entityId, ViewCalculatorWithCache viewCalculator);

    /**
     * Creates a sample semantic modification on an existing concept to demonstrate change tracking.
     * This creates a new comment/annotation semantic on the specified concept.
     * @param conceptId The public ID (UUID) of the concept to annotate
     * @param comment The comment text to add as an annotation
     * @return ChangeHistoryResponse showing the change that was made
     */
    ChangeHistoryResponse createSampleChange(String conceptId, String comment);

    /**
     * Gets all comment semantics attached to a concept.
     * @param conceptId The public ID (UUID) of the concept
     * @return ConceptSemanticsResponse containing all comments for this concept
     */
    ConceptSemanticsResponse getConceptComments(String conceptId);

    /**
     * Gets all comment semantics attached to a concept using the specified view calculator.
     * @param conceptId The public ID (UUID) of the concept
     * @param viewCalculator The view calculator with coordinate overrides
     * @return ConceptSemanticsResponse containing all comments for this concept
     */
    ConceptSemanticsResponse getConceptComments(String conceptId, ViewCalculatorWithCache viewCalculator);

    /**
     * Gets all semantics of any pattern attached to a concept.
     * @param conceptId The public ID (UUID) of the concept
     * @return ConceptSemanticsResponse containing all semantics for this concept
     */
    ConceptSemanticsResponse getConceptSemantics(String conceptId);

    /**
     * Gets all semantics of any pattern attached to a concept using the specified view calculator.
     * @param conceptId The public ID (UUID) of the concept
     * @param viewCalculator The view calculator with coordinate overrides
     * @return ConceptSemanticsResponse containing all semantics for this concept
     */
    ConceptSemanticsResponse getConceptSemantics(String conceptId, ViewCalculatorWithCache viewCalculator);

    /**
     * Gets all semantics of any pattern attached to a concept (gRPC proto response).
     * @param conceptId The public ID (UUID) of the concept
     * @return TinkarConceptSemanticsResponse proto containing all semantics for this concept
     */
    TinkarConceptSemanticsResponse getConceptSemanticsProto(String conceptId);

    /**
     * Gets all semantics of any pattern attached to a concept (gRPC proto response) using the specified view calculator.
     * @param conceptId The public ID (UUID) of the concept
     * @param viewCalculator The view calculator with coordinate overrides
     * @return TinkarConceptSemanticsResponse proto containing all semantics for this concept
     */
    TinkarConceptSemanticsResponse getConceptSemanticsProto(String conceptId, ViewCalculatorWithCache viewCalculator);

    /**
     * Gets comprehensive change history for a concept including all attached semantics.
     * This shows changes to the concept itself AND changes to all comments, descriptions, etc.
     * @param conceptId The public ID (UUID) of the concept
     * @return ConceptChangeHistoryResponse containing the full change history
     */
    ConceptChangeHistoryResponse getConceptChangeHistory(String conceptId);

    /**
     * Gets comprehensive change history for a concept including all attached semantics,
     * using the specified view calculator for coordinate resolution.
     * @param conceptId The public ID (UUID) of the concept
     * @param viewCalculator The view calculator with coordinate overrides
     * @return ConceptChangeHistoryResponse containing the full change history
     */
    ConceptChangeHistoryResponse getConceptChangeHistory(String conceptId, ViewCalculatorWithCache viewCalculator);

    /**
     * Saves all pending changes to persistent storage (disk).
     * Changes made via createSampleChange are held in memory until this method is called.
     * This supports a review workflow where changes can be reviewed before being committed to disk.
     * @return A message indicating the save result
     */
    String saveChanges();

    /**
     * Discards all pending changes that have not been saved to disk.
     * This effectively reverts any changes made since the last save or server start.
     * Note: This requires a data reload to take effect.
     * @return A message indicating the discard result
     */
    String discardChanges();

    /**
     * Creates a new descendant relationship between a parent concept and an existing concept.
     * This adds an IS-A relationship making the descendant a child of the parent.
     * @param parentConceptId The public ID (UUID) of the parent concept
     * @param descendantConceptId The public ID (UUID) of the concept to make a descendant
     * @return DescendantOperationResponse indicating success or failure
     */
    DescendantOperationResponse addDescendant(String parentConceptId, String descendantConceptId);

    /**
     * Creates a new concept with the given name and adds it as a descendant of the parent concept.
     * This is a convenience method that creates both the concept and the IS-A relationship.
     * @param parentConceptId The public ID (UUID) of the parent concept
     * @param conceptName The fully qualified name for the new concept
     * @return DescendantOperationResponse containing the newly created concept's ID
     */
    DescendantOperationResponse createAndAddDescendant(String parentConceptId, String conceptName);

    /**
     * Removes a descendant relationship between a parent concept and a descendant concept.
     * This removes the IS-A relationship making the descendant no longer a child of the parent.
     * @param parentConceptId The public ID (UUID) of the parent concept
     * @param descendantConceptId The public ID (UUID) of the descendant concept to remove
     * @return DescendantOperationResponse indicating success or failure
     */
    DescendantOperationResponse removeDescendant(String parentConceptId, String descendantConceptId);

    // ── Admin: Import / Export / Reasoner ────────────────────────────

    /**
     * Imports a Tinkar changeset from a protobuf ZIP file.
     * @param importFile the ZIP file containing delimited TinkarMsg protobuf + manifest
     * @param useMultiPass if true, use multi-pass import to resolve forward references
     * @return EntityCountSummaryResponse with counts of imported entities
     */
    EntityCountSummaryResponse importChangeset(File importFile, boolean useMultiPass);

    /**
     * Runs the full reasoner classification pipeline:
     * init -> extractData -> loadData -> computeInferences -> writeInferredResults.
     * @return ReasonerResultsResponse with classification results
     */
    ReasonerResultsResponse runReasoner();
}
