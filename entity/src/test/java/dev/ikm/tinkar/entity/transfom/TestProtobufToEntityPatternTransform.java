package dev.ikm.tinkar.entity.transfom;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.schema.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import static dev.ikm.tinkar.entity.transfom.ProtobufToEntityTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestProtobufToEntityPatternTransform {
    private PatternEntity mockPatternEntity;


    @BeforeAll
    public void init() {
    }
    //TODO - Add unit tests for variations of Pattern Version transformation

    @Test
    @DisplayName("Transform a Pattern Chronology With Zero Versions Present")
    public void patternChronologyTransformWithZeroVersion() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given a PBPatternChronology with no Pattern Versions present
            PBPatternChronology pbPatternChronology = PBPatternChronology.newBuilder()
                    .setPublicId(createPBPublicId(conceptMap.get(TEST_CONCEPT_NAME)))
                    .build();

            // When we transform PBPatternChronology

            // Then we will throw a Runtime exception
            assertThrows(Throwable.class, () -> ProtobufTransformer.getInstance().transformPatternChronology(pbPatternChronology), "Not allowed to have no pattern versions.");
        });

    }
    @Test
    @DisplayName("Transform a Pattern Chronology With One Version Present")
    public void patternChronologyTransformWithOneVersion(){
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given a PBPatternChronology with a pattern version present
            Concept statusConcept = conceptMap.get(STATUS_CONCEPT_NAME);
            Concept authorConcept = conceptMap.get(AUTHOR_CONCEPT_NAME);
            Concept moduleConcept = conceptMap.get(MODULE_CONCEPT_NAME);
            Concept pathConcept = conceptMap.get(PATH_CONCEPT_NAME);
            Concept testConcept = conceptMap.get(TEST_CONCEPT_NAME);
            Concept meaningConcept = conceptMap.get(MEANING_CONCEPT_NAME);
            Concept dataTypeConcept = conceptMap.get(DATATYPE_CONCEPT_NAME);
            Concept purposeConcept = conceptMap.get(PURPOSE_CONCEPT_NAME);
            Concept referencedComponentPurposeConcept = conceptMap.get(REF_COMP_PURPOSE_CONCEPT_NAME);
            Concept referencedComponentMeaningConcept = conceptMap.get(REF_COMP_MEANING_CONCEPT_NAME);

            PBStampVersion pbStampVersionOne = PBStampVersion.newBuilder()
                    .setStatus(createPBPublicId(statusConcept))
                    .setTime(nowTimestamp())
                    .setAuthor(createPBPublicId(authorConcept))
                    .setModule(createPBPublicId(moduleConcept))
                    .setPath(createPBPublicId(pathConcept))
                    .build();

            PBStampChronology pbStampChronologyOne = PBStampChronology.newBuilder()
                    .setPublicId(createPBPublicId(testConcept))
                    .addStampVersions(pbStampVersionOne)
                    .build();

            PBFieldDefinition pbFieldDefinitionOne = PBFieldDefinition.newBuilder()
                    .setMeaning(createPBPublicId(meaningConcept))
                    .setDataType(createPBPublicId(dataTypeConcept))
                    .setPurpose(createPBPublicId(purposeConcept))
                    .build();

            PBPatternVersion pbPatternVersionOne = PBPatternVersion.newBuilder()
                    .setStamp(pbStampChronologyOne)
                    .setReferencedComponentPurpose(createPBPublicId(referencedComponentPurposeConcept))
                    .setReferencedComponentMeaning(createPBPublicId(referencedComponentMeaningConcept))
                    .addFieldDefinitions(pbFieldDefinitionOne)
                    .build();

            PBPatternChronology pbPatternChronologyOne = PBPatternChronology.newBuilder()
                    .setPublicId(createPBPublicId(testConcept))
                    .addVersions(pbPatternVersionOne)
                    .build();

            // When we transform PBPatternChronology
            PatternEntity actualPatternChronologyOne = ProtobufTransformer.getInstance().transformPatternChronology(pbPatternChronologyOne);

            // Then the resulting PatternChronology should match the original PBPatternChronology
            assertEquals(nid(testConcept), actualPatternChronologyOne.nid(), "Nid's did not match in Pattern Chronology.");
            assertTrue(PublicId.equals(testConcept.publicId(), actualPatternChronologyOne.publicId()), "Public Id's of the pattern chronology do not match.");
            assertEquals(1, actualPatternChronologyOne.versions().size(), "Versions are empty");
        });


    }

    @Test
    @DisplayName("Transform a Pattern Chronology With Two Versions Present")
    public void patternChronologyTransformWithTwoVersions() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given a PBPatternChronology with two pattern versions present
            Concept statusConcept = conceptMap.get(STATUS_CONCEPT_NAME);
            Concept authorConcept = conceptMap.get(AUTHOR_CONCEPT_NAME);
            Concept moduleConcept = conceptMap.get(MODULE_CONCEPT_NAME);
            Concept pathConcept = conceptMap.get(PATH_CONCEPT_NAME);
            Concept testConcept = conceptMap.get(TEST_CONCEPT_NAME);
            Concept meaningConcept = conceptMap.get(MEANING_CONCEPT_NAME);
            Concept dataTypeConcept = conceptMap.get(DATATYPE_CONCEPT_NAME);
            Concept purposeConcept = conceptMap.get(PURPOSE_CONCEPT_NAME);
            Concept referencedComponentPurposeConcept = conceptMap.get(REF_COMP_PURPOSE_CONCEPT_NAME);
            Concept referencedComponentMeaningConcept = conceptMap.get(REF_COMP_MEANING_CONCEPT_NAME);

            PBStampVersion pbStampVersionTwo = PBStampVersion.newBuilder()
                    .setStatus(createPBPublicId(statusConcept))
                    .setTime(nowTimestamp())
                    .setAuthor(createPBPublicId(authorConcept))
                    .setModule(createPBPublicId(moduleConcept))
                    .setPath(createPBPublicId(pathConcept))
                    .build();

            PBStampChronology pbStampChronologyTwo = PBStampChronology.newBuilder()
                    .setPublicId(createPBPublicId(testConcept))
                    .addStampVersions(pbStampVersionTwo)
                    .build();

            PBFieldDefinition pbFieldDefinitionTwo = PBFieldDefinition.newBuilder()
                    .setMeaning(createPBPublicId(meaningConcept))
                    .setDataType(createPBPublicId(dataTypeConcept))
                    .setPurpose(createPBPublicId(purposeConcept))
                    .build();

            PBStampVersion pbStampVersionOne = PBStampVersion.newBuilder()
                    .setStatus(createPBPublicId(statusConcept))
                    .setTime(nowTimestamp())
                    .setAuthor(createPBPublicId(authorConcept))
                    .setModule(createPBPublicId(moduleConcept))
                    .setPath(createPBPublicId(pathConcept))
                    .build();

            PBStampChronology pbStampChronologyOne = PBStampChronology.newBuilder()
                    .setPublicId(createPBPublicId(testConcept))
                    .addStampVersions(pbStampVersionOne)
                    .build();

            PBFieldDefinition pbFieldDefinitionOne = PBFieldDefinition.newBuilder()
                    .setMeaning(createPBPublicId(meaningConcept))
                    .setDataType(createPBPublicId(dataTypeConcept))
                    .setPurpose(createPBPublicId(purposeConcept))
                    .build();

            PBPatternVersion pbPatternVersionOne = PBPatternVersion.newBuilder()
                    .setStamp(pbStampChronologyOne)
                    .setReferencedComponentPurpose(createPBPublicId(referencedComponentPurposeConcept))
                    .setReferencedComponentMeaning(createPBPublicId(referencedComponentMeaningConcept))
                    .addFieldDefinitions(pbFieldDefinitionOne)
                    .build();

            PBPatternVersion pbPatternVersionTwo = PBPatternVersion.newBuilder()
                    .setStamp(pbStampChronologyTwo)
                    .setReferencedComponentPurpose(createPBPublicId(referencedComponentPurposeConcept))
                    .setReferencedComponentMeaning(createPBPublicId(referencedComponentMeaningConcept))
                    .addFieldDefinitions(pbFieldDefinitionTwo)
                    .addFieldDefinitions(pbFieldDefinitionTwo)
                    .build();

            PBPatternChronology pbPatternChronologyOne = PBPatternChronology.newBuilder()
                    .setPublicId(createPBPublicId(testConcept))
                    .addVersions(pbPatternVersionOne)
                    .addVersions(pbPatternVersionTwo)
                    .build();

            // When we transform PBPatternChronology
            PatternEntity actualPatternChronologyOne = ProtobufTransformer.getInstance().transformPatternChronology(pbPatternChronologyOne);

            // Then the resulting PatternChronology should match the original PBPatternChronology
            assertEquals(nid(testConcept), actualPatternChronologyOne.nid(), "Nid's did not match in Pattern Chronology.");
            assertTrue(PublicId.equals(testConcept.publicId(), actualPatternChronologyOne.publicId()), "Public Id's of the pattern chronology do not match.");
            assertEquals(2, actualPatternChronologyOne.versions().size(), "Versions are empty");
        });

    }
}
