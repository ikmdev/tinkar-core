package dev.ikm.tinkar.entity.transfom;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.schema.ConceptChronology;
import dev.ikm.tinkar.schema.ConceptVersion;
import dev.ikm.tinkar.schema.StampChronology;
import dev.ikm.tinkar.schema.StampVersion;
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

            dev.ikm.tinkar.schema.StampVersion pbStampVersion = dev.ikm.tinkar.schema.StampVersion.newBuilder()
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

            ConceptVersion pbConceptVersion = ConceptVersion.newBuilder()
                    .setStamp(pbStampChronology)
                    .build();

            ConceptChronology pbConceptChronology = ConceptChronology.newBuilder()
                    .addVersions(pbConceptVersion)
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
            ConceptChronology pbConceptChronology = ConceptChronology.newBuilder()
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

            ConceptVersion pbConceptVersion = ConceptVersion.newBuilder()
                    .setStamp(pbStampChronology)
                    .build();

            ConceptChronology pbConceptChronology = ConceptChronology.newBuilder()
                    .setPublicId(createPBPublicId(testConcept))
                    .addVersions(pbConceptVersion)
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

            StampVersion pbStampVersionTwo = StampVersion.newBuilder()
                    .setStatus(createPBPublicId(statusConcept))
                    .setTime(nowTimestamp())
                    .setAuthor(createPBPublicId(authorConcept))
                    .setModule(createPBPublicId(moduleConcept))
                    .setPath(createPBPublicId(pathConcept))
                    .build();

            StampChronology pbStampChronologyTwo = StampChronology.newBuilder()
                    .setPublicId(createPBPublicId(testConcept))
                    .addVersions(pbStampVersionTwo)
                    .build();

            StampVersion pbStampVersionOne = StampVersion.newBuilder()
                    .setStatus(createPBPublicId(statusConcept))
                    .setTime(nowTimestamp())
                    .setAuthor(createPBPublicId(authorConcept))
                    .setModule(createPBPublicId(moduleConcept))
                    .setPath(createPBPublicId(pathConcept))
                    .build();

            StampChronology pbStampChronologyOne = StampChronology.newBuilder()
                    .setPublicId(createPBPublicId(testConcept))
                    .addVersions(pbStampVersionOne)
                    .build();

            ConceptVersion pbConceptVersionOne = ConceptVersion.newBuilder()
                    .setStamp(pbStampChronologyOne)
                    .build();

            ConceptVersion pbConceptVersionTwo = ConceptVersion.newBuilder()
                    .setStamp(pbStampChronologyTwo)
                    .build();

            ConceptChronology pbConceptChronology = ConceptChronology.newBuilder()
                    .setPublicId(ProtobufToEntityTestHelper.createPBPublicId(testConcept))
                    .addVersions(pbConceptVersionOne)
                    .addVersions(pbConceptVersionTwo)
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
