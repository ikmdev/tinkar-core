package dev.ikm.tinkar.integration.snomed.relationship;

import dev.ikm.tinkar.entity.SemanticRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static dev.ikm.tinkar.integration.snomed.core.MockEntity.getNid;
import static dev.ikm.tinkar.integration.snomed.core.SnomedCTConstants.RELATIONSHIP_PATTERN;
import static dev.ikm.tinkar.integration.snomed.core.SnomedCTConstants.RELATIONSHIP_PATTERN_UUID;
import static dev.ikm.tinkar.integration.snomed.core.SnomedCTHelper.openSession;
import static dev.ikm.tinkar.integration.snomed.relationship.SnomedCTRelationshipSemantic.createRelationshipSemantics;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestRelationshipSemantics {

    @Test
    @DisplayName("Test Relationship Semantic with active for Snomed to Entity Relationship.")
    @Order(1)
    public void testCreateRelationshipSemantic(){
        openSession((mockedStaticEntity) -> {
            List<SemanticRecord> semanticRecords =  createRelationshipSemantics(this,"sct2_Relationship_Full_US1000124_20220901_1.txt", RELATIONSHIP_PATTERN);
            SemanticRecord semanticRecord = semanticRecords.get(0);
            assertEquals(1, semanticRecord.versions().size(), "Has more than one row");
        });
    }

    @Test
    @DisplayName("Test Relationship Semantic For Identifier pattern.")
    @Order(2)
    public void testReltionshipPatternIdentifierSemantic(){
        openSession((mockedStaticEntity) -> {
            List<SemanticRecord> semanticRecords =  createRelationshipSemantics(this,"sct2_Relationship_Full_US1000124_20220901_1.txt", RELATIONSHIP_PATTERN);
            SemanticRecord semanticRecord = semanticRecords.get(0);
            assertEquals(getNid(RELATIONSHIP_PATTERN_UUID), semanticRecord.patternNid(), "Has more than one row");
        });
   }
}
