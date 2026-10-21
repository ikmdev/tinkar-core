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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What an NCBI Gene origin holds and how it was retrieved: {@code manifest.json} beside the
 * reports. The taxa are the organisms the origin was scoped to, each with the gene IDs its
 * search returned, so an import can be scoped to some of them again.
 *
 * @param format      the origin format, {@value #FORMAT}
 * @param retrievedAt when NCBI was queried
 * @param releaseDate the date the origin is dated by: the retrieval date, as NCBI Gene has no
 *                    release of its own
 * @param taxa        the organisms scoped, in the order they were queried
 * @param geneReports   how many gene reports the origin holds
 * @param withoutReport the gene IDs a search returned that NCBI Datasets had no report for, which
 *                      the origin therefore lacks; expected empty, since the searches exclude
 *                      NCBI's placeholder records
 * @param requests      every URL requested, in order
 */
public record OriginManifest(String format, Instant retrievedAt, LocalDate releaseDate, List<ScopedTaxon> taxa,
                             long geneReports, List<String> withoutReport, List<String> requests) {

    /** The format this importer reads and the builder writes. */
    public static final String FORMAT = "ncbi-gene-origin/1";

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
    }

    /**
     * One organism the origin was scoped to, with the genes Entrez found alive under it and its
     * descendants (strains included).
     *
     * @param taxId        the NCBI Taxonomy ID
     * @param organismName NCBI Taxonomy's name for it, empty when not retrieved
     * @param query        the Entrez search term
     * @param count        how many gene IDs the search reported
     * @param geneIds      the gene IDs, in the search's order
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
     * @throws ImportException when the manifest is not of this format or is incomplete
     */
    public static OriginManifest parse(JsonNode node) {
        String format = node.path("format").asText("");
        if (!FORMAT.equals(format)) {
            throw new ImportException("The origin manifest's format is " + (format.isEmpty() ? "missing" : format)
                    + ", not " + FORMAT);
        }
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
        List<String> withoutReport = new ArrayList<>();
        for (JsonNode geneId : node.path("withoutReport")) {
            withoutReport.add(geneId.asText());
        }
        List<String> requests = new ArrayList<>();
        for (JsonNode request : node.path("requests")) {
            requests.add(request.asText());
        }
        try {
            return new OriginManifest(format, Instant.parse(required(node, "retrievedAt")),
                    LocalDate.parse(required(node, "releaseDate")), taxa, node.path("geneReports").asLong(-1),
                    withoutReport, requests);
        } catch (DateTimeParseException e) {
            throw new ImportException("The origin manifest's retrievedAt or releaseDate is unreadable", e);
        }
    }

    /**
     * This manifest as JSON, in the field order the builder writes.
     *
     * @return the JSON
     */
    public ObjectNode toJson() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("format", format);
        root.put("retrievedAt", retrievedAt.toString());
        root.put("releaseDate", releaseDate.toString());
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

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isEmpty()) {
            throw new ImportException("The origin manifest has no " + field);
        }
        return value;
    }
}
