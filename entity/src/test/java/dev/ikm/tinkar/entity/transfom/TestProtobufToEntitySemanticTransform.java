package dev.ikm.tinkar.entity.transfom;

import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.component.Semantic;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.schema.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static dev.ikm.tinkar.entity.transfom.ProtobufToEntityTestHelper.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestProtobufToEntitySemanticTransform {
    private Semantic mockSemantic;

    @Test
    @DisplayName("Transform a Semantic Chronology With Zero Versions Present")
    public void semanticChronologyTransformWithZeroVersion(){
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given a PBSemanticChronology with a no Semantic Versions present
            SemanticChronology pbSemanticChronology = SemanticChronology.newBuilder()
                    .setPublicId(createPBPublicId(conceptMap.get(TEST_CONCEPT_NAME)))
                    .setReferencedComponent(createPBPublicId(conceptMap.get(MODULE_CONCEPT_NAME)))
                    .setPatternForSemantic(createPBPublicId(conceptMap.get(PATH_CONCEPT_NAME)))
                    .build();

            // When we transform PBSemanticChronology

            // Then we will throw a Runtime exception
            assertThrows(Throwable.class, () -> TinkarSchemaToEntityTransformer.getInstance().transformSemanticChronology(pbSemanticChronology), "Not allowed to have no semantic versions.");
        });
    }
    @Test
    @DisplayName("Transform a Semantic Chronology With One Version Present")
    public void semanticChronologyTransformWithOneVersion(){
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given a PBSemanticChronology with a no Semantic Versions present
            Concept testConcept = conceptMap.get(TEST_CONCEPT_NAME);

            StampVersion pbStampVersion = StampVersion.newBuilder()
                    .setStatus(createPBPublicId(conceptMap.get(STATUS_CONCEPT_NAME)))
                    .setTime(nowTimestamp())
                    .setAuthor(createPBPublicId(conceptMap.get(AUTHOR_CONCEPT_NAME)))
                    .setModule(createPBPublicId(conceptMap.get(MODULE_CONCEPT_NAME)))
                    .setPath(createPBPublicId(conceptMap.get(PATH_CONCEPT_NAME)))
                    .build();

            StampChronology pbStampChronology = StampChronology.newBuilder()
                    .setPublicId(createPBPublicId(testConcept))
                    .addVersions(pbStampVersion)
                    .build();

            String expectedStringValue = "Testing Field Transformation with a string.";
            Field pbFieldString = Field.newBuilder()
                    .setString(expectedStringValue)
                    .build();

            SemanticVersion pbSemanticVersion = SemanticVersion.newBuilder()
                    .setStamp(pbStampChronology)
                    .addFields(pbFieldString)
                    .build();

            SemanticChronology pbSemanticChronology = SemanticChronology.newBuilder()
                    .setPublicId(createPBPublicId(conceptMap.get(TEST_CONCEPT_NAME)))
                    .setReferencedComponent(createPBPublicId(conceptMap.get(MODULE_CONCEPT_NAME)))
                    .setPatternForSemantic(createPBPublicId(conceptMap.get(PATH_CONCEPT_NAME)))
                    .addVersions(pbSemanticVersion)
                    .build();

            // When we transform PBSemanticChronology
            SemanticEntity actualSemanticChronology = TinkarSchemaToEntityTransformer.getInstance().transformSemanticChronology(pbSemanticChronology);

            // Then we compare the PBSemanticChronology to the expected one
            assertEquals(1, actualSemanticChronology.versions().size(), "Versions are missing from the Semantic Chronology.");
        });
    }

    @Test
    @DisplayName("Transform a Semantic Chronology With Missing Public ID's")
    public void semanticChronologyTransformWithAMissingPublicId(){
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given a PBSemanticChronology with a no Semantic Public ID's present
            Concept testConcept = conceptMap.get(TEST_CONCEPT_NAME);

            StampVersion pbStampVersion = StampVersion.newBuilder()
                    .setStatus(createPBPublicId(conceptMap.get(STATUS_CONCEPT_NAME)))
                    .setTime(nowTimestamp())
                    .setAuthor(createPBPublicId(conceptMap.get(AUTHOR_CONCEPT_NAME)))
                    .setModule(createPBPublicId(conceptMap.get(MODULE_CONCEPT_NAME)))
                    .setPath(createPBPublicId(conceptMap.get(PATH_CONCEPT_NAME)))
                    .build();

            StampChronology pbStampChronology = StampChronology.newBuilder()
                    .setPublicId(createPBPublicId(testConcept))
                    .addVersions(pbStampVersion)
                    .build();

            String expectedStringValue = "Testing Field Transformation with a string.";
            Field pbFieldString = Field.newBuilder()
                    .setString(expectedStringValue)
                    .build();

            SemanticVersion pbSemanticVersion = SemanticVersion.newBuilder()
                    .setStamp(pbStampChronology)
                    .addFields(pbFieldString)
                    .build();

            SemanticChronology pbSemanticChronology = SemanticChronology.newBuilder()
                    .setReferencedComponent(createPBPublicId(conceptMap.get(MODULE_CONCEPT_NAME)))
                    .setPatternForSemantic(createPBPublicId(conceptMap.get(PATH_CONCEPT_NAME)))
                    .addVersions(pbSemanticVersion)
                    .build();

            // When we transform PBSemanticChronology

            // Then we will throw a Runtime exception
            assertThrows(Throwable.class, () -> TinkarSchemaToEntityTransformer.getInstance().transformSemanticChronology(pbSemanticChronology), "Not allowed to have no semantic versions.");
            });
    }
    //TODO: Add more coverage to Semantic missing fields
}
