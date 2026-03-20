package dev.ikm.tinkar.service.controller.admin;

import dev.ikm.tinkar.service.dto.EntityCountSummaryResponse;
import dev.ikm.tinkar.service.dto.ReasonerResultsResponse;
import dev.ikm.tinkar.service.proto.EntityCountSummaryProto;
import dev.ikm.tinkar.service.proto.ExportEntitiesRequest;
import dev.ikm.tinkar.service.proto.ExportEntitiesResponse;
import dev.ikm.tinkar.service.proto.IkeAdminGrpc;
import dev.ikm.tinkar.service.proto.ImportChangesetRequest;
import dev.ikm.tinkar.service.proto.ImportChangesetResponse;
import dev.ikm.tinkar.service.proto.ReasonerResultsProto;
import dev.ikm.tinkar.service.proto.RunReasonerRequest;
import dev.ikm.tinkar.service.proto.RunReasonerResponse;
import dev.ikm.tinkar.service.service.TinkarService;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.List;

/**
 * Tier 3: Admin / Data Management — gRPC controller.
 *
 * Operations for importing changesets, exporting entity data,
 * and running the reasoner classification pipeline.
 * Target audience: platform operators, DevOps, CI/CD pipelines.
 */
@GrpcService
@Slf4j
public class AdminGrpcController extends IkeAdminGrpc.IkeAdminImplBase {

    private final TinkarService tinkarService;

    public AdminGrpcController(TinkarService tinkarService) {
        this.tinkarService = tinkarService;
    }

    @Override
    public void importChangeset(ImportChangesetRequest request,
            StreamObserver<ImportChangesetResponse> responseObserver) {
        log.info("IkeAdmin importChangeset request ({} bytes, multiPass={})",
                request.getChangesetData().size(), request.getUseMultiPass());

        File tempFile = null;
        try {
            // Write uploaded bytes to temp file
            tempFile = Files.createTempFile("tinkar-import-", ".zip").toFile();
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                request.getChangesetData().writeTo(fos);
            }

            // Proto3 bools default to false; callers should set use_multi_pass=true explicitly
            EntityCountSummaryResponse result = tinkarService.importChangeset(tempFile, request.getUseMultiPass());

            ImportChangesetResponse.Builder builder = ImportChangesetResponse.newBuilder()
                    .setSuccess(result.success())
                    .setErrorMessage(result.errorMessage() != null ? result.errorMessage() : "");

            if (result.success()) {
                builder.setEntityCounts(EntityCountSummaryProto.newBuilder()
                        .setConceptsCount(result.conceptsCount())
                        .setSemanticsCount(result.semanticsCount())
                        .setPatternsCount(result.patternsCount())
                        .setStampsCount(result.stampsCount())
                        .setTotalCount(result.totalCount())
                        .build());
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Failed to process import request: {}", e.getMessage(), e);
            responseObserver.onNext(ImportChangesetResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                    .build());
            responseObserver.onCompleted();
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    @Override
    public void runReasoner(RunReasonerRequest request,
            StreamObserver<RunReasonerResponse> responseObserver) {
        log.info("IkeAdmin runReasoner request");

        ReasonerResultsResponse result = tinkarService.runReasoner();

        RunReasonerResponse.Builder builder = RunReasonerResponse.newBuilder()
                .setSuccess(result.success())
                .setErrorMessage(result.errorMessage() != null ? result.errorMessage() : "");

        if (result.success()) {
            builder.setResults(ReasonerResultsProto.newBuilder()
                    .setClassifiedConceptCount(result.classifiedConceptCount())
                    .setInferredChangesCount(result.inferredChangesCount())
                    .setNavigationChangesCount(result.navigationChangesCount())
                    .setEquivalentSetsCount(result.equivalentSetsCount())
                    .setCyclesCount(result.cyclesCount())
                    .setOrphansCount(result.orphansCount())
                    .build());
            builder.setDurationMs(result.durationMs());
        }

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }
}
