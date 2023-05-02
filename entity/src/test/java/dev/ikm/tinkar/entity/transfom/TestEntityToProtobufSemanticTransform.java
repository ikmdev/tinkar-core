package dev.ikm.tinkar.entity.transfom;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.schema.*;
import org.eclipse.collections.api.list.ImmutableList;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.ikm.tinkar.entity.transfom.ProtobufToEntityTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

public class TestEntityToProtobufSemanticTransform {
    @Test
    @DisplayName("Transform a Entity Semantic Chronology With Zero Versions/Values Present")
    public void semanticChronologyTransformWithZeroVersion() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given an Entity Semantic Version
            // When we transform our Entity Semantic Version into a PBSemanticVersion
            // Then the resulting PBSemanticVersion should match the original entity value
            assertThrows(Throwable.class, () -> EntityTransformer.getInstance().createPBSemanticVersions(RecordListBuilder.make().build()), "Not allowed to have an empty Semantic Version.");
        });
    }

    @Test
    @DisplayName("Transform a Entity Semantic Version with all values present")
    public void semanticChronologyTransformWithOneVersion() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given an Entity Semantic Version
            SemanticVersionRecord mockSemanticVersion = mock(SemanticVersionRecord.class);

            EntityTransformer entityTransformer = spy(EntityTransformer.getInstance());

            doReturn(PBStampChronology.getDefaultInstance()).when(entityTransformer).createPBStampChronology(any());
            doReturn(List.of(PBField.getDefaultInstance())).when(entityTransformer).createPBFields(any());

            // When we transform our Entity Semantic Version into a PBSemanticVersion
            List<PBSemanticVersion> actualPBSemanticVersion = entityTransformer.createPBSemanticVersions(RecordListBuilder.make().add(mockSemanticVersion));

            // Then the resulting PBSemanticVersion should match the original entity value
            verify(entityTransformer, times(1)).createPBStampChronology(any());
            verify(entityTransformer, times(1)).createPBFields(any());
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

            EntityTransformer entityTransformer = spy(EntityTransformer.getInstance());

            doReturn(PBStampChronology.getDefaultInstance()).when(entityTransformer).createPBStampChronology(any());
            doReturn(List.of(PBField.getDefaultInstance())).when(entityTransformer).createPBFields(any());

            // When we transform our Entity Semantic Versions into a PBSemanticVersion
            List<PBSemanticVersion> actualPBSemanticVersion = entityTransformer.createPBSemanticVersions(RecordListBuilder.make().add(mockSemanticVersion).addAndBuild(mockSemanticVersion));

            // Then the resulting PBSemanticVersion should match the original entity value
            verify(entityTransformer, times(2)).createPBStampChronology(any());
            verify(entityTransformer, times(2)).createPBFields(any());
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

            EntityTransformer entityTransformer = spy(EntityTransformer.getInstance());

            doReturn(List.of(PBField.getDefaultInstance())).when(entityTransformer).createPBFields(any());

            // When we transform our Entity Semantic Versions into a PBSemanticVersion

            // Then the resulting PBSemanticVersion should throw an exception
            assertThrows(Throwable.class, () -> EntityTransformer.getInstance().createPBSemanticVersions(RecordListBuilder.make().add(mockSemanticVersion)), "Not allowed to have an empty Stamp in Semantic Version.");
        });
    }

    @Test
    @DisplayName("Transform a Entity Semantic Version With a Missing Field")
    public void semanticVersionTransformWithAMissingField() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given an Entity Semantic Version
            SemanticVersionRecord mockSemanticVersion = mock(SemanticVersionRecord.class);

            EntityTransformer entityTransformer = spy(EntityTransformer.getInstance());

            doReturn(PBStampChronology.getDefaultInstance()).when(entityTransformer).createPBStampChronology(any());
            // When we transform our Entity Semantic Versions into a PBSemanticVersion

            // Then the resulting PBSemanticVersion should throw an exception
            assertThrows(Throwable.class, () -> EntityTransformer.getInstance().createPBSemanticVersions(RecordListBuilder.make().add(mockSemanticVersion)), "Not allowed to have an empty Field in Semantic Version.");
        });
    }
}

