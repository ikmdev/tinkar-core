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
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.ConceptEntityVersion;
import dev.ikm.tinkar.entity.EntityHandle;
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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SNOMED CT RF2 importer over the IKE starter set, on a release small enough to read: a root,
 * two children stated by OWL axioms, one of them retired a year later, with descriptions, US
 * dialect acceptability, and a LOINC code as an alternate identifier. Two effective times make it
 * a full release with history. Then RxNorm OWL over it: a class with only a label, and a drug
 * that cross-references a SNOMED CT concept and is defined by both.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SnomedRf2ImporterIT {

    private static final String ROOT = "138875005";
    private static final String HEART = "80891009";
    private static final String OLD_HEART = "302509004";
    private static final String CORE = "900000000000207008";
    private static final String FSN = "900000000000003001";
    private static final String SYNONYM = "900000000000013009";
    private static final String NOT_CASE_SENSITIVE = "900000000000448009";
    private static final String US = "900000000000509007";
    private static final String PREFERRED = "900000000000548007";
    private static final String LOINC_CODE_SCHEME = "705114005";
    private static final String FINDING_SITE = "363698007";

    @TempDir
    Path temp;

    private Path release;
    private Path rxnormDir;

    @BeforeAll
    void startStore(@TempDir Path dir) throws IOException {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
        release = writeRelease(dir.resolve("SnomedCT_InternationalRF2_PRODUCTION_20250101T120000Z"));
        rxnormDir = Files.createDirectories(dir.resolve("rxnorm"));
    }

    @AfterAll
    void stopStore() {
        TestHelper.stopDatabase();
    }

    @Test
    @Order(1)
    void importsTheRelease() {
        ImportReport report = importRelease();
        assertEquals("SnomedCT_InternationalRF2_PRODUCTION_20250101T120000Z", report.sourceVersion());
        assertTrue(report.written() > 0, report.toString());
        assertEquals(2L, report.details().get("OWL axioms"), report.toString());

        StampCalculator stamps = Calculators.Stamp.DevelopmentLatest();
        long heart = nid(HEART);
        assertTrue(EntityHandle.get(heart).entity().isPresent(), "the concept is written");
        Latest<ConceptEntityVersion> oldHeart = stamps.latest(nid(OLD_HEART));
        assertTrue(oldHeart.isPresent());
        assertFalse(oldHeart.get().active(), "a concept retired in the later release is inactive");
        assertEquals(2, EntityHandle.get(nid(OLD_HEART)).expectConcept().versions().size(),
                "a full release keeps both versions");

        SemanticEntityVersion description = only(heart, KernelPattern.DESCRIPTION_PATTERN.nid(), stamps);
        assertEquals("Heart structure (body structure)", description.fieldValues().get(1));
        assertEquals(KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid(),
                ((EntityFacade) description.fieldValues().get(3)).nid(),
                "SNOMED's description type resolves to the kernel's");
        assertEquals(KernelConcept.DESCRIPTION_NOT_CASE_SENSITIVE.nid(), ((EntityFacade) description.fieldValues().get(2)).nid());

        SemanticEntityVersion dialect = only(description.nid(), KernelPattern.US_DIALECT_PATTERN.nid(), stamps);
        assertEquals(KernelConcept.PREFERRED.nid(), ((EntityFacade) dialect.fieldValues().get(0)).nid());

        List<Object> identifiers = EntityService.get().semanticsForComponentOfPattern(heart, KernelPattern.IDENTIFIER_PATTERN.nid())
                .map(s -> stamps.latest(s).get().fieldValues().get(1)).map(Object.class::cast).toList();
        assertTrue(identifiers.contains(HEART), "the SCTID is an identifier: " + identifiers);
        assertTrue(identifiers.contains("8867-4"), "the LOINC code is an identifier: " + identifiers);

        assertTrue(EntityService.get().semanticsForComponentOfPattern(heart, KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid())
                .findAny().isPresent(), "the OWL axiom becomes an EL++ stated axiom");
    }

    @Test
    @Order(2)
    void konceptsNameParentsNotRoleFillers() {
        long heart = nid(HEART);
        String yaml = dev.ikm.tinkar.entity.builder.KonceptExtractor.extractYaml(null, candidate -> candidate == heart);
        String broader = yaml.lines().filter(line -> line.strip().startsWith("broader:")).findFirst().orElseThrow();
        // The SNOMED CT root is the kernel's Health concept, which carries its UUID.
        assertEquals("broader: [HealthConcept]", broader.strip(),
                "only the stated parent, not the finding site's filler, is broader");
        String axiom = yaml.lines().filter(line -> line.strip().startsWith("axiom:")).findFirst().orElseThrow();
        assertTrue(axiom.contains("∃"), "the role is rendered as a restriction: " + axiom);
    }

    @Test
    @Order(3)
    void replayingWritesNothing() {
        ImportReport again = importRelease();
        assertEquals(0, again.written(), again.toString());
        assertEquals(0, again.versioned(), again.toString());
        assertTrue(again.unchanged() > 0, again.toString());
    }

    @Test
    @Order(4)
    void importsRxnormOverSnomed() throws IOException {
        Path owl = rxnormDir.resolve("Pilot-Defined-RxNorm-with-SNCT-classes-2024-04-10-test.owl");
        Files.writeString(owl, String.join("\n",
                "Prefix(:=<http://mor.nlm.nih.gov/RXNORM/>)",
                "Ontology(<http://mor.nlm.nih.gov/RXNORM/test>",
                "# Class: <http://mor.nlm.nih.gov/RXNORM/999> (Drug)",
                "AnnotationAssertion(rdfs:label <http://mor.nlm.nih.gov/RXNORM/999> \"RxNorm drug\")",
                "SubClassOf(<http://mor.nlm.nih.gov/RXNORM/999> <http://snomed.info/id/" + ROOT + ">)",
                "",
                "# Class: <http://mor.nlm.nih.gov/RXNORM/1191> (Aspirin)",
                "AnnotationAssertion(:RxNorm_Name <http://mor.nlm.nih.gov/RXNORM/1191> \"aspirin\")",
                "AnnotationAssertion(oboInOwl:hasDbXref <http://mor.nlm.nih.gov/RXNORM/1191> \"RxCUI: 1191\")",
                "AnnotationAssertion(oboInOwl:hasDbXref <http://mor.nlm.nih.gov/RXNORM/1191> \"SNOMEDCT: " + OLD_HEART + "\")",
                "EquivalentClasses(<http://mor.nlm.nih.gov/RXNORM/1191> ObjectIntersectionOf(<http://mor.nlm.nih.gov/RXNORM/999> <http://snomed.info/id/" + HEART + ">))",
                ")", ""));
        ImportReport report = inLoadPhase("rxnorm-owl", owl);
        assertEquals(2L, report.details().get("classes"), report.toString());
        assertEquals(2L, report.details().get("axioms"), report.toString());

        long aspirin = nid(OLD_HEART);
        assertTrue(PrimitiveData.publicId(aspirin).asUuidList().size() >= 2,
                "the RxNorm concept is merged with the SNOMED CT concept it cross-references");
        StampCalculator stamps = Calculators.Stamp.DevelopmentLatest();
        List<Object> identifiers = EntityService.get().semanticsForComponentOfPattern(aspirin, KernelPattern.IDENTIFIER_PATTERN.nid())
                .map(s -> stamps.latest(s).get().fieldValues().get(1)).map(Object.class::cast).toList();
        assertTrue(identifiers.contains("1191"), "the RxCUI is an identifier: " + identifiers);
        assertTrue(EntityService.get().semanticsForComponentOfPattern(aspirin, KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid())
                .findAny().isPresent(), "the RxNorm axiom becomes an EL++ stated axiom");

        // RxNorm's transform writes RxNorm's version of the merged concept's definition, at RxNorm's
        // time, and leaves SNOMED CT's history to SNOMED CT: a path holds one version per time.
        SemanticEntity<SemanticEntityVersion> definition = EntityService.get()
                .semanticsForComponentOfPattern(aspirin, KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid())
                .findFirst().orElseThrow();
        Map<String, List<String>> versionsByTimeOnPath = new java.util.TreeMap<>();
        for (SemanticEntityVersion version : definition.versions()) {
            versionsByTimeOnPath.computeIfAbsent(version.stamp().time() + " on " + version.stamp().pathNid(),
                    key -> new java.util.ArrayList<>()).add(PrimitiveData.text(version.stamp().moduleNid()));
        }
        versionsByTimeOnPath.forEach((timeOnPath, modules) ->
                assertEquals(1, modules.size(), "versions at " + timeOnPath + ": " + modules));
        long rxnormTime = java.time.LocalDate.parse("2024-04-10").atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant().toEpochMilli();
        List<Long> rxnormVersionTimes = definition.versions().stream()
                .filter(version -> "RxNorm Author".equals(PrimitiveData.text(version.stamp().authorNid())))
                .map(version -> version.stamp().time()).toList();
        assertEquals(List.of(rxnormTime), rxnormVersionTimes, "RxNorm adds one version, at its own release time");

        List<SemanticEntityVersion> names = EntityService.get()
                .semanticsForComponentOfPattern(aspirin, KernelPattern.DESCRIPTION_PATTERN.nid())
                .map(s -> stamps.latest(s).get()).toList();
        SemanticEntityVersion rxnormName = names.stream().filter(d -> "aspirin".equals(d.fieldValues().get(1))).findFirst().orElseThrow();
        assertEquals(KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE.nid(), ((EntityFacade) rxnormName.fieldValues().get(3)).nid(),
                "on a concept merged with SNOMED CT, RxNorm's name is a regular name, SNOMED CT's FSN the only one");

        long drug = PrimitiveData.nid(PublicIds.of(dev.ikm.tinkar.common.util.uuid.UuidT5Generator.get(
                UuidUtil.SNOMED_NAMESPACE, "999rxnorm")));
        List<Object> drugIdentifiers = EntityService.get().semanticsForComponentOfPattern(drug, KernelPattern.IDENTIFIER_PATTERN.nid())
                .map(s -> stamps.latest(s).get().fieldValues().get(1)).map(Object.class::cast).toList();
        assertTrue(drugIdentifiers.contains("999"), "a class with only a label carries its IRI's RxCUI: " + drugIdentifiers);
    }

    /**
     * An extension over the edition, as the LOINC collaboration and NUVA are imported: its own
     * author and module, and an OWL axiom on a concept of the edition, at its own effective time.
     * Its transform writes the extension's version of that concept's definition and leaves the
     * edition's history to the edition: a path holds one version per time.
     */
    @Test
    @Order(5)
    void importsAnExtensionOverTheEdition(@TempDir Path dir) throws IOException {
        String extensionModule = "11010000107";
        Path extension = dir.resolve("SnomedCT_TestExtension_PRODUCTION_XX1010000_20250301T120000Z");
        Path terminology = Files.createDirectories(extension.resolve("Full/Terminology"));
        write(terminology.resolve("sct2_Concept_Full_XX1010000_20250301.txt"),
                "id\teffectiveTime\tactive\tmoduleId\tdefinitionStatusId");
        write(terminology.resolve("sct2_sRefset_OWLExpressionFull_XX1010000_20250301.txt"),
                "id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\towlExpression",
                "00000000-0000-4000-8000-000000000005\t20250301\t1\t" + extensionModule + "\t733073007\t" + HEART
                        + "\tSubClassOf(:" + HEART + " :" + FINDING_SITE + ")");
        ImportReport report = inLoadPhase("snomed-rf2", extension, Map.of("author", "Test Extension Author"));
        assertEquals(1L, report.details().get("OWL axioms"), report.toString());

        SemanticEntity<SemanticEntityVersion> definition = EntityService.get()
                .semanticsForComponentOfPattern(nid(HEART), KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid())
                .findFirst().orElseThrow();
        Map<String, List<String>> versionsByTimeOnPath = new java.util.TreeMap<>();
        for (SemanticEntityVersion version : definition.versions()) {
            versionsByTimeOnPath.computeIfAbsent(version.stamp().time() + " on " + version.stamp().pathNid(),
                    key -> new java.util.ArrayList<>()).add(PrimitiveData.text(version.stamp().authorNid()));
        }
        versionsByTimeOnPath.forEach((timeOnPath, authors) ->
                assertEquals(1, authors.size(), "versions at " + timeOnPath + ": " + authors));
        long extensionTime = java.time.LocalDate.parse("2025-03-01").atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant().toEpochMilli();
        List<Long> extensionVersionTimes = definition.versions().stream()
                .filter(version -> "Test Extension Author".equals(PrimitiveData.text(version.stamp().authorNid())))
                .map(version -> version.stamp().time()).toList();
        assertEquals(List.of(extensionTime), extensionVersionTimes, "the extension adds one version, at its own time");
    }

    private ImportReport importRelease() {
        return inLoadPhase("snomed-rf2", release);
    }

    private static ImportReport inLoadPhase(String importer, Path source) {
        return inLoadPhase(importer, source, Map.of());
    }

    /** An import as kb-assemble runs one: inside the bulk-load bracket. */
    private static ImportReport inLoadPhase(String importer, Path source, Map<String, String> options) {
        EntityService.get().beginLoadPhase();
        try {
            return KnowledgeImporters.named(importer).importInto(new ImportSource(source, options),
                    new StoreWriter(Calculators.Stamp.DevelopmentLatest()));
        } finally {
            EntityService.get().endLoadPhase();
        }
    }

    private static long nid(String sctid) {
        return PrimitiveData.nid(PublicIds.of(UuidUtil.fromSNOMED(sctid)));
    }

    private static SemanticEntityVersion only(long componentNid, long patternNid, StampCalculator stamps) {
        List<SemanticEntity<SemanticEntityVersion>> semantics =
                EntityService.get().semanticsForComponentOfPattern(componentNid, patternNid).toList();
        assertEquals(1, semantics.size(), "one semantic of the pattern on the component");
        return stamps.latest(semantics.getFirst()).get();
    }

    private static Path writeRelease(Path root) throws IOException {
        Path terminology = Files.createDirectories(root.resolve("Full/Terminology"));
        Path language = Files.createDirectories(root.resolve("Full/Refset/Language"));
        write(terminology.resolve("sct2_Concept_Full_INT_20250101.txt"),
                "id\teffectiveTime\tactive\tmoduleId\tdefinitionStatusId",
                ROOT + "\t20240101\t1\t" + CORE + "\t900000000000074008",
                HEART + "\t20240101\t1\t" + CORE + "\t900000000000074008",
                FINDING_SITE + "\t20240101\t1\t" + CORE + "\t900000000000074008",
                OLD_HEART + "\t20240101\t1\t" + CORE + "\t900000000000074008",
                OLD_HEART + "\t20250101\t0\t" + CORE + "\t900000000000074008");
        write(terminology.resolve("sct2_Description_Full-en_INT_20250101.txt"),
                "id\teffectiveTime\tactive\tmoduleId\tconceptId\tlanguageCode\ttypeId\tterm\tcaseSignificanceId",
                "100001011\t20240101\t1\t" + CORE + "\t" + ROOT + "\ten\t" + FSN + "\tSNOMED CT Concept (SNOMED RT+CTV3)\t" + NOT_CASE_SENSITIVE,
                "100002011\t20240101\t1\t" + CORE + "\t" + HEART + "\ten\t" + FSN + "\tHeart structure (body structure)\t" + NOT_CASE_SENSITIVE,
                "100003011\t20240101\t1\t" + CORE + "\t" + OLD_HEART + "\ten\t" + SYNONYM + "\tCardiac structure\t" + NOT_CASE_SENSITIVE);
        UUID dialectRow = UUID.fromString("00000000-0000-4000-8000-000000000001");
        write(language.resolve("der2_cRefset_LanguageFull-en_INT_20250101.txt"),
                "id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tacceptabilityId",
                dialectRow + "\t20240101\t1\t" + CORE + "\t" + US + "\t100002011\t" + PREFERRED);
        write(terminology.resolve("sct2_Identifier_Full_INT_20250101.txt"),
                "alternateIdentifier\teffectiveTime\tactive\tmoduleId\tidentifierSchemeId\treferencedComponentId",
                "8867-4\t20240101\t1\t" + CORE + "\t" + LOINC_CODE_SCHEME + "\t" + HEART);
        write(terminology.resolve("sct2_sRefset_OWLExpressionFull_INT_20250101.txt"),
                "id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\towlExpression",
                "00000000-0000-4000-8000-000000000002\t20240101\t1\t" + CORE + "\t733073007\t" + HEART
                        + "\tSubClassOf(:" + HEART + " ObjectIntersectionOf(:" + ROOT + " ObjectSomeValuesFrom(:609096000"
                        + " ObjectSomeValuesFrom(:" + FINDING_SITE + " :" + OLD_HEART + "))))",
                "00000000-0000-4000-8000-000000000003\t20240101\t1\t" + CORE + "\t733073007\t" + OLD_HEART
                        + "\tSubClassOf(:" + OLD_HEART + " :" + ROOT + ")",
                "00000000-0000-4000-8000-000000000004\t20240101\t1\t" + CORE + "\t762103008\t734147008"
                        + "\tOntology(<http://snomed.info/sct/900000000000207008>)");
        return root;
    }

    private static void write(Path file, String... lines) throws IOException {
        Files.writeString(file, String.join("\r\n", lines) + "\r\n");
    }
}
