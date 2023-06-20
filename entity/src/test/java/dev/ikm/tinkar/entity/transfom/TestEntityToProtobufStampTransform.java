package dev.ikm.tinkar.entity.transfom;

import com.google.protobuf.Timestamp;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.schema.StampChronology;
import dev.ikm.tinkar.schema.StampVersion;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.State;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static dev.ikm.tinkar.entity.transfom.ProtobufToEntityTestHelper.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestEntityToProtobufStampTransform {

    @Test
    @DisplayName("Transform a Entity Stamp Version With all Values Present")
    public void testEntitytoProtobufStampVersionTransformWithValuesPresent() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given a Stamp Version Entity
            long expectedTime = nowEpochSeconds();
            Timestamp expectedTimestamp = nowTimestamp();
            Concept authorConcept = conceptMap.get(AUTHOR_CONCEPT_NAME);
            Concept moduleConcept = conceptMap.get(MODULE_CONCEPT_NAME);
            Concept pathConcept = conceptMap.get(PATH_CONCEPT_NAME);

            //Mocking
            StampVersionRecord mockStampVersion = mock(StampVersionRecord.class);
            when(mockStampVersion.state()).thenReturn(State.ACTIVE);
            when(mockStampVersion.author()).thenReturn((ConceptFacade) authorConcept);
            when(mockStampVersion.module()).thenReturn((ConceptFacade) moduleConcept);
            when(mockStampVersion.path()).thenReturn((ConceptFacade) pathConcept);
            when(mockStampVersion.time()).thenReturn(expectedTime);

            // When we transform our StampVersion into a PBStampVersion
            List<StampVersion> actualPBStampVersion = EntityToTinkarSchemaTransformer.getInstance().createPBStampVersions(new RecordListBuilder<StampVersionRecord>().addAndBuild(mockStampVersion));

            // Then the resulting PBStampVersion should match the original entity value.
            assertEquals(createPBPublicId(State.ACTIVE.publicId()), actualPBStampVersion.get(0).getStatus(), "The States/Statuses do not match in PBStampVersion.");
            assertEquals(createPBPublicId(authorConcept.publicId()), actualPBStampVersion.get(0).getAuthor(), "The Authors do not match in PBStampVersion.");
            assertEquals(createPBPublicId(moduleConcept.publicId()), actualPBStampVersion.get(0).getModule(), "The Modules do not match in PBStampVersion.");
            assertEquals(createPBPublicId(pathConcept.publicId()), actualPBStampVersion.get(0).getPath(), "The Paths do not match in PBStampVersion.");
            assertEquals(expectedTime, actualPBStampVersion.get(0).getTime().getSeconds(), "The Timestamps do not match in PBStampVersion.");
        });
    }

    @Test
    @DisplayName("Transform a Entity Stamp Version With Status being Blank")
    public void stampVersionTransformWithStatusBeingBlank() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given a Stamp Version Entity
            long expectedTime = nowEpochSeconds();
            Timestamp expectedTimestamp = nowTimestamp();
            Concept authorConcept = conceptMap.get(AUTHOR_CONCEPT_NAME);
            Concept moduleConcept = conceptMap.get(MODULE_CONCEPT_NAME);
            Concept pathConcept = conceptMap.get(PATH_CONCEPT_NAME);

            //Mocking
            StampVersionRecord mockStampVersion = mock(StampVersionRecord.class);
            when(mockStampVersion.author()).thenReturn((ConceptFacade) authorConcept);
            when(mockStampVersion.module()).thenReturn((ConceptFacade) moduleConcept);
            when(mockStampVersion.path()).thenReturn((ConceptFacade) pathConcept);
            when(mockStampVersion.time()).thenReturn(expectedTime);

            // When we transform our StampVersion into a PBStampVersion

            // Then the resulting PBStampVersion should throw an exception if Status is not present.
            assertThrows(Throwable.class, () -> EntityToTinkarSchemaTransformer.getInstance().createPBStampVersions(new RecordListBuilder<StampVersionRecord>().addAndBuild(mockStampVersion)), "Not allowed to have an empty status in a STAMP.");
        });
    }

    /**
     * Testing the transformation of a StampVersion Protobuf object to a Protobuf Message with a missing Author.
     */
    @Test
    @DisplayName("Transform a Entity Stamp Version With Author being Blank")
    public void stampVersionTransformWithAuthorBeingBlank() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given a Stamp Version Entity
            long expectedTime = nowEpochSeconds();
            Timestamp expectedTimestamp = nowTimestamp();
            Concept moduleConcept = conceptMap.get(MODULE_CONCEPT_NAME);
            Concept pathConcept = conceptMap.get(PATH_CONCEPT_NAME);

            //Mocking
            StampVersionRecord mockStampVersion = mock(StampVersionRecord.class);
            when(mockStampVersion.module()).thenReturn((ConceptFacade) moduleConcept);
            when(mockStampVersion.path()).thenReturn((ConceptFacade) pathConcept);
            when(mockStampVersion.time()).thenReturn(expectedTime);

            // When we transform our StampVersion into a PBStampVersion

            // Then the resulting PBStampVersion should throw an exception if Author is not present.
            assertThrows(Throwable.class, () -> EntityToTinkarSchemaTransformer.getInstance().createPBStampVersions(new RecordListBuilder<StampVersionRecord>().addAndBuild(mockStampVersion)), "Not allowed to have an empty author in a STAMP.");
        });
    }

    /**
     * Testing the transformation of a StampVersion Protobuf object to a Protobuf Message with a missing Module.
     */
    @Test
    @DisplayName("Transform a Entity Stamp Version With Module being Blank")
    public void stampVersionTransformWithModuleBeingBlank() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given a Stamp Version Entity
            long expectedTime = nowEpochSeconds();
            Timestamp expectedTimestamp = nowTimestamp();
            Concept authorConcept = conceptMap.get(AUTHOR_CONCEPT_NAME);
            Concept pathConcept = conceptMap.get(PATH_CONCEPT_NAME);

            //Mocking
            StampVersionRecord mockStampVersion = mock(StampVersionRecord.class);
            when(mockStampVersion.state()).thenReturn(State.ACTIVE);
            when(mockStampVersion.author()).thenReturn((ConceptFacade) authorConcept);
            when(mockStampVersion.path()).thenReturn((ConceptFacade) pathConcept);
            when(mockStampVersion.time()).thenReturn(expectedTime);

            // When we transform our StampVersion into a PBStampVersion

            // Then the resulting PBStampVersion should throw an exception if Module is not present.
            assertThrows(Throwable.class, () -> EntityToTinkarSchemaTransformer.getInstance().createPBStampVersions(new RecordListBuilder<StampVersionRecord>().addAndBuild(mockStampVersion)), "Not allowed to have an empty module in a STAMP.");
        });
    }

    /**
     * Testing the transformation of a StampVersion Protobuf object to a Protobuf Message with a missing Path.
     */
    @Test
    @DisplayName("Transform a Entity Stamp Version With Path being Blank")
    public void stampVersionTransformWithPathBeingBlank() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given a Stamp Version Entity
            long expectedTime = nowEpochSeconds();
            Timestamp expectedTimestamp = nowTimestamp();
            Concept authorConcept = conceptMap.get(AUTHOR_CONCEPT_NAME);
            Concept moduleConcept = conceptMap.get(MODULE_CONCEPT_NAME);

            //Mocking
            StampVersionRecord mockStampVersion = mock(StampVersionRecord.class);
            when(mockStampVersion.state()).thenReturn(State.ACTIVE);
            when(mockStampVersion.author()).thenReturn((ConceptFacade) authorConcept);
            when(mockStampVersion.module()).thenReturn((ConceptFacade) moduleConcept);
            when(mockStampVersion.time()).thenReturn(expectedTime);
            // When we transform our StampVersion into a PBStampVersion

            // Then the resulting PBStampVersion should throw an exception if Path is not present.
            assertThrows(Throwable.class, () -> EntityToTinkarSchemaTransformer.getInstance().createPBStampVersions(new RecordListBuilder<StampVersionRecord>().addAndBuild(mockStampVersion)), "Not allowed to have an empty Path in a STAMP.");
        });
    }

    //FIXME: Is there a better way to implement this test?
    @Test
    @DisplayName("Transform a Entity Stamp Chronology With No Versions")
    public void stampChronologyTransformWithZeroVersions(){
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given a Stamp Chronology Entity
            StampEntity<StampVersionRecord> mockedStampEntity = mock(StampEntity.class);
            when(mockedStampEntity.nid()).thenReturn(21423);
            // When we transform our StampVersion into a PBStampVersion

            // Then the resulting PBStampVersion should throw an exception because there is an empty stamp version.
            assertThrows(Throwable.class, () -> EntityToTinkarSchemaTransformer.getInstance().createPBStampChronology(mockedStampEntity), "Not allowed to have an empty stamp version in a StampChronology.");

        });
    }

    @Test
    @DisplayName("Transform a Entity Stamp Chronology With One Version")
    public void stampChronologyTransformWithOneVersion(){
        openSession(this, (mockedEntityService, conceptMap) -> {
            PublicId randomPublicID = PublicIds.newRandom();
            PublicId stampPublicID = PublicIds.newRandom();

            long expectedTime = nowEpochSeconds();
            Timestamp expectedTimestamp = nowTimestamp();
            Concept conceptPublicId = conceptMap.get(TEST_CONCEPT_NAME);
            Concept authorConcept = conceptMap.get(AUTHOR_CONCEPT_NAME);
            Concept moduleConcept = conceptMap.get(MODULE_CONCEPT_NAME);
            Concept pathConcept = conceptMap.get(PATH_CONCEPT_NAME);

            StampEntity<StampVersionRecord> mockedStampEntityVersion = mock(StampEntity.class);

            ConceptRecord mockConceptChronology = mock(ConceptRecord.class);

            StampRecord mockedStampChronology = mock(StampRecord.class);

            StampVersionRecord mockedStampVersion = mock(StampVersionRecord.class);

            when(mockedStampVersion.publicId()).thenReturn(stampPublicID);
            when(mockedStampVersion.state()).thenReturn(State.ACTIVE);
            when(mockedStampVersion.author()).thenReturn((ConceptFacade) authorConcept);
            when(mockedStampVersion.module()).thenReturn((ConceptFacade) moduleConcept);
            when(mockedStampVersion.path()).thenReturn((ConceptFacade) pathConcept);
            when(mockedStampVersion.time()).thenReturn(expectedTime);
            when(mockedStampVersion.stamp()).thenReturn(mockedStampChronology);

            ImmutableList<StampVersionRecord> versions = Lists.immutable.of(mockedStampVersion);
            when(mockedStampChronology.versions()).thenReturn(versions);

            when(mockedStampEntityVersion.asUuidList()).thenReturn(randomPublicID.asUuidList());
            when(mockedStampEntityVersion.publicId()).thenReturn(randomPublicID);
            when(mockedStampEntityVersion.versions()).thenReturn(new RecordListBuilder<StampVersionRecord>().addAndBuild(mockedStampVersion));

            // When we perform the transform
            StampChronology actualPBStampChronology = EntityToTinkarSchemaTransformer.getInstance().createPBStampChronology(mockedStampEntityVersion);

            //TODO: Add in Mockito Verify statements here

            // Then we assure that the values match
            assertEquals(createPBPublicId(randomPublicID), actualPBStampChronology.getPublicId(), "The public ID's of the expected Stamp Chronology and actual do not match.");
            assertEquals(1, actualPBStampChronology.getVersionsCount(), "The size of Stamp Versions do not match those expected in the Stamp Chronology");
            assertEquals(createPBPublicId(authorConcept.publicId()), actualPBStampChronology.getVersions(0).getAuthor(), "The public ID's of the expected Stamp Chronology's Author and actual do not match.");
            assertEquals(createPBPublicId(moduleConcept.publicId()), actualPBStampChronology.getVersions(0).getModule(), "The public ID's of the expected Stamp Chronology's Module and actual do not match.");
            assertEquals(createPBPublicId(pathConcept.publicId()), actualPBStampChronology.getVersions(0).getPath(), "The public ID's of the expected Stamp Chronology's Path and actual do not match.");
        });
    }

    @Test
    @DisplayName("Transform a Entity Stamp Version With Two Versions")
    public void stampVersionTransformWithTwoVersions(){
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given a Stamp Version Entity
            long expectedTime = nowEpochSeconds();
            Timestamp expectedTimestamp = nowTimestamp();
            Concept authorConcept = conceptMap.get(AUTHOR_CONCEPT_NAME);
            Concept moduleConcept = conceptMap.get(MODULE_CONCEPT_NAME);
            Concept pathConcept = conceptMap.get(PATH_CONCEPT_NAME);

            //Mocking
            StampVersionRecord mockStampVersionOne = mock(StampVersionRecord.class);
            when(mockStampVersionOne.state()).thenReturn(State.ACTIVE);
            when(mockStampVersionOne.author()).thenReturn((ConceptFacade) authorConcept);
            when(mockStampVersionOne.module()).thenReturn((ConceptFacade) moduleConcept);
            when(mockStampVersionOne.path()).thenReturn((ConceptFacade) pathConcept);
            when(mockStampVersionOne.time()).thenReturn(expectedTime);

            StampVersionRecord mockStampVersionTwo = mock(StampVersionRecord.class);
            when(mockStampVersionTwo.state()).thenReturn(State.ACTIVE);
            when(mockStampVersionTwo.author()).thenReturn((ConceptFacade) authorConcept);
            when(mockStampVersionTwo.module()).thenReturn((ConceptFacade) moduleConcept);
            when(mockStampVersionTwo.path()).thenReturn((ConceptFacade) pathConcept);
            when(mockStampVersionTwo.time()).thenReturn(expectedTime+5);

            RecordListBuilder<StampVersionRecord> stampVersionRecords = new RecordListBuilder<StampVersionRecord>().add(mockStampVersionOne);
            stampVersionRecords.add(mockStampVersionTwo);
            stampVersionRecords.build();

            // When we transform our StampVersion into a PBStampVersion
            List<StampVersion> actualPBStampVersion = EntityToTinkarSchemaTransformer.getInstance().createPBStampVersions(stampVersionRecords);

            // Then the resulting PBStampVersions should match the original entity value.
            assertEquals(2, actualPBStampVersion.size(),"There are missing STAMP Versions in the Stamp Chronology.");
            assertEquals(createPBPublicId(State.ACTIVE.publicId()), actualPBStampVersion.get(0).getStatus(), "The States/Statuses do not match in PBStampVersionOne.");
            assertEquals(createPBPublicId(authorConcept.publicId()), actualPBStampVersion.get(0).getAuthor(), "The Authors do not match in PBStampVersionOne.");
            assertEquals(createPBPublicId(moduleConcept.publicId()), actualPBStampVersion.get(0).getModule(), "The Modules do not match in PBStampVersionOne.");
            assertEquals(createPBPublicId(pathConcept.publicId()), actualPBStampVersion.get(0).getPath(), "The Paths do not match in PBStampVersionOne.");
            assertEquals(expectedTime, actualPBStampVersion.get(0).getTime().getSeconds(), "The Timestamps do not match in PBStampVersionOne.");
            assertEquals(createPBPublicId(State.ACTIVE.publicId()), actualPBStampVersion.get(1).getStatus(), "The States/Statuses do not match in PBStampVersionTwo.");
            assertEquals(createPBPublicId(authorConcept.publicId()), actualPBStampVersion.get(1).getAuthor(), "The Authors do not match in PBStampVersionTwo.");
            assertEquals(createPBPublicId(moduleConcept.publicId()), actualPBStampVersion.get(1).getModule(), "The Modules do not match in PBStampVersionTwo.");
            assertEquals(createPBPublicId(pathConcept.publicId()), actualPBStampVersion.get(1).getPath(), "The Paths do not match in PBStampVersionTwo.");
            assertEquals(expectedTime+5, actualPBStampVersion.get(1).getTime().getSeconds(), "The Timestamps do not match in PBStampVersionTwo.");
        });
    }
    //TODO: Add test to check if a stamp chronology can be created with two stamp version of the same type (and time).
}
