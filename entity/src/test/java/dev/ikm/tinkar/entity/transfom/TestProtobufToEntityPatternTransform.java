package dev.ikm.tinkar.entity.transfom;

import com.google.protobuf.Timestamp;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.schema.*;
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
public class TestProtobufToEntityPatternTransform {
    private Concept testConcept;
    private Concept statusConcept;
    private Concept authorConcept;
    private Concept moduleConcept;
    private Concept pathConcept;
    private Concept referencedComponentPurposeConcept;
    private Concept referencedComponentMeaningConcept;
    //Field Definition Concepts
    private Concept meaningConcept;
    private Concept dataTypeConcept;
    private Concept purposeConcept;
    private MockedStatic<Entity> mockedEntityService;
    private PatternEntity mockPatternEntity;
    private long expectedTime;


    @BeforeAll
    public void init() {
        statusConcept = EntityProxy.Concept.make("statusConcept", UUID.fromString("d130880f-a8aa-4ac5-8265-483deab701ec"));
        authorConcept = EntityProxy.Concept.make("authorConcept", UUID.fromString("76fdab49-b0ee-4c83-900e-8064103ef3b0"));
        moduleConcept = EntityProxy.Concept.make("moduleConcept", UUID.fromString("840928b5-480c-4e8d-af77-7c817e880aed"));
        pathConcept = EntityProxy.Concept.make("pathConcept", UUID.fromString("4fa15e05-5c48-470a-a6f0-2080e725e6fb"));
        testConcept = EntityProxy.Concept.make("testConcept", UUID.fromString("e813eb92-7d07-5035-8d43-e81249f5b36e"));
        referencedComponentPurposeConcept = EntityProxy.Concept.make("referencedComponentPurposeConcept", UUID.fromString("f76f0c7b-3180-4712-b4d6-f4a6854c0cd1"));
        referencedComponentMeaningConcept = EntityProxy.Concept.make("referencedComponentMeaningConcept", UUID.fromString("0ecad4ab-dc5e-4803-ae42-a8582ee24904"));
        meaningConcept = EntityProxy.Concept.make("meaningConcept", UUID.fromString("01465728-40d7-404a-b863-05072fba32f9"));
        dataTypeConcept = EntityProxy.Concept.make("dataTypeConcept", UUID.fromString("7eeebc88-b092-4750-b4ce-d0464cce8721"));
        purposeConcept = EntityProxy.Concept.make("purposeConcept", UUID.fromString("0f3d353a-54e3-4bb3-b4a0-e9a8bf7ce472"));

        mockedEntityService = Mockito.mockStatic(Entity.class);
        mockedEntityService.when(() -> Entity.nid(statusConcept.publicId())).thenReturn(10);
        mockedEntityService.when(() -> Entity.nid(authorConcept.publicId())).thenReturn(20);
        mockedEntityService.when(() -> Entity.nid(moduleConcept.publicId())).thenReturn(30);
        mockedEntityService.when(() -> Entity.nid(pathConcept.publicId())).thenReturn(40);
        mockedEntityService.when(() -> Entity.nid(testConcept.publicId())).thenReturn(50);
        mockedEntityService.when(() -> Entity.nid(referencedComponentPurposeConcept.publicId())).thenReturn(60);
        mockedEntityService.when(() -> Entity.nid(referencedComponentMeaningConcept.publicId())).thenReturn(70);
        mockedEntityService.when(() -> Entity.nid(meaningConcept.publicId())).thenReturn(80);
        mockedEntityService.when(() -> Entity.nid(dataTypeConcept.publicId())).thenReturn(90);
        mockedEntityService.when(() -> Entity.nid(purposeConcept.publicId())).thenReturn(100);

        mockPatternEntity = mock(PatternEntity.class);

        expectedTime = Instant.now().getEpochSecond();

    }

    //TODO - Add unit tests for variations of Pattern Version transformation

