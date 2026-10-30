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
import dev.ikm.tinkar.importer.ImportException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One gene as the importer reads it: from NCBI Datasets' report ({@code GET
 * /datasets/v2/gene/id/{ids}}, one element of {@code reports[]}), parsed by {@link #parse}, or
 * from the rows of NCBI Gene's bulk files that a bulk-file origin holds, joined by the origin's
 * reader. Everything else stays in the origin, untouched.
 *
 * @param geneId              the NCBI Gene ID
 * @param symbol              the gene symbol ({@code N}, {@code PB2})
 * @param description         NCBI's description ({@code nucleocapsid phosphoprotein}), empty when
 *                            the report has none
 * @param taxId               the NCBI Taxonomy ID of the organism the gene is annotated on, which
 *                            for a virus is a reference strain
 * @param taxname             that organism's name
 * @param type                the gene's type
 * @param orientation         the gene's strand, when the report states one
 * @param locusTag            the locus tag, empty when the report has none
 * @param swissProtAccessions the UniProtKB/Swiss-Prot accessions, in the report's order
 * @param summary             NCBI's RefSeq summary, when the report has one
 * @param annotations         the annotations placing the gene on RefSeq assemblies, in the
 *                            report's order
 */
public record GeneReport(String geneId, String symbol, String description, String taxId, String taxname,
                         GeneType type, Optional<Orientation> orientation, String locusTag,
                         List<String> swissProtAccessions, Optional<String> summary,
                         List<Annotation> annotations) {

    /**
     * Validates and copies.
     */
    public GeneReport {
        Objects.requireNonNull(geneId, "geneId");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(taxId, "taxId");
        Objects.requireNonNull(taxname, "taxname");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(orientation, "orientation");
        Objects.requireNonNull(locusTag, "locusTag");
        swissProtAccessions = List.copyOf(Objects.requireNonNull(swissProtAccessions, "swissProtAccessions"));
        Objects.requireNonNull(summary, "summary");
        annotations = List.copyOf(Objects.requireNonNull(annotations, "annotations"));
    }

    /**
     * The kinds of gene NCBI Gene distinguishes ({@code type} in a report).
     */
    public enum GeneType {
        /** A protein-coding gene. */
        PROTEIN_CODING,
        /** A pseudogene. */
        PSEUDO,
        /** A non-coding RNA gene. */
        NC_RNA("ncRNA"),
        /** A ribosomal RNA gene. */
        R_RNA("rRNA"),
        /** A transfer RNA gene. */
        T_RNA("tRNA"),
        /** A small nuclear RNA gene. */
        SN_RNA("snRNA"),
        /** A small nucleolar RNA gene. */
        SNO_RNA("snoRNA"),
        /** A small cytoplasmic RNA gene. */
        SC_RNA("scRNA"),
        /** A biological region NCBI Gene records as a gene. */
        BIOLOGICAL_REGION,
        /** A gene of another kind NCBI names. */
        OTHER,
        /** A gene whose kind NCBI does not state. */
        UNKNOWN,
        /** A type this importer does not know; the report's text is kept in the origin. */
        UNRECOGNIZED;

        private final String reported;

        GeneType() {
            this.reported = name();
        }

        GeneType(String reported) {
            this.reported = reported;
        }

        /**
         * The type a report's {@code type} names.
         *
         * @param reported the report's text
         * @return the type, {@link #UNKNOWN} for an absent text, {@link #UNRECOGNIZED} for a text
         *         this importer does not know
         */
        public static GeneType named(String reported) {
            if (reported == null || reported.isBlank()) {
                return UNKNOWN;
            }
            for (GeneType type : values()) {
                if (type.reported.equals(reported)) {
                    return type;
                }
            }
            return UNRECOGNIZED;
        }

        /**
         * The type a {@code type_of_gene} of NCBI Gene's {@code gene_info} file names
         * ({@code protein-coding}, {@code ncRNA}, {@code biological-region}).
         *
         * @param typeOfGene the file's text
         * @return the type, {@link #UNKNOWN} for an absent text ({@code -}), {@link #UNRECOGNIZED}
         *         for a text this importer does not know
         */
        public static GeneType ofGeneInfo(String typeOfGene) {
            if (typeOfGene == null || typeOfGene.isBlank() || typeOfGene.strip().equals("-")) {
                return UNKNOWN;
            }
            return switch (typeOfGene.strip()) {
                case "protein-coding" -> PROTEIN_CODING;
                case "pseudo" -> PSEUDO;
                case "ncRNA" -> NC_RNA;
                case "rRNA" -> R_RNA;
                case "tRNA" -> T_RNA;
                case "snRNA" -> SN_RNA;
                case "snoRNA" -> SNO_RNA;
                case "scRNA" -> SC_RNA;
                case "biological-region" -> BIOLOGICAL_REGION;
                case "other" -> OTHER;
                case "unknown" -> UNKNOWN;
                default -> UNRECOGNIZED;
            };
        }
    }

    /**
     * The strand a gene, or a location, lies on.
     */
    public enum Orientation {
        /** The forward strand ({@code plus}). */
        PLUS("plus"),
        /** The reverse strand ({@code minus}). */
        MINUS("minus");

        private final String reported;

        Orientation(String reported) {
            this.reported = reported;
        }

        /**
         * The orientation a report's text names.
         *
         * @param reported the report's text ({@code plus} or {@code minus})
         * @return the orientation, if the text names one
         */
        public static Optional<Orientation> named(String reported) {
            if (reported == null) {
                return Optional.empty();
            }
            for (Orientation orientation : values()) {
                if (orientation.reported.equalsIgnoreCase(reported.strip())) {
                    return Optional.of(orientation);
                }
            }
            return Optional.empty();
        }

        /**
         * The orientation an {@code orientation} of NCBI Gene's {@code gene2refseq} file names.
         *
         * @param sign the file's text: {@code +} or {@code -}, {@code ?} when not applicable
         * @return the orientation, if the text names one
         */
        public static Optional<Orientation> ofSign(String sign) {
            if (sign == null) {
                return Optional.empty();
            }
            return switch (sign.strip()) {
                case "+" -> Optional.of(PLUS);
                case "-" -> Optional.of(MINUS);
                default -> Optional.empty();
            };
        }
    }

    /**
     * An annotation of the gene on one RefSeq assembly.
     *
     * @param assemblyAccession     the assembly's accession ({@code GCF_009858895.2})
     * @param assemblyName          the assembly's name, empty when not stated
     * @param annotationName        the annotation's name, empty when not stated
     * @param annotationReleaseDate when the annotation was released, if stated
     * @param genomicLocations      where the gene lies, in the report's order
     */
    public record Annotation(String assemblyAccession, String assemblyName, String annotationName,
                             Optional<LocalDate> annotationReleaseDate, List<GenomicLocation> genomicLocations) {

        /**
         * Validates and copies.
         */
        public Annotation {
            Objects.requireNonNull(assemblyAccession, "assemblyAccession");
            Objects.requireNonNull(assemblyName, "assemblyName");
            Objects.requireNonNull(annotationName, "annotationName");
            Objects.requireNonNull(annotationReleaseDate, "annotationReleaseDate");
            genomicLocations = List.copyOf(Objects.requireNonNull(genomicLocations, "genomicLocations"));
        }
    }

    /**
     * Where a gene lies on one RefSeq genomic sequence: a closed range of one-based positions.
     *
     * @param accessionVersion the sequence's RefSeq accession and version ({@code NC_045512.2})
     * @param sequenceName     the sequence's name within its assembly, empty when not stated
     * @param begin            the first position, one-based
     * @param end              the last position, one-based, inclusive
     * @param orientation      the strand, when the report states one
     */
    public record GenomicLocation(String accessionVersion, String sequenceName, long begin, long end,
                                  Optional<Orientation> orientation) {

        /**
         * Validates.
         */
        public GenomicLocation {
            Objects.requireNonNull(accessionVersion, "accessionVersion");
            Objects.requireNonNull(sequenceName, "sequenceName");
            Objects.requireNonNull(orientation, "orientation");
            if (begin < 1 || end < begin) {
                throw new IllegalArgumentException("A genomic range is one-based and begins before it ends: "
                        + accessionVersion + " " + begin + "-" + end);
            }
        }
    }

    /**
     * The distinct RefSeq genomic accession.versions the gene is placed on, in the order the
     * report places them.
     *
     * @return the accession.versions
     */
    public List<String> refSeqAccessions() {
        Set<String> accessions = new LinkedHashSet<>();
        for (Annotation annotation : annotations) {
            for (GenomicLocation location : annotation.genomicLocations()) {
                accessions.add(location.accessionVersion());
            }
        }
        return List.copyOf(accessions);
    }

    /**
     * Reads a report: an element of Datasets' {@code reports[]} (an object holding {@code gene}),
     * or the {@code gene} object itself.
     *
     * @param node the report
     * @return the gene
     * @throws ImportException when the report lacks a gene ID, symbol, or organism
     */
    public static GeneReport parse(JsonNode node) {
        JsonNode gene = node.has("gene") ? node.get("gene") : node;
        String geneId = text(gene, "gene_id");
        if (geneId.isEmpty()) {
            throw new ImportException("A gene report has no gene_id: " + node);
        }
        String symbol = text(gene, "symbol");
        String taxId = text(gene, "tax_id");
        String taxname = text(gene, "taxname");
        if (symbol.isEmpty() || taxId.isEmpty() || taxname.isEmpty()) {
            throw new ImportException("Gene " + geneId + " lacks its symbol, tax_id or taxname");
        }
        List<String> swissProt = new ArrayList<>();
        for (JsonNode accession : gene.path("swiss_prot_accessions")) {
            swissProt.add(accession.asText());
        }
        Optional<String> summary = Optional.empty();
        for (JsonNode entry : gene.path("summary")) {
            String text = text(entry, "description");
            if (!text.isEmpty()) {
                summary = Optional.of(text);
                break;
            }
        }
        List<Annotation> annotations = new ArrayList<>();
        for (JsonNode annotation : gene.path("annotations")) {
            annotations.add(parseAnnotation(geneId, annotation));
        }
        return new GeneReport(geneId, symbol, text(gene, "description"), taxId, taxname,
                GeneType.named(text(gene, "type")), Orientation.named(textOrNull(gene, "orientation")),
                text(gene, "locus_tag"), swissProt, summary, annotations);
    }

    private static Annotation parseAnnotation(String geneId, JsonNode annotation) {
        List<GenomicLocation> locations = new ArrayList<>();
        for (JsonNode location : annotation.path("genomic_locations")) {
            JsonNode range = location.path("genomic_range");
            String accession = text(location, "genomic_accession_version");
            if (accession.isEmpty()) {
                throw new ImportException("Gene " + geneId + " has a genomic location with no accession.version");
            }
            try {
                locations.add(new GenomicLocation(accession, text(location, "sequence_name"),
                        Long.parseLong(text(range, "begin")), Long.parseLong(text(range, "end")),
                        Orientation.named(textOrNull(range, "orientation"))));
            } catch (IllegalArgumentException e) {
                throw new ImportException("Gene " + geneId + " has an unreadable genomic range on " + accession, e);
            }
        }
        Optional<LocalDate> released = Optional.empty();
        String date = text(annotation, "annotation_release_date");
        if (!date.isEmpty()) {
            try {
                released = Optional.of(LocalDate.parse(date));
            } catch (DateTimeParseException e) {
                throw new ImportException("Gene " + geneId + " has an unreadable annotation_release_date: " + date, e);
            }
        }
        return new Annotation(text(annotation, "assembly_accession"), text(annotation, "assembly_name"),
                text(annotation, "annotation_name"), released, locations);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isValueNode() ? value.asText().strip() : "";
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isValueNode() ? value.asText() : null;
    }
}
