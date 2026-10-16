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
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The GUDID importer on a slice of the real release: every row of AccessGUDID's 2026-10-01
 * delimited release for 33 devices, and the rows of the FDA's product classification file
 * (2026-10-12) for their product codes. The devices were chosen to cover each case the importer
 * handles: several product codes, none, a product code newer than the classification file
 * ({@code QND}), each issuing agency, package identifiers, premarket submissions, and obsolete
 * GMDN codes.
 *
 * <p>Identity parity: the UUIDs asserted are what IKM's {@code gudid-data} transformer mints for
 * these rows, a type 5 UUID in its namespace from the same name, computed independently of
 * tinkar-core with Python's {@code uuid.uuid5}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GudidReleaseSliceIT {

    private static final String SLICE = "/gudid/AccessGUDID_Delimited_Full_Release_20261001-slice";

    // Device 07613365501254: two product codes, JEH and BZG, both anesthesiology.
    private static final UUID SPIROMETER = UUID.fromString("e2971a27-1add-5479-8933-b20c7e357865");
    private static final UUID SPIROMETER_OWL_AXIOM = UUID.fromString("752432f5-6ff1-5dcc-a717-ecfb577804a1");
    private static final UUID JEH = UUID.fromString("4674879e-e5f6-594c-99ae-5ff51243eb88");
    private static final UUID BZG = UUID.fromString("d169509f-4887-5611-8bbe-67f64ea95aae");
    private static final UUID JEH_AXIOM = UUID.fromString("0cb0b893-b3c7-5e1e-9492-9e7d9792b2be");
    // GMDN code 30878, compression stocking.
    private static final UUID GMDN_30878 = UUID.fromString("45bae3d9-728e-5b91-8fdc-a6e452bd39e3");
    // Device D952CM0034, identified by ICCBBA.
    private static final UUID PORCELAIN = UUID.fromString("36416fdd-6cb5-5777-a96e-ed19990c15ae");
    private static final UUID PORCELAIN_ICCBBA_ID = UUID.fromString("3bb614d0-595b-51bc-93aa-4cc77543882f");
    // Device 07613353162702: premarket submission K173234, and GMDN code 60461, obsolete.
    private static final UUID LITHOTRIPTOR = UUID.fromString("25a93a31-c314-5ca0-a965-147d852c4874");
    private static final UUID LITHOTRIPTOR_PREMARKET = UUID.fromString("72359d9a-25c4-5c1b-908d-db165e463fe6");
    private static final UUID LITHOTRIPTOR_OBSOLETE_GMDN = UUID.fromString("679e1c60-8319-5de9-a9bb-0db66f22d090");

    private static final UUID ICCBBA = UUID.fromString("8cf247be-cc9e-4a0e-9ab9-77330209a398");

    private Path slice;

    @BeforeAll
    void startStore() throws URISyntaxException {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
        slice = Path.of(assertNotNullAndGet(GudidReleaseSliceIT.class.getResource(SLICE)).toURI());
    }

    @AfterAll
    void stopStore() {
        TestHelper.stopDatabase();
    }

    @Test
    @Order(1)
    void importsTheSlice() {
        ImportReport report = inLoadPhase(slice);
        Map<String, Long> details = report.details();
        assertEquals(14L, details.get("product codes"), report.toString());
        assertEquals(0L, details.get("product code rows skipped"), report.toString());
        assertEquals(33L, details.get("devices"), report.toString());
        assertEquals(0L, details.get("device rows skipped"), report.toString());
        assertEquals(33L, details.get("device axioms"), report.toString());
        assertEquals(3L, details.get("device product code rows with no product code"), "QND is newer than foiclass");
        assertEquals(6L, details.get("devices under the unknown product code"), report.toString());
        assertEquals(46L, details.get("device identifiers"), report.toString());
        assertEquals(0L, details.get("device identifier rows skipped"), "every agency has a concept");
        assertEquals(20L, details.get("premarket submissions"), report.toString());
        assertEquals(17L, details.get("GMDN terms"), report.toString());
        assertEquals(38L, details.get("device GMDN terms"), report.toString());
    }

    @Test
    @Order(2)
    void mintsIkmsIdentities() {
        long spirometer = nid(SPIROMETER);
        assertEquals(spirometer, semantic(SPIROMETER_OWL_AXIOM).referencedComponentNid());
        // Product codes sort by code (BZG, JEH), so a replay writes the same text.
        assertEquals("SubClassOf(:[" + SPIROMETER + "] ObjectIntersectionOf(:[" + BZG + "] :[" + JEH + "]))",
                latest(SPIROMETER_OWL_AXIOM).fieldValues().get(0));
        assertEquals(nid(JEH), semantic(JEH_AXIOM).referencedComponentNid(), "JEH's stated axiom, under anesthesiology");
        assertTrue(PrimitiveData.get().hasPublicId(PublicIds.of(GMDN_30878)), "the GMDN term's concept");

        SemanticEntityVersion iccbba = latest(PORCELAIN_ICCBBA_ID);
        assertEquals(nid(PORCELAIN), semantic(PORCELAIN_ICCBBA_ID).referencedComponentNid());
        assertEquals("D952CM0034", iccbba.fieldValues().get(1));
        assertEquals(nid(ICCBBA), ((EntityFacade) iccbba.fieldValues().get(0)).nid(), "issued by ICCBBA");

        assertEquals(nid(LITHOTRIPTOR), semantic(LITHOTRIPTOR_PREMARKET).referencedComponentNid());
        assertEquals("K173234", latest(LITHOTRIPTOR_PREMARKET).fieldValues().get(1));
        SemanticEntityVersion obsolete = latest(LITHOTRIPTOR_OBSOLETE_GMDN);
        assertFalse(obsolete.active(), "an obsolete GMDN code's semantic is inactive");
        assertTrue(((LongIdSet) obsolete.fieldValues().get(0)).contains(
                nid(dev.ikm.tinkar.common.util.uuid.UuidT5Generator.get(
                        UUID.fromString("3094dbd1-60cf-44a6-92e3-0bb32ca4d3de"), "GMDN_60461"))));
    }

    @Test
    @Order(3)
    void transformsEveryDeviceAxiom() {
        StampCalculator stamps = Calculators.Stamp.DevelopmentLatest();
        for (UUID device : new UUID[]{SPIROMETER, PORCELAIN, LITHOTRIPTOR}) {
            assertTrue(EntityService.get().semanticsForComponentOfPattern(nid(device),
                    KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid())
                    .anyMatch(s -> stamps.latest(s).isPresent()), "the device's EL++ stated axiom: " + device);
        }
    }

    @Test
    @Order(4)
    void replayingWritesNothing() {
        ImportReport again = inLoadPhase(slice);
        assertEquals(0, again.written(), again.toString());
        assertEquals(0, again.versioned(), again.toString());
        assertTrue(again.unchanged() > 0, again.toString());
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

    private static <T> T assertNotNullAndGet(T value) {
        assertNotNull(value, "the slice is a test resource");
        return value;
    }

    private static long nid(UUID uuid) {
        PublicId id = PublicIds.of(uuid);
        assertTrue(PrimitiveData.get().hasPublicId(id), "no component " + uuid);
        return PrimitiveData.nid(id);
    }

    private static SemanticEntity<SemanticEntityVersion> semantic(UUID uuid) {
        return EntityHandle.get(nid(uuid)).expectSemantic();
    }

    private static SemanticEntityVersion latest(UUID semantic) {
        return Calculators.Stamp.DevelopmentLatest().latest(semantic(semantic)).get();
    }
}
