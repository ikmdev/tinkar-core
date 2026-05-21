package dev.ikm.tinkar.service.util;

import dev.ikm.tinkar.schema.StampVersion;
import dev.ikm.tinkar.service.dto.ConceptSearchResponse;
import dev.ikm.tinkar.service.dto.ConceptSearchResponse.GroupedSearchResult;
import dev.ikm.tinkar.service.dto.ConceptSearchResponse.MatchingSemantic;
import dev.ikm.tinkar.service.dto.ConceptSearchResponse.SemanticSearchResult;
import dev.ikm.tinkar.service.dto.SearchSortOption;
import dev.ikm.tinkar.service.dto.TinkarSearchQueryResponse;
import dev.ikm.tinkar.service.proto.TinkarConceptDescriptions;
import dev.ikm.tinkar.service.proto.TinkarConceptSearchWithSortResponse;
import dev.ikm.tinkar.service.proto.TinkarSearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProtoConversionUtilsTest {

    // ── toDto: proto → DTO ────────────────────────────────────────────────────

    @Test
    void toDto_mapsScalarFields() {
        var proto = dev.ikm.tinkar.service.proto.TinkarSearchQueryResponse.newBuilder()
                .setQuery("diabetes")
                .setTotalCount(3)
                .setSuccess(true)
                .setErrorMessage("")
                .setCreatedAt(12345L)
                .build();

        TinkarSearchQueryResponse dto = ProtoConversionUtils.toDto(proto);

        assertThat(dto.query()).isEqualTo("diabetes");
        assertThat(dto.totalCount()).isEqualTo(3);
        assertThat(dto.success()).isTrue();
        assertThat(dto.createdAt()).isEqualTo(12345L);
    }

    @Test
    void toDto_emptyErrorMessage_becomesNull() {
        var proto = dev.ikm.tinkar.service.proto.TinkarSearchQueryResponse.newBuilder()
                .setSuccess(true)
                .setErrorMessage("")
                .setCreatedAt(1L)
                .build();

        assertThat(ProtoConversionUtils.toDto(proto).errorMessage()).isNull();
    }

    @Test
    void toDto_nonEmptyErrorMessage_preserved() {
        var proto = dev.ikm.tinkar.service.proto.TinkarSearchQueryResponse.newBuilder()
                .setSuccess(false)
                .setErrorMessage("entity not found")
                .setCreatedAt(1L)
                .build();

        assertThat(ProtoConversionUtils.toDto(proto).errorMessage()).isEqualTo("entity not found");
    }

    @Test
    void toDto_createdAtZero_fallsBackToCurrentTime() {
        long before = System.currentTimeMillis();
        var proto = dev.ikm.tinkar.service.proto.TinkarSearchQueryResponse.newBuilder()
                .setCreatedAt(0L)
                .build();

        long actual = ProtoConversionUtils.toDto(proto).createdAt();
        long after = System.currentTimeMillis();

        assertThat(actual).isBetween(before, after);
    }

    @Test
    void toDto_noResults_returnsEmptyList() {
        var proto = dev.ikm.tinkar.service.proto.TinkarSearchQueryResponse.newBuilder()
                .setCreatedAt(1L)
                .build();

        assertThat(ProtoConversionUtils.toDto(proto).results()).isEmpty();
    }

    @Test
    void toDto_mapsResultDescriptionsAndPublicId() {
        String uuid = "aaaaaaaa-0000-0000-0000-000000000001";

        var result = TinkarSearchResult.newBuilder()
                .setPublicId(dev.ikm.tinkar.schema.PublicId.newBuilder().addUuids(uuid).build())
                .setDescriptions(TinkarConceptDescriptions.newBuilder()
                        .setFullyQualifiedName("Diabetes mellitus (disorder)")
                        .setRegularName("Diabetes")
                        .setDefinition("Metabolic disease")
                        .build())
                .setStamp(StampVersion.newBuilder().setTime(9999L).build())
                .build();

        var proto = dev.ikm.tinkar.service.proto.TinkarSearchQueryResponse.newBuilder()
                .setTotalCount(1)
                .addResults(result)
                .setSuccess(true)
                .setCreatedAt(1L)
                .build();

        TinkarSearchQueryResponse dto = ProtoConversionUtils.toDto(proto);

        assertThat(dto.results()).hasSize(1);
        var r = dto.results().get(0);
        assertThat(r.publicId()).containsExactly(uuid);
        assertThat(r.descriptions().fullyQualifiedName()).isEqualTo("Diabetes mellitus (disorder)");
        assertThat(r.descriptions().regularName()).isEqualTo("Diabetes");
        assertThat(r.descriptions().definition()).isEqualTo("Metabolic disease");
        assertThat(r.stamp().time()).isEqualTo(9999L);
    }

    @Test
    void toDto_mapsStampPublicIds() {
        String statusUuid = "aaaaaaaa-0000-0000-0000-000000000001";
        String authorUuid = "bbbbbbbb-0000-0000-0000-000000000002";
        String moduleUuid = "cccccccc-0000-0000-0000-000000000003";
        String pathUuid   = "dddddddd-0000-0000-0000-000000000004";

        var stamp = StampVersion.newBuilder()
                .setStatusPublicId(dev.ikm.tinkar.schema.PublicId.newBuilder().addUuids(statusUuid).build())
                .setAuthorPublicId(dev.ikm.tinkar.schema.PublicId.newBuilder().addUuids(authorUuid).build())
                .setModulePublicId(dev.ikm.tinkar.schema.PublicId.newBuilder().addUuids(moduleUuid).build())
                .setPathPublicId(dev.ikm.tinkar.schema.PublicId.newBuilder().addUuids(pathUuid).build())
                .setTime(42000L)
                .build();

        var result = TinkarSearchResult.newBuilder()
                .setPublicId(dev.ikm.tinkar.schema.PublicId.newBuilder().addUuids(statusUuid).build())
                .setDescriptions(TinkarConceptDescriptions.newBuilder().build())
                .setStamp(stamp)
                .build();

        var proto = dev.ikm.tinkar.service.proto.TinkarSearchQueryResponse.newBuilder()
                .addResults(result)
                .setCreatedAt(1L)
                .build();

        var s = ProtoConversionUtils.toDto(proto).results().get(0).stamp();
        assertThat(s.statusPublicId()).isEqualTo(statusUuid);
        assertThat(s.authorPublicId()).isEqualTo(authorUuid);
        assertThat(s.modulePublicId()).isEqualTo(moduleUuid);
        assertThat(s.pathPublicId()).isEqualTo(pathUuid);
        assertThat(s.time()).isEqualTo(42000L);
    }

    @Test
    void toDto_stampMissingPublicIds_returnsNull() {
        var result = TinkarSearchResult.newBuilder()
                .setPublicId(dev.ikm.tinkar.schema.PublicId.newBuilder().build())
                .setDescriptions(TinkarConceptDescriptions.newBuilder().build())
                .setStamp(StampVersion.newBuilder().build()) // no public IDs set
                .build();

        var proto = dev.ikm.tinkar.service.proto.TinkarSearchQueryResponse.newBuilder()
                .addResults(result)
                .setCreatedAt(1L)
                .build();

        var s = ProtoConversionUtils.toDto(proto).results().get(0).stamp();
        assertThat(s.statusPublicId()).isNull();
        assertThat(s.authorPublicId()).isNull();
        assertThat(s.modulePublicId()).isNull();
        assertThat(s.pathPublicId()).isNull();
    }

    // ── Sort option conversions ───────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(SearchSortOption.class)
    void sortOptionRoundtrip_dtoToProtoAndBack(SearchSortOption dto) {
        var proto = ProtoConversionUtils.toSortOptionProto(dto);
        assertThat(ProtoConversionUtils.toSortOptionDto(proto)).isEqualTo(dto);
    }

    @Test
    void toSortOptionDto_null_defaultsToTopComponent() {
        assertThat(ProtoConversionUtils.toSortOptionDto(null)).isEqualTo(SearchSortOption.TOP_COMPONENT);
    }

    @Test
    void toSortOptionProto_null_defaultsToTopComponent() {
        assertThat(ProtoConversionUtils.toSortOptionProto(null))
                .isEqualTo(dev.ikm.tinkar.service.proto.SearchSortOption.TOP_COMPONENT);
    }

    // ── toConceptSearchWithSortProto: DTO → proto ─────────────────────────────

    @Test
    void toConceptSearchWithSortProto_errorResponse_setsErrorFields() {
        ConceptSearchResponse dto = ConceptSearchResponse.error("diabetes", "service unavailable");

        TinkarConceptSearchWithSortResponse proto = ProtoConversionUtils.toConceptSearchWithSortProto(dto);

        assertThat(proto.getQuery()).isEqualTo("diabetes");
        assertThat(proto.getSuccess()).isFalse();
        assertThat(proto.getErrorMessage()).isEqualTo("service unavailable");
        assertThat(proto.getTotalCount()).isZero();
        assertThat(proto.getResultsList()).isEmpty();
        assertThat(proto.getGroupedResultsList()).isEmpty();
    }

    @Test
    void toConceptSearchWithSortProto_flatResults_mapsAllFields() {
        var semantic = new SemanticSearchResult(
                List.of("uuid-1", "uuid-2"),
                "Diabetes mellitus (disorder)",
                "Diabetes",
                "<b>Diabetes</b>",
                0.95f,
                true);

        ConceptSearchResponse dto = ConceptSearchResponse.successFlat(
                "diabetes", SearchSortOption.SEMANTIC, List.of(semantic));

        TinkarConceptSearchWithSortResponse proto = ProtoConversionUtils.toConceptSearchWithSortProto(dto);

        assertThat(proto.getSuccess()).isTrue();
        assertThat(proto.getTotalCount()).isEqualTo(1);
        assertThat(proto.getSortBy()).isEqualTo(dev.ikm.tinkar.service.proto.SearchSortOption.SEMANTIC);
        assertThat(proto.getResultsList()).hasSize(1);

        var r = proto.getResults(0);
        assertThat(r.getPublicIdList()).containsExactly("uuid-1", "uuid-2");
        assertThat(r.getFullyQualifiedName()).isEqualTo("Diabetes mellitus (disorder)");
        assertThat(r.getRegularName()).isEqualTo("Diabetes");
        assertThat(r.getHighlightedText()).isEqualTo("<b>Diabetes</b>");
        assertThat(r.getScore()).isEqualTo(0.95f);
        assertThat(r.getActive()).isTrue();
    }

    @Test
    void toConceptSearchWithSortProto_groupedResults_mapsGroupAndSemantics() {
        var matchingSemantic = new MatchingSemantic("<b>diab</b>", "diab", 0.8f, 2, 999);
        var group = new GroupedSearchResult(
                List.of("group-uuid"),
                "Diabetes mellitus (disorder)",
                true,
                0.9f,
                List.of(matchingSemantic),
                42);

        ConceptSearchResponse dto = ConceptSearchResponse.successGrouped(
                "diab", SearchSortOption.TOP_COMPONENT, List.of(group), 1L);

        TinkarConceptSearchWithSortResponse proto = ProtoConversionUtils.toConceptSearchWithSortProto(dto);

        assertThat(proto.getGroupedResultsList()).hasSize(1);
        var g = proto.getGroupedResults(0);
        assertThat(g.getPublicIdList()).containsExactly("group-uuid");
        assertThat(g.getFullyQualifiedName()).isEqualTo("Diabetes mellitus (disorder)");
        assertThat(g.getActive()).isTrue();
        assertThat(g.getTopScore()).isEqualTo(0.9f);
        assertThat(g.getConceptNid()).isEqualTo(42);

        assertThat(g.getMatchingSemanticsList()).hasSize(1);
        var m = g.getMatchingSemantics(0);
        assertThat(m.getHighlightedText()).isEqualTo("<b>diab</b>");
        assertThat(m.getPlainText()).isEqualTo("diab");
        assertThat(m.getScore()).isEqualTo(0.8f);
        assertThat(m.getFieldIndex()).isEqualTo(2);
        assertThat(m.getSemanticNid()).isEqualTo(999);
    }

    @Test
    void toConceptSearchWithSortProto_emptyResponse_setsCreatedAt() {
        ConceptSearchResponse dto = ConceptSearchResponse.empty("q");

        long before = System.currentTimeMillis();
        TinkarConceptSearchWithSortResponse proto = ProtoConversionUtils.toConceptSearchWithSortProto(dto);
        long after = System.currentTimeMillis();

        assertThat(proto.getCreatedAt()).isBetween(before, after);
    }

    @Test
    void toConceptSearchWithSortProto_nullResultsAndGroups_producesNoEntries() {
        // ConceptSearchResponse.empty() sets both results and groupedResults to null
        ConceptSearchResponse dto = ConceptSearchResponse.empty("nothing");

        TinkarConceptSearchWithSortResponse proto = ProtoConversionUtils.toConceptSearchWithSortProto(dto);

        assertThat(proto.getResultsList()).isEmpty();
        assertThat(proto.getGroupedResultsList()).isEmpty();
        assertThat(proto.getSuccess()).isTrue();
    }
}
