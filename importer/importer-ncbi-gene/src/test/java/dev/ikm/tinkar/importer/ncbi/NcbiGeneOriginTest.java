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

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ikm.tinkar.importer.ImportException;
import dev.ikm.tinkar.importer.ncbi.OriginManifest.ScopedTaxon;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Reading an origin from a directory and from a zip, and round-tripping its manifest.
 */
class NcbiGeneOriginTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final OriginManifest MANIFEST = OriginManifest.ofDatasetsReports(
            Instant.parse("2026-10-20T18:00:00Z"), LocalDate.of(2026, 10, 20),
            List.of(new ScopedTaxon("2697049", "Severe acute respiratory syndrome coronavirus 2",
                            "txid2697049[Organism:exp] AND alive[prop]", 1, List.of("43740575")),
                    new ScopedTaxon("11320", "Influenza A virus", "txid11320[Organism:exp] AND alive[prop]", 1,
                            List.of("956536"))),
            2, List.of(), List.of("https://example.org/one", "https://example.org/two"));

    private static final String PB2 = """
            {"gene": {"gene_id": "956536", "symbol": "PB2", "description": "polymerase PB2", "tax_id": "211044",
             "taxname": "Influenza A virus (A/Puerto Rico/8/1934(H1N1))", "type": "PROTEIN_CODING", "orientation": "plus",
             "annotations": [{"assembly_accession": "GCF_000865725.1", "genomic_locations": [
               {"genomic_accession_version": "NC_002023.1", "sequence_name": "1",
                "genomic_range": {"begin": "28", "end": "2307", "orientation": "plus"}}]}],
             "locus_tag": "FLUAVs1gp1"}, "query": ["956536"]}""";

    @Test
    @DisplayName("A directory holding the two files, at any depth, reads in file order")
    void readsADirectory(@TempDir Path dir) throws IOException {
        Path origin = writeOrigin(dir.resolve("ncbi-gene-origin-20261020"), "nested/deeper");
        try (NcbiGeneOrigin opened = NcbiGeneOrigin.open(origin)) {
            assertEquals("ncbi-gene-origin-20261020", opened.name());
            assertEquals(Optional.of(LocalDate.of(2026, 10, 20)), opened.dateInName());
            assertEquals(MANIFEST, opened.manifest());
            List<GeneReport> reports = opened.reports();
            assertEquals(List.of("43740575", "956536"), reports.stream().map(GeneReport::geneId).toList());
            assertEquals(Set.of("956536"), opened.manifest().geneIdsOf(List.of("11320")));
        }
    }

    @Test
    @DisplayName("A zip holding the two files reads the same")
    void readsAZip(@TempDir Path dir) throws IOException {
        Path origin = writeOrigin(dir.resolve("unpacked"), "");
        Path zip = dir.resolve("ncbi-gene-origin-20261020.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (String name : List.of(NcbiGeneOrigin.MANIFEST_FILE, NcbiGeneOrigin.REPORTS_FILE)) {
                out.putNextEntry(new ZipEntry("ncbi-gene-origin-20261020/" + name));
                out.write(Files.readAllBytes(origin.resolve(name)));
                out.closeEntry();
            }
        }
        try (NcbiGeneOrigin opened = NcbiGeneOrigin.open(zip)) {
            assertEquals(Optional.of(LocalDate.of(2026, 10, 20)), opened.dateInName());
            assertEquals(MANIFEST, opened.manifest());
            assertEquals(2, opened.reports().size());
        }
    }

    @Test
    @DisplayName("A file found twice, or not at all, refuses")
    void refusesAmbiguity(@TempDir Path dir) throws IOException {
        Path origin = writeOrigin(dir.resolve("twice"), "");
        Files.copy(origin.resolve(NcbiGeneOrigin.MANIFEST_FILE), Files.createDirectories(origin.resolve("copy")).resolve(NcbiGeneOrigin.MANIFEST_FILE));
        assertThrows(ImportException.class, () -> NcbiGeneOrigin.open(origin));
        assertThrows(ImportException.class, () -> NcbiGeneOrigin.open(Files.createDirectories(dir.resolve("empty"))));
    }

    @Test
    @DisplayName("A manifest of another format, or scoped to a taxon one asks for and it lacks, refuses")
    void refusesTheWrongManifest() throws IOException {
        assertThrows(ImportException.class, () -> OriginManifest.parse(MAPPER.readTree("{\"format\": \"other/1\"}")));
        assertThrows(ImportException.class, () -> MANIFEST.geneIdsOf(List.of("9606")));
    }

    private static Path writeOrigin(Path root, String below) throws IOException {
        Path files = Files.createDirectories(below.isEmpty() ? root : root.resolve(below));
        Files.writeString(files.resolve(NcbiGeneOrigin.MANIFEST_FILE),
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(MANIFEST.toJson()), StandardCharsets.UTF_8);
        Files.writeString(files.resolve(NcbiGeneOrigin.REPORTS_FILE),
                MAPPER.writeValueAsString(MAPPER.readTree(GeneReportTest.SARS_COV_2_N)) + "\n"
                        + MAPPER.writeValueAsString(MAPPER.readTree(PB2)) + "\n", StandardCharsets.UTF_8);
        return root;
    }
}
