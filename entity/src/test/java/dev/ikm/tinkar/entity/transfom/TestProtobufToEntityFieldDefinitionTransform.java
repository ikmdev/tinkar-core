package dev.ikm.tinkar.entity.transfom;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.component.FieldDefinition;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.FieldDefinitionRecord;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.schema.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import static dev.ikm.tinkar.entity.transfom.ProtobufToEntityTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestProtobufToEntityFieldDefinitionTransform {
    @Test
    @DisplayName("Transform a Field Definition Transform With All Fields Present")
    public void testEntityFieldDefinitionTransformWithAllFieldPresent() {
        openSession(this, (mockedEntityService, conceptMap) -> {
        // Given a PB Field Description
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

        PatternEntity actualPatternChronologyOne = ProtobufTransformer.getInstance().transformPatternChronology(pbPatternChronologyOne);

            PBFieldDefinition pbFieldDefinition = PBFieldDefinition.newBuilder()
                    .setMeaning(createPBPublicId(meaningConcept))
                    .setDataType(createPBPublicId(dataTypeConcept))
                    .setPurpose(createPBPublicId(purposeConcept))
                    .build();

            // When we transform a PBFieldDef
            FieldDefinitionRecord actualFieldDefinition = ProtobufTransformer.getInstance().transformFieldDefinitionRecord(pbFieldDefinition, actualPatternChronologyOne.nid(), actualPatternChronologyOne.nid());

            // Then we will create a Field Definition
            assertEquals(nid(dataTypeConcept), actualFieldDefinition.dataTypeNid(), "Nids did not match in Field Definitions Data Type.");
            assertEquals(nid(meaningConcept), actualFieldDefinition.meaningNid(), "Nids did not match in Field Definitions Meaning.");
            assertEquals(nid(purposeConcept), actualFieldDefinition.purposeNid(), "Nids did not match in Field Definitions Purpose.");
            assertEquals(actualPatternChronologyOne.nid(), actualFieldDefinition.patternNid());
        });
    }

    @Test
    @DisplayName("Transform a Field Definition Transform With a Missing Data Type")
    public void testEntityFieldDefinitionTransformWithMissingDataType() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            /**
             * Making Concept
             */
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

            PatternEntity actualPatternChronologyOne = ProtobufTransformer.getInstance().transformPatternChronology(pbPatternChronologyOne);
            /*
             * End of making Concept
             */

            PBFieldDefinition pbFieldDefinition = PBFieldDefinition.newBuilder()
                    .setMeaning(createPBPublicId(meaningConcept))
                    .setPurpose(createPBPublicId(purposeConcept))
                    .build();

            // When we transform PBFieldDef

            // Then we will throw an exception for a missing DataType field
            assertThrows(Throwable.class, () -> ProtobufTransformer.getInstance().transformFieldDefinitionRecord(pbFieldDefinition, actualPatternChronologyOne.nid(), actualPatternChronologyOne.nid()), "Not allowed to have a missing DataType in field definitions.");
        });
    }

    @Test
    @DisplayName("Transform a Field Definition Transform With a Missing Meaning")
    public void testEntityFieldDefinitionTransformWithMissingMeaning() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            /**
             * Making Concept
             */
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

            PatternEntity actualPatternChronologyOne = ProtobufTransformer.getInstance().transformPatternChronology(pbPatternChronologyOne);
            /*
             * End of making Concept
             */

            PBFieldDefinition pbFieldDefinition = PBFieldDefinition.newBuilder()
                    .setDataType(createPBPublicId(dataTypeConcept))
                    .setPurpose(createPBPublicId(purposeConcept))
                    .build();

            // When we transform PBFieldDef

            // Then we will throw an exception for a missing meaning field
            assertThrows(Throwable.class, () -> ProtobufTransformer.getInstance().transformFieldDefinitionRecord(pbFieldDefinition, actualPatternChronologyOne.nid(), actualPatternChronologyOne.nid()), "Not allowed to have a missing Meaning in field definitions..");
        });
    }

    @Test
    @DisplayName("Transform a Field Definition Transform With a Missing purpose")
    public void testEntityFieldDefinitionTransformWithMissingPurpose() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            /**
             * Making Concept
             */
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

            PatternEntity actualPatternChronologyOne = ProtobufTransformer.getInstance().transformPatternChronology(pbPatternChronologyOne);
            /*
             * End of making Concept
             */

            PBFieldDefinition pbFieldDefinition = PBFieldDefinition.newBuilder()
                    .setMeaning(createPBPublicId(meaningConcept))
                    .setDataType(createPBPublicId(dataTypeConcept))
                    .build();

            // When we transform PBFieldDef

            // Then we will throw an exception for a missing Purpose field
            assertThrows(Throwable.class, () -> ProtobufTransformer.getInstance().transformFieldDefinitionRecord(pbFieldDefinition, actualPatternChronologyOne.nid(), actualPatternChronologyOne.nid()), "Not allowed to have a missing Purpose in field definitions..");
        });
    }
}

