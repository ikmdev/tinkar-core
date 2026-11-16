/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
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

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.ConceptEntityVersion;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.importer.ImportReport;
import dev.ikm.tinkar.importer.ImportSource;
import dev.ikm.tinkar.importer.KnowledgeImporters;
import dev.ikm.tinkar.importer.StoreWriter;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The LOINC importer over the IKE starter set, on a release small enough to read: seven parts and
 * one the codes do not use, a component hierarchy with a component that has two parents and one the
 * part table lacks, and five codes: an active one with all six axes, a trial, a discouraged and a
 * deprecated one, and one naming a component the part table does not have.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LoincImporterIT {

    private static final UUID NAMESPACE = UUID.fromString("7b880c4c-8e54-5625-863c-c8504fa78243");

    private Path release;

    @BeforeAll
    void startStore(@TempDir Path dir) throws IOException {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
        release = writeRelease(dir.resolve("Loinc_2.99"));
    }

    @AfterAll
    void stopStore() {
        TestHelper.stopDatabase();
    }

    @Test
    @Order(1)
    void importsTheRelease() {
        ImportReport report = importRelease();
        assertEquals("Loinc_2.99", report.sourceVersion());
        assertTrue(report.written() > 0, report.toString());
        assertEquals(7L, report.details().get("parts"), report.toString());
        assertEquals(5L, report.details().get("codes"), report.toString());
        assertEquals(1L, report.details().get("left out: the axiom of a code naming a COMPONENT the part table lacks"),
                report.toString());

        StampCalculator stamps = Calculators.Stamp.DevelopmentLatest();
        long code = nid("718-7");
        Latest<ConceptEntityVersion> active = stamps.latest(code);
        assertTrue(active.isPresent() && active.get().active(), "an active code is active");

        List<SemanticEntityVersion> descriptions = versions(code, KernelPattern.DESCRIPTION_PATTERN.nid(), stamps);
        List<Object> texts = descriptions.stream().map(v -> v.fieldValues().get(1)).map(Object.class::cast).toList();
        assertTrue(texts.contains("Hemoglobin [Mass/volume] in Blood"), "the long common name: " + texts);
        assertTrue(texts.contains("Hgb Bld-mCnc"), "the short name: " + texts);
        assertTrue(texts.contains("a \"quoted\", comma-bearing definition"), "CSV quoting is read: " + texts);

        List<Object> identifiers = versions(code, KernelPattern.IDENTIFIER_PATTERN.nid(), stamps).stream()
                .map(v -> v.fieldValues().get(1)).map(Object.class::cast).toList();
        assertEquals(List.of("718-7"), identifiers, "the LOINC number is an identifier");

        assertEquals(1, versions(code, KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid(), stamps).size(),
                "the six axes, an OWL axiom, become an EL++ stated axiom");
        assertEquals(1, versions(code, pattern("LOINC Class Pattern"), stamps).size(), "the class");
        assertEquals("g/dL", versions(code, pattern("Example UCUM Units Pattern"), stamps).getFirst().fieldValues().getFirst());
        assertEquals(1, versions(code, pattern("Test Orderable Membership Pattern"), stamps).size(), "both: orderable");
        assertEquals(1, versions(code, pattern("Test Reportable Membership Pattern"), stamps).size(), "both: reportable");
    }

    @Test
    @Order(2)
    void statusIsRead() {
        StampCalculator stamps = Calculators.Stamp.DevelopmentLatest();
        assertEquals(1, versions(nid("99999-9"), pattern("LOINC Trial Status Pattern"), stamps).size(), "a trial code");
        assertEquals(1, versions(nid("33333-3"), pattern("LOINC Discouraged Status Pattern"), stamps).size(), "a discouraged code");
        Latest<ConceptEntityVersion> deprecated = stamps.latest(nid("11111-1"));
        assertTrue(deprecated.isPresent());
        assertFalse(deprecated.get().active(), "a deprecated code is inactive");
    }

    @Test
    @Order(3)
    void componentsTakeTheirHierarchyParents() {
        StampCalculator stamps = Calculators.Stamp.DevelopmentLatest();
        // Hemoglobin states two parents in the hierarchy: one stated axiom naming both.
        assertEquals(1, versions(nid("LP1000-1"), KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid(), stamps).size());
        assertTrue(PrimitiveData.publicId(nid("LP432695-7")).asUuidList().contains(UuidT5Generator.get(NAMESPACE, "Component")),
                "the hierarchy's root joins the Component the starter data wrote");
        assertTrue(EntityService.get().semanticsForComponentOfPattern(nid("LP2000-2"), KernelPattern.IDENTIFIER_PATTERN.nid())
                .findAny().isPresent(), "a component the part table lacks is a concept of its own");
    }

    @Test
    @Order(4)
    void aPartIsAKindOfItsType() {
        StampCalculator stamps = Calculators.Stamp.DevelopmentLatest();
        assertEquals(1, versions(nid("LP2"), KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid(), stamps).size());
        assertThrows(IllegalStateException.class, () -> nid("LP9"),
                "a part of a type the codes do not use is left out: the store has no such component");
    }

    @Test
    @Order(5)
    void starterDataIsALayerOfItsOwn() {
        StampCalculator stamps = Calculators.Stamp.DevelopmentLatest();
        long starterAuthor = nid("LOINC Starter Data Author");
        long releaseAuthor = nid("Regenstrief Institute, Inc. Author");
        // A starter concept under Attribute (an OWL sub-property axiom) and one under another parent (stated directly):
        // each has its parent as exactly one stated axiom, written by the starter author on the Primordial module and path.
        for (String name : List.of("Component", "Test Orderable", "Observable Entity", "LOINC Module")) {
            List<SemanticEntityVersion> axioms = versions(nid(name), KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid(), stamps);
            assertEquals(1, axioms.size(), name + " has its parent as one stated axiom");
            assertEquals(starterAuthor, axioms.getFirst().authorNid(), name + ": the starter data author");
            assertEquals(KernelConcept.PRIMORDIAL_MODULE.nid(), axioms.getFirst().moduleNid(), name + ": the Primordial module");
            assertEquals(KernelConcept.PRIMORDIAL_PATH.nid(), axioms.getFirst().pathNid(), name + ": the Primordial path");
            assertEquals(PrimitiveData.PRE_INCEPTION_TIME, axioms.getFirst().time(), name + ": the Primordial time");
            Latest<ConceptEntityVersion> concept = stamps.latest(nid(name));
            assertEquals(starterAuthor, concept.get().authorNid(), name + " concept: the starter data author");
        }
        // The release is the release author's, on the LOINC module and the development path.
        List<SemanticEntityVersion> release = versions(nid("718-7"), KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid(), stamps);
        assertEquals(releaseAuthor, release.getFirst().authorNid());
        assertEquals(KernelConcept.DEVELOPMENT_PATH.nid(), release.getFirst().pathNid());
        assertEquals(nid("LOINC Module"), release.getFirst().moduleNid());
        // A pattern the starter data wrote is the starter author's too.
        assertEquals(starterAuthor, stamps.latest(pattern("LOINC Class Pattern")).get().authorNid());
    }

    @Test
    @Order(6)
    void replayingWritesNothing() {
        ImportReport again = importRelease();
        assertEquals(0, again.written(), again.toString());
        assertEquals(0, again.versioned(), again.toString());
        assertTrue(again.unchanged() > 0, again.toString());
    }

    private ImportReport importRelease() {
        EntityService.get().beginLoadPhase();
        try {
            return KnowledgeImporters.named("loinc").importInto(new ImportSource(release, Map.of("releaseDate", "2026-03-21")),
                    new StoreWriter(Calculators.Stamp.DevelopmentLatest()));
        } finally {
            EntityService.get().endLoadPhase();
        }
    }

    private static long nid(String name) {
        return PrimitiveData.nid(PublicIds.of(UuidT5Generator.get(NAMESPACE, name)));
    }

    private static long pattern(String name) {
        return PrimitiveData.nid(PublicIds.of(UuidT5Generator.get(NAMESPACE, name)));
    }

    private static List<SemanticEntityVersion> versions(long componentNid, long patternNid, StampCalculator stamps) {
        return EntityService.get().semanticsForComponentOfPattern(componentNid, patternNid)
                .map(semantic -> stamps.latest((SemanticEntity<SemanticEntityVersion>) semantic))
                .filter(Latest::isPresent).map(Latest::get).toList();
    }

    private static Path writeRelease(Path root) throws IOException {
        Files.createDirectories(root.resolve("AccessoryFiles/PartFile"));
        Files.createDirectories(root.resolve("AccessoryFiles/ComponentHierarchyBySystem"));
        Files.createDirectories(root.resolve("LoincTable"));
        write(root.resolve("AccessoryFiles/PartFile/Part.csv"),
                "\"PartNumber\",\"PartTypeName\",\"PartName\",\"PartDisplayName\",\"Status\"",
                "\"LP1000-1\",\"COMPONENT\",\"Hemoglobin\",\"Hemoglobin\",\"ACTIVE\"",
                "\"LP2\",\"PROPERTY\",\"MCnc\",\"Mass concentration\",\"ACTIVE\"",
                "\"LP3\",\"TIME\",\"Pt\",\"Point in time (spot)\",\"ACTIVE\"",
                "\"LP4\",\"SYSTEM\",\"Bld\",\"Blood\",\"ACTIVE\"",
                "\"LP5\",\"SCALE\",\"Qn\",\"Quantitative\",\"ACTIVE\"",
                "\"LP6\",\"CLASS\",\"HEM/BC\",\"Hematology/Cell counts\",\"ACTIVE\"",
                "\"LP7\",\"METHOD\",\"Automated count\",\"Automated count\",\"ACTIVE\"",
                "\"LP9\",\"ADJUSTMENT\",\"adjusted\",\"adjusted\",\"ACTIVE\"");
        write(root.resolve("AccessoryFiles/ComponentHierarchyBySystem/ComponentHierarchyBySystem.csv"),
                "\"PATH_TO_ROOT\",\"SEQUENCE\",\"IMMEDIATE_PARENT\",\"CODE\",\"CODE_TEXT\"",
                "\"\",\"1\",\"\",\"LP432695-7\",\"{component}\"",
                "\"LP432695-7\",\"1\",\"LP432695-7\",\"LP29693-6\",\"Laboratory\"",
                "\"LP432695-7.LP29693-6\",\"1\",\"LP29693-6\",\"LP1000-1\",\"Hemoglobin\"",
                "\"LP432695-7\",\"1\",\"LP432695-7\",\"LP1000-1\",\"Hemoglobin\"",
                "\"LP432695-7\",\"1\",\"LP432695-7\",\"LP2000-2\",\"Orphan component\"");
        write(root.resolve("LoincTable/Loinc.csv"),
                "\"LOINC_NUM\",\"COMPONENT\",\"PROPERTY\",\"TIME_ASPCT\",\"SYSTEM\",\"SCALE_TYP\",\"METHOD_TYP\",\"CLASS\","
                        + "\"DefinitionDescription\",\"STATUS\",\"CONSUMER_NAME\",\"CLASSTYPE\",\"RELATEDNAMES2\",\"SHORTNAME\","
                        + "\"ORDER_OBS\",\"EXAMPLE_UNITS\",\"LONG_COMMON_NAME\",\"DisplayName\"",
                "\"718-7\",\"Hemoglobin\",\"MCnc\",\"Pt\",\"Bld\",\"Qn\",\"\",\"HEM/BC\","
                        + "\"a \"\"quoted\"\", comma-bearing definition\",\"ACTIVE\",\"Hemoglobin blood\",\"1\",\"Hb; Hgb\","
                        + "\"Hgb Bld-mCnc\",\"Both\",\"g/dL\",\"Hemoglobin [Mass/volume] in Blood\",\"Hemoglobin\"",
                "\"99999-9\",\"Hemoglobin\",\"MCnc\",\"Pt\",\"Bld\",\"Qn\",\"Automated count\",\"HEM/BC\","
                        + "\"\",\"TRIAL\",\"\",\"1\",\"\",\"Hgb trial\",\"Order\",\"\",\"Hemoglobin trial\",\"\"",
                "\"33333-3\",\"Hemoglobin\",\"MCnc\",\"Pt\",\"Bld\",\"Qn\",\"\",\"HEM/BC\","
                        + "\"\",\"DISCOURAGED\",\"\",\"1\",\"\",\"Hgb disc\",\"Observation\",\"\",\"Hemoglobin discouraged\",\"\"",
                "\"11111-1\",\"Hemoglobin\",\"MCnc\",\"Pt\",\"Bld\",\"Qn\",\"\",\"HEM/BC\","
                        + "\"\",\"DEPRECATED\",\"\",\"1\",\"\",\"Hgb old\",\"\",\"\",\"Hemoglobin deprecated\",\"\"",
                "\"44444-4\",\"Unknown stuff\",\"MCnc\",\"Pt\",\"Bld\",\"Qn\",\"\",\"HEM/BC\","
                        + "\"\",\"ACTIVE\",\"\",\"1\",\"\",\"Unk\",\"Subset\",\"\",\"Unknown component\",\"\"");
        return root;
    }

    private static void write(Path file, String... lines) throws IOException {
        Files.writeString(file, String.join("\r\n", lines) + "\r\n");
    }
}
