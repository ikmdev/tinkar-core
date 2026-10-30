/*
 * Copyright © 2026 IKE Network (support@ike.network)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.importer.ncbi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.ikm.tinkar.importer.ImportException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What an NCBI Gene origin holds and how it was made: {@code manifest.json} beside the gene data.
 * The taxa are the organisms the origin was scoped to, each with its gene IDs, so an import can be
 * scoped to some of them again.
 *
 * <p>An origin is one of two {@linkplain Format formats}. A {@link Format#DATASETS_REPORTS} origin
 * holds gene reports as NCBI Datasets returned them, and its manifest lists every URL requested. A
 * {@link Format#BULK_FILES} origin holds rows filtered from NCBI Gene's bulk files, and its
 * manifest also records each file downloaded ({@link SourceFile}: where from, the server's date,
 * its size and SHA-256), each extract the origin holds ({@link Extract}: which file it was filtered
 * from, by what rule, how many rows, its SHA-256), and each scoped taxon's gene count beside the
 * count Entrez reported for it at the time ({@link EntrezCheck}).
 *
 * @param format        the origin's format
 * @param retrievedAt   when NCBI was read
 * @param releaseDate   the date the origin is dated by: the retrieval date, as NCBI Gene has no
 *                      release of its own
 * @param taxa          the organisms scoped, in the order given
 * @param geneReports   how many genes the origin holds
 * @param withoutReport the gene IDs a search returned that NCBI Datasets had no report for, which
 *                      the origin therefore lacks; expected empty, and always empty for a bulk-file
 *                      origin
 * @param requests      every URL requested, in order
 * @param sources       the files downloaded, for a bulk-file origin; empty otherwise
 * @param extracts      the files the origin holds, for a bulk-file origin; empty otherwise
 * @param entrezChecks  each scoped taxon's gene count beside Entrez's, for a bulk-file origin;
 *                      empty otherwise
 */
