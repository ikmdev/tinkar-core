package dev.ikm.tinkar.integration.snomed.concept;

import dev.ikm.tinkar.entity.StampRecord;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.UUID;

import static dev.ikm.tinkar.integration.snomed.concept.SnomedTextToConcept.createSTAMPChronology;
import static dev.ikm.tinkar.integration.snomed.concept.SnomedTextToConcept.getStampUUID;
import static dev.ikm.tinkar.integration.snomed.core.MockEntity.getNid;
import static dev.ikm.tinkar.integration.snomed.core.TinkarStarterConceptUtil.*;
import static dev.ikm.tinkar.integration.snomed.core.TinkarStarterDataHelper.openSession;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestSnomedTextToConcept {
    @Test
    @Order(1)
    @DisplayName("Test for one row of inactive stamp data in Concept File")
    public void testStampWithOneActiveRow() {
        openSession((mockStaticEntityService, starterData) -> {
            List<String> row = loadSnomedFile(TestSnomedTextToConcept.class, "sct2_Concept_Full_US1000124_20220901_1.txt");
            StampRecord record = createSTAMPChronology(row.get(0));
            assertEquals(1, record.versions().size(), "Has more than one row");
        });
    }

    @Test
    @Order(2)
    @DisplayName("Test for Active stamp in Concept File")
    public void testStampWithActiveTransformResult() {
        openSession((mockStaticEntityService, starterData) -> {
            List<String> row = loadSnomedFile(TestSnomedTextToConcept.class, "sct2_Concept_Full_US1000124_20220901_1.txt");

            StampRecord record = createSTAMPChronology(row.get(0));
            UUID testStampUUID = getStampUUID(row.stream().map(SnomedTextToConcept.Concept::new).findFirst().orElse(null));

            // Assert that stamp is Active
            assertEquals(getNid(ACTIVE), record.stateNid(), "State is not active");
            assertEquals(getNid(SNOMED_CT_AUTHOR), record.authorNid(), "Author couldn't be referenced");
            assertEquals(getNid(DEVELOPMENT_PATH), record.pathNid(), "Path could not be referenced");
            assertEquals(getNid(SNOMED_TEXT_MODULE_ID), record.moduleNid(), "Module could ot be referenced");
            assertEquals(getNid(testStampUUID), record.nid(), "Stamp UUID was not populated");
        });

    }

    @Test
    @Order(3)
    @DisplayName("Test for one row of inactive stamp data in Concept File")
    public void testStampWithOneInactiveRow() {
        openSession((mockStaticEntityService, starterData) -> {
            List<String> row = loadSnomedFile(TestSnomedTextToConcept.class, "sct2_Concept_Full_US1000124_20220901_2.txt");
            StampRecord record = createSTAMPChronology(row.get(0));
            assertEquals(1, record.versions().size(), "Has more than one row");
        });
    }

    @Test
    @Order(4)
    @DisplayName("Test for Inactive stamp in Concept File")
    public void testStampWithInactiveTransformResult() {
        openSession((mockStaticEntityService, starterData) -> {
            List<String> row = loadSnomedFile(TestSnomedTextToConcept.class, "sct2_Concept_Full_US1000124_20220901_2.txt");
            StampRecord record = createSTAMPChronology(row.get(0));
            UUID testStampUUID = getStampUUID(row.stream().map(SnomedTextToConcept.Concept::new).findFirst().orElse(null));

            // Assert that stamp is Active
            assertEquals(getNid(INACTIVE), record.stateNid(), "State is active");
            assertEquals(getNid(SNOMED_CT_AUTHOR), record.authorNid(), "Author couldn't be referenced");
            assertEquals(getNid(DEVELOPMENT_PATH), record.pathNid(), "Path could not be referenced");
            assertEquals(getNid(SNOMED_TEXT_MODULE_ID), record.moduleNid(), "Module could ot be referenced");
            assertEquals(getNid(testStampUUID), record.nid(), "Stamp " + testStampUUID + " UUID was not populated");
        });

    }

}