    @Test
    public void patternChronologyTransformWithZeroVersion(){
        // Given a PBPatternChronology with no Pattern Versions present
        PBPatternChronology pbPatternChronology = PBPatternChronology.newBuilder()
                .setPublicId(ProtobufToEntityTestHelper.createPBPublicId(testConcept))
                .build();

        // When we transform PBPatternChronology

        // Then we will throw a Runtime exception
        assertThrows(Throwable.class, () -> ProtobufTransformer.transformPatternChronology(pbPatternChronology), "Not allowed to have no pattern versions.");
    }
    @Test
    public void patternChronologyTransformWithOneVersion(){
        // Given a PBPatternChronology with a pattern version present
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

        PBFieldDefinition pbFieldDefinitionOne = PBFieldDefinition.newBuilder()
                .setMeaning(ProtobufToEntityTestHelper.createPBPublicId(meaningConcept))
                .setDataType(ProtobufToEntityTestHelper.createPBPublicId(dataTypeConcept))
                .setPurpose(ProtobufToEntityTestHelper.createPBPublicId(purposeConcept))
                .build();

        PBPatternVersion pbPatternVersionOne = PBPatternVersion.newBuilder()
                .setStamp(pbStampChronologyOne)
                .setReferencedComponentPurpose(ProtobufToEntityTestHelper.createPBPublicId(referencedComponentPurposeConcept))
                .setReferencedComponentMeaning(ProtobufToEntityTestHelper.createPBPublicId(referencedComponentMeaningConcept))
                .addFieldDefinitions(pbFieldDefinitionOne)
                .build();

        PBPatternChronology pbPatternChronologyOne = PBPatternChronology.newBuilder()
                .setPublicId(ProtobufToEntityTestHelper.createPBPublicId(testConcept))
                .addVersions(pbPatternVersionOne)
                .build();

        // When we transform PBPatternChronology
        PatternEntity actualPatternChronologyOne = ProtobufTransformer.transformPatternChronology(pbPatternChronologyOne);

        // Then the resulting PatternChronology should match the original PBPatternChronology
        assertEquals(50, actualPatternChronologyOne.nid(), "Nid's did not match in Pattern Chronology.");
        assertTrue(PublicId.equals(testConcept.publicId(), actualPatternChronologyOne.publicId()), "Public Id's of the pattern chronology do not match.");
        assertEquals(1, actualPatternChronologyOne.versions().size(), "Versions are empty");
    }

    @Test
    public void patternChronologyTransformWithTwoVersions(){
        // Given a PBPatternChronology with two pattern versions present
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

        PBFieldDefinition pbFieldDefinitionTwo = PBFieldDefinition.newBuilder()
                .setMeaning(ProtobufToEntityTestHelper.createPBPublicId(meaningConcept))
                .setDataType(ProtobufToEntityTestHelper.createPBPublicId(dataTypeConcept))
                .setPurpose(ProtobufToEntityTestHelper.createPBPublicId(purposeConcept))
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

        PBFieldDefinition pbFieldDefinitionOne = PBFieldDefinition.newBuilder()
                .setMeaning(ProtobufToEntityTestHelper.createPBPublicId(meaningConcept))
                .setDataType(ProtobufToEntityTestHelper.createPBPublicId(dataTypeConcept))
                .setPurpose(ProtobufToEntityTestHelper.createPBPublicId(purposeConcept))
                .build();

        PBPatternVersion pbPatternVersionOne = PBPatternVersion.newBuilder()
                .setStamp(pbStampChronologyOne)
                .setReferencedComponentPurpose(ProtobufToEntityTestHelper.createPBPublicId(referencedComponentPurposeConcept))
                .setReferencedComponentMeaning(ProtobufToEntityTestHelper.createPBPublicId(referencedComponentMeaningConcept))
                .addFieldDefinitions(pbFieldDefinitionOne)
                .build();

        PBPatternVersion pbPatternVersionTwo = PBPatternVersion.newBuilder()
                .setStamp(pbStampChronologyTwo)
                .setReferencedComponentPurpose(ProtobufToEntityTestHelper.createPBPublicId(referencedComponentPurposeConcept))
                .setReferencedComponentMeaning(ProtobufToEntityTestHelper.createPBPublicId(referencedComponentMeaningConcept))
                .addFieldDefinitions(pbFieldDefinitionTwo)
                .addFieldDefinitions(pbFieldDefinitionTwo)
                .build();

        PBPatternChronology pbPatternChronologyOne = PBPatternChronology.newBuilder()
                .setPublicId(ProtobufToEntityTestHelper.createPBPublicId(testConcept))
                .addVersions(pbPatternVersionOne)
                .addVersions(pbPatternVersionTwo)
                .build();

        // When we transform PBPatternChronology
        PatternEntity actualPatternChronologyOne = ProtobufTransformer.transformPatternChronology(pbPatternChronologyOne);

        // Then the resulting PatternChronology should match the original PBPatternChronology
        assertEquals(50, actualPatternChronologyOne.nid(), "Nid's did not match in Pattern Chronology.");
        assertTrue(PublicId.equals(testConcept.publicId(), actualPatternChronologyOne.publicId()), "Public Id's of the pattern chronology do not match.");
        assertEquals(2, actualPatternChronologyOne.versions().size(), "Versions are empty");
    }
}
