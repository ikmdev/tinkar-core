package dev.ikm.tinkar.integration.snomed.description;

import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static dev.ikm.tinkar.integration.snomed.core.SnomedCTHelper.openSession;
import static dev.ikm.tinkar.integration.snomed.description.SnomedCTDescriptionSemantic.createDescriptionSemantics;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SnomedCTDescriptionSemanticTest {

    public static final String DESCRIPTION_PATTERN = "Description Pattern";

    @Test
    @DisplayName("Test Description Semantic with multiple versions for Versions")
    @Order(1)
    public void testCreateDescriptionSemanticVersions(){
        openSession((mockedStaticEntity) -> {
            List<SemanticRecord> semanticRecords =  createDescriptionSemantics(this,"sct2_Description_Full-en_US1000124_20220901_5.txt", DESCRIPTION_PATTERN);
            SemanticRecord semanticRecord = semanticRecords.get(0);
            assertEquals(2, semanticRecord.versions().size(), "Has more than one row");
        });
    }

    @Test
    @DisplayName("Test Description Semantic with multiple versions for Data.")
    @Order(2)
    public void testCreateDescriptionSemantic(){
        openSession((mockedStaticEntity) -> {
            List<SemanticRecord> semanticRecords =  createDescriptionSemantics(this,"sct2_Description_Full-en_US1000124_20220901_5.txt", DESCRIPTION_PATTERN);
            SemanticRecord semanticRecord = semanticRecords.get(0);
            SemanticVersionRecord semanticVersionRecord0 = semanticRecord.versions().get(0);
            SemanticVersionRecord semanticVersionRecord1 = semanticRecord.versions().get(1);
            assertNotEquals(semanticVersionRecord0.stampNid(), semanticVersionRecord1.stampNid(), "The Stamp versions are same");
            assertEquals(semanticVersionRecord0.chronology().patternNid(), semanticVersionRecord1.chronology().patternNid(),"Chronology mismatch");

            Object [] fieldValues0 = (Object[]) semanticVersionRecord0.fieldValues().get(0);
            Object [] fieldValues1 = (Object[]) semanticVersionRecord1.fieldValues().get(0);
            assertArrayEquals(fieldValues0 , fieldValues1);
        });
    }
}
