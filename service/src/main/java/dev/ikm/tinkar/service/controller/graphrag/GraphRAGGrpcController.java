package dev.ikm.tinkar.service.controller.graphrag;

import dev.ikm.tinkar.service.dto.SearchSortOption;
import dev.ikm.tinkar.service.proto.*;
import dev.ikm.tinkar.service.service.TinkarService;
import dev.ikm.tinkar.service.util.ProtoConversionUtils;
import dev.ikm.tinkar.schema.PublicId;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * Tier 1: Graph RAG Ready — gRPC controller.
 *
 * Simple, opinionated API for ML/RAG engineers and data scientists.
 * All responses are pre-resolved to human-readable form using server-side defaults.
 */
@GrpcService
@Slf4j
public class GraphRAGGrpcController extends IkeGraphRAGGrpc.IkeGraphRAGImplBase {

    private final TinkarService tinkarService;

    public GraphRAGGrpcController(TinkarService tinkarService) {
        this.tinkarService = tinkarService;
    }

    @Override
    public void search(TinkarSearchQueryRequest request,
                       StreamObserver<TinkarSearchQueryResponse> responseObserver) {
        log.info("IkeGraphRAG search request for query: {}", request.getQuery());
        responseObserver.onNext(tinkarService.search(request.getQuery()));
        responseObserver.onCompleted();
    }

    @Override
    public void conceptSearch(TinkarConceptSearchRequest request,
            StreamObserver<TinkarSearchQueryResponse> responseObserver) {
        log.info("IkeGraphRAG conceptSearch request for query: {} with maxResults: {}",
                request.getQuery(), request.getMaxResults());
        Integer maxResults = request.getMaxResults() > 0 ? request.getMaxResults() : null;
        responseObserver.onNext(tinkarService.conceptSearch(request.getQuery(), maxResults));
        responseObserver.onCompleted();
    }

    @Override
    public void conceptSearchWithSort(TinkarConceptSearchWithSortRequest request,
                                      StreamObserver<TinkarConceptSearchWithSortResponse> responseObserver) {
        log.info("IkeGraphRAG conceptSearchWithSort request for query: {} with maxResults: {} and sortBy: {}",
                request.getQuery(), request.getMaxResults(), request.getSortBy());
        Integer maxResults = request.getMaxResults() > 0 ? request.getMaxResults() : null;
        SearchSortOption sortOption = ProtoConversionUtils.toSortOptionDto(request.getSortBy());
        responseObserver.onNext(ProtoConversionUtils.toConceptSearchWithSortProto(
                tinkarService.conceptSearchWithSort(request.getQuery(), maxResults, sortOption)));
        responseObserver.onCompleted();
    }

    @Override
    public void getEntity(TinkarConceptIdRequest request,
            StreamObserver<TinkarSearchQueryResponse> responseObserver) {
        String conceptId = extractConceptId(request.getPublicId());
        log.info("IkeGraphRAG getEntity request for conceptId: {}", conceptId);
        responseObserver.onNext(tinkarService.getEntity(conceptId));
        responseObserver.onCompleted();
    }

    @Override
    public void getChildConcepts(TinkarConceptIdRequest request,
            StreamObserver<TinkarSearchQueryResponse> responseObserver) {
        String conceptId = extractConceptId(request.getPublicId());
        log.info("IkeGraphRAG getChildConcepts request for conceptId: {}", conceptId);
        responseObserver.onNext(tinkarService.getChildConcepts(conceptId));
        responseObserver.onCompleted();
    }

    @Override
    public void getDescendantConcepts(TinkarConceptIdRequest request,
            StreamObserver<TinkarSearchQueryResponse> responseObserver) {
        String conceptId = extractConceptId(request.getPublicId());
        log.info("IkeGraphRAG getDescendantConcepts request for conceptId: {}", conceptId);
        responseObserver.onNext(tinkarService.getDescendantConcepts(conceptId));
        responseObserver.onCompleted();
    }

    @Override
    public void getLIDRRecordConceptsFromTestKit(TinkarConceptIdRequest request,
            StreamObserver<TinkarSearchQueryResponse> responseObserver) {
        String conceptId = extractConceptId(request.getPublicId());
        log.info("IkeGraphRAG getLIDRRecordConceptsFromTestKit request for conceptId: {}", conceptId);
        responseObserver.onNext(tinkarService.getLIDRRecordConceptsFromTestKit(conceptId));
        responseObserver.onCompleted();
    }

    @Override
    public void getResultConformanceConceptsFromLIDRRecord(TinkarConceptIdRequest request,
            StreamObserver<TinkarSearchQueryResponse> responseObserver) {
        String conceptId = extractConceptId(request.getPublicId());
        log.info("IkeGraphRAG getResultConformanceConceptsFromLIDRRecord request for conceptId: {}", conceptId);
        responseObserver.onNext(tinkarService.getResultConformanceConceptsFromLIDRRecord(conceptId));
        responseObserver.onCompleted();
    }

    @Override
    public void getAllowedResultConceptsFromResultConformance(TinkarConceptIdRequest request,
            StreamObserver<TinkarSearchQueryResponse> responseObserver) {
        String conceptId = extractConceptId(request.getPublicId());
        log.info("IkeGraphRAG getAllowedResultConceptsFromResultConformance request for conceptId: {}", conceptId);
        responseObserver.onNext(tinkarService.getAllowedResultConceptsFromResultConformance(conceptId));
        responseObserver.onCompleted();
    }

    @Override
    public void rebuildSearchIndex(TinkarRebuildIndexRequest request,
            StreamObserver<TinkarRebuildIndexResponse> responseObserver) {
        log.info("IkeGraphRAG rebuildSearchIndex request");
        String message = tinkarService.rebuildSearchIndex();
        boolean success = !message.startsWith("Failed");
        TinkarRebuildIndexResponse response = TinkarRebuildIndexResponse.newBuilder()
                .setMessage(message)
                .setSuccess(success)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private String extractConceptId(PublicId publicId) {
        if (publicId == null || publicId.getUuidsList().isEmpty()) {
            return "";
        }
        return publicId.getUuids(0);
    }
}
