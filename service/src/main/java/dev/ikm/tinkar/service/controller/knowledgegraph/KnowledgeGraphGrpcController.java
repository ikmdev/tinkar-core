package dev.ikm.tinkar.service.controller.knowledgegraph;

import dev.ikm.tinkar.service.dto.*;
import dev.ikm.tinkar.service.dto.SavedLanguageCoordinateResponse;
import dev.ikm.tinkar.service.dto.SavedNavigationCoordinateResponse;
import dev.ikm.tinkar.service.dto.SavedStampCoordinateResponse;
import dev.ikm.tinkar.service.proto.*;
import dev.ikm.tinkar.service.proto.CoordinateOverride;
import dev.ikm.tinkar.service.proto.TinkarSearchQueryResponse;
import dev.ikm.tinkar.service.service.CoordinateFactory;
import dev.ikm.tinkar.service.service.CoordinateStoreService;
import dev.ikm.tinkar.service.service.TinkarService;
import dev.ikm.tinkar.coordinate.language.LanguageCoordinateRecord;
import dev.ikm.tinkar.coordinate.navigation.NavigationCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculatorWithCache;
import dev.ikm.tinkar.schema.PublicId;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;

/**
 * Tier 2: Concept-Aware (Knowledge Graph) — gRPC controller.
 *
 * Exposes the concept-oriented structure with semantic patterns, STAMP info,
 * and version history. Supports optional coordinate overrides for STAMP filtering
 * and navigation mode. Target audience: analytics engineers, knowledge graph practitioners.
 */
@GrpcService
@Slf4j
public class KnowledgeGraphGrpcController extends IkeKnowledgeGraphGrpc.IkeKnowledgeGraphImplBase {

    private final TinkarService tinkarService;
    private final CoordinateStoreService coordinateStoreService;

    public KnowledgeGraphGrpcController(TinkarService tinkarService, CoordinateStoreService coordinateStoreService) {
        this.tinkarService = tinkarService;
        this.coordinateStoreService = coordinateStoreService;
    }

    @Override
    public void inspectConcept(KnowledgeGraphConceptRequest request,
            StreamObserver<TinkarConceptSemanticsResponse> responseObserver) {
        String conceptId = extractConceptId(request.getPublicId());
        log.info("IkeKnowledgeGraph inspectConcept request for conceptId: {}", conceptId);
        ViewCalculatorWithCache calc = buildCalculator(request.getCoordinateOverride());
        responseObserver.onNext(tinkarService.inspectConceptProto(conceptId, calc));
        responseObserver.onCompleted();
    }

    @Override
    public void getChildConcepts(KnowledgeGraphConceptRequest request,
            StreamObserver<dev.ikm.tinkar.service.proto.TinkarSearchQueryResponse> responseObserver) {
        String conceptId = extractConceptId(request.getPublicId());
        log.info("IkeKnowledgeGraph getChildConcepts request for conceptId: {}", conceptId);
        ViewCalculatorWithCache calc = buildCalculator(request.getCoordinateOverride());
        responseObserver.onNext(tinkarService.getChildConcepts(conceptId, calc));
        responseObserver.onCompleted();
    }

    @Override
    public void getDescendantConcepts(KnowledgeGraphConceptRequest request,
            StreamObserver<TinkarSearchQueryResponse> responseObserver) {
        String conceptId = extractConceptId(request.getPublicId());
        log.info("IkeKnowledgeGraph getDescendantConcepts request for conceptId: {}", conceptId);
        ViewCalculatorWithCache calc = buildCalculator(request.getCoordinateOverride());
        responseObserver.onNext(tinkarService.getDescendantConcepts(conceptId, calc));
        responseObserver.onCompleted();
    }

