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
package dev.ikm.tinkar.integration.importer;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.FieldDefinitionForEntity;
import dev.ikm.tinkar.entity.PatternEntityVersion;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.importer.ImportException;
import dev.ikm.tinkar.importer.ImportReport;
import dev.ikm.tinkar.importer.ImportSource;
import dev.ikm.tinkar.importer.KnowledgeImporters;
import dev.ikm.tinkar.importer.StoreWriter;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The NCBI Gene importer over the IKE starter set, on the origin the builder retrieved from NCBI
 * on 2026-10-20 for SARS-CoV-2, influenza A and B, and RSV: 135 gene reports on 11 organisms (the
 * species, and every reference strain NCBI annotates), each gene at one genomic location on one
 * RefSeq sequence (20 of them with no orientation stated). First scoped to SARS-CoV-2, then the
 * whole origin, then replayed.
 *
 * <p>Identity parity: the UUIDs asserted are type 5 UUIDs computed independently of tinkar-core
 * with Python's {@code uuid.uuid5}, in the importer's default namespace
 * ({@code uuid5(NAMESPACE_URL, "https://www.ncbi.nlm.nih.gov/gene")}) from the names the
 * importer documents ({@code gene|43740575}, {@code taxon|2697049}, {@code shared|gene},
 * {@code shared|genomic location}), and from each concept's UUID for its semantics ({@code fqn},
 * {@code ncbi gene id}, {@code refseq accession|NC_045512.2}, {@code stated axioms},
 * {@code genomic location|NC_045512.2|28274|29533}).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NcbiGeneImporterIT {

    private static final String ORIGIN = "/ncbi-gene/ncbi-gene-origin-20261020";

    // The importer's own concepts.
    private static final UUID MODULE = UUID.fromString("e9fa005b-3acc-513f-a673-f89aff53b346");
    private static final UUID AUTHOR = UUID.fromString("13f779b7-6e35-5bef-a76b-082fb9adbef0");
    private static final UUID GENE_ROOT = UUID.fromString("99698f8c-763f-5890-a99f-f233a76f8aff");
    private static final UUID ORGANISM_ROOT = UUID.fromString("7b438eb0-adcd-520e-a4d6-70d991067b61");
    private static final UUID ORGANISM_ROLE = UUID.fromString("0c68a30c-57b1-5637-9ebb-7d44226d336b");
    private static final UUID GENE_ID_SOURCE = UUID.fromString("f1a5b921-af30-5a7c-8a55-8a65d9888b46");
    private static final UUID TAXONOMY_ID_SOURCE = UUID.fromString("34ab9b7d-5b21-5867-b15e-968e8500597f");
    private static final UUID REFSEQ_SOURCE = UUID.fromString("2d192bd5-e5aa-5f0b-8829-62ed911420ca");
    private static final UUID STRAND = UUID.fromString("e98cab30-2a67-5e16-9a52-a4d245834a03");
    private static final UUID PLUS_STRAND = UUID.fromString("4b4ecf22-3cf7-5c44-a78a-dbdd5305b50a");
    private static final UUID MINUS_STRAND = UUID.fromString("9a9ee14a-d506-5f9d-810d-cda5507a9ba5");
    private static final UUID UNSTATED_STRAND = UUID.fromString("45f7a451-142d-57a6-a087-c419fdbc71f4");

    // The genomic location pattern, and the concepts giving it and its fields a meaning and a purpose.
    private static final UUID GENOMIC_LOCATION_PATTERN = UUID.fromString("34d5d880-3d3d-5838-8373-1588da043ae1");
    private static final UUID GENOMIC_LOCATION_FQN = UUID.fromString("f09abcb4-b898-5cc3-9b1c-17b97ad7f767");
    private static final UUID LOCATED_GENE = UUID.fromString("126f9d8f-e85f-54b9-91f6-c07528109cca");
    private static final UUID GENOMIC_PLACEMENT = UUID.fromString("65c8ec3b-6c3e-5415-b2a3-543a880c04f7");
    private static final UUID REFSEQ_SEQUENCE = UUID.fromString("beeeebca-7fd7-59b3-bf6c-1ce743fc5f5f");
    private static final UUID SEQUENCE_IDENTITY = UUID.fromString("a84a4b08-a3a3-5a0f-96ef-262662111645");
    private static final UUID RANGE_BEGIN = UUID.fromString("5fa12cea-9273-582f-8200-7d26c7f954d3");
    private static final UUID LOWER_BOUND = UUID.fromString("20d54260-a31c-5280-af26-f9c6db65bbf0");
    private static final UUID RANGE_END = UUID.fromString("c9beb3bf-a3e6-5942-923f-f41a8e549b63");
    private static final UUID UPPER_BOUND = UUID.fromString("6f40a466-24eb-5a02-b1ff-a2af3181a15c");
    private static final UUID READING_DIRECTION = UUID.fromString("2ea56d58-dd49-51ce-9c0c-67feef8484de");

    // SARS-CoV-2 N, gene 43740575 on NC_045512.2, organism 2697049.
    private static final UUID N_CONCEPT = UUID.fromString("b8bc745c-bbdf-5931-92bc-105b858c52b7");
    private static final UUID N_FQN = UUID.fromString("dafe4c4d-9e1d-5e58-b95b-f42d74a6ee93");
    private static final UUID N_NAME = UUID.fromString("0fa9666e-3141-509b-915e-eb682fcdb62c");
    private static final UUID N_DESCRIPTION = UUID.fromString("89257136-390f-5e6e-adc8-2d69103f06af");
    private static final UUID N_DEFINITION = UUID.fromString("f6762acb-583f-5a77-80db-9d6d461da43f");
    private static final UUID N_GENE_ID = UUID.fromString("08295545-73de-550b-b922-7626958cb259");
    private static final UUID N_REFSEQ = UUID.fromString("094e6481-1b0b-511f-b067-5598b8188e97");
    private static final UUID N_AXIOMS = UUID.fromString("e6d64fbc-9360-5915-921c-db2fe2293548");
    private static final UUID N_LOCATION = UUID.fromString("5b919c72-8f73-51a1-a533-cd65db60ebc6");
    private static final UUID SARS_COV_2 = UUID.fromString("6e277f3f-5e9c-5378-b059-4509531c31c6");
    private static final UUID SARS_COV_2_NAME = UUID.fromString("e068633a-3340-59b9-88b3-5b1144382309");
    private static final UUID SARS_COV_2_TAXONOMY_ID = UUID.fromString("ee9dede6-d0ea-5fdf-973c-1e60c8a95994");
    private static final UUID SARS_COV_2_AXIOMS = UUID.fromString("258ff01c-5144-5895-916f-45f19c7349bb");

    // Influenza A/Puerto Rico/8/1934(H1N1), organism 211044: PB2 (956536, NC_002023.1) and PA (956535).
    private static final UUID PB2_CONCEPT = UUID.fromString("fd25631f-eb69-50df-ab30-b9922fb6dc55");
    private static final UUID PB2_REFSEQ = UUID.fromString("58254970-3ad5-5008-9bab-f91ec9e14ce0");
    private static final UUID PA_CONCEPT = UUID.fromString("4f18eca2-839e-539f-89d8-4a336f616448");
    private static final UUID PR8 = UUID.fromString("786c6f20-ca88-5564-bb5b-bcab992dde2c");

    // RSV, organism 11250, which NCBI files two reference genomes under: N on NC_001781.1 and on NC_038235.1.
    private static final UUID RSV_N_1781_FQN = UUID.fromString("f9989295-1a58-5808-80ab-04ad14aee88b");
    private static final UUID RSV_N_38235_FQN = UUID.fromString("a4a50e09-acb6-588e-ba89-9998184da8dd");

    // RSV A, organism 208893: N (1494470) on NC_001803.1 1125-2329, a location NCBI states no orientation for.
    private static final UUID RSV_A_N_CONCEPT = UUID.fromString("5deeb00e-f914-5673-b7f4-b53799b75a55");
    private static final UUID RSV_A_N_LOCATION = UUID.fromString("ce366341-19f0-505a-b43e-366c90ae1591");

    private Path origin;

    @BeforeAll
    void startStore() throws URISyntaxException {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
        origin = Path.of(assertNotNullAndGet(NcbiGeneImporterIT.class.getResource(ORIGIN)).toURI());
    }

    @AfterAll
    void stopStore() {
        TestHelper.stopDatabase();
    }

    @Test
    @Order(1)
    void importsTheGenesOfOneTaxon() {
        ImportReport report = inLoadPhase(origin, Map.of("taxa", "2697049"));
        assertEquals("ncbi-gene-origin-20261020", report.sourceVersion());
        assertEquals(Optional.of(Instant.parse("2026-10-20T00:00:00Z")), report.releaseDate());
        assertTrue(report.written() > 0, report.toString());
        Map<String, Long> details = report.details();
        assertEquals(22L, details.get("shared concepts"), report.toString());
        assertEquals(1L, details.get("organisms"), report.toString());
        assertEquals(11L, details.get("genes"), report.toString());
        assertEquals(21L, details.get("gene names"), "11 symbols and 10 descriptions: ORF10's description is its symbol");
        assertEquals(11L, details.get("gene definitions"), "every SARS-CoV-2 gene has a RefSeq summary");
        assertEquals(11L, details.get("NCBI Gene identifiers"), report.toString());
        assertEquals(11L, details.get("RefSeq accession identifiers"), report.toString());
        assertEquals(11L, details.get("genomic locations"), "each SARS-CoV-2 gene at one location");
        assertEquals(124L, details.get("genes out of scope"), report.toString());
        assertFalse(PrimitiveData.get().hasPublicId(PublicIds.of(PB2_CONCEPT)), "an influenza gene is out of scope");
    }

    @Test
    @Order(2)
    void mintsTheDocumentedIdentities() {
        for (UUID concept : List.of(MODULE, AUTHOR, GENE_ROOT, ORGANISM_ROOT, ORGANISM_ROLE, GENE_ID_SOURCE,
                TAXONOMY_ID_SOURCE, REFSEQ_SOURCE, STRAND, PLUS_STRAND, MINUS_STRAND, UNSTATED_STRAND, LOCATED_GENE,
                GENOMIC_PLACEMENT,
                REFSEQ_SEQUENCE, SEQUENCE_IDENTITY, RANGE_BEGIN, LOWER_BOUND, RANGE_END, UPPER_BOUND, READING_DIRECTION,
                N_CONCEPT, SARS_COV_2)) {
            assertTrue(PrimitiveData.get().hasPublicId(PublicIds.of(concept)), "concept " + concept);
        }
        assertTrue(conceptsIn(latest(t5("stated axioms", PLUS_STRAND))).contains(nid(STRAND)), "a strand is under Strand");
        assertTrue(conceptsIn(latest(t5("stated axioms", MINUS_STRAND))).contains(nid(STRAND)), "a strand is under Strand");
        assertTrue(conceptsIn(latest(t5("stated axioms", UNSTATED_STRAND))).contains(nid(STRAND)), "so is the unstated one");
        long n = nid(N_CONCEPT);
        assertEquals(n, semantic(N_FQN).referencedComponentNid());
        assertEquals("N gene on NC_045512.2 (Severe acute respiratory syndrome coronavirus 2)",
                latest(N_FQN).fieldValues().get(1));
        assertEquals("N", latest(N_NAME).fieldValues().get(1));
        assertEquals("nucleocapsid phosphoprotein", latest(N_DESCRIPTION).fieldValues().get(1));
        assertTrue(((String) latest(N_DEFINITION).fieldValues().get(1)).contains("nucleocapsid"), "NCBI's summary");

        SemanticEntityVersion geneId = latest(N_GENE_ID);
        assertEquals(n, semantic(N_GENE_ID).referencedComponentNid());
        assertEquals(nid(KernelPattern.IDENTIFIER_PATTERN.publicId()), semantic(N_GENE_ID).patternNid());
        assertEquals(nid(GENE_ID_SOURCE), ((EntityFacade) geneId.fieldValues().get(0)).nid());
        assertEquals("43740575", geneId.fieldValues().get(1));
        SemanticEntityVersion refSeq = latest(N_REFSEQ);
        assertEquals(nid(REFSEQ_SOURCE), ((EntityFacade) refSeq.fieldValues().get(0)).nid());
        assertEquals("NC_045512.2", refSeq.fieldValues().get(1));

        List<Long> definition = conceptsIn(latest(N_AXIOMS));
        assertEquals(n, semantic(N_AXIOMS).referencedComponentNid());
        assertTrue(definition.contains(nid(GENE_ROOT)), "a kind of Gene");
        assertTrue(definition.contains(nid(ORGANISM_ROLE)), "with the organism role");
        assertTrue(definition.contains(nid(SARS_COV_2)), "filled by its organism");

        assertEquals("Severe acute respiratory syndrome coronavirus 2", latest(SARS_COV_2_NAME).fieldValues().get(1));

        SemanticEntityVersion location = latest(N_LOCATION);
        assertEquals(n, semantic(N_LOCATION).referencedComponentNid());
        assertEquals(nid(GENOMIC_LOCATION_PATTERN), semantic(N_LOCATION).patternNid());
        assertEquals(List.of("NC_045512.2", 28274, 29533), location.fieldValues().subList(0, 3).castToList());
        assertEquals(nid(PLUS_STRAND), ((EntityFacade) location.fieldValues().get(3)).nid(), "N is read from the plus strand");
        SemanticEntityVersion taxonomyId = latest(SARS_COV_2_TAXONOMY_ID);
        assertEquals(nid(TAXONOMY_ID_SOURCE), ((EntityFacade) taxonomyId.fieldValues().get(0)).nid());
        assertEquals("2697049", taxonomyId.fieldValues().get(1));
        assertTrue(conceptsIn(latest(SARS_COV_2_AXIOMS)).contains(nid(ORGANISM_ROOT)), "an organism is under Organism");
    }

    @Test
    @Order(3)
    void definesTheGenomicLocationPattern() {
        long patternNid = nid(GENOMIC_LOCATION_PATTERN);
        assertEquals("Genomic location (NCBI)", latest(GENOMIC_LOCATION_FQN).fieldValues().get(1));
        assertEquals(patternNid, semantic(GENOMIC_LOCATION_FQN).referencedComponentNid(), "the pattern is named");
        PatternEntityVersion pattern = Calculators.Stamp.DevelopmentLatest().<PatternEntityVersion>latest(patternNid).get();
        assertEquals(nid(LOCATED_GENE), pattern.semanticMeaningNid());
        assertEquals(nid(GENOMIC_PLACEMENT), pattern.semanticPurposeNid());
        assertEquals(4, pattern.fieldDefinitions().size());
        assertField(pattern.fieldDefinitions().get(0), 0, REFSEQ_SEQUENCE, SEQUENCE_IDENTITY, KernelConcept.STRING.publicId());
        assertField(pattern.fieldDefinitions().get(1), 1, RANGE_BEGIN, LOWER_BOUND, KernelConcept.INTEGER_FIELD.publicId());
        assertField(pattern.fieldDefinitions().get(2), 2, RANGE_END, UPPER_BOUND, KernelConcept.INTEGER_FIELD.publicId());
        assertField(pattern.fieldDefinitions().get(3), 3, STRAND, READING_DIRECTION, KernelConcept.COMPONENT_FIELD.publicId());
    }

    @Test
    @Order(4)
    void importsTheWholeOrigin() {
        ImportReport report = inLoadPhase(origin, Map.of());
        assertTrue(report.written() > 0, report.toString());
        assertTrue(report.unchanged() > 0, "the SARS-CoV-2 genes are already there: " + report);
        assertEquals(0, report.versioned(), report.toString());
        Map<String, Long> details = report.details();
        assertEquals(11L, details.get("organisms"), "the species and every reference strain: " + report);
        assertEquals(135L, details.get("genes"), report.toString());
        assertEquals(266L, details.get("gene names"), report.toString());
        assertEquals(11L, details.get("gene definitions"), report.toString());
        assertEquals(135L, details.get("RefSeq accession identifiers"), "each gene on one sequence");
        assertEquals(135L, details.get("genomic locations"), "each gene at one location");
        assertEquals(0L, details.get("genes out of scope"), report.toString());

        assertEquals("NC_002023.1", latest(PB2_REFSEQ).fieldValues().get(1));
        assertEquals(nid(PB2_CONCEPT), semantic(PB2_REFSEQ).referencedComponentNid());
        assertTrue(conceptsIn(latest(t5("stated axioms", PA_CONCEPT))).contains(nid(PR8)),
                "PA is a gene of A/Puerto Rico/8/1934(H1N1), the strain NCBI annotates, not of influenza A");
        assertEquals("N gene on NC_001781.1 (human respiratory syncytial virus)", latest(RSV_N_1781_FQN).fieldValues().get(1));
        assertEquals("N gene on NC_038235.1 (human respiratory syncytial virus)", latest(RSV_N_38235_FQN).fieldValues().get(1),
                "the same gene of RSV's two reference genomes, told apart by sequence");

        SemanticEntityVersion unoriented = latest(RSV_A_N_LOCATION);
        assertEquals(nid(RSV_A_N_CONCEPT), semantic(RSV_A_N_LOCATION).referencedComponentNid());
        assertEquals(List.of("NC_001803.1", 1125, 2329), unoriented.fieldValues().subList(0, 3).castToList());
        assertEquals(nid(UNSTATED_STRAND), ((EntityFacade) unoriented.fieldValues().get(3)).nid(),
                "a location NCBI states no orientation for is on the unstated strand");
    }

    @Test
    @Order(5)
    void replayingWritesNothing() {
        ImportReport again = inLoadPhase(origin, Map.of());
        assertEquals(0, again.written(), again.toString());
        assertEquals(0, again.versioned(), again.toString());
        assertTrue(again.unchanged() > 0, again.toString());
    }

    @Test
    @Order(6)
    void refusesATaxonTheOriginLacks() {
        assertThrows(ImportException.class, () -> inLoadPhase(origin, Map.of("taxa", "9606")), "human genes are not in this origin");
        assertThrows(ImportException.class, () -> inLoadPhase(origin, Map.of("taxa", "SARS-CoV-2")));
    }

    /** An import as kb-assemble runs one: inside the bulk-load bracket. */
    private static ImportReport inLoadPhase(Path source, Map<String, String> options) {
        EntityService.get().beginLoadPhase();
        try {
            return KnowledgeImporters.named("ncbi-gene").importInto(new ImportSource(source, options),
                    new StoreWriter(Calculators.Stamp.DevelopmentLatest()));
        } finally {
            EntityService.get().endLoadPhase();
        }
    }

    private static void assertField(FieldDefinitionForEntity field, int index, UUID meaning, UUID purpose, PublicId dataType) {
        assertEquals(index, field.indexInPattern());
        assertEquals(nid(meaning), field.meaningNid(), "meaning of field " + index);
        assertEquals(nid(purpose), field.purposeNid(), "purpose of field " + index);
        assertEquals(nid(dataType), field.dataTypeNid(), "data type of field " + index);
    }

    private static <T> T assertNotNullAndGet(T value) {
        assertNotNull(value, "the origin is a test resource");
        return value;
    }

    private static UUID t5(String name, UUID namespace) {
        return dev.ikm.tinkar.common.util.uuid.UuidT5Generator.get(namespace, name);
    }

    private static long nid(UUID uuid) {
        return nid(PublicIds.of(uuid));
    }

    private static long nid(PublicId id) {
        assertTrue(PrimitiveData.get().hasPublicId(id), "no component " + id);
        return PrimitiveData.nid(id);
    }

    private static SemanticEntity<SemanticEntityVersion> semantic(UUID uuid) {
        return EntityHandle.get(nid(uuid)).expectSemantic();
    }

    private static SemanticEntityVersion latest(UUID semantic) {
        StampCalculator stamps = Calculators.Stamp.DevelopmentLatest();
        return stamps.latest(semantic(semantic)).get();
    }

    /** The concepts a stated axiom's tree refers to: parents, role types and fillers alike. */
    private static List<Long> conceptsIn(SemanticEntityVersion axiom) {
        DiTreeEntity tree = assertInstanceOf(DiTreeEntity.class, axiom.fieldValues().get(0));
        List<Long> concepts = new ArrayList<>();
        collect(tree, tree.root(), concepts);
        return concepts;
    }

    private static void collect(DiTreeEntity tree, EntityVertex vertex, List<Long> concepts) {
        for (Object value : vertex.properties().values()) {
            if (value instanceof EntityFacade facade) {
                concepts.add(facade.nid());
            }
        }
        tree.successors(vertex.vertexIndex()).forEach(child -> collect(tree, tree.vertex(child), concepts));
    }
}
