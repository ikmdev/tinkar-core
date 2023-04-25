package dev.ikm.tinkar.integration.snomed.concept;

import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.ConceptVersionRecord;
import dev.ikm.tinkar.entity.StampRecord;
import org.eclipse.collections.api.list.ImmutableList;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.UUID;

import static dev.ikm.tinkar.integration.snomed.concept.SnomedTextToConcept.*;
import static dev.ikm.tinkar.integration.snomed.core.MockEntity.getNid;
import static dev.ikm.tinkar.integration.snomed.core.TinkarStarterConceptUtil.*;
import static dev.ikm.tinkar.integration.snomed.core.TinkarStarterDataHelper.openSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestSnomedTextToConcept {

    @Test
    @Order(1)
    @DisplayName("Test for one row of inactive stamp data in Concept File")
    public void testStampWithOneActiveRow() {
        openSession((mockStaticEntityService, starterData) -> {
            List<String> row = loadSnomedFile(TestSnomedTextToConcept.class, "sct2_Concept_Full_US1000124_20220901_1.txt");
            StampRecord record = createStampChronology(row.get(0));
            assertEquals(1, record.versions().size(), "Has more than one row");
        });
    }

    @Test
    @Order(2)
    @DisplayName("Test for Active stamp in Concept File")
    public void testStampWithActiveTransformResult() {
        openSession((mockStaticEntityService, starterData) -> {
            List<String> rows = loadSnomedFile(TestSnomedTextToConcept.class, "sct2_Concept_Full_US1000124_20220901_1.txt");

            StampRecord record = createStampChronology(rows.get(0));
            UUID testStampUUID = getStampUUID(rows.get(0));

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
            StampRecord record = createStampChronology(row.get(0));
            assertEquals(1, record.versions().size(), "Has more than one row");
        });
    }

    @Test
    @Order(4)
    @DisplayName("Test for Inactive stamp in Concept File")
    public void testStampWithInactiveTransformResult() {
        openSession((mockStaticEntityService, starterData) -> {
            List<String> rows = loadSnomedFile(TestSnomedTextToConcept.class, "sct2_Concept_Full_US1000124_20220901_2.txt");
            StampRecord record = createStampChronology(rows.get(0));
            UUID testStampUUID = getStampUUID(rows.get(0));

            // Assert that stamp is Active
            assertEquals(getNid(INACTIVE), record.stateNid(), "State is active");
            assertEquals(getNid(SNOMED_CT_AUTHOR), record.authorNid(), "Author couldn't be referenced");
            assertEquals(getNid(DEVELOPMENT_PATH), record.pathNid(), "Path could not be referenced");
            assertEquals(getNid(SNOMED_TEXT_MODULE_ID), record.moduleNid(), "Module could ot be referenced");
            assertEquals(getNid(testStampUUID), record.nid(), "Stamp " + testStampUUID + " UUID was not populated");
        });

    }

    @Test
    @Order(5)
    @DisplayName("Test for Concept with single version record")
    public void testConceptWithSingleVersion() {
        openSession((mockStaticEntityService, starterData) -> {
            List<String> rows = loadSnomedFile(TestSnomedTextToConcept.class, "sct2_Concept_Full_US1000124_20220901_3.txt");
            ConceptRecord conceptRecord = createConceptChronology(rows.get(0));
            ImmutableList<ConceptVersionRecord> conceptVersionsRecord = conceptRecord.versions();

            UUID testConceptUUID = getConceptUUID(rows.get(0));

            // Assert that stamp is Active
            assertEquals(1, conceptVersionsRecord.size(), "More than 1 concept versions exist");
            assertEquals(getNid(testConceptUUID), conceptRecord.nid(), "Concept " + testConceptUUID + " UUID was not populated");
        });

    }

    @Test
    @Order(6)
    @DisplayName("Test for Concept version record")
    public void testConceptVersions() {
        openSession((mockStaticEntityService, starterData) -> {
            List<String> rows = loadSnomedFile(TestSnomedTextToConcept.class, "sct2_Concept_Full_US1000124_20220901_3.txt");
            ConceptRecord conceptRecord = createConceptChronology(rows.get(0));
            ImmutableList<ConceptVersionRecord> conceptVersionsRecord = conceptRecord.versions();
            ConceptVersionRecord firstConceptVersionsRecord = conceptRecord.versions().get(0);

            UUID testStampUUID = getStampUUID(rows.get(0));
            UUID testConceptUuid = getConceptUUID(rows.get(0));

            assertEquals(getNid(testConceptUuid), conceptRecord.nid(), "Concept " + testConceptUuid + " UUID was not populated");
            assertEquals(getNid(testStampUUID), firstConceptVersionsRecord.stampNid(), "Stamp " + testStampUUID + " is not associated with given concept version");
            assertEquals(conceptRecord, firstConceptVersionsRecord.chronology(), "Concept was not referenced correctly in concept version record.");
        });
    }

    @Test
    @Order(7)
    @DisplayName("Test for Concept with multiple version concept record")
    public void testForSingleConceptMultipleVersion() {
        openSession((mockStaticEntityService, starterData) -> {
            List<String> rows = loadSnomedFile(TestSnomedTextToConcept.class, "sct2_Concept_Full_US1000124_20220901_4.txt");
            List<ConceptRecord> conceptRecord = createConceptFromMultipleVersions(TestSnomedTextToConcept.class, "sct2_Concept_Full_US1000124_20220901_4.txt");

            assertTrue(rows.size() > 1, "File with single or no rows exist");
            assertTrue(conceptRecord.size() == 1, "File with more than one concept exist");
        });

    }

    @Test
    @Order(8)
    @DisplayName("Test for ConceptVersion to refer to same parent concept")
    public void testConceptWithMultipleVersionofSameConcept() {
        openSession((mockStaticEntityService, starterData) -> {
            List<ConceptRecord> conceptRecord = createConceptFromMultipleVersions(TestSnomedTextToConcept.class, "sct2_Concept_Full_US1000124_20220901_4.txt");
            ConceptRecord singleConcept = conceptRecord.get(0);
            ImmutableList<ConceptVersionRecord> conceptVersionsRecord = singleConcept.versions();

            assertTrue(conceptVersionsRecord.size() == 2 , "0 or 1  concept version  exist");
            assertTrue(conceptVersionsRecord.get(0).chronology().equals(conceptVersionsRecord.get(1).chronology())  ,
                    "Both versions refer to different parent chronologies");
        });

    }

    @Test
    @Order(9)
    @DisplayName("Test for 8th text file to create multiple concepts with multiple version")
    public void testMultipleConceptsWithMultipleVersion() {
        openSession((mockStaticEntityService, starterData) -> {
            List<ConceptRecord> conceptRecord = createConceptFromMultipleVersions(TestSnomedTextToConcept.class, "sct2_Concept_Full_US1000124_20220901_8.txt");
            assertTrue(conceptRecord.size() > 1, "File with one concept exist");
        });

    }

}
