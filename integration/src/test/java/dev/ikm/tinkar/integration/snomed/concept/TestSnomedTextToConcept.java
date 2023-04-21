package dev.ikm.tinkar.integration.snomed.concept;

import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.StampVersionRecord;
import dev.ikm.tinkar.integration.snomed.core.MockEntity;
import dev.ikm.tinkar.integration.snomed.core.TinkarStarterConceptUtil;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static dev.ikm.tinkar.integration.snomed.core.TinkarStarterConceptUtil.INACTIVE;
import static dev.ikm.tinkar.integration.snomed.core.TinkarStarterDataHelper.openSession;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestSnomedTextToConcept extends SnomedTextToConcept {
    List<StampVersionRecord> stampVersionRecordList;
    @BeforeAll
    public void setupSuite() {
        List<String> row = loadSnomedFile(TestSnomedTextToConcept.class, "sct2_Concept_Full_US1000124_20220901_1.txt");
        stampVersionRecordList = new ArrayList<>();
    }

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

            // Assert that stamp is Active
            assertEquals(MockEntity.getNid(TinkarStarterConceptUtil.ACTIVE), record.stateNid(), "State is not active");
            assertEquals(MockEntity.getNid(TinkarStarterConceptUtil.SNOMED_CT_AUTHOR), record.authorNid(), "Author couldn't be referenced");
            assertEquals(MockEntity.getNid(TinkarStarterConceptUtil.DEVELOPMENT_PATH), record.pathNid(), "Path could not be referenced");
            assertEquals(MockEntity.getNid(TinkarStarterConceptUtil.SNOMED_TEXT_MODULE_ID), record.moduleNid(), "Module could ot be referenced");
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

            // Assert that stamp is Active
            assertEquals(MockEntity.getNid(INACTIVE), record.stateNid(), "State is active");
            assertEquals(MockEntity.getNid(TinkarStarterConceptUtil.SNOMED_CT_AUTHOR), record.authorNid(), "Author couldn't be referenced");
            assertEquals(MockEntity.getNid(TinkarStarterConceptUtil.DEVELOPMENT_PATH), record.pathNid(), "Path could not be referenced");
            assertEquals(MockEntity.getNid(TinkarStarterConceptUtil.SNOMED_TEXT_MODULE_ID), record.moduleNid(), "Module could ot be referenced");
        });

    }

}