    @Override
    public void saveStampCoordinate(SaveStampCoordinateRequest request,
            StreamObserver<dev.ikm.tinkar.service.proto.SavedStampCoordinateResponse> responseObserver) {
        log.info("IkeKnowledgeGraph saveStampCoordinate request");
        StampCoordinateDto dto = protoStampToDto(
                request.hasSettings() ? request.getSettings() : null);
        SavedStampCoordinateResponse saved = coordinateStoreService.saveStamp(dto);
        responseObserver.onNext(toProtoStampResponse(saved));
        responseObserver.onCompleted();
    }

    @Override
    public void listStampCoordinates(ListStampCoordinatesRequest request,
            StreamObserver<ListStampCoordinatesResponse> responseObserver) {
        log.info("IkeKnowledgeGraph listStampCoordinates request");
        List<SavedStampCoordinateResponse> list = coordinateStoreService.findAllStamp();
        ListStampCoordinatesResponse response = ListStampCoordinatesResponse.newBuilder()
                .addAllCoordinates(list.stream().map(this::toProtoStampResponse).toList())
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void saveNavigationCoordinate(SaveNavigationCoordinateRequest request,
            StreamObserver<dev.ikm.tinkar.service.proto.SavedNavigationCoordinateResponse> responseObserver) {
        log.info("IkeKnowledgeGraph saveNavigationCoordinate request");
        NavigationCoordinateDto dto = protoNavToDto(
                request.hasSettings() ? request.getSettings() : null);
        SavedNavigationCoordinateResponse saved = coordinateStoreService.saveNavigation(dto);
        responseObserver.onNext(toProtoNavResponse(saved));
        responseObserver.onCompleted();
    }

    @Override
    public void listNavigationCoordinates(ListNavigationCoordinatesRequest request,
            StreamObserver<ListNavigationCoordinatesResponse> responseObserver) {
        log.info("IkeKnowledgeGraph listNavigationCoordinates request");
        List<SavedNavigationCoordinateResponse> list = coordinateStoreService.findAllNavigation();
        ListNavigationCoordinatesResponse response = ListNavigationCoordinatesResponse.newBuilder()
                .addAllCoordinates(list.stream().map(this::toProtoNavResponse).toList())
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void saveLanguageCoordinate(SaveLanguageCoordinateRequest request,
            StreamObserver<dev.ikm.tinkar.service.proto.SavedLanguageCoordinateResponse> responseObserver) {
        log.info("IkeKnowledgeGraph saveLanguageCoordinate request");
        LanguageCoordinateDto dto = protoLangToDto(
                request.hasSettings() ? request.getSettings() : null);
        SavedLanguageCoordinateResponse saved = coordinateStoreService.saveLanguage(dto);
        responseObserver.onNext(toProtoLangResponse(saved));
        responseObserver.onCompleted();
    }

    @Override
    public void listLanguageCoordinates(ListLanguageCoordinatesRequest request,
            StreamObserver<ListLanguageCoordinatesResponse> responseObserver) {
        log.info("IkeKnowledgeGraph listLanguageCoordinates request");
        List<SavedLanguageCoordinateResponse> list = coordinateStoreService.findAllLanguage();
        ListLanguageCoordinatesResponse response = ListLanguageCoordinatesResponse.newBuilder()
                .addAllCoordinates(list.stream().map(this::toProtoLangResponse).toList())
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getSemanticsWithCoordinate(SemanticsWithCoordinateRequest request,
            StreamObserver<TinkarConceptSemanticsResponse> responseObserver) {
        String conceptId = extractConceptId(request.getConceptPublicId());
        log.info("IkeKnowledgeGraph getSemanticsWithCoordinate conceptId={}", conceptId);

        String stampId = request.getStampCoordinateId().isEmpty() ? null : request.getStampCoordinateId();
        String navId = request.getNavigationCoordinateId().isEmpty() ? null : request.getNavigationCoordinateId();
        String langId = request.getLanguageCoordinateId().isEmpty() ? null : request.getLanguageCoordinateId();

        StampCoordinateRecord stampCoord = resolveStampCoordinate(stampId);
        LanguageCoordinateRecord langCoord = resolveLanguageCoordinate(langId);
        NavigationCoordinateRecord navCoord = resolveNavigationCoordinate(navId);
        ViewCalculatorWithCache calc = CoordinateFactory.buildCalculator(stampCoord, langCoord, navCoord);
        responseObserver.onNext(tinkarService.inspectConceptProto(conceptId, calc));
        responseObserver.onCompleted();
    }

    // ── Conversion helpers ────────────────────────────────────────────────────

    private StampCoordinateDto protoStampToDto(StampCoordinateSettings proto) {
        if (proto == null) return null;
        String allowedStates = proto.getAllowedStates() == AllowedStates.ACTIVE_AND_INACTIVE
                ? null : proto.getAllowedStates().name();
        Long positionTime = proto.getPositionTime() != 0 ? proto.getPositionTime() : null;
        String positionPathId = proto.getPositionPathId().isEmpty() ? null : proto.getPositionPathId();
        List<String> moduleIds = proto.getModuleIdsList().isEmpty() ? null : proto.getModuleIdsList();
        List<String> excludedModuleIds = proto.getExcludedModuleIdsList().isEmpty() ? null : proto.getExcludedModuleIdsList();
        List<String> modulePriorityIds = proto.getModulePriorityIdsList().isEmpty() ? null : proto.getModulePriorityIdsList();
        return new StampCoordinateDto(allowedStates, positionTime, positionPathId, moduleIds, excludedModuleIds, modulePriorityIds);
    }

    private NavigationCoordinateDto protoNavToDto(NavigationCoordinateSettings proto) {
        if (proto == null) return null;
        PremiseType premiseType = proto.getPremiseType() == ProtoPremiseType.STATED ? PremiseType.STATED : null;
        return new NavigationCoordinateDto(premiseType);
    }

    private StampCoordinateSettings dtoStampToProto(StampCoordinateDto dto) {
        if (dto == null) return StampCoordinateSettings.getDefaultInstance();
        var builder = StampCoordinateSettings.newBuilder();
        if (dto.allowedStates() != null) {
            builder.setAllowedStates(switch (dto.allowedStates().toUpperCase()) {
                case "ACTIVE" -> AllowedStates.ACTIVE;
                case "INACTIVE" -> AllowedStates.INACTIVE;
                default -> AllowedStates.ACTIVE_AND_INACTIVE;
            });
        }
        if (dto.positionTime() != null) builder.setPositionTime(dto.positionTime());
        if (dto.positionPathId() != null) builder.setPositionPathId(dto.positionPathId());
        if (dto.moduleIds() != null) builder.addAllModuleIds(dto.moduleIds());
        if (dto.excludedModuleIds() != null) builder.addAllExcludedModuleIds(dto.excludedModuleIds());
        if (dto.modulePriorityIds() != null) builder.addAllModulePriorityIds(dto.modulePriorityIds());
        return builder.build();
    }

    private dev.ikm.tinkar.service.proto.SavedStampCoordinateResponse toProtoStampResponse(SavedStampCoordinateResponse dto) {
        return dev.ikm.tinkar.service.proto.SavedStampCoordinateResponse.newBuilder()
                .setId(dto.id() != null ? dto.id() : "")
                .setSettings(dtoStampToProto(dto.settings()))
                .setCreatedAt(dto.createdAt() != null ? dto.createdAt() : "")
                .build();
    }

    private NavigationCoordinateSettings dtoNavToProto(NavigationCoordinateDto dto) {
        if (dto == null) return NavigationCoordinateSettings.getDefaultInstance();
        var builder = NavigationCoordinateSettings.newBuilder();
        if (dto.premiseType() != null) {
            builder.setPremiseType(dto.premiseType() == PremiseType.STATED ? ProtoPremiseType.STATED : ProtoPremiseType.INFERRED);
        }
        return builder.build();
    }

    private dev.ikm.tinkar.service.proto.SavedNavigationCoordinateResponse toProtoNavResponse(SavedNavigationCoordinateResponse dto) {
        return dev.ikm.tinkar.service.proto.SavedNavigationCoordinateResponse.newBuilder()
                .setId(dto.id() != null ? dto.id() : "")
                .setSettings(dtoNavToProto(dto.settings()))
                .setCreatedAt(dto.createdAt() != null ? dto.createdAt() : "")
                .build();
    }

    private LanguageCoordinateDto protoLangToDto(LanguageCoordinateSettings proto) {
        if (proto == null) return null;
        LanguagePreset preset = switch (proto.getLanguagePreset()) {
            case US_ENGLISH_FULLY_QUALIFIED_NAME -> LanguagePreset.US_ENGLISH_FULLY_QUALIFIED_NAME;
            case GB_ENGLISH_PREFERRED_NAME -> LanguagePreset.GB_ENGLISH_PREFERRED_NAME;
            case GB_ENGLISH_FULLY_QUALIFIED_NAME -> LanguagePreset.GB_ENGLISH_FULLY_QUALIFIED_NAME;
            case ANY_LANGUAGE_REGULAR_NAME -> LanguagePreset.ANY_LANGUAGE_REGULAR_NAME;
            case ANY_LANGUAGE_FULLY_QUALIFIED_NAME -> LanguagePreset.ANY_LANGUAGE_FULLY_QUALIFIED_NAME;
            case ANY_LANGUAGE_DEFINITION -> LanguagePreset.ANY_LANGUAGE_DEFINITION;
            case SPANISH_PREFERRED_NAME -> LanguagePreset.SPANISH_PREFERRED_NAME;
            case SPANISH_FULLY_QUALIFIED_NAME -> LanguagePreset.SPANISH_FULLY_QUALIFIED_NAME;
            default -> LanguagePreset.US_ENGLISH_REGULAR_NAME;
        };
        return new LanguageCoordinateDto(preset);
    }

    private LanguageCoordinateSettings dtoLangToProto(LanguageCoordinateDto dto) {
        if (dto == null) return LanguageCoordinateSettings.getDefaultInstance();
        var builder = LanguageCoordinateSettings.newBuilder();
        if (dto.languagePreset() != null) {
            builder.setLanguagePreset(switch (dto.languagePreset()) {
                case US_ENGLISH_REGULAR_NAME -> ProtoLanguagePreset.US_ENGLISH_REGULAR_NAME;
                case US_ENGLISH_FULLY_QUALIFIED_NAME -> ProtoLanguagePreset.US_ENGLISH_FULLY_QUALIFIED_NAME;
                case GB_ENGLISH_PREFERRED_NAME -> ProtoLanguagePreset.GB_ENGLISH_PREFERRED_NAME;
                case GB_ENGLISH_FULLY_QUALIFIED_NAME -> ProtoLanguagePreset.GB_ENGLISH_FULLY_QUALIFIED_NAME;
                case ANY_LANGUAGE_REGULAR_NAME -> ProtoLanguagePreset.ANY_LANGUAGE_REGULAR_NAME;
                case ANY_LANGUAGE_FULLY_QUALIFIED_NAME -> ProtoLanguagePreset.ANY_LANGUAGE_FULLY_QUALIFIED_NAME;
                case ANY_LANGUAGE_DEFINITION -> ProtoLanguagePreset.ANY_LANGUAGE_DEFINITION;
                case SPANISH_PREFERRED_NAME -> ProtoLanguagePreset.SPANISH_PREFERRED_NAME;
                case SPANISH_FULLY_QUALIFIED_NAME -> ProtoLanguagePreset.SPANISH_FULLY_QUALIFIED_NAME;
            });
        }
        return builder.build();
    }

    private dev.ikm.tinkar.service.proto.SavedLanguageCoordinateResponse toProtoLangResponse(SavedLanguageCoordinateResponse dto) {
        return dev.ikm.tinkar.service.proto.SavedLanguageCoordinateResponse.newBuilder()
                .setId(dto.id() != null ? dto.id() : "")
                .setSettings(dtoLangToProto(dto.settings()))
                .setCreatedAt(dto.createdAt() != null ? dto.createdAt() : "")
                .build();
    }

    private StampCoordinateRecord resolveStampCoordinate(String stampCoordinateId) {
        if (stampCoordinateId == null) {
            return CoordinateFactory.buildStampCoordinate(null);
        }
        SavedStampCoordinateResponse saved = coordinateStoreService.findStampById(stampCoordinateId)
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("No stamp coordinate found with id: " + stampCoordinateId)
                        .asRuntimeException());
        return CoordinateFactory.buildStampCoordinate(saved.settings());
    }

    private NavigationCoordinateRecord resolveNavigationCoordinate(String navigationCoordinateId) {
        if (navigationCoordinateId == null) {
            return CoordinateFactory.buildNavigationCoordinate(null);
        }
        SavedNavigationCoordinateResponse saved = coordinateStoreService.findNavigationById(navigationCoordinateId)
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("No navigation coordinate found with id: " + navigationCoordinateId)
                        .asRuntimeException());
        return CoordinateFactory.buildNavigationCoordinate(saved.settings());
    }

    private LanguageCoordinateRecord resolveLanguageCoordinate(String languageCoordinateId) {
        if (languageCoordinateId == null) {
            return CoordinateFactory.buildLanguageCoordinate(null);
        }
        SavedLanguageCoordinateResponse saved = coordinateStoreService.findLanguageById(languageCoordinateId)
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("No language coordinate found with id: " + languageCoordinateId)
                        .asRuntimeException());
        return CoordinateFactory.buildLanguageCoordinate(saved.settings());
    }

