package dev.ikm.tinkar.integration.snomed.language;

import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.integration.snomed.core.MockEntity;
import org.eclipse.collections.api.list.ImmutableList;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.UUID;

import static dev.ikm.tinkar.integration.snomed.core.MockEntity.getNid;
import static dev.ikm.tinkar.integration.snomed.core.TinkarStarterConceptUtil.*;
import static dev.ikm.tinkar.integration.snomed.core.TinkarStarterDataHelper.openSession;
import static dev.ikm.tinkar.integration.snomed.language.SnomedToLanguageTransform.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestSnomedToLanguageTransform {

    @Test
    @Order(1)
    @DisplayName("Test for one row of inactive stamp data in Language File")
    public void testStampWithOneActiveRow() {
        openSession((mockStaticEntityService, starterData) -> {
            List<String> row = loadSnomedFile(TestSnomedToLanguageTransform.class, "der2_cRefset_LanguageFull-en_US1000124_20220901_1.txt");
            StampRecord record = createStampChronology(row.get(0));
            assertEquals(1, record.versions().size(), "Has more than one row");
        });
    }

    @Test
    @Order(2)
    @DisplayName("Test for Active stamp in Language File")
    public void testStampWithActiveTransformResult() {
        openSession((mockStaticEntityService, starterData) -> {
            List<String> rows = loadSnomedFile(TestSnomedToLanguageTransform.class, "der2_cRefset_LanguageFull-en_US1000124_20220901_1.txt");

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
    @DisplayName("Test for one row of inactive stamp data in Language File")
    public void testStampWithOneInactiveRow() {
        openSession((mockStaticEntityService, starterData) -> {
            List<String> row = loadSnomedFile(TestSnomedToLanguageTransform.class, "der2_cRefset_LanguageFull-en_US1000124_20220901_2.txt");
            StampRecord record = createStampChronology(row.get(0));
            assertEquals(1, record.versions().size(), "Has more than one row");
        });
    }

    @Test
    @Order(4)
    @DisplayName("Test for Inactive stamp in Language File")
    public void testStampWithInactiveTransformResult() {
        openSession((mockStaticEntityService, starterData) -> {
            List<String> rows = loadSnomedFile(TestSnomedToLanguageTransform.class, "der2_cRefset_LanguageFull-en_US1000124_20220901_2.txt");
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
    @DisplayName("Test language for identifier semantic")
    public void testLanguageWithIdentifierSemantic() {
        openSession((mockStaticEntityService, starterData) -> {
            List<String> rows = loadSnomedFile(TestSnomedToLanguageTransform.class, "der2_cRefset_LanguageFull-en_US1000124_20220901_3.txt");
            UUID identifierPatternUUID = getIdentifierPatternUUID();
            UUID identifierSemanticUUID = getIdentifierSemanticUUID(rows.get(0));
            UUID referenceComponentUUID = getIdentifierReferenceComponentUUID(rows.get(0));

            // testing values
            SemanticRecord testIdentifierSemanticRecord = createLanguageIdentifierSemantic(rows.get(0));

            assertTrue(rows.size() == 1, "File with no or more than one language row exist");
            assertEquals(MockEntity.getNid(identifierSemanticUUID),testIdentifierSemanticRecord.nid(),  "Identifier Semantic nids doesnt match");
            assertEquals(MockEntity.getNid(identifierPatternUUID),testIdentifierSemanticRecord.patternNid(),  "Identifier Semantic pattern nids doesnt match");
            assertEquals(MockEntity.getNid(referenceComponentUUID),testIdentifierSemanticRecord.referencedComponentNid(),  "Identifier Semantic referencecomponent nids doesnt match");
        });
    }

    @Test
    @Order(6)
    @DisplayName("Test language for single identifier semantic version")
    public void testLanguageWithIdentifierSemanticVersion() {
        openSession((mockStaticEntityService, starterData) -> {
            List<String> rows = loadSnomedFile(TestSnomedToLanguageTransform.class, "der2_cRefset_LanguageFull-en_US1000124_20220901_3.txt");
            UUID identifierPatternUUID = getIdentifierPatternUUID();
            UUID identifierSemanticUUID = getIdentifierSemanticUUID(rows.get(0));
            UUID referenceComponentUUID = getIdentifierReferenceComponentUUID(rows.get(0));

            Integer expectedStampRecordNid = createStampChronology(rows.get(0)).nid();

            // testing values
            SemanticRecord testIdentifierSemanticRecord = createLanguageIdentifierSemantic(rows.get(0));
            ImmutableList<SemanticVersionRecord> testSemanticVersionRecord = testIdentifierSemanticRecord.versions();

            assertTrue(testSemanticVersionRecord.size() == 1,  "No version or more than one version of identifier semantic pattern exist");
            assertEquals(expectedStampRecordNid,testSemanticVersionRecord.get(0).stampNid(),  "Doesnt point to same stamp entity");
            assertEquals(MockEntity.getNid(identifierPatternUUID),testSemanticVersionRecord.get(0).patternNid(),  "Do not reference expected parent pattern entity");
            assertEquals(MockEntity.getNid(identifierSemanticUUID),testSemanticVersionRecord.get(0).nid(),  "Do not reference expected semantic entity");
            assertEquals(MockEntity.getNid(referenceComponentUUID),testSemanticVersionRecord.get(0).referencedComponentNid(),  "Do not reference expected parent semantic entity");
        });

    }

    @Test
    @Order(7)
    @DisplayName("Test language for language acceptability semantic")
    public void testLanguageWithLanguageAcceptabilitySemantic() {
        openSession((mockStaticEntityService, starterData) -> {
            List<String> rows = loadSnomedFile(TestSnomedToLanguageTransform.class, "der2_cRefset_LanguageFull-en_US1000124_20220901_4.txt");
            UUID languageAcceptabilityPatternUUID = getLanguageAcceptabilityPatternUUID();
            UUID languageAcceptabilitySemanticUUID = getLanguageAcceptabilitySemanticUUID(rows.get(0));
            UUID referenceComponentUUID = getLanguageAcceptabilityReferenceComponentUUID(rows.get(0));

            // testing values
            SemanticRecord testLanguageAcceptabilitySemanticRecord = createLanguageAceeptabilitySemantic(rows.get(0));

            assertTrue(rows.size() == 1, "File with no or more than one language concept row exist");
            assertEquals(getNid(languageAcceptabilitySemanticUUID),testLanguageAcceptabilitySemanticRecord.nid(),  "Language Acceptability Semantic nids doesnt match");
            assertEquals(getNid(languageAcceptabilityPatternUUID),testLanguageAcceptabilitySemanticRecord.patternNid(),  "Language Acceptability Semantic pattern nids doesnt match");
            assertEquals(getNid(referenceComponentUUID),testLanguageAcceptabilitySemanticRecord.referencedComponentNid(),  "Language Acceptability Semantic referencecomponent nids doesnt match");
        });
    }

    @Test
    @Order(8)
    @DisplayName("Test language for single language acceptability semantic version")
    public void testLanguageWithLanguageAcceptabilitySemanticVersion() {
        openSession((mockStaticEntityService, starterData) -> {
            List<String> rows = loadSnomedFile(TestSnomedToLanguageTransform.class, "der2_cRefset_LanguageFull-en_US1000124_20220901_4.txt");
            UUID languageAcceptabilityPatternUUID = getLanguageAcceptabilityPatternUUID();
            UUID languageAcceptabilitySemanticUUID = getLanguageAcceptabilitySemanticUUID(rows.get(0));
            UUID languageAcceptabilityReferenceComponentUUID = getLanguageAcceptabilityReferenceComponentUUID(rows.get(0));

            Integer expectedStampRecordNid = createStampChronology(rows.get(0)).nid();

            // testing values
            SemanticRecord testLanguageAcceptabilitySemanticRecord = createLanguageAceeptabilitySemantic(rows.get(0));
            ImmutableList<SemanticVersionRecord> testSemanticVersionRecord = testLanguageAcceptabilitySemanticRecord.versions();

            assertTrue(testSemanticVersionRecord.size() == 1,  "No version or more than one version of identifier semantic pattern exist");
            assertEquals(expectedStampRecordNid,testSemanticVersionRecord.get(0).stampNid(),  "Doesnt point to same stamp entity");
            assertEquals(MockEntity.getNid(languageAcceptabilityPatternUUID),testSemanticVersionRecord.get(0).patternNid(),  "Do not reference expected parent pattern entity");
            assertEquals(MockEntity.getNid(languageAcceptabilitySemanticUUID),testSemanticVersionRecord.get(0).nid(),  "Do not reference expected semantic entity");
            assertEquals(MockEntity.getNid(languageAcceptabilityReferenceComponentUUID),testSemanticVersionRecord.get(0).referencedComponentNid(),  "Do not reference expected parent semantic entity");
        });

    }

}
