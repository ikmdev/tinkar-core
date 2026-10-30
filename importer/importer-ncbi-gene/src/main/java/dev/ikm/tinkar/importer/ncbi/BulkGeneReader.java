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

import dev.ikm.tinkar.importer.ImportException;
import dev.ikm.tinkar.importer.ncbi.GeneReport.Annotation;
import dev.ikm.tinkar.importer.ncbi.GeneReport.GeneType;
import dev.ikm.tinkar.importer.ncbi.GeneReport.GenomicLocation;
import dev.ikm.tinkar.importer.ncbi.GeneReport.Orientation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reads the genes of a {@linkplain OriginManifest.Format#BULK_FILES bulk-file origin}: rows of NCBI
 * Gene's {@code gene_info}, {@code gene2refseq} and {@code gene_summary} files, and NCBI
 * Taxonomy's scientific names, joined by gene and taxon into the same {@link GeneReport} a
 * Datasets report gives.
 *
 * <p>The join, per gene row of {@code gene_info.tsv} (a {@code NEWENTRY} placeholder row is not a
 * gene and is skipped):
 * <ul>
 *   <li>symbol, description, type and locus tag from the row ({@code -} read as absent);</li>
 *   <li>the organism's name from {@code taxonomy_names.dmp};</li>
 *   <li>the summary from {@code gene_summary.tsv}, NCBI RefSeq's when there are several;</li>
 *   <li>the genomic locations from {@code gene2refseq.tsv}: each distinct (accession.version,
 *   start, end, orientation, assembly) of the gene's rows, in file order, grouped into one
 *   annotation per assembly. The file repeats a location on every row of the gene's RNA and
 *   protein, and gives positions zero-based, so a location's begin and end are the file's plus
 *   one. RefSeqGene records ({@code NG_}) are left out: they are the gene's own region as a
 *   sequence, not a place on a genome, and Datasets does not report them. A location whose end
 *   comes before its begin crosses the origin of a circular sequence; it is left out and
 *   counted, since a one-based closed range cannot hold it.</li>
 * </ul>
 * The locations and summaries are read first and held by gene; the genes then stream in file
 * order.
 */
final class BulkGeneReader {

    /** The {@code gene_info} rows' file name. */
    static final String GENE_INFO_FILE = "gene_info.tsv";
    /** The {@code gene2refseq} rows' file name. */
    static final String GENE2REFSEQ_FILE = "gene2refseq.tsv";
    /** The {@code gene_summary} rows' file name. */
    static final String GENE_SUMMARY_FILE = "gene_summary.tsv";
    /** The scientific-name rows of NCBI Taxonomy's {@code names.dmp}: the file name. */
    static final String TAXONOMY_NAMES_FILE = "taxonomy_names.dmp";
    /** The symbol of NCBI's placeholder records, one per organism, which are not genes. */
    static final String NEWENTRY = "NEWENTRY";
    /** The accession prefix of RefSeqGene records, which are not places on a genome. */
    static final String REFSEQ_GENE_PREFIX = "NG_";
    /** The text the bulk files write for an absent value. */
    static final String ABSENT = "-";

    private static final Logger LOG = LoggerFactory.getLogger(BulkGeneReader.class);

    /** One distinct location of a gene, as {@code gene2refseq} states it, positions already one-based. */
    private record Placement(String accessionVersion, long begin, long end, String sign, String assembly) {
    }

    private final NcbiGeneOrigin.OriginFile geneInfo;
    private final Map<String, String> taxonNames;
    private final Map<String, Set<Placement>> placements;
    private final Map<String, String> summaries;

    private BulkGeneReader(NcbiGeneOrigin.OriginFile geneInfo, Map<String, String> taxonNames,
                           Map<String, Set<Placement>> placements, Map<String, String> summaries) {
        this.geneInfo = geneInfo;
        this.taxonNames = taxonNames;
        this.placements = placements;
        this.summaries = summaries;
    }

    /**
     * The genes of a bulk-file origin, in {@code gene_info.tsv} order. The stream must be closed.
     *
     * @param geneInfo    the {@code gene_info} rows
     * @param gene2refseq the {@code gene2refseq} rows
     * @param summaries   the {@code gene_summary} rows
     * @param names       the scientific-name rows of {@code names.dmp}
     * @return the genes
     * @throws ImportException when a file cannot be read, lacks a column the join needs, or names a
     *                         taxon the names file lacks
     */
    static Stream<GeneReport> genes(NcbiGeneOrigin.OriginFile geneInfo, NcbiGeneOrigin.OriginFile gene2refseq,
                                    NcbiGeneOrigin.OriginFile summaries, NcbiGeneOrigin.OriginFile names) {
        BulkGeneReader reader = new BulkGeneReader(geneInfo, readNames(names), readPlacements(gene2refseq),
                readSummaries(summaries));
        return reader.stream();
    }

    private Stream<GeneReport> stream() {
        BufferedReader reader = open(geneInfo);
        Columns columns = Columns.of(readHeader(reader, geneInfo), geneInfo, "#tax_id", "GeneID", "Symbol",
                "LocusTag", "description", "type_of_gene");
        return reader.lines()
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(line -> line.split("\t", -1))
                .filter(row -> !NEWENTRY.equals(columns.get(row, "Symbol")))
                .map(row -> gene(row, columns))
                .onClose(() -> close(reader, geneInfo));
    }

    private GeneReport gene(String[] row, Columns columns) {
        String geneId = columns.get(row, "GeneID");
        String taxId = columns.get(row, "#tax_id");
        String symbol = columns.get(row, "Symbol");
        if (geneId.isEmpty() || symbol.isEmpty() || symbol.equals(ABSENT)) {
            throw new ImportException("A row of " + geneInfo.where() + " lacks its GeneID or Symbol: " + String.join("\t", row));
        }
        String taxname = taxonNames.get(taxId);
        if (taxname == null) {
            throw new ImportException("Gene " + geneId + " is of taxon " + taxId + ", which "
                    + TAXONOMY_NAMES_FILE + " does not name");
        }
        Map<String, List<GenomicLocation>> byAssembly = new LinkedHashMap<>();
        Set<Optional<Orientation>> orientations = new LinkedHashSet<>();
        for (Placement placement : placements.getOrDefault(geneId, Set.of())) {
            Optional<Orientation> orientation = Orientation.ofSign(placement.sign());
            orientations.add(orientation);
            byAssembly.computeIfAbsent(placement.assembly(), assembly -> new ArrayList<>())
                    .add(new GenomicLocation(placement.accessionVersion(), "", placement.begin(), placement.end(),
                            orientation));
        }
        List<Annotation> annotations = new ArrayList<>();
        byAssembly.forEach((assembly, locations) ->
                annotations.add(new Annotation("", assembly, "", Optional.empty(), locations)));
        Optional<Orientation> geneOrientation = orientations.size() == 1 ? orientations.iterator().next() : Optional.empty();
        return new GeneReport(geneId, symbol, present(columns.get(row, "description")), taxId, taxname,
                GeneType.ofGeneInfo(columns.get(row, "type_of_gene")), geneOrientation,
                present(columns.get(row, "LocusTag")), List.of(), Optional.ofNullable(summaries.get(geneId)),
                annotations);
    }

    private static Map<String, String> readNames(NcbiGeneOrigin.OriginFile names) {
        Map<String, String> byTaxon = new HashMap<>();
        try (BufferedReader reader = open(names)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                String[] fields = dumpFields(line);
                if (fields.length >= 4 && fields[3].equals("scientific name")) {
                    byTaxon.put(fields[0], fields[1]);
                }
            }
        } catch (IOException e) {
            throw new ImportException("Cannot read " + names.where(), e);
        }
        return byTaxon;
    }

    private static Map<String, Set<Placement>> readPlacements(NcbiGeneOrigin.OriginFile gene2refseq) {
        Map<String, Set<Placement>> byGene = new HashMap<>();
        long crossingOrigin = 0;
        List<String> crossingExamples = new ArrayList<>();
        try (BufferedReader reader = open(gene2refseq)) {
            Columns columns = Columns.of(readHeader(reader, gene2refseq), gene2refseq, "GeneID",
                    "genomic_nucleotide_accession.version", "start_position_on_the_genomic_accession",
                    "end_position_on_the_genomic_accession", "orientation", "assembly");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] row = line.split("\t", -1);
                String accession = columns.get(row, "genomic_nucleotide_accession.version");
                String start = columns.get(row, "start_position_on_the_genomic_accession");
                String end = columns.get(row, "end_position_on_the_genomic_accession");
                if (accession.equals(ABSENT) || accession.startsWith(REFSEQ_GENE_PREFIX)
                        || start.equals(ABSENT) || end.equals(ABSENT)) {
                    continue;
                }
                String geneId = columns.get(row, "GeneID");
                long begin;
                long last;
                try {
                    begin = Long.parseLong(start) + 1;
                    last = Long.parseLong(end) + 1;
                } catch (NumberFormatException e) {
                    throw new ImportException("Gene " + geneId + " has an unreadable position on " + accession
                            + " in " + gene2refseq.where(), e);
                }
                if (last < begin) {
                    crossingOrigin++;
                    if (crossingExamples.size() < 10) {
                        crossingExamples.add(geneId + " " + accession + " " + begin + ".." + last);
                    }
                    continue;
                }
                String assembly = columns.get(row, "assembly");
                byGene.computeIfAbsent(geneId, id -> new LinkedHashSet<>()).add(new Placement(accession, begin, last,
                        columns.get(row, "orientation"), assembly.equals(ABSENT) ? "" : assembly));
            }
        } catch (IOException e) {
            throw new ImportException("Cannot read " + gene2refseq.where(), e);
        }
        if (crossingOrigin > 0) {
            LOG.warn("Left out {} genomic locations that cross the origin of a circular sequence, such as {}",
                    crossingOrigin, crossingExamples);
        }
        return byGene;
    }

    private static Map<String, String> readSummaries(NcbiGeneOrigin.OriginFile summaries) {
        Map<String, String> byGene = new HashMap<>();
        try (BufferedReader reader = open(summaries)) {
            Columns columns = Columns.of(readHeader(reader, summaries), summaries, "GeneID", "Source", "Summary");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] row = line.split("\t", -1);
                String summary = columns.get(row, "Summary");
                if (summary.isEmpty() || summary.equals(ABSENT)) {
                    continue;
                }
                String geneId = columns.get(row, "GeneID");
                if (columns.get(row, "Source").equals("RefSeq") || !byGene.containsKey(geneId)) {
                    byGene.put(geneId, summary);
                }
            }
        } catch (IOException e) {
            throw new ImportException("Cannot read " + summaries.where(), e);
        }
        return byGene;
    }

    /**
     * A {@code .dmp} row's fields: NCBI Taxonomy separates them by {@code \t|\t} and ends the row
     * with {@code \t|}.
     *
     * @param line the row
     * @return its fields, stripped
     */
    static String[] dumpFields(String line) {
        String trimmed = line.endsWith("\t|") ? line.substring(0, line.length() - 2) : line;
        String[] fields = trimmed.split("\t\\|\t", -1);
        for (int i = 0; i < fields.length; i++) {
            fields[i] = fields[i].strip();
        }
        return fields;
    }

    private static String present(String value) {
        return value.equals(ABSENT) ? "" : value;
    }

    private static BufferedReader open(NcbiGeneOrigin.OriginFile file) {
        try {
            return new BufferedReader(new InputStreamReader(file.open(), StandardCharsets.UTF_8), 1 << 16);
        } catch (IOException e) {
            throw new ImportException("Cannot read " + file.where(), e);
        }
    }

    private static String readHeader(BufferedReader reader, NcbiGeneOrigin.OriginFile file) {
        try {
            String header = reader.readLine();
            if (header == null || !header.startsWith("#")) {
                throw new ImportException(file.where() + " does not begin with its header line");
            }
            return header;
        } catch (IOException e) {
            throw new ImportException("Cannot read " + file.where(), e);
        }
    }

    private static void close(BufferedReader reader, NcbiGeneOrigin.OriginFile file) {
        try {
            reader.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot close " + file.where(), e);
        }
    }

    /** A bulk file's columns, found by their header names. */
    private record Columns(Map<String, Integer> index, String where) {

        static Columns of(String header, NcbiGeneOrigin.OriginFile file, String... required) {
            Map<String, Integer> index = new HashMap<>();
            String[] names = header.split("\t", -1);
            for (int i = 0; i < names.length; i++) {
                index.put(names[i].strip(), i);
            }
            for (String name : required) {
                if (!index.containsKey(name)) {
                    throw new ImportException(file.where() + " has no column " + name + "; its header is " + header);
                }
            }
            return new Columns(index, file.where());
        }

        String get(String[] row, String column) {
            int i = index.get(column);
            if (i >= row.length) {
                throw new ImportException("A row of " + where + " has no " + column + ": " + String.join("\t", row));
            }
            return row[i].strip();
        }
    }
}
