package dev.ikm.tinkar.entity.transfom;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.ConceptVersionRecord;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.schema.ConceptVersion;
import dev.ikm.tinkar.schema.StampChronology;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static dev.ikm.tinkar.entity.transfom.ProtobufToEntityTestHelper.openSession;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestEntityToProtobufConceptTransform {

    @Test
    @DisplayName("Transform a Entity Concept Chronology With Zero Versions/Values Present")
    public void conceptChronologyTransformWithZeroVersion() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given an Entity Concept Version
            // When we transform our Entity Concept Version into a PBConceptVersion
            // Then the resulting PBConceptVersion should match the original entity value
            assertThrows(Throwable.class, () -> EntityToTinkarSchemaTransformer.getInstance().createPBConceptVersions(RecordListBuilder.make().build()), "Not allowed to have an empty Concept Version.");
        });
    }

    @Test
    @DisplayName("Transform a Entity Concept Chronology with all values")
    public void conceptChronologyTransformWithOneVersion() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            ConceptEntity conceptPublic = mock(ConceptEntity.class);
            PublicId conceptPublicId = PublicIds.newRandom();
            when(conceptPublic.publicId()).thenReturn(conceptPublic);

            ConceptVersionRecord mockConceptVersion = mock(ConceptVersionRecord.class);
            when(mockConceptVersion.publicId()).thenReturn(conceptPublic);

            EntityToTinkarSchemaTransformer entityTransformer = spy(EntityToTinkarSchemaTransformer.getInstance());

            doReturn(StampChronology.getDefaultInstance()).when(entityTransformer).createPBStampChronology(any());
            // When we transform our Entity Pattern Version into a PBPatternVersion
            List<ConceptVersion> actualPBConceptVersion = entityTransformer.createPBConceptVersions(RecordListBuilder.make().with(mockConceptVersion).build());

            // Then the resulting PBConceptVersion should match the original entity value
            assertEquals(1, actualPBConceptVersion.size(), "The size of the Concept Chronology does not match the expected.");
            assertFalse(actualPBConceptVersion.isEmpty(), "The Concept Version is empty.");
            assertTrue(actualPBConceptVersion.get(0).hasStamp(), "The Concept Chronology is missing a STAMP.");
        });
    }

    @Test
    @DisplayName("Transform a Entity Concept Version with two versions present")
    public void conceptVersionTransformWithTwoVersions() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            ConceptEntity conceptPublic = mock(ConceptEntity.class);
            PublicId conceptPublicId = PublicIds.newRandom();
            when(conceptPublic.publicId()).thenReturn(conceptPublic);

            ConceptVersionRecord mockConceptVersion = mock(ConceptVersionRecord.class);
            when(mockConceptVersion.publicId()).thenReturn(conceptPublic);

            EntityToTinkarSchemaTransformer entityTransformer = spy(EntityToTinkarSchemaTransformer.getInstance());

            doReturn(StampChronology.getDefaultInstance()).when(entityTransformer).createPBStampChronology(any());
            // When we transform our Entity Pattern Version into a PBPatternVersion
            List<ConceptVersion> actualPBConceptVersion = entityTransformer.createPBConceptVersions(RecordListBuilder.make().with(mockConceptVersion).addAndBuild(mockConceptVersion));

            // Then the resulting PBConceptVersion should match the original entity value
            assertEquals(2, actualPBConceptVersion.size(), "The size of the Concept Chronology does not match the expected.");
            assertFalse(actualPBConceptVersion.isEmpty(), "The Concept Version is empty.");
            assertTrue(actualPBConceptVersion.get(0).hasStamp(), "The Concept Chronology is missing a STAMP.");
            assertTrue(actualPBConceptVersion.get(1).hasStamp(), "The Concept Chronology is missing a STAMP.");
        });
    }
}
