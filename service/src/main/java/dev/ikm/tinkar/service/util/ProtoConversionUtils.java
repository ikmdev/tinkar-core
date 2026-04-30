package dev.ikm.tinkar.service.util;

import dev.ikm.tinkar.schema.StampVersion;
import dev.ikm.tinkar.service.dto.ConceptSearchResponse;
import dev.ikm.tinkar.service.dto.SearchSortOption;
import dev.ikm.tinkar.service.dto.TinkarSearchQueryResponse;
import dev.ikm.tinkar.service.dto.TinkarSearchQueryResponse.Descriptions;
import dev.ikm.tinkar.service.dto.TinkarSearchQueryResponse.SearchResult;
import dev.ikm.tinkar.service.dto.TinkarSearchQueryResponse.Stamp;
import dev.ikm.tinkar.service.proto.TinkarConceptSearchWithSortResponse;
import dev.ikm.tinkar.service.proto.TinkarGroupedSearchResult;
import dev.ikm.tinkar.service.proto.TinkarMatchingSemantic;
import dev.ikm.tinkar.service.proto.TinkarSearchResult;
import dev.ikm.tinkar.service.proto.TinkarSemanticSearchResult;

import java.util.List;

public final class ProtoConversionUtils {

    private ProtoConversionUtils() {}

    // ── Proto → DTO (used by REST controllers) ──────────────────────────────

    public static TinkarSearchQueryResponse toDto(dev.ikm.tinkar.service.proto.TinkarSearchQueryResponse proto) {
        List<SearchResult> results = proto.getResultsList().stream()
                .map(ProtoConversionUtils::toSearchResultDto)
                .toList();
        return new TinkarSearchQueryResponse(
                proto.getQuery(),
                proto.getTotalCount(),
                results,
                proto.getSuccess(),
                proto.getErrorMessage().isEmpty() ? null : proto.getErrorMessage());
    }

    private static SearchResult toSearchResultDto(TinkarSearchResult proto) {
        List<String> publicIds = proto.getPublicId().getUuidsList();
        Descriptions descriptions = new Descriptions(
                proto.getDescriptions().getFullyQualifiedName(),
                proto.getDescriptions().getRegularName(),
                proto.getDescriptions().getDefinition());
        Stamp stamp = toStampDto(proto.getStamp());
        return new SearchResult(publicIds, descriptions, stamp);
    }

    private static Stamp toStampDto(StampVersion proto) {
        String statusPublicId = proto.hasStatusPublicId() && !proto.getStatusPublicId().getUuidsList().isEmpty()
                ? proto.getStatusPublicId().getUuids(0) : null;
        String authorPublicId = proto.hasAuthorPublicId() && !proto.getAuthorPublicId().getUuidsList().isEmpty()
                ? proto.getAuthorPublicId().getUuids(0) : null;
        String modulePublicId = proto.hasModulePublicId() && !proto.getModulePublicId().getUuidsList().isEmpty()
                ? proto.getModulePublicId().getUuids(0) : null;
        String pathPublicId = proto.hasPathPublicId() && !proto.getPathPublicId().getUuidsList().isEmpty()
                ? proto.getPathPublicId().getUuids(0) : null;
        return new Stamp(statusPublicId, authorPublicId, modulePublicId, pathPublicId, proto.getTime());
    }

    // ── Sort option conversion ────────────────────────────────────────────────

    public static SearchSortOption toSortOptionDto(dev.ikm.tinkar.service.proto.SearchSortOption grpcSortOption) {
        if (grpcSortOption == null) {
            return SearchSortOption.TOP_COMPONENT;
        }
        return switch (grpcSortOption) {
            case TOP_COMPONENT -> SearchSortOption.TOP_COMPONENT;
            case TOP_COMPONENT_ALPHA -> SearchSortOption.TOP_COMPONENT_ALPHA;
            case SEMANTIC -> SearchSortOption.SEMANTIC;
            case SEMANTIC_ALPHA -> SearchSortOption.SEMANTIC_ALPHA;
            default -> SearchSortOption.TOP_COMPONENT;
        };
    }