public record OriginManifest(Format format, Instant retrievedAt, LocalDate releaseDate, List<ScopedTaxon> taxa,
                             long geneReports, List<String> withoutReport, List<String> requests,
                             List<SourceFile> sources, List<Extract> extracts, List<EntrezCheck> entrezChecks) {

    /**
     * The formats of an origin, each named in its manifest's {@code format}.
     */
    public enum Format {
        /** Gene reports as NCBI Datasets returned them, in {@code genes.jsonl}. */
        DATASETS_REPORTS("ncbi-gene-origin/1"),
        /** Rows filtered from NCBI Gene's bulk files and NCBI Taxonomy's names. */
        BULK_FILES("ncbi-gene-origin/2");

        private final String text;

        Format(String text) {
            this.text = text;
        }

        /**
         * The format's name in a manifest.
         *
         * @return the name ({@code ncbi-gene-origin/2})
         */
        public String text() {
            return text;
        }

        /**
         * The format a manifest's {@code format} names.
         *
         * @param text the manifest's text
         * @return the format, if the text names one
         */
        public static Optional<Format> named(String text) {
            for (Format format : values()) {
                if (format.text.equals(text)) {
                    return Optional.of(format);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Validates and copies.
     */
    public OriginManifest {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(retrievedAt, "retrievedAt");
        Objects.requireNonNull(releaseDate, "releaseDate");
        taxa = List.copyOf(Objects.requireNonNull(taxa, "taxa"));
        withoutReport = List.copyOf(Objects.requireNonNull(withoutReport, "withoutReport"));
        requests = List.copyOf(Objects.requireNonNull(requests, "requests"));
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        extracts = List.copyOf(Objects.requireNonNull(extracts, "extracts"));
        entrezChecks = List.copyOf(Objects.requireNonNull(entrezChecks, "entrezChecks"));
    }

    /**
     * A manifest of a {@link Format#DATASETS_REPORTS} origin, which records no files.
     *
     * @param retrievedAt   when NCBI was queried
     * @param releaseDate   the date the origin is dated by
     * @param taxa          the organisms scoped, in the order queried
     * @param geneReports   how many gene reports the origin holds
     * @param withoutReport the gene IDs NCBI Datasets had no report for
     * @param requests      every URL requested, in order
     * @return the manifest
     */
    public static OriginManifest ofDatasetsReports(Instant retrievedAt, LocalDate releaseDate, List<ScopedTaxon> taxa,
                                                   long geneReports, List<String> withoutReport, List<String> requests) {
        return new OriginManifest(Format.DATASETS_REPORTS, retrievedAt, releaseDate, taxa, geneReports, withoutReport,
                requests, List.of(), List.of(), List.of());
    }

    /**
     * One organism the origin was scoped to, with the genes found alive under it and its
     * descendants (strains included).
     *
     * @param taxId        the NCBI Taxonomy ID
     * @param organismName NCBI Taxonomy's name for it, empty when not retrieved
     * @param query        how its genes were found: the Entrez search term, or for a bulk-file
     *                     origin the rule that filtered the bulk files
     * @param count        how many gene IDs were found
     * @param geneIds      the gene IDs, in the search's order, or for a bulk-file origin in gene ID
     *                     order
     */
    public record ScopedTaxon(String taxId, String organismName, String query, long count, List<String> geneIds) {

        /**
         * Validates and copies.
         */
        public ScopedTaxon {
            Objects.requireNonNull(taxId, "taxId");
            Objects.requireNonNull(organismName, "organismName");
            Objects.requireNonNull(query, "query");
            geneIds = List.copyOf(Objects.requireNonNull(geneIds, "geneIds"));
        }
    }

    /**
     * A file a bulk-file origin was filtered from, as downloaded.
     *
     * @param name         the file's name ({@code gene_info.gz})
     * @param url          where it was downloaded from
     * @param lastModified the server's {@code Last-Modified} for it, as sent
     * @param bytes        its size
     * @param sha256       its SHA-256, lowercase hexadecimal
     */
    public record SourceFile(String name, String url, String lastModified, long bytes, String sha256) {

        /**
         * Validates.
         */
        public SourceFile {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(url, "url");
            Objects.requireNonNull(lastModified, "lastModified");
            Objects.requireNonNull(sha256, "sha256");
        }
    }

    /**
     * A file a bulk-file origin holds: rows filtered from a source file, verbatim, under the
     * source's own header line.
     *
     * @param name   the file's name in the origin ({@code gene_info.tsv})
     * @param source the name of the source file it was filtered from
     * @param filter the rule that kept a row
     * @param rows   how many rows it holds, its header line not counted
     * @param sha256 its SHA-256, lowercase hexadecimal
     */
    public record Extract(String name, String source, String filter, long rows, String sha256) {

        /**
         * Validates.
         */
        public Extract {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(filter, "filter");
            Objects.requireNonNull(sha256, "sha256");
        }
    }

    /**
     * A scoped taxon's gene count in a bulk-file origin beside the count Entrez reported for the
     * same taxon when the origin was built. The bulk files are refreshed daily and Entrez is live,
     * so the two can differ by a few genes; a large difference says the filter is wrong.
     *
     * @param taxId       the NCBI Taxonomy ID
     * @param query       the Entrez search term
     * @param entrezCount the count Entrez reported
     * @param originCount the genes the origin holds for the taxon
     */
    public record EntrezCheck(String taxId, String query, long entrezCount, long originCount) {

        /**
         * Validates.
         */
        public EntrezCheck {
            Objects.requireNonNull(taxId, "taxId");
            Objects.requireNonNull(query, "query");
        }
    }

    /**
     * The scoped taxon with an ID.
     *
     * @param taxId the NCBI Taxonomy ID
     * @return the taxon, if the origin was scoped to it
     */
    public Optional<ScopedTaxon> taxon(String taxId) {
        return taxa.stream().filter(taxon -> taxon.taxId().equals(taxId)).findFirst();
    }

    /**
     * The gene IDs of some scoped taxa, each taxon's in its order, without repeats.
     *
     * @param taxIds the taxa's NCBI Taxonomy IDs
     * @return the gene IDs
     * @throws ImportException when a taxon is not one the origin was scoped to
     */
    public Set<String> geneIdsOf(Collection<String> taxIds) {
        Set<String> geneIds = new LinkedHashSet<>();
        for (String taxId : taxIds) {
            ScopedTaxon taxon = taxon(taxId).orElseThrow(() -> new ImportException("The origin is not scoped to taxon "
                    + taxId + "; its taxa are " + taxa.stream().map(ScopedTaxon::taxId).toList()));
            geneIds.addAll(taxon.geneIds());
        }
        return geneIds;
    }

    /**
     * Reads a manifest.
     *
     * @param node the manifest's JSON
     * @return the manifest
     * @throws ImportException when the manifest is of no known format or is incomplete
     */
    public static OriginManifest parse(JsonNode node) {
        String formatText = node.path("format").asText("");
        Format format = Format.named(formatText).orElseThrow(() -> new ImportException("The origin manifest's format is "
                + (formatText.isEmpty() ? "missing" : formatText) + ", not one of "
                + Arrays.stream(Format.values()).map(Format::text).toList()));
        List<ScopedTaxon> taxa = new ArrayList<>();
        for (JsonNode taxon : node.path("taxa")) {
            List<String> geneIds = new ArrayList<>();
            for (JsonNode geneId : taxon.path("geneIds")) {
                geneIds.add(geneId.asText());
            }
            String taxId = taxon.path("taxId").asText("");
            if (taxId.isEmpty()) {
                throw new ImportException("A scoped taxon in the origin manifest has no taxId");
            }
            taxa.add(new ScopedTaxon(taxId, taxon.path("organismName").asText(""), taxon.path("query").asText(""),
                    taxon.path("count").asLong(geneIds.size()), geneIds));
        }
        List<SourceFile> sources = new ArrayList<>();
        for (JsonNode source : node.path("sources")) {
            sources.add(new SourceFile(source.path("name").asText(""), source.path("url").asText(""),
                    source.path("lastModified").asText(""), source.path("bytes").asLong(-1),
                    source.path("sha256").asText("")));
        }
        List<Extract> extracts = new ArrayList<>();
        for (JsonNode extract : node.path("extracts")) {
            extracts.add(new Extract(extract.path("name").asText(""), extract.path("source").asText(""),
                    extract.path("filter").asText(""), extract.path("rows").asLong(-1),
                    extract.path("sha256").asText("")));
        }
        List<EntrezCheck> entrezChecks = new ArrayList<>();
        for (JsonNode check : node.path("entrezChecks")) {
            entrezChecks.add(new EntrezCheck(check.path("taxId").asText(""), check.path("query").asText(""),
                    check.path("entrezCount").asLong(-1), check.path("originCount").asLong(-1)));
        }
        try {
            return new OriginManifest(format, Instant.parse(required(node, "retrievedAt")),
                    LocalDate.parse(required(node, "releaseDate")), taxa, node.path("geneReports").asLong(-1),
                    texts(node.path("withoutReport")), texts(node.path("requests")), sources, extracts, entrezChecks);
        } catch (DateTimeParseException e) {
            throw new ImportException("The origin manifest's retrievedAt or releaseDate is unreadable", e);
        }
    }

    /**
     * This manifest as JSON, in the field order the builders write. A {@link Format#DATASETS_REPORTS}
     * manifest carries no file lists, so it reads as it always has.
     *
     * @return the JSON
     */
    public ObjectNode toJson() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("format", format.text());
        root.put("retrievedAt", retrievedAt.toString());
        root.put("releaseDate", releaseDate.toString());
        if (format == Format.BULK_FILES) {
            ArrayNode sourcesNode = root.putArray("sources");
            for (SourceFile source : sources) {
                ObjectNode sourceNode = sourcesNode.addObject();
                sourceNode.put("name", source.name());
                sourceNode.put("url", source.url());
                sourceNode.put("lastModified", source.lastModified());
                sourceNode.put("bytes", source.bytes());
                sourceNode.put("sha256", source.sha256());
            }
            ArrayNode extractsNode = root.putArray("extracts");
            for (Extract extract : extracts) {
                ObjectNode extractNode = extractsNode.addObject();
                extractNode.put("name", extract.name());
                extractNode.put("source", extract.source());
                extractNode.put("filter", extract.filter());
                extractNode.put("rows", extract.rows());
                extractNode.put("sha256", extract.sha256());
            }
            ArrayNode checksNode = root.putArray("entrezChecks");
            for (EntrezCheck check : entrezChecks) {
                ObjectNode checkNode = checksNode.addObject();
                checkNode.put("taxId", check.taxId());
                checkNode.put("query", check.query());
                checkNode.put("entrezCount", check.entrezCount());
                checkNode.put("originCount", check.originCount());
            }
        }
        ArrayNode taxaNode = root.putArray("taxa");
        for (ScopedTaxon taxon : taxa) {
            ObjectNode taxonNode = taxaNode.addObject();
            taxonNode.put("taxId", taxon.taxId());
            taxonNode.put("organismName", taxon.organismName());
            taxonNode.put("query", taxon.query());
            taxonNode.put("count", taxon.count());
            ArrayNode ids = taxonNode.putArray("geneIds");
            taxon.geneIds().forEach(ids::add);
        }
        root.put("geneReports", geneReports);
        ArrayNode withoutReportNode = root.putArray("withoutReport");
        withoutReport.forEach(withoutReportNode::add);
        ArrayNode requestsNode = root.putArray("requests");
        requests.forEach(requestsNode::add);
        return root;
    }

    private static List<String> texts(JsonNode array) {
        List<String> texts = new ArrayList<>();
        for (JsonNode text : array) {
            texts.add(text.asText());
        }
        return texts;
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isEmpty()) {
            throw new ImportException("The origin manifest has no " + field);
        }
        return value;
    }
}
