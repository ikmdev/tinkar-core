package dev.ikm.tinkar.integration.snomed.language;

import dev.ikm.tinkar.entity.StampRecord;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.List;

import static dev.ikm.tinkar.integration.snomed.core.MockEntity.getNid;
import static dev.ikm.tinkar.integration.snomed.core.SnomedCTHelper.*;
import static dev.ikm.tinkar.integration.snomed.core.SnomedCTConstants.*;
import static dev.ikm.tinkar.integration.snomed.core.SnomedCTStampChronology.createSTAMPChronologyForAllRecords;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestSnomedToEntityLanguage {

    @BeforeAll
    public void setupSuite() throws IOException {

    }

    @AfterAll
    public void teardownSuite() throws IOException{

    }
    @Test
    @DisplayName("Test Snomed to Entity Language.")
    public void testSnomedToEntityLanguage(){
        openSession((mockedStaticEntity) -> {
            List<StampRecord> stampRecords =  createSTAMPChronologyForAllRecords(this,"der2_cRefset_LanguageFull-en_US1000124_20220901_1.txt");
            StampRecord record = stampRecords.get(0);
            assertEquals(1, record.versions().size(), "Has more than one row");
        });
    }

    @Test
    @DisplayName("Test Stamp with active Transform Result for Snomed to Entity Language.")
    public void testStampWithActiveTransformResult(){
        openSession((mockedStaticEntity) -> {
            List<StampRecord> stampRecords =  createSTAMPChronologyForAllRecords(this,"der2_cRefset_LanguageFull-en_US1000124_20220901_1.txt");
            StampRecord record = stampRecords.get(0);
            assertEquals(getNid(ACTIVE_UUID), record.stateNid(), "State is not active");
            assertEquals(getNid(SNOMED_CT_AUTHOR_UUID), record.authorNid(), "Author couldn't be referenced");
            assertEquals(getNid(DEVELOPMENT_PATH_UUID), record.pathNid(), "Path could not be referenced");
            assertEquals(getNid(SNOMED_TEXT_MODULE_ID_UUID), record.moduleNid(), "Module could not be referenced");
        });
    }

    @Test
    @DisplayName("Test Stamp with Inactive Transform Result for Snomed to Entity Language.")
    public void testStampWithInActiveTransformResult(){
              openSession((mockedStaticEntity) -> {
            List<StampRecord> stampRecords =  createSTAMPChronologyForAllRecords(this,"der2_cRefset_LanguageFull-en_US1000124_20220901_2.txt");
            StampRecord record = stampRecords.get(0);
            assertEquals(getNid(INACTIVE_UUID), record.stateNid(), "State is active");
            assertEquals(getNid(SNOMED_CT_AUTHOR_UUID), record.authorNid(), "Author couldn't be referenced");
            assertEquals(getNid(DEVELOPMENT_PATH_UUID), record.pathNid(), "Path could not be referenced");
            assertEquals(getNid(SNOMED_TEXT_MODULE_ID_UUID), record.moduleNid(), "Module could not be referenced");
        });
    }

}
