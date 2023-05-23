package dev.ikm.tinkar.entity.transfom;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.schema.PBConceptChronology;
import dev.ikm.tinkar.schema.PBConceptVersion;
import dev.ikm.tinkar.schema.PBStampChronology;
import dev.ikm.tinkar.schema.PBStampVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static dev.ikm.tinkar.entity.transfom.ProtobufToEntityTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestProtobufToEntityConceptTransform {

    @Test
    @DisplayName("Transform a Concept Chronology With Zero Public Id's")
    public void conceptChronologyTransformWithZeroPublicIds(){
        openSession(this, (mockedEntityService, conceptMap) -> {

            // Given a PBConceptChronology with a no Stamp Versions present
            Concept testConcept = conceptMap.get(TEST_CONCEPT_NAME);

            PBStampVersion pbStampVersion = PBStampVersion.newBuilder()
                    .setStatus(createPBPublicId(conceptMap.get(STATUS_CONCEPT_NAME)))
                    .setTime(nowTimestamp())
                    .setAuthor(createPBPublicId(conceptMap.get(AUTHOR_CONCEPT_NAME)))
                    .setModule(createPBPublicId(conceptMap.get(MODULE_CONCEPT_NAME)))
                    .setPath(createPBPublicId(conceptMap.get(PATH_CONCEPT_NAME)))
                    .build();

            PBStampChronology pbStampChronology = PBStampChronology.newBuilder()
                    .setPublicId(createPBPublicId(testConcept))
                    .addStampVersions(pbStampVersion)
                    .build();

            PBConceptVersion pbConceptVersion = PBConceptVersion.newBuilder()
                    .setStamp(pbStampChronology)
                    .build();

            PBConceptChronology pbConceptChronology = PBConceptChronology.newBuilder()
                    .addConceptVersions(pbConceptVersion)
                    .build();

            // When we transform PBConceptChronology

            // Then we will throw a Runtime exception
            assertThrows(Throwable.class, () -> TinkarSchemaToEntityTransformer.getInstance().transformConceptChronology(pbConceptChronology), "Not allowed to have no public id's.");
        });
    }

    @Test
    @DisplayName("Transform a Concept Chronology With Zero Versions")
    public void conceptChronologyTransformWithZeroVersion(){
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given a PBConceptChronology with a no Stamp Versions present
            PBConceptChronology pbConceptChronology = PBConceptChronology.newBuilder()
                    .setPublicId(createPBPublicId(conceptMap.get(TEST_CONCEPT_NAME)))
                    .build();

            // When we transform PBConceptChronology

            // Then we will throw a Runtime exception
            assertThrows(Throwable.class, () -> TinkarSchemaToEntityTransformer.getInstance().transformConceptChronology(pbConceptChronology), "Not allowed to have no stamp versions.");
        });
    }
    @Test
    @DisplayName("Transform a Concept Chronology With One Version")
    public void conceptChronologyTransformWithOneVersion(){
        openSession(this, (mockedEntity, conceptMap) -> {
            // Given a PBConceptChronology with a one Stamp Version present
            Concept testConcept = conceptMap.get(TEST_CONCEPT_NAME);

            PBStampVersion pbStampVersion = PBStampVersion.newBuilder()
                    .setStatus(createPBPublicId(conceptMap.get(STATUS_CONCEPT_NAME)))
                    .setTime(nowTimestamp())
                    .setAuthor(createPBPublicId(conceptMap.get(AUTHOR_CONCEPT_NAME)))
                    .setModule(createPBPublicId(conceptMap.get(MODULE_CONCEPT_NAME)))
                    .setPath(createPBPublicId(conceptMap.get(PATH_CONCEPT_NAME)))
                    .build();

            PBStampChronology pbStampChronology = PBStampChronology.newBuilder()
                    .setPublicId(createPBPublicId(testConcept))
                    .addStampVersions(pbStampVersion)
                    .build();

            PBConceptVersion pbConceptVersion = PBConceptVersion.newBuilder()
                    .setStamp(pbStampChronology)
                    .build();

            PBConceptChronology pbConceptChronology = PBConceptChronology.newBuilder()
                    .setPublicId(createPBPublicId(testConcept))
                    .addConceptVersions(pbConceptVersion)
                    .build();

            // When we transform PBConceptChronology
            ConceptEntity actualConceptChronology = TinkarSchemaToEntityTransformer.getInstance().transformConceptChronology(pbConceptChronology);

            // Then the resulting ConceptChronology should match the original PBConceptChronology
            assertEquals(nid(testConcept), actualConceptChronology.nid(), "Nid's did not match in Concept Chronology.");
            assertTrue(PublicId.equals(testConcept.publicId(), actualConceptChronology.publicId()), "Public Id's of the concept chronology do not match.");
            assertEquals(1, actualConceptChronology.versions().size(), "Versions are empty");
            //TODO: do we need to test details of Stamp Version?
//            assertEquals(expectedTime, actualConceptChronology.versions().get(0).time(), "Time did not match");
        });

    }

    // TODO write test to fail when stamp version is the same (time, author, etc.)
    @Test
    @DisplayName("Transform a Concept Chronology With Two Versions")
    public void conceptChronologyTransformWithTwoVersions() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given a PBConceptChronology with two Stamp Versions present
            Concept testConcept = conceptMap.get(TEST_CONCEPT_NAME);
            Concept statusConcept = conceptMap.get(STATUS_CONCEPT_NAME);
            Concept authorConcept = conceptMap.get(AUTHOR_CONCEPT_NAME);
            Concept moduleConcept = conceptMap.get(MODULE_CONCEPT_NAME);
            Concept pathConcept = conceptMap.get(PATH_CONCEPT_NAME);

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

            PBConceptVersion pbConceptVersionOne = PBConceptVersion.newBuilder()
                    .setStamp(pbStampChronologyOne)
                    .build();

            PBConceptVersion pbConceptVersionTwo = PBConceptVersion.newBuilder()
                    .setStamp(pbStampChronologyTwo)
                    .build();

            PBConceptChronology pbConceptChronology = PBConceptChronology.newBuilder()
                    .setPublicId(ProtobufToEntityTestHelper.createPBPublicId(testConcept))
                    .addConceptVersions(pbConceptVersionOne)
                    .addConceptVersions(pbConceptVersionTwo)
                    .build();

            // When we transform PBConceptChronology
            ConceptEntity actualConceptChronologyTwo = TinkarSchemaToEntityTransformer.getInstance().transformConceptChronology(pbConceptChronology);

            // Then the resulting ConceptChronology should match the original PBConceptChronology
            assertEquals(nid(testConcept), actualConceptChronologyTwo.nid(), "Nid's did not match in concept Chronology.");
            assertTrue(PublicId.equals(testConcept.publicId(), actualConceptChronologyTwo.publicId()), "Public Id's of the concept chronology do not match.");
            assertEquals(2, actualConceptChronologyTwo.versions().size(), "Versions are empty");
            //TODO: do we need to test details of Stamp Version?
        });
    }
}
