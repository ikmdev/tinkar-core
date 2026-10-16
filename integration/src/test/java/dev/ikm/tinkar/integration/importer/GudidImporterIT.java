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

import dev.ikm.tinkar.common.id.LongIdSet;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.ConceptEntityVersion;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.importer.ImportReport;
import dev.ikm.tinkar.importer.ImportSource;
import dev.ikm.tinkar.importer.KnowledgeImporters;
import dev.ikm.tinkar.importer.StoreWriter;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.KernelPattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The GUDID importer over the IKE starter set, on a release small enough to read, laid out in
 * AccessGUDID's delimited columns: three product codes (one with no specialty, one name in
 * Windows-1252), four devices (one deactivated, one with two product codes, one with none, one
 * with a product code the classification file lacks), identifiers from two agencies and one the
 * starter data has no concept for, premarket submissions, and two GMDN terms, one obsolete for a
 * device.
 *
 * <p>Identity parity: the UUIDs asserted are what IKM's {@code gudid-data} transformer mints, a
 * type 5 UUID in its namespace from the same name, computed independently of tinkar-core.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GudidImporterIT {

    private static final String RELEASE = "AccessGUDID_Delimited_Full_Release_20261001";
    private static final String PACER = "00812345000011";
    private static final String COUNTER = "00812345000028";
    private static final String RETIRED = "00812345000035";
    private static final String STRAY = "00812345000042";

    // What IKM's transformer mints, from Python's uuid.uuid5 in namespace 3094dbd1-60cf-44a6-92e3-0bb32ca4d3de.
    private static final UUID PACER_CONCEPT = UUID.fromString("6f70de65-bf84-5ba7-8709-00c1126fcf79");
    private static final UUID COUNTER_CONCEPT = UUID.fromString("7eb69e90-601e-5f24-b27d-7cdd41a4e155");
    private static final UUID DXY_CONCEPT = UUID.fromString("9a59b856-b34d-53c9-b2be-5b7bb5b2d11e");
    private static final UUID GKZ_CONCEPT = UUID.fromString("1377eefc-c200-5e94-a191-0577d4a0149a");
    private static final UUID FZZ_CONCEPT = UUID.fromString("248936cb-380d-5966-8699-23cefcc0f0c2");
    private static final UUID UNKNOWN_PRODUCT_CODE = UUID.fromString("65eb6c72-4f19-515c-a183-a9cf0679aa21");
    private static final UUID PACEMAKER_GMDN = UUID.fromString("14364ccc-ed9f-53cd-9204-ba4b90361ddc");
    private static final UUID DXY_AXIOM = UUID.fromString("263227b4-bfef-5099-8514-8c902a55b12b");
    private static final UUID FZZ_AXIOM = UUID.fromString("da2953a3-b482-5840-8715-262ec64f1486");
    private static final UUID DXY_NAME = UUID.fromString("84ec9be6-fe6e-5fa5-8830-b6085b897337");
    private static final UUID DXY_CODE = UUID.fromString("4becfba1-5956-5742-aa18-8fa50dc1a0ad");
    private static final UUID PACER_OWL_AXIOM = UUID.fromString("7455ba16-060b-5d81-ad5b-d35d11f50a44");
    private static final UUID PACER_PACKAGE_DI = UUID.fromString("36131c9a-8621-5499-b349-667466e2b13b");
    private static final UUID PACER_PREMARKET = UUID.fromString("6144b5d0-52a2-55f2-86cf-1ca71643db74");
    private static final UUID PACER_GMDN = UUID.fromString("b9a5dff0-29d7-5863-a2a2-3f5013eee20f");

    // IKM's GudidTerm concepts, from the starter data the importer carries.
    private static final UUID UNKNOWN_SPECIALTY = UUID.fromString("a2daa52b-7f15-404e-adfe-f30959817d04");
    private static final UUID CARDIOVASCULAR = UUID.fromString("97cce489-04fb-47ae-abf4-d0cf4185ff36");
    private static final UUID GS1 = UUID.fromString("bd5c11e4-b0eb-4971-b5bf-7d1ccc778a73");
    private static final UUID GMDN_TERMS_PATTERN = UUID.fromString("5b8fbc02-465b-4b7c-bf12-f11a8e87db4a");

    private Path release;
    private Path archives;

    @BeforeAll
    void startStore(@TempDir Path dir) throws IOException {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
        release = writeRelease(dir.resolve(RELEASE));
        archives = writePublishedArchives(dir.resolve("gudid-published.zip"), release);
    }

    @AfterAll
    void stopStore() {
        TestHelper.stopDatabase();
    }

    @Test
    @Order(1)
    void importsTheRelease() {
        ImportReport report = inLoadPhase(release);
        assertEquals(RELEASE, report.sourceVersion());
        assertTrue(report.written() > 0, report.toString());
        Map<String, Long> details = report.details();
        assertTrue(details.get("starter data components") > 0, report.toString());
        assertEquals(3L, details.get("product codes"), report.toString());
        assertEquals(4L, details.get("devices"), report.toString());
        assertEquals(4L, details.get("device axioms"), report.toString());
        assertEquals(2L, details.get("devices under the unknown product code"), report.toString());
        assertEquals(1L, details.get("device product code rows with no product code"), report.toString());
        assertEquals(3L, details.get("device identifiers"), report.toString());
        assertEquals(1L, details.get("device identifier rows skipped"), "an agency with no concept is skipped");
        assertEquals(2L, details.get("premarket submissions"), report.toString());
        assertEquals(2L, details.get("GMDN terms"), report.toString());
        assertEquals(3L, details.get("device GMDN terms"), report.toString());
    }

    @Test
    @Order(2)
    void mintsIkmsIdentities() {
        for (UUID concept : List.of(PACER_CONCEPT, COUNTER_CONCEPT, DXY_CONCEPT, FZZ_CONCEPT, UNKNOWN_PRODUCT_CODE,
                PACEMAKER_GMDN)) {
            assertTrue(PrimitiveData.get().hasPublicId(PublicIds.of(concept)), "IKM's concept " + concept);
        }
        long dxy = nid(DXY_CONCEPT);
        long pacer = nid(PACER_CONCEPT);
        assertEquals(dxy, semantic(DXY_AXIOM).referencedComponentNid(), "the product code's stated axiom");
        assertEquals("Implantable pacemaker pulse-generator", latest(DXY_NAME).fieldValues().get(1));
        SemanticEntityVersion code = latest(DXY_CODE);
        assertEquals("DXY", code.fieldValues().get(1));
        assertEquals(pacer, semantic(PACER_OWL_AXIOM).referencedComponentNid(), "the device's OWL axiom");
        assertEquals("SubClassOf(:[" + PACER_CONCEPT + "] :[" + DXY_CONCEPT + "])",
                latest(PACER_OWL_AXIOM).fieldValues().get(0));
        SemanticEntityVersion packageDi = latest(PACER_PACKAGE_DI);
        assertEquals("10812345000018", packageDi.fieldValues().get(1));
        assertEquals(nid(GS1), ((EntityFacade) packageDi.fieldValues().get(0)).nid(), "issued by GS1");
        assertEquals("P123456", latest(PACER_PREMARKET).fieldValues().get(1));
        SemanticEntityVersion gmdn = latest(PACER_GMDN);
        assertEquals(nid(GMDN_TERMS_PATTERN), semantic(PACER_GMDN).patternNid());
        assertTrue(((LongIdSet) gmdn.fieldValues().get(0)).contains(nid(PACEMAKER_GMDN)), "the device's GMDN term");
    }

    @Test
    @Order(3)
    void statesProductCodesUnderTheirSpecialties() {
        assertTrue(conceptsIn(latest(DXY_AXIOM)).contains(nid(CARDIOVASCULAR)), "DXY is cardiovascular");
        assertTrue(conceptsIn(latest(FZZ_AXIOM)).contains(nid(UNKNOWN_SPECIALTY)),
                "a product code with no specialty is under the unknown specialty");
        List<Object> names = EntityService.get()
                .semanticsForComponentOfPattern(nid(FZZ_CONCEPT), KernelPattern.DESCRIPTION_PATTERN.nid())
                .map(s -> stamps().latest(s).get().fieldValues().get(1)).map(Object.class::cast).toList();
        assertTrue(names.contains("Instrument, surgical, café grade"), "the classification file reads as Windows-1252: " + names);
    }

    @Test
    @Order(4)
    void writesDevicesWithTheirStates() {
        StampCalculator stamps = stamps();
        Latest<ConceptEntityVersion> retired = stamps.latest(deviceNid(RETIRED));
        assertTrue(retired.isPresent());
        assertFalse(retired.get().active(), "a deactivated device is inactive");

        long pacer = nid(PACER_CONCEPT);
        List<Object> names = EntityService.get()
                .semanticsForComponentOfPattern(pacer, KernelPattern.DESCRIPTION_PATTERN.nid())
                .map(s -> stamps.latest(s).get().fieldValues().get(1)).map(Object.class::cast).toList();
        assertEquals(List.of("Pacer X1"), names, "named by brand name and version or model number");

        for (long device : List.of(pacer, nid(COUNTER_CONCEPT), deviceNid(STRAY))) {
            assertTrue(EntityService.get().semanticsForComponentOfPattern(device,
                    KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid()).findAny().isPresent(),
                    "the device's OWL axiom becomes an EL++ stated axiom");
        }
        assertTrue(conceptsIn(stamps.latest(EntityService.get().semanticsForComponentOfPattern(nid(COUNTER_CONCEPT),
                        KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid()).findFirst().orElseThrow()).get())
                        .containsAll(List.of(nid(FZZ_CONCEPT), nid(GKZ_CONCEPT))),
                "a device with two product codes is a kind of both");
        assertTrue(conceptsIn(stamps.latest(EntityService.get().semanticsForComponentOfPattern(deviceNid(STRAY),
                        KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid()).findFirst().orElseThrow()).get())
                        .contains(nid(UNKNOWN_PRODUCT_CODE)),
                "a device whose product code the classification file lacks is under the unknown product code");

        List<SemanticEntity<SemanticEntityVersion>> retiredGmdn = EntityService.get()
                .semanticsForComponentOfPattern(deviceNid(RETIRED), nid(GMDN_TERMS_PATTERN)).toList();
        assertEquals(1, retiredGmdn.size());
        assertFalse(stamps.latest(retiredGmdn.getFirst()).get().active(), "an obsolete GMDN code's semantic is inactive");
    }

    @Test
    @Order(5)
    void replayingThePublishedArchivesWritesNothing() {
        ImportReport again = inLoadPhase(archives);
        assertEquals(0, again.written(), again.toString());
        assertEquals(0, again.versioned(), again.toString());
        assertTrue(again.unchanged() > 0, again.toString());
        assertEquals(4L, again.details().get("devices"), "the archives as published read the same release");
    }

    /** An import as kb-assemble runs one: inside the bulk-load bracket. */
    private static ImportReport inLoadPhase(Path source) {
        EntityService.get().beginLoadPhase();
        try {
            return KnowledgeImporters.named("gudid").importInto(new ImportSource(source, Map.of()),
                    new StoreWriter(Calculators.Stamp.DevelopmentLatest()));
        } finally {
            EntityService.get().endLoadPhase();
        }
    }

    private static StampCalculator stamps() {
        return Calculators.Stamp.DevelopmentLatest();
    }

    private static long nid(UUID uuid) {
        PublicId id = PublicIds.of(uuid);
        assertTrue(PrimitiveData.get().hasPublicId(id), "no component " + uuid);
        return PrimitiveData.nid(id);
    }

    private static long deviceNid(String primaryDi) {
        return nid(dev.ikm.tinkar.common.util.uuid.UuidT5Generator.get(UUID.fromString("3094dbd1-60cf-44a6-92e3-0bb32ca4d3de"), primaryDi));
    }

    private static SemanticEntity<SemanticEntityVersion> semantic(UUID uuid) {
        return EntityHandle.get(nid(uuid)).expectSemantic();
    }

    private static SemanticEntityVersion latest(UUID semantic) {
        return stamps().latest(semantic(semantic)).get();
    }

    /** The concepts a stated axiom's tree refers to. */
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

    private static Path writeRelease(Path root) throws IOException {
        Path gudid = Files.createDirectories(root.resolve("src/gudid"));
        Path foi = Files.createDirectories(root.resolve("src/foi"));
        write(foi.resolve("foiclass.txt"), Charset.forName("windows-1252"),
                "REVIEW_PANEL|MEDICALSPECIALTY|PRODUCTCODE|DEVICENAME|DEVICECLASS|UNCLASSIFIED_REASON|GMPEXEMPTFLAG",
                "CV|CV|DXY|Implantable pacemaker pulse-generator|3||N",
                "HE|HE|GKZ|Counter, differential cell|2||N",
                "SU||FZZ|Instrument, surgical, café grade|1||Y");
        write(gudid.resolve("device.txt"), StandardCharsets.UTF_8,
                "PrimaryDI|publicDeviceRecordKey|publicVersionStatus|deviceRecordStatus|publicVersionNumber"
                        + "|publicVersionDate|devicePublishDate|deviceCommDistributionEndDate|deviceCommDistributionStatus"
                        + "|brandName|versionModelNumber|catalogNumber|dunsNumber|companyName",
                PACER + "|0a1b2c3d-0000-4000-8000-000000000001|Update|Published|2|2024-03-02|2024-03-01||In Commercial Distribution|Pacer|X1|CAT-1|123456789|Acme Devices",
                COUNTER + "|0a1b2c3d-0000-4000-8000-000000000002|New|Published|1|2023-05-10|2023-05-10||In Commercial Distribution|CellCount|CC-2|CAT-2|123456789|Acme Devices",
                RETIRED + "|0a1b2c3d-0000-4000-8000-000000000003|Update|Deactivated|3|2022-02-01|2022-01-15|2022-01-31|Not in Commercial Distribution|Old|O-1|CAT-3|123456789|Acme Devices",
                STRAY + "|0a1b2c3d-0000-4000-8000-000000000004|New|Published|1|2025-07-04|2025-07-04||In Commercial Distribution|Stray|S-9|CAT-4|123456789|Acme Devices");
        write(gudid.resolve("productCodes.txt"), StandardCharsets.UTF_8,
                "PrimaryDI|productCode|productCodeName",
                PACER + "|DXY|Implantable pacemaker pulse-generator",
                COUNTER + "|GKZ|Counter, differential cell",
                COUNTER + "|FZZ|Instrument, surgical",
                STRAY + "|ZZZ|Not in the classification file");
        write(gudid.resolve("identifiers.txt"), StandardCharsets.UTF_8,
                "PrimaryDI|deviceId|deviceIdType|deviceIdIssuingAgency|containsDINumber|pkgQuantity|pkgDiscontinueDate|pkgStatus|pkgType",
                PACER + "|" + PACER + "|Primary|GS1||||",
                PACER + "|10812345000018|Package|GS1|" + PACER + "|10||In Commercial Distribution|Box",
                COUNTER + "|+H123CC20|Primary|HIBCC||||",
                RETIRED + "|ACME-1|Primary|ACME||||");
        write(gudid.resolve("premarketSubmissions.txt"), StandardCharsets.UTF_8,
                "PrimaryDI|submissionNumber|supplementNumber",
                PACER + "|P123456|S001",
                COUNTER + "|K987654|");
        write(gudid.resolve("gmdnTerms.txt"), StandardCharsets.UTF_8,
                "PrimaryDI|gmdnPTName|gmdnPTDefinition|gmdnCode|gmdnCodeStatus|implantable",
                PACER + "|Pacemaker|An implantable device that paces the heart.|35852|Active|true",
                COUNTER + "|Cell counter|A device that counts blood cells.|47569|Active|false",
                RETIRED + "|Pacemaker|An implantable device that paces the heart.|35852|Obsolete|true");
        return root;
    }

    /**
     * The release as published: AccessGUDID's archive with its files at the root, and the FDA's
     * {@code foiclass.zip}, both inside one archive.
     */
    private static Path writePublishedArchives(Path zip, Path release) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry(RELEASE + ".zip"));
            out.write(zipOf(release.resolve("src/gudid")));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("foiclass.zip"));
            out.write(zipOf(release.resolve("src/foi")));
            out.closeEntry();
        }
        return zip;
    }

    private static byte[] zipOf(Path directory) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes); Stream<Path> files = Files.list(directory)) {
            for (Path file : files.sorted().toList()) {
                out.putNextEntry(new ZipEntry(file.getFileName().toString()));
                Files.copy(file, (OutputStream) out);
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static void write(Path file, Charset charset, String... lines) throws IOException {
        Files.writeString(file, String.join("\r\n", lines) + "\r\n", charset);
    }
}
