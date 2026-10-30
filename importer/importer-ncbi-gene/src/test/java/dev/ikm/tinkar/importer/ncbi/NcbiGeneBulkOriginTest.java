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
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ikm.tinkar.importer.ImportException;
import dev.ikm.tinkar.importer.ncbi.GeneReport.Annotation;
import dev.ikm.tinkar.importer.ncbi.GeneReport.GeneType;
import dev.ikm.tinkar.importer.ncbi.GeneReport.GenomicLocation;
import dev.ikm.tinkar.importer.ncbi.GeneReport.Orientation;
import dev.ikm.tinkar.importer.ncbi.NcbiGeneBulkOriginBuilder.Fetched;
import dev.ikm.tinkar.importer.ncbi.NcbiGeneBulkOriginBuilder.Source;
import dev.ikm.tinkar.importer.ncbi.OriginManifest.EntrezCheck;
import dev.ikm.tinkar.importer.ncbi.OriginManifest.Extract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Building a bulk-file origin from NCBI's files, served here from a temporary directory, and
 * reading its genes back: the taxonomy subtree as the scope, the verbatim extracts and their
 * checksums, the zero-based positions read one-based, and the rows that do not place a gene.
 */
class NcbiGeneBulkOriginTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String GENE_INFO_HEADER = "#tax_id\tGeneID\tSymbol\tLocusTag\tSynonyms\tdbXrefs\tchromosome"
            + "\tmap_location\tdescription\ttype_of_gene\tSymbol_from_nomenclature_authority"
            + "\tFull_name_from_nomenclature_authority\tNomenclature_status\tOther_designations\tModification_date"
            + "\tFeature_type";
    private static final String GENE2REFSEQ_HEADER = "#tax_id\tGeneID\tstatus\tRNA_nucleotide_accession.version"
            + "\tRNA_nucleotide_gi\tprotein_accession.version\tprotein_gi\tgenomic_nucleotide_accession.version"
            + "\tgenomic_nucleotide_gi\tstart_position_on_the_genomic_accession\tend_position_on_the_genomic_accession"
            + "\torientation\tassembly\tmature_peptide_accession.version\tmature_peptide_gi\tSymbol";
    private static final String SUMMARY_HEADER = "#tax_id\tGeneID\tSource\tSummary";

    private static final List<String> GENE_INFO = List.of(
            geneInfo("2697049", "43740575", "N", "GU280_gp10", "nucleocapsid phosphoprotein", "protein-coding"),
            geneInfo("2697049", "43740587", "NEWENTRY", "-", "Record to support submission of GeneRIFs", "other"),
            geneInfo("9606", "7157", "TP53", "-", "tumor protein p53", "protein-coding"),
            geneInfo("10090", "22059", "Trp53", "-", "transformation related protein 53", "protein-coding"),
            geneInfo("63221", "123", "X1", "-", "-", "ncRNA"));
    private static final List<String> GENE2REFSEQ = List.of(
            gene2refseq("2697049", "43740575", "YP_009724397.2", "NC_045512.2", "28273", "29532", "+", "-"),
            gene2refseq("2697049", "43740575", "YP_009724397.3", "NC_045512.2", "28273", "29532", "+", "-"),
            gene2refseq("9606", "7157", "NP_000537.3", "NC_000017.11", "7661778", "7687537", "-",
                    "Reference GRCh38.p14 Primary Assembly"),
            gene2refseq("9606", "7157", "NP_000537.3", "NC_060941.1", "7682340", "7701024", "-",
                    "Alternate T2T-CHM13v2.0"),
            gene2refseq("9606", "7157", "NP_000537.3", "NG_017013.2", "5000", "24148", "+", "-"),
            gene2refseq("9606", "7157", "NP_000537.3", "-", "-", "-", "?", "-"),
            gene2refseq("63221", "123", "-", "NC_012920.1", "15000", "199", "+", "-"),
            gene2refseq("10090", "22059", "NP_035770.2", "NC_000077.7", "69471184", "69482698", "+", "-"));
    private static final List<String> SUMMARIES = List.of(
            "9606\t7157\tRefSeq\tThis gene encodes a tumor suppressor protein.",
            "10090\t22059\tRefSeq\tA mouse gene.");
    private static final List<String> NODES = List.of(
            "1\t|\t1\t|\tno rank\t|", "10239\t|\t1\t|\tsuperkingdom\t|", "694009\t|\t10239\t|\tspecies\t|",
            "2697049\t|\t694009\t|\tno rank\t|", "9605\t|\t1\t|\tgenus\t|", "9606\t|\t9605\t|\tspecies\t|",
            "63221\t|\t9606\t|\tsubspecies\t|", "10090\t|\t1\t|\tspecies\t|");
    private static final List<String> NAMES = List.of(
            "10239\t|\tViruses\t|\t\t|\tscientific name\t|",
            "2697049\t|\tSevere acute respiratory syndrome coronavirus 2\t|\t\t|\tscientific name\t|",
            "9606\t|\thuman\t|\t\t|\tgenbank common name\t|",
            "9606\t|\tHomo sapiens\t|\t\t|\tscientific name\t|",
            "63221\t|\tHomo sapiens neanderthalensis\t|\t\t|\tscientific name\t|",
            "10090\t|\tMus musculus\t|\t\t|\tscientific name\t|");

    @Test
    @DisplayName("Builds an origin of the taxonomy subtrees, verbatim rows under each file's header, with checksums")
    void buildsAnOrigin(@TempDir Path dir) throws IOException {
        Map<String, Path> served = serve(dir.resolve("served"));
        Path zip = new NcbiGeneBulkOriginBuilder(new FakeNcbi(served)).build(List.of("9606", "10239"),
                dir.resolve("ncbi-gene-origin-20261021.zip"), dir.resolve("cache"), LocalDate.of(2026, 10, 21));

        try (NcbiGeneOrigin origin = NcbiGeneOrigin.open(zip)) {
            OriginManifest manifest = origin.manifest();
            assertEquals(OriginManifest.Format.BULK_FILES, manifest.format());
            assertEquals(LocalDate.of(2026, 10, 21), manifest.releaseDate());
            assertEquals(3, manifest.geneReports(), "the mouse gene is out of scope and NEWENTRY is not a gene");
            assertEquals(List.of("123", "7157"), manifest.taxon("9606").orElseThrow().geneIds(),
                    "a subspecies of human falls under human");
            assertEquals("Homo sapiens", manifest.taxon("9606").orElseThrow().organismName());
            assertEquals(List.of("43740575"), manifest.taxon("10239").orElseThrow().geneIds());
            assertEquals(List.of(new EntrezCheck("9606", NcbiGeneOriginBuilder.SEARCH_TERM.formatted("9606"), 2, 2),
                    new EntrezCheck("10239", NcbiGeneOriginBuilder.SEARCH_TERM.formatted("10239"), 1, 1)),
                    manifest.entrezChecks());

            for (Source source : Source.values()) {
                OriginManifest.SourceFile file = manifest.sources().stream()
                        .filter(sourceFile -> sourceFile.name().equals(source.fileName())).findFirst().orElseThrow();
                assertEquals(sha256(Files.readAllBytes(served.get(source.url()))), file.sha256(), source.fileName());
                assertEquals(Files.size(served.get(source.url())), file.bytes());
                assertEquals("Tue, 20 Oct 2026 05:55:16 GMT", file.lastModified());
            }
            Map<String, Long> rows = new LinkedHashMap<>();
            manifest.extracts().forEach(extract -> rows.put(extract.name(), extract.rows()));
            assertEquals(Map.of(BulkGeneReader.GENE_INFO_FILE, 4L, BulkGeneReader.GENE2REFSEQ_FILE, 6L,
                    BulkGeneReader.GENE_SUMMARY_FILE, 1L, BulkGeneReader.TAXONOMY_NAMES_FILE, 4L), rows);
            try (ZipFile archive = new ZipFile(zip.toFile())) {
                for (Extract extract : manifest.extracts()) {
                    ZipEntry entry = archive.getEntry("ncbi-gene-origin-20261021/" + extract.name());
                    byte[] content;
                    try (InputStream in = archive.getInputStream(entry)) {
                        content = in.readAllBytes();
                    }
                    assertEquals(sha256(content), extract.sha256(), extract.name());
                }
                String geneInfo;
                try (InputStream in = archive.getInputStream(
                        archive.getEntry("ncbi-gene-origin-20261021/" + BulkGeneReader.GENE_INFO_FILE))) {
                    geneInfo = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                assertEquals(GENE_INFO_HEADER + "\n" + GENE_INFO.get(0) + "\n" + GENE_INFO.get(1) + "\n" + GENE_INFO.get(2)
                        + "\n" + GENE_INFO.get(4) + "\n", geneInfo, "rows are kept verbatim, in file order");
            }
            assertEquals(3, origin.reports().size());
        }
    }

    @Test
    @DisplayName("Reads genes: positions one-based, repeats folded, RefSeqGene and origin-crossing locations left out")
    void readsGenes(@TempDir Path dir) throws IOException {
        Map<String, Path> served = serve(dir.resolve("served"));
        Path origin = new NcbiGeneBulkOriginBuilder(new FakeNcbi(served)).build(List.of("9606", "10239"),
                dir.resolve("origin"), dir.resolve("cache"), LocalDate.of(2026, 10, 21));
        Map<String, GeneReport> genes = new LinkedHashMap<>();
        try (NcbiGeneOrigin opened = NcbiGeneOrigin.open(origin)) {
            opened.reports().forEach(gene -> genes.put(gene.geneId(), gene));
        }
        assertEquals(List.of("43740575", "7157", "123"), List.copyOf(genes.keySet()));

        GeneReport n = genes.get("43740575");
        assertEquals("N", n.symbol());
        assertEquals("nucleocapsid phosphoprotein", n.description());
        assertEquals("Severe acute respiratory syndrome coronavirus 2", n.taxname());
        assertEquals(GeneType.PROTEIN_CODING, n.type());
        assertEquals("GU280_gp10", n.locusTag());
        assertEquals(Optional.of(Orientation.PLUS), n.orientation());
        assertEquals(List.of(new Annotation("", "", "", Optional.empty(), List.of(
                new GenomicLocation("NC_045512.2", "", 28274, 29533, Optional.of(Orientation.PLUS))))), n.annotations(),
                "the same location on two protein rows is one location, positions one-based as Datasets gives them");
        assertEquals(List.of("NC_045512.2"), n.refSeqAccessions());

        GeneReport tp53 = genes.get("7157");
        assertEquals(Optional.of("This gene encodes a tumor suppressor protein."), tp53.summary());
        assertEquals(List.of("Reference GRCh38.p14 Primary Assembly", "Alternate T2T-CHM13v2.0"),
                tp53.annotations().stream().map(Annotation::assemblyName).toList(), "one annotation per assembly");
        assertEquals(List.of("NC_000017.11", "NC_060941.1"), tp53.refSeqAccessions(), "the RefSeqGene record is no place");
        assertEquals(new GenomicLocation("NC_000017.11", "", 7661779, 7687538, Optional.of(Orientation.MINUS)),
                tp53.annotations().getFirst().genomicLocations().getFirst());

        GeneReport x1 = genes.get("123");
        assertEquals("Homo sapiens neanderthalensis", x1.taxname());
        assertEquals("", x1.description(), "- is read as absent");
        assertEquals(GeneType.NC_RNA, x1.type());
        assertEquals(List.of(), x1.annotations(), "a location crossing a circular sequence's origin is left out");
    }

    @Test
    @DisplayName("Reuses a cached file the server still serves, and refuses a taxon NCBI Taxonomy lacks")
    void reusesTheCacheAndRefusesAnUnknownTaxon(@TempDir Path dir) throws IOException {
        Map<String, Path> served = serve(dir.resolve("served"));
        FakeNcbi ncbi = new FakeNcbi(served);
        NcbiGeneBulkOriginBuilder builder = new NcbiGeneBulkOriginBuilder(ncbi);
        builder.build(List.of("10239"), dir.resolve("one"), dir.resolve("cache"), LocalDate.of(2026, 10, 21));
        assertThrows(ImportException.class, () -> new NcbiGeneBulkOriginBuilder(ncbi).build(List.of("424242"),
                dir.resolve("two"), dir.resolve("cache"), LocalDate.of(2026, 10, 21)));
        assertThrows(ImportException.class, () -> new NcbiGeneBulkOriginBuilder(ncbi).build(List.of("Viruses"),
                dir.resolve("three"), dir.resolve("cache"), LocalDate.of(2026, 10, 21)));
        assertTrue(Files.isRegularFile(dir.resolve("cache").resolve(Source.GENE_INFO.fileName())));
    }

    @Test
    @DisplayName("A bulk-file manifest round-trips, and a Datasets manifest keeps its old shape")
    void manifestsRoundTrip(@TempDir Path dir) throws IOException {
        Path origin = new NcbiGeneBulkOriginBuilder(new FakeNcbi(serve(dir.resolve("served")))).build(List.of("9606"),
                dir.resolve("origin"), dir.resolve("cache"), LocalDate.of(2026, 10, 21));
        JsonNode json = MAPPER.readTree(origin.resolve(NcbiGeneOrigin.MANIFEST_FILE).toFile());
        OriginManifest manifest = OriginManifest.parse(json);
        assertEquals(MAPPER.writeValueAsString(json), MAPPER.writeValueAsString(manifest.toJson()));
        assertEquals("ncbi-gene-origin/2", json.path("format").asText());

        OriginManifest datasets = OriginManifest.ofDatasetsReports(manifest.retrievedAt(), manifest.releaseDate(),
                manifest.taxa(), 2, List.of(), List.of("https://example.org/one"));
        assertTrue(datasets.toJson().path("sources").isMissingNode(), "a Datasets manifest records no files");
        assertEquals(datasets, OriginManifest.parse(datasets.toJson()));
    }

    /** Writes the served files: three gzip files and a tar.gz holding nodes.dmp and names.dmp. */
    private static Map<String, Path> serve(Path dir) throws IOException {
        Files.createDirectories(dir);
        Map<String, Path> served = new LinkedHashMap<>();
        served.put(Source.GENE_INFO.url(), gzip(dir.resolve("gene_info.gz"), GENE_INFO_HEADER, GENE_INFO));
        served.put(Source.GENE2REFSEQ.url(), gzip(dir.resolve("gene2refseq.gz"), GENE2REFSEQ_HEADER, GENE2REFSEQ));
        served.put(Source.GENE_SUMMARY.url(), gzip(dir.resolve("gene_summary.gz"), SUMMARY_HEADER, SUMMARIES));
        Path taxdump = dir.resolve("taxdump.tar.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(taxdump))) {
            tarEntry(out, "citations.dmp", "");
            tarEntry(out, "names.dmp", String.join("\n", NAMES) + "\n");
            tarEntry(out, "nodes.dmp", String.join("\n", NODES) + "\n");
            out.write(new byte[1024]);
        }
        served.put(Source.TAXONOMY.url(), taxdump);
        return served;
    }

    private static Path gzip(Path file, String header, List<String> rows) throws IOException {
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write((header + "\n" + String.join("\n", rows) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    /** One file of a POSIX tar: a 512-byte header, the content, padding to 512. */
    private static void tarEntry(OutputStream out, String name, String content) throws IOException {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        byte[] header = new byte[512];
        put(header, 0, name);
        put(header, 100, "0000644");
        put(header, 124, String.format("%011o", data.length));
        put(header, 136, String.format("%011o", 0));
        header[156] = '0';
        put(header, 257, "ustar");
        out.write(header);
        out.write(data);
        int padding = (512 - data.length % 512) % 512;
        out.write(new byte[padding]);
    }

    private static void put(byte[] header, int offset, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, header, offset, bytes.length);
    }

    private static String geneInfo(String taxId, String geneId, String symbol, String locusTag, String description,
                                   String type) {
        return String.join("\t", taxId, geneId, symbol, locusTag, "-", "-", "-", "-", description, type, "-", "-",
                "-", "-", "20261019", "-");
    }

    private static String gene2refseq(String taxId, String geneId, String protein, String genomic, String start,
                                      String end, String orientation, String assembly) {
        return String.join("\t", taxId, geneId, "REVIEWED", "-", "-", protein, "-", genomic, "-", start, end,
                orientation, assembly, "-", "-", "X");
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** NCBI served from local files: every file dated the same, Entrez counting the fixture's genes. */
    private static final class FakeNcbi implements NcbiGeneBulkOriginBuilder.Fetcher {
        private final Map<String, Path> served;

        FakeNcbi(Map<String, Path> served) {
            this.served = served;
        }

        @Override
        public Fetched fetch(String url, Path file) {
            try {
                Files.copy(served.get(url), file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new ImportException("Cannot serve " + url, e);
            }
            return new Fetched(file, "Tue, 20 Oct 2026 05:55:16 GMT");
        }

        @Override
        public JsonNode get(String url) {
            String term = URLDecoder.decode(url.substring(url.indexOf("term=") + 5), StandardCharsets.UTF_8);
            long count = term.startsWith("txid9606[") ? 2 : term.startsWith("txid10239[") ? 1 : 0;
            try {
                return MAPPER.readTree("{\"esearchresult\": {\"count\": \"" + count + "\"}}");
            } catch (IOException e) {
                throw new ImportException("Cannot answer " + url, e);
            }
        }
    }
}
