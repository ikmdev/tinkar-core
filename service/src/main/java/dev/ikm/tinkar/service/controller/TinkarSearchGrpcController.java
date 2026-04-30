package dev.ikm.tinkar.service.controller;

import dev.ikm.tinkar.service.dto.SearchSortOption;
import dev.ikm.tinkar.service.proto.*;
import dev.ikm.tinkar.service.service.TinkarService;
import dev.ikm.tinkar.service.util.ProtoConversionUtils;
import dev.ikm.tinkar.schema.PublicId;
import dev.ikm.tinkar.service.controller.graphrag.GraphRAGGrpcController;
import dev.ikm.tinkar.service.controller.knowledgegraph.KnowledgeGraphGrpcController;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * @deprecated Use {@link GraphRAGGrpcController} (Tier 1)
 *             and {@link KnowledgeGraphGrpcController} (Tier 2) instead.
 */
@Deprecated
@GrpcService
@Slf4j
public class TinkarSearchGrpcController extends TinkarSearchServiceGrpc.TinkarSearchServiceImplBase {

    private final TinkarService tinkarService;

    public TinkarSearchGrpcController(TinkarService tinkarService) {
        this.tinkarService = tinkarService;
    }

    @Override
    public void search(TinkarSearchQueryRequest request,
            StreamObserver<TinkarSearchQueryResponse> responseObserver) {
        log.info("gRPC search request for query: {}", request.getQuery());
        responseObserver.onNext(tinkarService.search(request.getQuery()));
        responseObserver.onCompleted();
    }

    @Override
    public void conceptSearch(TinkarConceptSearchRequest request,
            StreamObserver<TinkarSearchQueryResponse> responseObserver) {
        log.info("gRPC conceptSearch request for query: {} with maxResults: {}",
                request.getQuery(), request.getMaxResults());
        Integer maxResults = request.getMaxResults() > 0 ? request.getMaxResults() : null;
        responseObserver.onNext(tinkarService.conceptSearch(request.getQuery(), maxResults));
        responseObserver.onCompleted();
    }

    @Override
    public void conceptSearchWithSort(TinkarConceptSearchWithSortRequest request,
                                      StreamObserver<TinkarConceptSearchWithSortResponse> responseObserver) {
        log.info("gRPC conceptSearchWithSort request for query: {} with maxResults: {} and sortBy: {}",
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
        log.info("gRPC getEntity request for conceptId: {}", conceptId);
        responseObserver.onNext(tinkarService.getEntity(conceptId));
        responseObserver.onCompleted();
    }

    @Override
    public void getChildConcepts(TinkarConceptIdRequest request,
            StreamObserver<TinkarSearchQueryResponse> responseObserver) {
        String conceptId = extractConceptId(request.getPublicId());
        log.info("gRPC getChildConcepts request for conceptId: {}", conceptId);
        responseObserver.onNext(tinkarService.getChildConcepts(conceptId));
        responseObserver.onCompleted();
    }

    @Override
    public void getDescendantConcepts(TinkarConceptIdRequest request,
            StreamObserver<TinkarSearchQueryResponse> responseObserver) {
        String conceptId = extractConceptId(request.getPublicId());
        log.info("gRPC getDescendantConcepts request for conceptId: {}", conceptId);
        responseObserver.onNext(tinkarService.getDescendantConcepts(conceptId));
        responseObserver.onCompleted();
    }

    @Override
    public void getLIDRRecordConceptsFromTestKit(TinkarConceptIdRequest request,
            StreamObserver<TinkarSearchQueryResponse> responseObserver) {
        String conceptId = extractConceptId(request.getPublicId());
        log.info("gRPC getLIDRRecordConceptsFromTestKit request for conceptId: {}", conceptId);
        responseObserver.onNext(tinkarService.getLIDRRecordConceptsFromTestKit(conceptId));
        responseObserver.onCompleted();
    }

    @Override
    public void getResultConformanceConceptsFromLIDRRecord(TinkarConceptIdRequest request,
            StreamObserver<TinkarSearchQueryResponse> responseObserver) {
        String conceptId = extractConceptId(request.getPublicId());
        log.info("gRPC getResultConformanceConceptsFromLIDRRecord request for conceptId: {}", conceptId);
        responseObserver.onNext(tinkarService.getResultConformanceConceptsFromLIDRRecord(conceptId));
        responseObserver.onCompleted();
    }

    @Override
    public void getAllowedResultConceptsFromResultConformance(TinkarConceptIdRequest request,
            StreamObserver<TinkarSearchQueryResponse> responseObserver) {
        String conceptId = extractConceptId(request.getPublicId());
        log.info("gRPC getAllowedResultConceptsFromResultConformance request for conceptId: {}", conceptId);
        responseObserver.onNext(tinkarService.getAllowedResultConceptsFromResultConformance(conceptId));
        responseObserver.onCompleted();
    }

    @Override
    public void rebuildSearchIndex(TinkarRebuildIndexRequest request,
            StreamObserver<TinkarRebuildIndexResponse> responseObserver) {
        log.info("gRPC rebuildSearchIndex request");
        String message = tinkarService.rebuildSearchIndex();
        boolean success = !message.startsWith("Failed");
        TinkarRebuildIndexResponse response = TinkarRebuildIndexResponse.newBuilder()
                .setMessage(message)
                .setSuccess(success)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void inspectConcept(TinkarConceptIdRequest request,
            StreamObserver<TinkarConceptSemanticsResponse> responseObserver) {
        String conceptId = extractConceptId(request.getPublicId());
        log.info("gRPC inspectConcept request for conceptId: {}", conceptId);
        responseObserver.onNext(tinkarService.inspectConceptProto(conceptId));
        responseObserver.onCompleted();
    }

    @Override
    public void loadConceptEntityGraph(TinkarConceptIdRequest request,
            StreamObserver<TinkarConceptEntityResponse> responseObserver) {
        String conceptId = extractConceptId(request.getPublicId());
        log.info("gRPC loadConceptEntityGraph request for conceptId: {}", conceptId);
        responseObserver.onNext(tinkarService.loadConceptEntityGraph(conceptId));
        responseObserver.onCompleted();
    }

    @Override
    public void getEntityByPublicId(TinkarConceptIdRequest request,
            StreamObserver<TinkarConceptEntityResponse> responseObserver) {
        String entityId = extractConceptId(request.getPublicId());
        log.debug("gRPC getEntityByPublicId request for entityId: {}", entityId);
        responseObserver.onNext(tinkarService.getEntityByPublicId(entityId));
        responseObserver.onCompleted();
    }

    private String extractConceptId(PublicId publicId) {
        if (publicId == null || publicId.getUuidsList().isEmpty()) {
            return "";
        }
        return publicId.getUuids(0);
    }
}
