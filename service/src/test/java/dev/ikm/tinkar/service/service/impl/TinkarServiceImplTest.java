package dev.ikm.tinkar.service.service.impl;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.PrimitiveDataService;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculatorWithCache;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.service.dto.ChangeHistoryResponse;
import dev.ikm.tinkar.service.dto.ConceptChangeHistoryResponse;
import dev.ikm.tinkar.service.dto.ConceptSearchResponse;
import dev.ikm.tinkar.service.dto.ConceptSemanticsResponse;
import dev.ikm.tinkar.service.dto.DescendantOperationResponse;
import dev.ikm.tinkar.service.dto.SearchSortOption;
import dev.ikm.tinkar.service.proto.TinkarConceptEntityResponse;
import dev.ikm.tinkar.service.proto.TinkarConceptSemanticsResponse;
import dev.ikm.tinkar.service.proto.TinkarSearchQueryResponse;
import dev.ikm.tinkar.service.service.TinkarPrimitive;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.factory.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TinkarServiceImplTest {

    @Mock
    TinkarPrimitive primitive;

    TinkarServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TinkarServiceImpl(primitive);
    }

    // ── getLIDRRecordConceptsFromTestKit ──────────────────────────────────────

    @Test
    void getLIDRRecordConceptsFromTestKit_emptyList_returnsSuccessWithZeroResults() {
        when(primitive.getPublicId(any())).thenReturn(publicId("aaaaaaaa-0000-0000-0000-000000000001"));
        when(primitive.getLidrRecordSemanticsFromTestKit(any())).thenReturn(List.of());

        TinkarSearchQueryResponse response =
                service.getLIDRRecordConceptsFromTestKit("aaaaaaaa-0000-0000-0000-000000000001");

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getTotalCount()).isZero();
        assertThat(response.getResultsList()).isEmpty();
        assertThat(response.getErrorMessage()).isEmpty();
        assertThat(response.getCreatedAt()).isGreaterThan(0L);
    }

    @Test
    void getLIDRRecordConceptsFromTestKit_primitiveThrows_returnsErrorResponse() {
        when(primitive.getPublicId(any())).thenReturn(publicId("aaaaaaaa-0000-0000-0000-000000000001"));
        when(primitive.getLidrRecordSemanticsFromTestKit(any()))
                .thenThrow(new RuntimeException("LIDR patterns not present in dataset"));

        TinkarSearchQueryResponse response =
                service.getLIDRRecordConceptsFromTestKit("aaaaaaaa-0000-0000-0000-000000000001");

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getTotalCount()).isZero();
        assertThat(response.getResultsList()).isEmpty();
        assertThat(response.getErrorMessage()).contains("LIDR patterns not present in dataset");
        assertThat(response.getCreatedAt()).isGreaterThan(0L);
    }

    @Test
    void getLIDRRecordConceptsFromTestKit_getPublicIdThrows_returnsErrorResponse() {
        when(primitive.getPublicId(any())).thenThrow(new IllegalArgumentException("invalid UUID"));

        TinkarSearchQueryResponse response =
                service.getLIDRRecordConceptsFromTestKit("not-a-uuid");

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("invalid UUID");
    }

    // ── getResultConformanceConceptsFromLIDRRecord ───────────────────────────

    @Test
    void getResultConformanceConceptsFromLIDRRecord_emptyList_returnsSuccessWithZeroResults() {
        when(primitive.getPublicId(any())).thenReturn(publicId("bbbbbbbb-0000-0000-0000-000000000001"));
        when(primitive.getResultConformancesFromLidrRecord(any())).thenReturn(List.of());

        TinkarSearchQueryResponse response =
                service.getResultConformanceConceptsFromLIDRRecord("bbbbbbbb-0000-0000-0000-000000000001");

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getTotalCount()).isZero();
        assertThat(response.getResultsList()).isEmpty();
    }

    @Test
    void getResultConformanceConceptsFromLIDRRecord_primitiveThrows_returnsErrorResponse() {
        when(primitive.getPublicId(any())).thenReturn(publicId("bbbbbbbb-0000-0000-0000-000000000001"));
        when(primitive.getResultConformancesFromLidrRecord(any()))
                .thenThrow(new RuntimeException("no entity key found"));

        TinkarSearchQueryResponse response =
                service.getResultConformanceConceptsFromLIDRRecord("bbbbbbbb-0000-0000-0000-000000000001");

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("no entity key found");
        assertThat(response.getCreatedAt()).isGreaterThan(0L);
    }

    // ── getAllowedResultConceptsFromResultConformance ─────────────────────────

    @Test
    void getAllowedResultConceptsFromResultConformance_emptyList_returnsSuccessWithZeroResults() {
        when(primitive.getPublicId(any())).thenReturn(publicId("cccccccc-0000-0000-0000-000000000001"));
        when(primitive.getAllowedResultsFromResultConformance(any())).thenReturn(List.of());

        TinkarSearchQueryResponse response =
                service.getAllowedResultConceptsFromResultConformance("cccccccc-0000-0000-0000-000000000001");

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getTotalCount()).isZero();
        assertThat(response.getResultsList()).isEmpty();
    }

    @Test
    void getAllowedResultConceptsFromResultConformance_primitiveThrows_returnsErrorResponse() {
        when(primitive.getPublicId(any())).thenReturn(publicId("cccccccc-0000-0000-0000-000000000001"));
        when(primitive.getAllowedResultsFromResultConformance(any()))
                .thenThrow(new RuntimeException("result conformance not found"));

        TinkarSearchQueryResponse response =
                service.getAllowedResultConceptsFromResultConformance("cccccccc-0000-0000-0000-000000000001");

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("result conformance not found");
    }

    // ── rebuildSearchIndex ───────────────────────────────────────────────────

    @Test
    void rebuildSearchIndex_success_returnsStartedMessage() throws Exception {
        PrimitiveDataService mockDataService = Mockito.mock(PrimitiveDataService.class);
        when(mockDataService.recreateLuceneIndex())
                .thenReturn(CompletableFuture.completedFuture(null));

        try (MockedStatic<PrimitiveData> staticPrimitiveData = Mockito.mockStatic(PrimitiveData.class)) {
            staticPrimitiveData.when(PrimitiveData::get).thenReturn(mockDataService);

            String result = service.rebuildSearchIndex();

            assertThat(result).containsIgnoringCase("rebuild");
            assertThat(result).containsIgnoringCase("started");
        }
    }

    @Test
    void rebuildSearchIndex_primitiveDataThrows_returnsFailureMessage() {
        try (MockedStatic<PrimitiveData> staticPrimitiveData = Mockito.mockStatic(PrimitiveData.class)) {
            staticPrimitiveData.when(PrimitiveData::get)
                    .thenThrow(new RuntimeException("data service not initialized"));

            String result = service.rebuildSearchIndex();

            assertThat(result).containsIgnoringCase("failed");
        }
    }

    @Test
    void rebuildSearchIndex_recreateLuceneIndexThrows_returnsFailureMessage() throws Exception {
        PrimitiveDataService mockDataService = Mockito.mock(PrimitiveDataService.class);
        when(mockDataService.recreateLuceneIndex())
                .thenThrow(new RuntimeException("index locked"));

        try (MockedStatic<PrimitiveData> staticPrimitiveData = Mockito.mockStatic(PrimitiveData.class)) {
            staticPrimitiveData.when(PrimitiveData::get).thenReturn(mockDataService);

            String result = service.rebuildSearchIndex();

            assertThat(result).containsIgnoringCase("failed");
        }
    }

    // ── search ───────────────────────────────────────────────────────────────

    @Test
    void search_emptyResults_returnsSuccessWithZeroResults() throws Exception {
        when(primitive.search(any(), anyInt())).thenReturn(List.of());

        TinkarSearchQueryResponse response = service.search("diabetes");

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getTotalCount()).isZero();
        assertThat(response.getResultsList()).isEmpty();
        assertThat(response.getErrorMessage()).isEmpty();
    }

    @Test
    void search_emptyDatabaseError_returnsSuccessWithZeroResults() throws Exception {
        when(primitive.search(any(), anyInt()))
                .thenThrow(new IllegalStateException("No entity key found for UUIDs [abc]"));

        TinkarSearchQueryResponse response = service.search("test");

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getTotalCount()).isZero();
        assertThat(response.getResultsList()).isEmpty();
    }

    @Test
    void search_otherIllegalStateException_returnsErrorResponse() throws Exception {
        when(primitive.search(any(), anyInt()))
                .thenThrow(new IllegalStateException("database locked"));

        TinkarSearchQueryResponse response = service.search("test");

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("database locked");
    }

    @Test
    void search_generalException_returnsErrorResponse() throws Exception {
        when(primitive.search(any(), anyInt()))
                .thenThrow(new RuntimeException("network timeout"));

        TinkarSearchQueryResponse response = service.search("test");

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("network timeout");
    }

    @Test
    void search_nullQuery_returnsSuccessWithEmptyQueryField() throws Exception {
        when(primitive.search(any(), anyInt())).thenReturn(List.of());

        TinkarSearchQueryResponse response = service.search(null);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getQuery()).isEmpty();
    }

    // ── getChildConcepts ─────────────────────────────────────────────────────

    @Test
    void getChildConcepts_emptyList_returnsSuccessWithZeroResults() {
        when(primitive.getPublicId(any())).thenReturn(publicId("aaaaaaaa-0000-0000-0000-000000000001"));
        when(primitive.childrenOf(any())).thenReturn(List.of());

        TinkarSearchQueryResponse response = service.getChildConcepts("aaaaaaaa-0000-0000-0000-000000000001");

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getTotalCount()).isZero();
        assertThat(response.getResultsList()).isEmpty();
    }

    @Test
    void getChildConcepts_getPublicIdThrows_returnsErrorResponse() {
        when(primitive.getPublicId(any())).thenThrow(new IllegalArgumentException("bad uuid"));

        TinkarSearchQueryResponse response = service.getChildConcepts("not-a-uuid");

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("bad uuid");
    }

    @Test
    void getChildConcepts_childrenOfThrows_returnsErrorResponse() {
        when(primitive.getPublicId(any())).thenReturn(publicId("aaaaaaaa-0000-0000-0000-000000000001"));
        when(primitive.childrenOf(any())).thenThrow(new RuntimeException("graph error"));

        TinkarSearchQueryResponse response = service.getChildConcepts("aaaaaaaa-0000-0000-0000-000000000001");

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("graph error");
    }

    // ── getDescendantConcepts ─────────────────────────────────────────────────

    @Test
    void getDescendantConcepts_emptyList_returnsSuccessWithZeroResults() {
        when(primitive.getPublicId(any())).thenReturn(publicId("bbbbbbbb-0000-0000-0000-000000000001"));
        when(primitive.descendantsOf(any())).thenReturn(List.of());

        TinkarSearchQueryResponse response = service.getDescendantConcepts("bbbbbbbb-0000-0000-0000-000000000001");

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getTotalCount()).isZero();
        assertThat(response.getResultsList()).isEmpty();
    }

    @Test
    void getDescendantConcepts_throws_returnsErrorResponse() {
        when(primitive.getPublicId(any())).thenReturn(publicId("bbbbbbbb-0000-0000-0000-000000000001"));
        when(primitive.descendantsOf(any())).thenThrow(new RuntimeException("traversal failed"));

        TinkarSearchQueryResponse response = service.getDescendantConcepts("bbbbbbbb-0000-0000-0000-000000000001");

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("traversal failed");
    }

    // ── getEntity ─────────────────────────────────────────────────────────────

    @Test
    void getEntity_getPublicIdThrows_returnsErrorResponse() {
        when(primitive.getPublicId(any())).thenThrow(new IllegalArgumentException("invalid id"));

        TinkarSearchQueryResponse response = service.getEntity("bad-id");

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("invalid id");
    }

    @Test
    void getEntity_throwsWithNullMessage_errorResponseHasUnknownError() {
        when(primitive.getPublicId(any())).thenThrow(new RuntimeException((String) null));

        TinkarSearchQueryResponse response = service.getEntity("some-id");

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isEqualTo("Unknown error");
    }

    // ── getChildConcepts (with ViewCalculatorWithCache) ───────────────────────

    @Test
    void getChildConceptsWithCalc_getPublicIdThrows_returnsErrorResponse() {
        when(primitive.getPublicId(any())).thenThrow(new RuntimeException("uuid lookup failed"));

        ViewCalculatorWithCache mockCalc = Mockito.mock(ViewCalculatorWithCache.class);
        TinkarSearchQueryResponse response = service.getChildConcepts("bad-id", mockCalc);

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("uuid lookup failed");
    }

    // ── getDescendantConcepts (with ViewCalculatorWithCache) ──────────────────

    @Test
    void getDescendantConceptsWithCalc_getPublicIdThrows_returnsErrorResponse() {
        when(primitive.getPublicId(any())).thenThrow(new RuntimeException("lookup failed"));

        ViewCalculatorWithCache mockCalc = Mockito.mock(ViewCalculatorWithCache.class);
        TinkarSearchQueryResponse response = service.getDescendantConcepts("bad-id", mockCalc);

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("lookup failed");
    }

    // ── conceptSearch ─────────────────────────────────────────────────────────

    @Test
    void conceptSearch_emptyDatabaseError_returnsSuccessWithZeroResults() {
        try (MockedStatic<Calculators.View> staticCalc = Mockito.mockStatic(Calculators.View.class)) {
            staticCalc.when(() -> Calculators.View.Default())
                    .thenThrow(new IllegalStateException("No entity key found for UUIDs [test]"));

            TinkarSearchQueryResponse response = service.conceptSearch("diabetes", 10);

            assertThat(response.getSuccess()).isTrue();
            assertThat(response.getTotalCount()).isZero();
        }
    }

    @Test
    void conceptSearch_otherIllegalStateException_returnsErrorResponse() {
        try (MockedStatic<Calculators.View> staticCalc = Mockito.mockStatic(Calculators.View.class)) {
            staticCalc.when(() -> Calculators.View.Default())
                    .thenThrow(new IllegalStateException("index not initialized"));

            TinkarSearchQueryResponse response = service.conceptSearch("test", null);

            assertThat(response.getSuccess()).isFalse();
            assertThat(response.getErrorMessage()).contains("index not initialized");
        }
    }

    @Test
    void conceptSearch_generalException_returnsErrorResponse() {
        try (MockedStatic<Calculators.View> staticCalc = Mockito.mockStatic(Calculators.View.class)) {
            staticCalc.when(() -> Calculators.View.Default())
                    .thenThrow(new RuntimeException("service unavailable"));

            TinkarSearchQueryResponse response = service.conceptSearch("test", 5);

            assertThat(response.getSuccess()).isFalse();
            assertThat(response.getErrorMessage()).contains("service unavailable");
        }
    }

    // ── conceptSearchWithSort ─────────────────────────────────────────────────

    @Test
    void conceptSearchWithSort_emptyDatabaseError_returnsEmptySuccessResponse() {
        try (MockedStatic<Calculators.View> staticCalc = Mockito.mockStatic(Calculators.View.class)) {
            staticCalc.when(() -> Calculators.View.Default())
                    .thenThrow(new IllegalStateException("No entity key found for UUIDs [test]"));

            ConceptSearchResponse response = service.conceptSearchWithSort("test", 10, SearchSortOption.TOP_COMPONENT);

            assertThat(response.success()).isTrue();
            assertThat(response.totalCount()).isZero();
        }
    }

    @Test
    void conceptSearchWithSort_otherIllegalStateException_returnsErrorResponse() {
        try (MockedStatic<Calculators.View> staticCalc = Mockito.mockStatic(Calculators.View.class)) {
            staticCalc.when(() -> Calculators.View.Default())
                    .thenThrow(new IllegalStateException("index corrupt"));

            ConceptSearchResponse response = service.conceptSearchWithSort("test", 10, SearchSortOption.SEMANTIC);

            assertThat(response.success()).isFalse();
            assertThat(response.errorMessage()).contains("index corrupt");
        }
    }

    @Test
    void conceptSearchWithSort_nullSortBy_defaultsToTopComponent_emptyDb() {
        try (MockedStatic<Calculators.View> staticCalc = Mockito.mockStatic(Calculators.View.class)) {
            staticCalc.when(() -> Calculators.View.Default())
                    .thenThrow(new IllegalStateException("No entity key found for UUIDs [test]"));

            ConceptSearchResponse response = service.conceptSearchWithSort("test", 10, null);

            assertThat(response.success()).isTrue();
        }
    }

    @Test
    void conceptSearchWithSort_generalException_returnsErrorResponse() {
        try (MockedStatic<Calculators.View> staticCalc = Mockito.mockStatic(Calculators.View.class)) {
            staticCalc.when(() -> Calculators.View.Default())
                    .thenThrow(new RuntimeException("timeout"));

            ConceptSearchResponse response = service.conceptSearchWithSort("test", 5, SearchSortOption.SEMANTIC_ALPHA);

            assertThat(response.success()).isFalse();
            assertThat(response.errorMessage()).contains("timeout");
        }
    }

    // ── getChangeHistory ─────────────────────────────────────────────────────

    @Test
    void getChangeHistory_withCalc_getPublicIdThrows_returnsErrorResponse() {
        ViewCalculatorWithCache mockCalc = Mockito.mock(ViewCalculatorWithCache.class);
        when(primitive.getPublicId(any())).thenThrow(new RuntimeException("entity not found"));

        ChangeHistoryResponse response = service.getChangeHistory("bad-id", mockCalc);

        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).contains("entity not found");
    }

    @Test
    void getChangeHistory_noCalc_delegatesToTwoArgVersion() {
        try (MockedStatic<Calculators.View> calcMock = Mockito.mockStatic(Calculators.View.class)) {
            ViewCalculatorWithCache mockCalc = Mockito.mock(ViewCalculatorWithCache.class);
            calcMock.when(() -> Calculators.View.Default()).thenReturn(mockCalc);
            when(primitive.getPublicId(any())).thenThrow(new RuntimeException("not found"));

            ChangeHistoryResponse response = service.getChangeHistory("any-id");

            assertThat(response.success()).isFalse();
        }
    }

    // ── createSampleChange ────────────────────────────────────────────────────

    @Test
    void createSampleChange_getPublicIdThrows_returnsErrorResponse() {
        when(primitive.getPublicId(any())).thenThrow(new RuntimeException("unknown concept"));

        ChangeHistoryResponse response = service.createSampleChange("bad-id", "test comment");

        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).contains("unknown concept");
    }

    // ── getConceptComments ───────────────────────────────────────────────────

    @Test
    void getConceptComments_withCalc_getPublicIdThrows_returnsErrorResponse() {
        ViewCalculatorWithCache mockCalc = Mockito.mock(ViewCalculatorWithCache.class);
        when(primitive.getPublicId(any())).thenThrow(new RuntimeException("not found"));

        ConceptSemanticsResponse response = service.getConceptComments("bad-id", mockCalc);

        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).contains("not found");
    }

    @Test
    void getConceptComments_noCalc_delegatesToTwoArgVersion() {
        try (MockedStatic<Calculators.View> calcMock = Mockito.mockStatic(Calculators.View.class)) {
            ViewCalculatorWithCache mockCalc = Mockito.mock(ViewCalculatorWithCache.class);
            calcMock.when(() -> Calculators.View.Default()).thenReturn(mockCalc);
            when(primitive.getPublicId(any())).thenThrow(new RuntimeException("not found"));

            ConceptSemanticsResponse response = service.getConceptComments("any-id");

            assertThat(response.success()).isFalse();
        }
    }

    // ── inspectConceptProto ───────────────────────────────────────────────────

    @Test
    void inspectConceptProto_withCalc_getPublicIdThrows_returnsErrorProto() {
        ViewCalculatorWithCache mockCalc = Mockito.mock(ViewCalculatorWithCache.class);
        when(primitive.getPublicId(any())).thenThrow(new RuntimeException("concept missing"));

        TinkarConceptSemanticsResponse response = service.inspectConceptProto("bad-id", mockCalc);

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("concept missing");
    }

    @Test
    void inspectConceptProto_noCalc_delegatesToTwoArgVersion() {
        try (MockedStatic<Calculators.View> calcMock = Mockito.mockStatic(Calculators.View.class)) {
            ViewCalculatorWithCache mockCalc = Mockito.mock(ViewCalculatorWithCache.class);
            calcMock.when(() -> Calculators.View.Default()).thenReturn(mockCalc);
            when(primitive.getPublicId(any())).thenThrow(new RuntimeException("missing"));

            TinkarConceptSemanticsResponse response = service.inspectConceptProto("any-id");

            assertThat(response.getSuccess()).isFalse();
        }
    }

    // ── inspectConcept ────────────────────────────────────────────────────────

    @Test
    void inspectConcept_withCalc_getPublicIdThrows_returnsErrorResponse() {
        ViewCalculatorWithCache mockCalc = Mockito.mock(ViewCalculatorWithCache.class);
        when(primitive.getPublicId(any())).thenThrow(new RuntimeException("lookup failed"));

        ConceptSemanticsResponse response = service.inspectConcept("bad-id", mockCalc);

        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).contains("lookup failed");
    }

    @Test
    void inspectConcept_noCalc_delegatesToTwoArgVersion() {
        try (MockedStatic<Calculators.View> calcMock = Mockito.mockStatic(Calculators.View.class)) {
            ViewCalculatorWithCache mockCalc = Mockito.mock(ViewCalculatorWithCache.class);
            calcMock.when(() -> Calculators.View.Default()).thenReturn(mockCalc);
            when(primitive.getPublicId(any())).thenThrow(new RuntimeException("not found"));

            ConceptSemanticsResponse response = service.inspectConcept("any-id");

            assertThat(response.success()).isFalse();
        }
    }

    // ── loadConceptEntityGraph ────────────────────────────────────────────────

    @Test
    void loadConceptEntityGraph_getPublicIdThrows_returnsErrorResponse() {
        when(primitive.getPublicId(any())).thenThrow(new RuntimeException("concept not found"));

        TinkarConceptEntityResponse response = service.loadConceptEntityGraph("bad-id");

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("concept not found");
    }

    // ── getEntityByPublicId ───────────────────────────────────────────────────

    @Test
    void getEntityByPublicId_getPublicIdThrows_returnsErrorResponse() {
        when(primitive.getPublicId(any())).thenThrow(new RuntimeException("bad public id"));

        TinkarConceptEntityResponse response = service.getEntityByPublicId("bad-id");

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("bad public id");
    }

    // ── getConceptChangeHistory ───────────────────────────────────────────────

    @Test
    void getConceptChangeHistory_withCalc_getPublicIdThrows_returnsErrorResponse() {
        ViewCalculatorWithCache mockCalc = Mockito.mock(ViewCalculatorWithCache.class);
        when(primitive.getPublicId(any())).thenThrow(new RuntimeException("not found"));

        ConceptChangeHistoryResponse response = service.getConceptChangeHistory("bad-id", mockCalc);

        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).contains("not found");
    }

    @Test
    void getConceptChangeHistory_noCalc_delegatesToTwoArgVersion() {
        try (MockedStatic<Calculators.View> calcMock = Mockito.mockStatic(Calculators.View.class)) {
            ViewCalculatorWithCache mockCalc = Mockito.mock(ViewCalculatorWithCache.class);
            calcMock.when(() -> Calculators.View.Default()).thenReturn(mockCalc);
            when(primitive.getPublicId(any())).thenThrow(new RuntimeException("not found"));

            ConceptChangeHistoryResponse response = service.getConceptChangeHistory("any-id");

            assertThat(response.success()).isFalse();
        }
    }

    // ── addDescendant ─────────────────────────────────────────────────────────

    @Test
    void addDescendant_getPublicIdThrows_returnsErrorResponse() {
        when(primitive.getPublicId(any())).thenThrow(new RuntimeException("parent not found"));

        DescendantOperationResponse response = service.addDescendant("bad-parent", "bad-child");

        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).contains("parent not found");
    }

    // ── createAndAddDescendant ────────────────────────────────────────────────

    @Test
    void createAndAddDescendant_getPublicIdThrows_returnsErrorResponse() {
        when(primitive.getPublicId(any())).thenThrow(new RuntimeException("parent not found"));

        DescendantOperationResponse response = service.createAndAddDescendant("bad-parent", "New Concept");

        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).contains("parent not found");
    }

    // ── removeDescendant ──────────────────────────────────────────────────────

    @Test
    void removeDescendant_getPublicIdThrows_returnsErrorResponse() {
        when(primitive.getPublicId(any())).thenThrow(new RuntimeException("concept not found"));

        DescendantOperationResponse response = service.removeDescendant("bad-parent", "bad-child");

        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).contains("concept not found");
    }

    // ── discardChanges ────────────────────────────────────────────────────────

    @Test
    void discardChanges_alwaysReturnsDiscardMessage() {
        String result = service.discardChanges();

        assertThat(result).containsIgnoringCase("discard");
    }

    // ── saveChanges ───────────────────────────────────────────────────────────

    @Test
    void saveChanges_succeeds_returnsSuccessMessage() {
        try (MockedStatic<PrimitiveData> staticPrimitiveData = Mockito.mockStatic(PrimitiveData.class)) {
            // save() is void — default mock does nothing (success)
            String result = service.saveChanges();
            assertThat(result).containsIgnoringCase("saved");
        }
    }

    @Test
    void saveChanges_primitiveDataSaveThrows_returnsFailureMessage() {
        try (MockedStatic<PrimitiveData> staticPrimitiveData = Mockito.mockStatic(PrimitiveData.class)) {
            staticPrimitiveData.when(PrimitiveData::save)
                    .thenThrow(new RuntimeException("data service not ready"));

            String result = service.saveChanges();

            assertThat(result).containsIgnoringCase("failed");
        }
    }

    // ── importChangeset ───────────────────────────────────────────────────────

    @Test
    void importChangeset_nonExistentFile_returnsErrorResponse() {
        dev.ikm.tinkar.service.dto.EntityCountSummaryResponse response =
                service.importChangeset(new java.io.File("/nonexistent/test.pb"), false);
        assertThat(response.success()).isFalse();
    }

    // ── runReasoner ───────────────────────────────────────────────────────────

    @Test
    void runReasoner_noServiceOrInitFails_returnsErrorResponse() {
        // In the test environment, either no ReasonerService SPI is found (empty list path)
        // or initialization fails — either way the method catches and returns an error.
        dev.ikm.tinkar.service.dto.ReasonerResultsResponse response = service.runReasoner();

        assertThat(response.success()).isFalse();
    }

    // ── publicIdToSearchResult (via EntityService mock) ───────────────────────

    @Test
    void getChildConcepts_singleResult_nidNotFound_returnsMinimalSuccessResult() {
        try (MockedStatic<Calculators.View> calcMock = Mockito.mockStatic(Calculators.View.class);
             MockedStatic<EntityService> entityMock = Mockito.mockStatic(EntityService.class)) {

            calcMock.when(() -> Calculators.View.Default())
                    .thenThrow(new IllegalStateException("db not ready"));

            EntityService mockEntityService = Mockito.mock(EntityService.class);
            entityMock.when(() -> EntityService.get()).thenReturn(mockEntityService);
            when(mockEntityService.nidForPublicId(any())).thenThrow(new RuntimeException("nid missing"));

            PublicId childId = publicId("cccccccc-0000-0000-0000-000000000001");
            when(primitive.getPublicId(any())).thenReturn(publicId("aaaaaaaa-0000-0000-0000-000000000001"));
            when(primitive.childrenOf(any())).thenReturn(List.of(childId));

            TinkarSearchQueryResponse response = service.getChildConcepts("parent-id");

            assertThat(response.getSuccess()).isTrue();
            assertThat(response.getTotalCount()).isEqualTo(1);
            assertThat(response.getResults(0).getDescriptions().getFullyQualifiedName()).isEmpty();
        }
    }

    @Test
    void getChildConcepts_singleResult_nidFound_emptyEntityGraph_returnsEmptyDescriptions() {
        try (MockedStatic<Calculators.View> calcMock = Mockito.mockStatic(Calculators.View.class);
             MockedStatic<EntityService> entityMock = Mockito.mockStatic(EntityService.class)) {

            calcMock.when(() -> Calculators.View.Default())
                    .thenThrow(new IllegalStateException("db not ready"));

            EntityService mockEntityService = Mockito.mock(EntityService.class);
            entityMock.when(() -> EntityService.get()).thenReturn(mockEntityService);
            when(mockEntityService.nidForPublicId(any())).thenReturn(123);
            when(mockEntityService.semanticNidsForComponent(anyInt())).thenReturn(new int[]{});
            when(mockEntityService.getEntityFast(anyInt())).thenReturn(null);

            PublicId childId = publicId("cccccccc-0000-0000-0000-000000000001");
            when(primitive.getPublicId(any())).thenReturn(publicId("aaaaaaaa-0000-0000-0000-000000000001"));
            when(primitive.childrenOf(any())).thenReturn(List.of(childId));

            TinkarSearchQueryResponse response = service.getChildConcepts("parent-id");

            assertThat(response.getSuccess()).isTrue();
            assertThat(response.getTotalCount()).isEqualTo(1);
            assertThat(response.getResults(0).getDescriptions().getFullyQualifiedName()).isEmpty();
            assertThat(response.getResults(0).getStamp().getTime()).isZero();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static PublicId publicId(String uuidStr) {
        UUID uuid = UUID.fromString(uuidStr);
        return new PublicId() {
            @Override
            public int uuidCount() { return 1; }

            @Override
            public void forEach(LongConsumer consumer) {
                consumer.accept(uuid.getMostSignificantBits());
                consumer.accept(uuid.getLeastSignificantBits());
            }

            @Override
            public ImmutableList<UUID> asUuidList() {
                return Lists.immutable.with(uuid);
            }
        };
    }
}
