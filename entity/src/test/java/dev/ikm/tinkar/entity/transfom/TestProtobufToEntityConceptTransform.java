package dev.ikm.tinkar.entity.transfom;

import com.google.protobuf.Timestamp;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.schema.PBConceptChronology;
import dev.ikm.tinkar.schema.PBConceptVersion;
import dev.ikm.tinkar.schema.PBStampChronology;
import dev.ikm.tinkar.schema.PBStampVersion;
import dev.ikm.tinkar.terms.EntityProxy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestProtobufToEntityConceptTransform {

    private Concept testConcept;
    private Concept statusConcept;
    private Concept authorConcept;
    private Concept pathConcept;
    private Concept moduleConcept;
    private long expectedTime;
    private MockedStatic<Entity> mockedEntityService;
    private ConceptEntity mockConceptEntity;
    @BeforeAll
    public void init() {
        testConcept = EntityProxy.Concept.make("testConcept", UUID.fromString("e813eb92-7d07-5035-8d43-e81249f5b36e"));
        moduleConcept = EntityProxy.Concept.make("moduleConcept", UUID.fromString("840928b5-480c-4e8d-af77-7c817e880aed"));
        pathConcept = EntityProxy.Concept.make("pathConcept", UUID.fromString("4fa15e05-5c48-470a-a6f0-2080e725e6fb"));
        authorConcept = EntityProxy.Concept.make("authorConcept", UUID.fromString("76fdab49-b0ee-4c83-900e-8064103ef3b0"));
        statusConcept = EntityProxy.Concept.make("statusConcept", UUID.fromString("d130880f-a8aa-4ac5-8265-483deab701ec"));

        mockedEntityService = Mockito.mockStatic(Entity.class);
        mockedEntityService.when(() -> Entity.nid(statusConcept.publicId())).thenReturn(10);
        mockedEntityService.when(() -> Entity.nid(authorConcept.publicId())).thenReturn(20);
        mockedEntityService.when(() -> Entity.nid(moduleConcept.publicId())).thenReturn(30);
        mockedEntityService.when(() -> Entity.nid(pathConcept.publicId())).thenReturn(40);
        mockedEntityService.when(() -> Entity.nid(testConcept.publicId())).thenReturn(50);

        mockConceptEntity = mock(ConceptEntity.class);

        expectedTime = Instant.now().getEpochSecond();

    }
    @Test
    public void conceptChronologyTransformWithZeroVersion(){
            // Given a PBConceptChronology with a no Stamp Versions present
            mockedEntityService.when(() -> Entity.nid(testConcept.publicId())).thenReturn(50);

            PBConceptChronology pbConceptChronology = PBConceptChronology.newBuilder()
                    .setPublicId(ProtobufToEntityTestHelper.createPBPublicId(testConcept))
                    .build();

            // When we transform PBConceptChronology

            // Then we will throw a Runtime exception
            assertThrows(Throwable.class, () -> ProtobufTransformer.transformConceptChronology(pbConceptChronology), "Not allowed to have no stamp versions.");
    }
    @Test
    public void conceptChronologyTransformWithOneVersion(){
            // Given a PBConceptChronology with a one Stamp Version present
            PBStampVersion pbStampVersion = PBStampVersion.newBuilder()
                    .setStatus(ProtobufToEntityTestHelper.createPBPublicId(statusConcept))
                    .setTime(Timestamp.newBuilder().setSeconds(expectedTime).build())
                    .setAuthor(ProtobufToEntityTestHelper.createPBPublicId(authorConcept))
                    .setModule(ProtobufToEntityTestHelper.createPBPublicId(moduleConcept))
                    .setPath(ProtobufToEntityTestHelper.createPBPublicId(pathConcept))
                    .build();

            PBStampChronology pbStampChronology = PBStampChronology.newBuilder()
                    .setPublicId(ProtobufToEntityTestHelper.createPBPublicId(testConcept))
                    .addStampVersions(pbStampVersion)
                    .build();

            PBConceptVersion pbConceptVersion = PBConceptVersion.newBuilder()
                    .setStamp(pbStampChronology)
                    .build();

            PBConceptChronology pbConceptChronology = PBConceptChronology.newBuilder()
                    .setPublicId(ProtobufToEntityTestHelper.createPBPublicId(testConcept))
                    .addConceptVersions(pbConceptVersion)
                    .build();

            // When we transform PBConceptChronology
            ConceptEntity actualConceptChronology = ProtobufTransformer.transformConceptChronology(pbConceptChronology);

            // Then the resulting ConceptChronology should match the original PBConceptChronology
            assertEquals(50, actualConceptChronology.nid(), "Nid's did not match in Concept Chronology.");
            assertTrue(PublicId.equals(testConcept.publicId(), actualConceptChronology.publicId()), "Public Id's of the concept chronology do not match.");
            assertEquals(1, actualConceptChronology.versions().size(), "Versions are empty");
            //TODO: do we need to test details of Stamp Version?
//            assertEquals(expectedTime, actualConceptChronology.versions().get(0).time(), "Time did not match");
    }

    @Test
    public void conceptChronologyTransformWithTwoVersions(){
        // Given a PBConceptChronology with two Stamp Versions present
        PBStampVersion pbStampVersionTwo = PBStampVersion.newBuilder()
                .setStatus(ProtobufToEntityTestHelper.createPBPublicId(statusConcept))
                .setTime(Timestamp.newBuilder().setSeconds(expectedTime).build())
                .setAuthor(ProtobufToEntityTestHelper.createPBPublicId(authorConcept))
                .setModule(ProtobufToEntityTestHelper.createPBPublicId(moduleConcept))
                .setPath(ProtobufToEntityTestHelper.createPBPublicId(pathConcept))
                .build();

        PBStampChronology pbStampChronologyTwo = PBStampChronology.newBuilder()
                .setPublicId(ProtobufToEntityTestHelper.createPBPublicId(testConcept))
                .addStampVersions(pbStampVersionTwo)
                .build();

        PBStampVersion pbStampVersionOne = PBStampVersion.newBuilder()
                .setStatus(ProtobufToEntityTestHelper.createPBPublicId(statusConcept))
                .setTime(Timestamp.newBuilder().setSeconds(expectedTime).build())
                .setAuthor(ProtobufToEntityTestHelper.createPBPublicId(authorConcept))
                .setModule(ProtobufToEntityTestHelper.createPBPublicId(moduleConcept))
                .setPath(ProtobufToEntityTestHelper.createPBPublicId(pathConcept))
                .build();

        PBStampChronology pbStampChronologyOne = PBStampChronology.newBuilder()
                .setPublicId(ProtobufToEntityTestHelper.createPBPublicId(testConcept))
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
        assertEquals(50, actualConceptChronologyTwo.nid(), "Nid's did not match in concept Chronology.");
        assertTrue(PublicId.equals(testConcept.publicId(), actualConceptChronologyTwo.publicId()), "Public Id's of the concept chronology do not match.");
        assertEquals(2, actualConceptChronologyTwo.versions().size(), "Versions are empty");
        //TODO: do we need to test details of Stamp Version?
    }
}