    public static dev.ikm.tinkar.service.proto.SearchSortOption toSortOptionProto(SearchSortOption dtoSortOption) {
        if (dtoSortOption == null) {
            return dev.ikm.tinkar.service.proto.SearchSortOption.TOP_COMPONENT;
        }
        return switch (dtoSortOption) {
            case TOP_COMPONENT -> dev.ikm.tinkar.service.proto.SearchSortOption.TOP_COMPONENT;
            case TOP_COMPONENT_ALPHA -> dev.ikm.tinkar.service.proto.SearchSortOption.TOP_COMPONENT_ALPHA;
            case SEMANTIC -> dev.ikm.tinkar.service.proto.SearchSortOption.SEMANTIC;
            case SEMANTIC_ALPHA -> dev.ikm.tinkar.service.proto.SearchSortOption.SEMANTIC_ALPHA;
        };
    }

    // ── DTO → Proto (used by gRPC controllers for conceptSearchWithSort) ────

    public static TinkarConceptSearchWithSortResponse toConceptSearchWithSortProto(ConceptSearchResponse dtoResponse) {
        TinkarConceptSearchWithSortResponse.Builder builder = TinkarConceptSearchWithSortResponse.newBuilder()
                .setQuery(dtoResponse.query() != null ? dtoResponse.query() : "")
                .setTotalCount(dtoResponse.totalCount() != null ? dtoResponse.totalCount() : 0L)
                .setSortBy(toSortOptionProto(dtoResponse.sortBy()))
                .setSuccess(dtoResponse.success() != null && dtoResponse.success());

        if (dtoResponse.errorMessage() != null) {
            builder.setErrorMessage(dtoResponse.errorMessage());
        }

        if (dtoResponse.results() != null) {
            for (ConceptSearchResponse.SemanticSearchResult result : dtoResponse.results()) {
                TinkarSemanticSearchResult.Builder resultBuilder = TinkarSemanticSearchResult.newBuilder()
                        .setFullyQualifiedName(result.fullyQualifiedName() != null ? result.fullyQualifiedName() : "")
                        .setScore(result.score() != null ? result.score() : 0f)
                        .setActive(result.active() != null && result.active());
                if (result.publicId() != null) resultBuilder.addAllPublicId(result.publicId());
                if (result.regularName() != null) resultBuilder.setRegularName(result.regularName());
                if (result.highlightedText() != null) resultBuilder.setHighlightedText(result.highlightedText());
                builder.addResults(resultBuilder.build());
            }
        }

        if (dtoResponse.groupedResults() != null) {
            for (ConceptSearchResponse.GroupedSearchResult group : dtoResponse.groupedResults()) {
                TinkarGroupedSearchResult.Builder groupBuilder = TinkarGroupedSearchResult.newBuilder()
                        .setFullyQualifiedName(group.fullyQualifiedName() != null ? group.fullyQualifiedName() : "")
                        .setTopScore(group.topScore() != null ? group.topScore() : 0f)
                        .setActive(group.active() != null && group.active());
                if (group.publicId() != null) groupBuilder.addAllPublicId(group.publicId());
                if (group.conceptNid() != null) groupBuilder.setConceptNid(group.conceptNid());

                if (group.matchingSemantics() != null) {
                    for (ConceptSearchResponse.MatchingSemantic semantic : group.matchingSemantics()) {
                        TinkarMatchingSemantic.Builder semanticBuilder = TinkarMatchingSemantic.newBuilder()
                                .setScore(semantic.score() != null ? semantic.score() : 0f);
                        if (semantic.highlightedText() != null) semanticBuilder.setHighlightedText(semantic.highlightedText());
                        if (semantic.plainText() != null) semanticBuilder.setPlainText(semantic.plainText());
                        if (semantic.fieldIndex() != null) semanticBuilder.setFieldIndex(semantic.fieldIndex());
                        if (semantic.semanticNid() != null) semanticBuilder.setSemanticNid(semantic.semanticNid());
                        groupBuilder.addMatchingSemantics(semanticBuilder.build());
                    }
                }

                builder.addGroupedResults(groupBuilder.build());
            }
        }

        return builder.build();
    }
}
