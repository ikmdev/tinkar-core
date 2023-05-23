package dev.ikm.tinkar.entity.transfom;

import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import dev.ikm.tinkar.schema.PBField;
import dev.ikm.tinkar.schema.PBSemanticVersion;
import dev.ikm.tinkar.schema.PBStampChronology;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.ikm.tinkar.entity.transfom.ProtobufToEntityTestHelper.openSession;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TestEntityToProtobufSemanticTransform {
    @Test
    @DisplayName("Transform a Entity Semantic Chronology With Zero Versions/Values Present")
    public void semanticChronologyTransformWithZeroVersion() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given an Entity Semantic Version
            // When we transform our Entity Semantic Version into a PBSemanticVersion
            // Then the resulting PBSemanticVersion should match the original entity value
            assertThrows(Throwable.class, () -> EntityToTinkarSchemaTransformer.getInstance().createPBSemanticVersions(RecordListBuilder.make().build()), "Not allowed to have an empty Semantic Version.");
        });
    }

    @Test
    @DisplayName("Transform a Entity Semantic Version with all values present")
    public void semanticChronologyTransformWithOneVersion() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given an Entity Semantic Version
            SemanticVersionRecord mockSemanticVersion = mock(SemanticVersionRecord.class);

            EntityToTinkarSchemaTransformer entityToTinkarSchemaTransformer = spy(EntityToTinkarSchemaTransformer.getInstance());

            doReturn(PBStampChronology.getDefaultInstance()).when(entityToTinkarSchemaTransformer).createPBStampChronology(any());
            doReturn(List.of(PBField.getDefaultInstance())).when(entityToTinkarSchemaTransformer).createPBFields(any());

            // When we transform our Entity Semantic Version into a PBSemanticVersion
            List<PBSemanticVersion> actualPBSemanticVersion = entityToTinkarSchemaTransformer.createPBSemanticVersions(RecordListBuilder.make().add(mockSemanticVersion));

            // Then the resulting PBSemanticVersion should match the original entity value
            verify(entityToTinkarSchemaTransformer, times(1)).createPBStampChronology(any());
            verify(entityToTinkarSchemaTransformer, times(1)).createPBFields(any());
            assertEquals(1, actualPBSemanticVersion.size(), "The versions are missing from semantic version.");
            assertTrue(actualPBSemanticVersion.get(0).hasStamp(), "The Semantic Version is missing a stamp.");
        });
    }


    @Test
    @DisplayName("Transform a Entity Semantic Version With Two Version Present")
    public void semanticVersionTransformWithTwoVersions() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given an Entity Semantic Version
            SemanticVersionRecord mockSemanticVersion = mock(SemanticVersionRecord.class);

            EntityToTinkarSchemaTransformer entityToTinkarSchemaTransformer = spy(EntityToTinkarSchemaTransformer.getInstance());

            doReturn(PBStampChronology.getDefaultInstance()).when(entityToTinkarSchemaTransformer).createPBStampChronology(any());
            doReturn(List.of(PBField.getDefaultInstance())).when(entityToTinkarSchemaTransformer).createPBFields(any());

            // When we transform our Entity Semantic Versions into a PBSemanticVersion
            List<PBSemanticVersion> actualPBSemanticVersion = entityToTinkarSchemaTransformer.createPBSemanticVersions(RecordListBuilder.make().add(mockSemanticVersion).addAndBuild(mockSemanticVersion));

            // Then the resulting PBSemanticVersion should match the original entity value
            verify(entityToTinkarSchemaTransformer, times(2)).createPBStampChronology(any());
            verify(entityToTinkarSchemaTransformer, times(2)).createPBFields(any());
            assertEquals(2, actualPBSemanticVersion.size(), "The versions are missing from semantic version.");
            assertTrue(actualPBSemanticVersion.get(0).hasStamp(), "The Semantic Version is missing a stamp in its first version.");
            assertTrue(actualPBSemanticVersion.get(1).hasStamp(), "The Semantic Version is missing a stamp in its second version.");
        });
    }

    @Test
    @DisplayName("Transform a Entity Semantic Version With a Missing Stamp")
    public void semanticVersionTransformWithAMissingStamp() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given an Entity Semantic Version
            SemanticVersionRecord mockSemanticVersion = mock(SemanticVersionRecord.class);

            EntityToTinkarSchemaTransformer entityToTinkarSchemaTransformer = spy(EntityToTinkarSchemaTransformer.getInstance());

            doReturn(List.of(PBField.getDefaultInstance())).when(entityToTinkarSchemaTransformer).createPBFields(any());

            // When we transform our Entity Semantic Versions into a PBSemanticVersion

            // Then the resulting PBSemanticVersion should throw an exception
            assertThrows(Throwable.class, () -> EntityToTinkarSchemaTransformer.getInstance().createPBSemanticVersions(RecordListBuilder.make().add(mockSemanticVersion)), "Not allowed to have an empty Stamp in Semantic Version.");
        });
    }

    @Test
    @DisplayName("Transform a Entity Semantic Version With a Missing Field")
    public void semanticVersionTransformWithAMissingField() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given an Entity Semantic Version
            SemanticVersionRecord mockSemanticVersion = mock(SemanticVersionRecord.class);

            EntityToTinkarSchemaTransformer entityToTinkarSchemaTransformer = spy(EntityToTinkarSchemaTransformer.getInstance());

            doReturn(PBStampChronology.getDefaultInstance()).when(entityToTinkarSchemaTransformer).createPBStampChronology(any());
            // When we transform our Entity Semantic Versions into a PBSemanticVersion

            // Then the resulting PBSemanticVersion should throw an exception
            assertThrows(Throwable.class, () -> EntityToTinkarSchemaTransformer.getInstance().createPBSemanticVersions(RecordListBuilder.make().add(mockSemanticVersion)), "Not allowed to have an empty Field in Semantic Version.");
        });
    }
}