    private ViewCalculatorWithCache buildCalculator(CoordinateOverride protoOverride) {
        if (protoOverride == null || protoOverride.equals(CoordinateOverride.getDefaultInstance())) {
            return CoordinateFactory.defaultCalculator();
        }
        String allowedStates = protoOverride.getAllowedStates() == AllowedStates.ACTIVE_AND_INACTIVE
                ? null : protoOverride.getAllowedStates().name();
        Long positionTime = protoOverride.getPositionTime() != 0 ? protoOverride.getPositionTime() : null;
        String positionPathId = protoOverride.getPositionPathId().isEmpty() ? null : protoOverride.getPositionPathId();
        List<String> moduleIds = protoOverride.getModuleIdsList().isEmpty() ? null : protoOverride.getModuleIdsList();
        List<String> excludedModuleIds = protoOverride.getExcludedModuleIdsList().isEmpty() ? null : protoOverride.getExcludedModuleIdsList();
        List<String> modulePriorityIds = protoOverride.getModulePriorityIdsList().isEmpty() ? null : protoOverride.getModulePriorityIdsList();
        PremiseType premiseType = protoOverride.getPremiseType() == ProtoPremiseType.STATED ? PremiseType.STATED : null;
        // US_ENGLISH_REGULAR_NAME is proto3 default (0) — treat as "use server default" (null)
        LanguagePreset languagePreset = protoOverride.getLanguagePreset() == ProtoLanguagePreset.US_ENGLISH_REGULAR_NAME
                ? null : protoLangToDto(LanguageCoordinateSettings.newBuilder()
                        .setLanguagePreset(protoOverride.getLanguagePreset()).build()).languagePreset();
        return CoordinateFactory.buildCalculator(new dev.ikm.tinkar.service.dto.CoordinateOverride(
                allowedStates, positionTime, positionPathId, moduleIds, excludedModuleIds, modulePriorityIds, premiseType, languagePreset));
    }

    private String extractConceptId(PublicId publicId) {
        if (publicId == null || publicId.getUuidsList().isEmpty()) {
            return "";
        }
        return publicId.getUuids(0);
    }
}
