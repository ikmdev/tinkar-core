package dev.ikm.tinkar.entity.transfom;

import com.google.protobuf.Timestamp;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.StampVersionRecord;
import dev.ikm.tinkar.schema.PBStampChronology;
import dev.ikm.tinkar.schema.PBStampVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;


import static dev.ikm.tinkar.entity.transfom.ProtobufToEntityTestHelper.createPBPublicId;
import static dev.ikm.tinkar.entity.transfom.ProtobufToEntityTestHelper.nowTimestamp;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestProtobufToEntityStampTransform extends AbstractTestProtobufTransform {
    private StampRecord mockStampRecord;

    @BeforeAll
    public void init() {
        super.init();
        mockStampRecord = mock(StampRecord.class);
    }

    /**
     * Testing the transformation of a StampVersion Protobuf object to an Entity
     */
    @Test
    public void stampVersionTransformWithStatusTimeAuthorModulePathPresent() {
        // Given a PBStampVersion
        Timestamp expectedTime = nowTimestamp();
        PBStampVersion pbStampVersion = PBStampVersion.newBuilder()
                .setStatus(createPBPublicId(statusConcept))
                .setTime(expectedTime)
                .setAuthor(createPBPublicId(authorConcept))
                .setModule(createPBPublicId(moduleConcept))
                .setPath(createPBPublicId(pathConcept))
                .build();


        // When we transform PBStampVersion
        StampVersionRecord actualStampVersionRecord = ProtobufTransformer.transformStampVersion(pbStampVersion, mockStampRecord);

        // Then the resulting StampVersionRecord should match the original PBStampVersion
        assertEquals(10, actualStampVersionRecord.stateNid(), "Status Nid did not match");
        assertEquals(expectedTime.getSeconds(), actualStampVersionRecord.time(), "Time did not match");
        assertEquals(20, actualStampVersionRecord.authorNid(), "Author Nid did not match");
        assertEquals(30, actualStampVersionRecord.moduleNid(), "Module Nid did not match");
        assertEquals(40, actualStampVersionRecord.pathNid(), "Path Nid did not match");
        assertEquals(mockStampRecord, actualStampVersionRecord.chronology(), "Stamp Record did not match");
    }

    //TODO - Create unit tests testing for runtime exception for each blank UUID/Public ID in STAMP values
    /**
     * Testing the transformation of a StampVersion Protobuf object to an Entity with a missing Status.
     */
    @Test
    public void stampVersionTransformWithStatusBeingBlankPublicId() {
        // Given a PBStampVersion with a missing Public Id for Status
        PBStampVersion pbStampVersion = PBStampVersion.newBuilder()
                .setStatus(createPBPublicId())
                .setTime(nowTimestamp())
                .setAuthor(createPBPublicId(authorConcept))
                .setModule(createPBPublicId(moduleConcept))
                .setPath(createPBPublicId(pathConcept))
                .build();

        // When we transform PBStampVersion

        // Then we will throw a Runtime exception
        assertThrows(Throwable.class, () -> ProtobufTransformer.transformStampVersion(pbStampVersion, mockStampRecord), "Not allowed to have empty UUID for status.");
    }

    /**
     * Testing the transformation of a StampVersion Protobuf object to an Entity with a missing Author.
     */
    @Test
    public void stampVersionTransformWithAuthorBeingBlankPublicId() {
        // Given a PBStampVersion with a missing Public Id for Author
        PBStampVersion pbStampVersion = PBStampVersion.newBuilder()
                .setStatus(createPBPublicId(statusConcept))
                .setTime(nowTimestamp())
                .setAuthor(createPBPublicId())
                .setModule(createPBPublicId(moduleConcept))
                .setPath(createPBPublicId(pathConcept))
                .build();

        // When we transform PBStampVersion

        // Then we will throw a Runtime exception
        assertThrows(Throwable.class, () -> ProtobufTransformer.transformStampVersion(pbStampVersion, mockStampRecord), "Not allowed to have empty UUID for author.");
    }

    /**
     * Testing the transformation of a StampVersion Protobuf object to an Entity with a missing Module.
     */
    @Test
    public void stampVersionTransformWithModuleBeingBlankPublicId() {
        // Given a PBStampVersion with a missing Public Id for Module
        PBStampVersion pbStampVersion = PBStampVersion.newBuilder()
                .setStatus(createPBPublicId(statusConcept))
                .setTime(nowTimestamp())
                .setAuthor(createPBPublicId(authorConcept))
                .setModule(createPBPublicId())
                .setPath(createPBPublicId(pathConcept))
                .build();

        // When we transform PBStampVersion

        // Then we will throw a Runtime exception
        assertThrows(Throwable.class, () -> ProtobufTransformer.transformStampVersion(pbStampVersion, mockStampRecord), "Not allowed to have empty UUID for module.");
    }

    /**
     * Testing the transformation of a StampVersion Protobuf object to an Entity with a missing Path.
     */
    @Test
    public void stampVersionTransformWithPathBeingBlankPublicId() {
        // Given a PBStampVersion with a missing Public Id for Path
        PBStampVersion pbStampVersion = PBStampVersion.newBuilder()
                .setStatus(createPBPublicId(statusConcept))
                .setTime(nowTimestamp())
                .setAuthor(createPBPublicId(authorConcept))
                .setModule(createPBPublicId(moduleConcept))
                .setPath(createPBPublicId())
                .build();

        // When we transform PBStampVersion

        // Then we will throw a Runtime exception
        assertThrows(Throwable.class, () -> ProtobufTransformer.transformStampVersion(pbStampVersion, mockStampRecord), "Not allowed to have empty UUID for path.");
    }

    /**
     * Testing the transformation of a StampChronology Protobuf object to an Entity with no versions present.
     *  TODO: THis should throw an exception but because we are creating the chonology with an empty list there must be a check in the transform
     */
    @Test
    public void stampChronologyTransformWithZeroVersion(){
        // Given a PBStampChronology with a no Stamp Versions present
        PBStampChronology pbStampChronology = PBStampChronology.newBuilder()
                .setPublicId(createPBPublicId(testConcept))
                .build();

        // When we transform PBStampChronology

        // Then we will throw a Runtime exception
        assertThrows(Throwable.class, () -> ProtobufTransformer.transformStampChronology(pbStampChronology), "Not allowed to have no stamp versions.");
    }

    /**
     * Testing the transformation of a StampChronology Protobuf object to an Entity with one version present.
     */
    @Test
    public void stampChronologyTransformWithOneVersion(){
        // Given a PBStampChronology with a one Stamp Version present
        Timestamp expectedTime = nowTimestamp();
        PBStampVersion pbStampVersion = PBStampVersion.newBuilder()
                .setStatus(createPBPublicId(statusConcept))
                .setTime(expectedTime)
                .setAuthor(createPBPublicId(authorConcept))
                .setModule(createPBPublicId(moduleConcept))
                .setPath(createPBPublicId(pathConcept))
                .build();

        PBStampChronology pbStampChronology = PBStampChronology.newBuilder()
                .setPublicId(createPBPublicId(testConcept))
                .addStampVersions(pbStampVersion)
                .build();

        // When we transform PBStampChronology
        StampRecord actualStampChronology = ProtobufTransformer.transformStampChronology(pbStampChronology);

        // Then the resulting StampChronology should match the original PBStampChronology
        assertEquals(50, actualStampChronology.nid(), "Nid's did not match in Stamp Chronology.");
        assertTrue(PublicId.equals(testConcept.publicId(), actualStampChronology.publicId()), "Public Id's of the stamp chronology do not match.");
        assertEquals(1, actualStampChronology.versions().size(), "Versions are empty");
        assertEquals(10, actualStampChronology.versions().get(0).stateNid(), "Status Nid did not match");
        assertEquals(expectedTime.getSeconds(), actualStampChronology.versions().get(0).time(), "Time did not match");
        assertEquals(20, actualStampChronology.versions().get(0).authorNid(), "Author Nid did not match");
        assertEquals(30, actualStampChronology.versions().get(0).moduleNid(), "Module Nid did not match");
        assertEquals(40, actualStampChronology.versions().get(0).pathNid(), "Path Nid did not match");
    }

    @Test
    public void stampChronologyTransformWithTwoVersions(){
        // Given a PBStampChronology with two Stamp Versions present
        Timestamp expectedTime1 = nowTimestamp();
        Timestamp expectedTime2 = nowTimestamp();

        PBStampVersion pbStampVersionOne = PBStampVersion.newBuilder()
                .setStatus(createPBPublicId(statusConcept))
                .setTime(expectedTime1)
                .setAuthor(createPBPublicId(authorConcept))
                .setModule(createPBPublicId(moduleConcept))
                .setPath(createPBPublicId(pathConcept))
                .build();
        PBStampVersion pbStampVersionTwo = PBStampVersion.newBuilder()
                .setStatus(createPBPublicId(statusConcept))
                .setTime(expectedTime2)
                .setAuthor(createPBPublicId(authorConcept))
                .setModule(createPBPublicId(moduleConcept))
                .setPath(createPBPublicId(pathConcept))
                .build();

        PBStampChronology pbStampChronology = PBStampChronology.newBuilder()
                .setPublicId(createPBPublicId(testConcept))
                .addStampVersions(pbStampVersionOne)
                .addStampVersions(pbStampVersionTwo)
                .build();

        // When we transform PBStampChronology
        StampRecord actualStampChronology = ProtobufTransformer.transformStampChronology(pbStampChronology);

        // Then the resulting StampChronology should match the original PBStampChronology
        assertEquals(nid(testConcept), actualStampChronology.nid(), "Nid's did not match in Stamp Chronology.");
        assertTrue(PublicId.equals(testConcept.publicId(), actualStampChronology.publicId()), "Public Id's of the stamp chronology do not match.");
        assertEquals(2, actualStampChronology.versions().size(), "Versions are empty");
        assertEquals(10, actualStampChronology.versions().get(0).stateNid(), "Status Nid did not match");
        assertEquals(expectedTime1.getSeconds(), actualStampChronology.versions().get(0).time(), "Time did not match");
        assertEquals(20, actualStampChronology.versions().get(0).authorNid(), "Author Nid did not match");
        assertEquals(30, actualStampChronology.versions().get(0).moduleNid(), "Module Nid did not match");
        assertEquals(40, actualStampChronology.versions().get(0).pathNid(), "Path Nid did not match");
        assertEquals(10, actualStampChronology.versions().get(1).stateNid(), "Status Nid did not match");
        assertEquals(expectedTime2.getSeconds(), actualStampChronology.versions().get(1).time(), "Time did not match");
        assertEquals(20, actualStampChronology.versions().get(1).authorNid(), "Author Nid did not match");
        assertEquals(30, actualStampChronology.versions().get(1).moduleNid(), "Module Nid did not match");
        assertEquals(40, actualStampChronology.versions().get(1).pathNid(), "Path Nid did not match");
    }
}
