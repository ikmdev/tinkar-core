package dev.ikm.tinkar.entity.transfom;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.schema.PBConceptChronology;
import dev.ikm.tinkar.schema.PBConceptVersion;
import dev.ikm.tinkar.schema.PBStampChronology;
import dev.ikm.tinkar.schema.PBStampVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;


import static dev.ikm.tinkar.entity.transfom.ProtobufToEntityTestHelper.createPBPublicId;
import static dev.ikm.tinkar.entity.transfom.ProtobufToEntityTestHelper.nowTimestamp;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestProtobufToEntityConceptTransform extends AbstractTestProtobufTransform{

    @BeforeAll
    public void init() {
        super.init();
    }
    @Test
    public void conceptChronologyTransformWithZeroVersion(){
            // Given a PBConceptChronology with a no Stamp Versions present
            PBConceptChronology pbConceptChronology = PBConceptChronology.newBuilder()
                    .setPublicId(createPBPublicId(testConcept))
                    .build();

            // When we transform PBConceptChronology

            // Then we will throw a Runtime exception
            assertThrows(Throwable.class, () -> ProtobufTransformer.transformConceptChronology(pbConceptChronology), "Not allowed to have no stamp versions.");
    }
    @Test
    public void conceptChronologyTransformWithOneVersion(){
            // Given a PBConceptChronology with a one Stamp Version present
            PBStampVersion pbStampVersion = PBStampVersion.newBuilder()
                    .setStatus(createPBPublicId(statusConcept))
                    .setTime(nowTimestamp())
                    .setAuthor(createPBPublicId(authorConcept))
                    .setModule(createPBPublicId(moduleConcept))
                    .setPath(createPBPublicId(pathConcept))
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
            ConceptEntity actualConceptChronology = ProtobufTransformer.transformConceptChronology(pbConceptChronology);

            // Then the resulting ConceptChronology should match the original PBConceptChronology
            int expectedConceptChronologyNid = nid(testConcept); // 50
            assertEquals(expectedConceptChronologyNid, actualConceptChronology.nid(), "Nid's did not match in Concept Chronology.");
            assertTrue(PublicId.equals(testConcept.publicId(), actualConceptChronology.publicId()), "Public Id's of the concept chronology do not match.");
            assertEquals(1, actualConceptChronology.versions().size(), "Versions are empty");
            //TODO: do we need to test details of Stamp Version?
//            assertEquals(expectedTime, actualConceptChronology.versions().get(0).time(), "Time did not match");
    }

    @Test
    public void conceptChronologyTransformWithTwoVersions(){
        // Given a PBConceptChronology with two Stamp Versions present
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
        ConceptEntity actualConceptChronologyTwo = ProtobufTransformer.transformConceptChronology(pbConceptChronology);

        // Then the resulting ConceptChronology should match the original PBConceptChronology
        assertEquals(nid(testConcept), actualConceptChronologyTwo.nid(), "Nid's did not match in concept Chronology.");
        assertTrue(PublicId.equals(testConcept.publicId(), actualConceptChronologyTwo.publicId()), "Public Id's of the concept chronology do not match.");
        assertEquals(2, actualConceptChronologyTwo.versions().size(), "Versions are empty");
        //TODO: do we need to test details of Stamp Version?
    }
}
