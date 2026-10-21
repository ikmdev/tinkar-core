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
import dev.ikm.tinkar.importer.ncbi.GeneReport.GeneType;
import dev.ikm.tinkar.importer.ncbi.GeneReport.GenomicLocation;
import dev.ikm.tinkar.importer.ncbi.GeneReport.Orientation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading NCBI Datasets gene reports as returned on 2026-10-20.
 */
class GeneReportTest {

    /** SARS-CoV-2 N, as {@code /gene/id/43740575} returned it, with its summary shortened. */
    static final String SARS_COV_2_N = """
            {"gene": {"gene_id": "43740575", "symbol": "N", "description": "nucleocapsid phosphoprotein",
             "tax_id": "2697049", "taxname": "Severe acute respiratory syndrome coronavirus 2",
             "type": "PROTEIN_CODING", "orientation": "plus", "swiss_prot_accessions": ["P0DTC9"],
             "annotations": [{"assembly_accession": "GCF_009858895.2", "assembly_name": "ASM985889v3",
               "annotation_name": "Annotation submitted by NCBI RefSeq", "annotation_release_date": "2020-07-18",
               "genomic_locations": [{"genomic_accession_version": "NC_045512.2", "sequence_name": "ANONYMOUS",
                 "genomic_range": {"begin": "28274", "end": "29533", "orientation": "plus"}}]}],
             "protein_count": 1,
             "summary": [{"description": "The nucleocapsid phosphoprotein is a structural protein that binds to the viral RNA genome."}],
             "gene_ontology": {}, "locus_tag": "GU280_gp10"},
             "query": ["43740575"]}
            """;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("A report element reads to its gene, organism, type, strand, locus tag and summary")
    void readsAReport() throws Exception {
        GeneReport gene = GeneReport.parse(MAPPER.readTree(SARS_COV_2_N));
        assertEquals("43740575", gene.geneId());
        assertEquals("N", gene.symbol());
        assertEquals("nucleocapsid phosphoprotein", gene.description());
        assertEquals("2697049", gene.taxId());
        assertEquals("Severe acute respiratory syndrome coronavirus 2", gene.taxname());
        assertEquals(GeneType.PROTEIN_CODING, gene.type());
        assertEquals(Optional.of(Orientation.PLUS), gene.orientation());
        assertEquals("GU280_gp10", gene.locusTag());
        assertEquals(List.of("P0DTC9"), gene.swissProtAccessions());
        assertTrue(gene.summary().orElseThrow().startsWith("The nucleocapsid phosphoprotein"));
    }

    @Test
    @DisplayName("An annotation places the gene on a RefSeq sequence, one-based and inclusive")
    void readsGenomicLocations() throws Exception {
        GeneReport gene = GeneReport.parse(MAPPER.readTree(SARS_COV_2_N));
        assertEquals(1, gene.annotations().size());
        GeneReport.Annotation annotation = gene.annotations().getFirst();
        assertEquals("GCF_009858895.2", annotation.assemblyAccession());
        assertEquals(Optional.of(LocalDate.of(2020, 7, 18)), annotation.annotationReleaseDate());
        assertEquals(List.of(new GenomicLocation("NC_045512.2", "ANONYMOUS", 28274, 29533, Optional.of(Orientation.PLUS))),
                annotation.genomicLocations());
        assertEquals(List.of("NC_045512.2"), gene.refSeqAccessions());
    }

    @Test
    @DisplayName("The gene object alone reads too, and absent fields read as empty")
    void readsABareGene() throws Exception {
        GeneReport gene = GeneReport.parse(MAPPER.readTree("""
                {"gene_id": "1", "symbol": "X", "tax_id": "2", "taxname": "An organism", "type": "tRNA"}
                """));
        assertEquals(GeneType.T_RNA, gene.type());
        assertEquals(Optional.empty(), gene.orientation());
        assertEquals("", gene.description());
        assertEquals("", gene.locusTag());
        assertEquals(Optional.empty(), gene.summary());
        assertEquals(List.of(), gene.annotations());
        assertEquals(List.of(), gene.refSeqAccessions());
    }

    @Test
    @DisplayName("A type this importer does not know is kept as unrecognized, not an error")
    void keepsAnUnknownType() {
        assertEquals(GeneType.UNRECOGNIZED, GeneType.named("NEW_KIND"));
        assertEquals(GeneType.UNKNOWN, GeneType.named(""));
        assertEquals(GeneType.NC_RNA, GeneType.named("ncRNA"));
    }

    @Test
    @DisplayName("Orientation is plus or minus, and nothing else")
    void readsOrientation() {
        assertEquals(Optional.of(Orientation.MINUS), Orientation.named("minus"));
        assertEquals(Optional.of(Orientation.PLUS), Orientation.named(" Plus "));
        assertEquals(Optional.empty(), Orientation.named("both"));
        assertEquals(Optional.empty(), Orientation.named(null));
    }

    @Test
    @DisplayName("A report without a gene ID, or with a range that ends before it begins, refuses")
    void refusesBrokenReports() throws Exception {
        assertThrows(ImportException.class, () -> GeneReport.parse(MAPPER.readTree("{\"gene\": {\"symbol\": \"N\"}}")));
        assertThrows(ImportException.class, () -> GeneReport.parse(MAPPER.readTree("""
                {"gene_id": "1", "symbol": "X", "tax_id": "2", "taxname": "An organism",
                 "annotations": [{"assembly_accession": "GCF_1", "genomic_locations": [
                   {"genomic_accession_version": "NC_1.1", "genomic_range": {"begin": "10", "end": "5"}}]}]}
                """)));
    }
}
