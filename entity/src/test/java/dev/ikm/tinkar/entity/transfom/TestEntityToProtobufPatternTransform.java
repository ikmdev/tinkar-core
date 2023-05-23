package dev.ikm.tinkar.entity.transfom;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.PatternVersionRecord;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.schema.PBFieldDefinition;
import dev.ikm.tinkar.schema.PBPatternVersion;
import dev.ikm.tinkar.schema.PBStampChronology;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static dev.ikm.tinkar.entity.transfom.ProtobufToEntityTestHelper.createPBPublicId;
import static dev.ikm.tinkar.entity.transfom.ProtobufToEntityTestHelper.openSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestEntityToProtobufPatternTransform {

    @Test
    @DisplayName("Transform zero Entity Pattern Version with zero values present")
    public void patternVersionTransformWithZeroVersion() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given an Entity Pattern Version
            // When we transform our Entity Pattern Version into a PBPatternVersion
            // Then the resulting PBPatternVersion should match the original entity value
            assertThrows(Throwable.class, () -> EntityToTinkarSchemaTransformer.getInstance().createPBPatternVersions(RecordListBuilder.make().build()), "Not allowed to have an empty Pattern Version.");
        });
    }

    @Test
    @DisplayName("Transform one Entity Pattern Version with all values present")
    public void patternVersionTransformWithOneVersion() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given an Entity Pattern Version
            ConceptEntity referencedComponentPurpose = mock(ConceptEntity.class);
            PublicId referencedComponentPurposePublicId = PublicIds.newRandom();
            when(referencedComponentPurpose.publicId()).thenReturn(referencedComponentPurpose);

            ConceptEntity referencedComponentMeaning = mock(ConceptEntity.class);
            PublicId referencedComponentMeaningPublicId = PublicIds.newRandom();
            when(referencedComponentMeaning.publicId()).thenReturn(referencedComponentMeaning);

            PatternVersionRecord mockPatternVersion = mock(PatternVersionRecord.class);
            when(mockPatternVersion.semanticPurpose()).thenReturn(referencedComponentPurpose);
            when(mockPatternVersion.semanticMeaning()).thenReturn(referencedComponentMeaning);
            when(mockPatternVersion.semanticPurpose().publicId()).thenReturn(referencedComponentPurposePublicId);
            when(mockPatternVersion.semanticMeaning().publicId()).thenReturn(referencedComponentMeaningPublicId);

            EntityToTinkarSchemaTransformer entityToTinkarSchemaTransformer = spy(EntityToTinkarSchemaTransformer.getInstance());

            doReturn(PBStampChronology.getDefaultInstance()).when(entityToTinkarSchemaTransformer).createPBStampChronology(any());
            doReturn(List.of(PBFieldDefinition.getDefaultInstance())).when(entityToTinkarSchemaTransformer).createPBFieldDefinitions(any());

            // When we transform our Entity Pattern Version into a PBPatternVersion
            List<PBPatternVersion> actualPBPatternVersion = entityToTinkarSchemaTransformer.createPBPatternVersions(RecordListBuilder.make().with(mockPatternVersion).build());

            // Then the resulting PBPatternVersion should match the original entity value
            verify(entityToTinkarSchemaTransformer, times(1)).createPBStampChronology(any());
            verify(entityToTinkarSchemaTransformer, times(1)).createPBFieldDefinitions(any());
            assertEquals(1, actualPBPatternVersion.size(), "The versions are missing from pattern version.");
            assertEquals(createPBPublicId(referencedComponentPurposePublicId), actualPBPatternVersion.get(0).getReferencedComponentPurpose(), "The referenced component purpose didn't match.");
            assertEquals(createPBPublicId(referencedComponentMeaningPublicId), actualPBPatternVersion.get(0).getReferencedComponentMeaning(), "The referenced component meaning didn't match.");
        });
    }

    @Test
    @DisplayName("Transform two Entity Pattern Version with all values present")
    public void patternVersionTransformWithTwoVersions() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given an Entity Pattern Version
            ConceptEntity referencedComponentPurpose = mock(ConceptEntity.class);
            PublicId referencedComponentPurposePublicId = PublicIds.newRandom();
            when(referencedComponentPurpose.publicId()).thenReturn(referencedComponentPurpose);

            ConceptEntity referencedComponentMeaning = mock(ConceptEntity.class);
            PublicId referencedComponentMeaningPublicId = PublicIds.newRandom();
            when(referencedComponentMeaning.publicId()).thenReturn(referencedComponentMeaning);

            PatternVersionRecord mockPatternVersion = mock(PatternVersionRecord.class);
            when(mockPatternVersion.semanticPurpose()).thenReturn(referencedComponentPurpose);
            when(mockPatternVersion.semanticMeaning()).thenReturn(referencedComponentMeaning);
            when(mockPatternVersion.semanticPurpose().publicId()).thenReturn(referencedComponentPurposePublicId);
            when(mockPatternVersion.semanticMeaning().publicId()).thenReturn(referencedComponentMeaningPublicId);

            EntityToTinkarSchemaTransformer entityToTinkarSchemaTransformer = spy(EntityToTinkarSchemaTransformer.getInstance());

            doReturn(PBStampChronology.getDefaultInstance()).when(entityToTinkarSchemaTransformer).createPBStampChronology(any());
            doReturn(List.of(PBFieldDefinition.getDefaultInstance())).when(entityToTinkarSchemaTransformer).createPBFieldDefinitions(any());

            // When we transform our Entity Pattern Version into a PBPatternVersion
            List<PBPatternVersion> actualPBPatternVersion = entityToTinkarSchemaTransformer.createPBPatternVersions(RecordListBuilder.make().add(mockPatternVersion).addAndBuild(mockPatternVersion));

            // Then the resulting PBPatternVersion should match the original entity value
            verify(entityToTinkarSchemaTransformer, times(2)).createPBStampChronology(any());
            verify(entityToTinkarSchemaTransformer, times(2)).createPBFieldDefinitions(any());
            assertEquals(2, actualPBPatternVersion.size(), "The versions are missing from pattern version.");
            assertEquals(createPBPublicId(referencedComponentPurposePublicId), actualPBPatternVersion.get(0).getReferencedComponentPurpose(), "The referenced component purpose didn't match.");
            assertEquals(createPBPublicId(referencedComponentMeaningPublicId), actualPBPatternVersion.get(0).getReferencedComponentMeaning(), "The referenced component meaning didn't match.");
            assertEquals(createPBPublicId(referencedComponentPurposePublicId), actualPBPatternVersion.get(1).getReferencedComponentPurpose(), "The referenced component purpose didn't match.");
            assertEquals(createPBPublicId(referencedComponentMeaningPublicId), actualPBPatternVersion.get(1).getReferencedComponentMeaning(), "The referenced component meaning didn't match.");
        });
    }

    @Test
    @DisplayName("Transform one Entity Pattern Version with Meaning missing and Purpose present")
    public void patternVersionTransformWithOneVersionWithMeaningMissingPurposePresent() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given an Entity Pattern Version
            ConceptEntity referencedComponentPurpose = mock(ConceptEntity.class);
            PublicId referencedComponentPurposePublicId = PublicIds.newRandom();
            when(referencedComponentPurpose.publicId()).thenReturn(referencedComponentPurpose);

            PatternVersionRecord mockPatternVersion = mock(PatternVersionRecord.class);
            when(mockPatternVersion.semanticPurpose()).thenReturn(referencedComponentPurpose);
            when(mockPatternVersion.semanticPurpose().publicId()).thenReturn(referencedComponentPurposePublicId);

            EntityToTinkarSchemaTransformer entityToTinkarSchemaTransformer = spy(EntityToTinkarSchemaTransformer.getInstance());

            doReturn(PBStampChronology.getDefaultInstance()).when(entityToTinkarSchemaTransformer).createPBStampChronology(any());
            doReturn(List.of(PBFieldDefinition.getDefaultInstance())).when(entityToTinkarSchemaTransformer).createPBFieldDefinitions(any());

            // When we transform our Entity Pattern Version into a PBPatternVersion

            // Then the resulting PBPatternVersion should match the original entity value
            assertThrows(Throwable.class, () -> EntityToTinkarSchemaTransformer.getInstance().createPBPatternVersions(RecordListBuilder.make().add(mockPatternVersion).addAndBuild(mockPatternVersion)), "Not allowed to have an empty Semantic Meaning for a Pattern Version.");
        });
    }

    @Test
    @DisplayName("Transform one Entity Pattern Version with Purpose missing and Meaning present")
    public void patternVersionTransformWithOneVersionWithPurposeMissingMeaningPresent() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given an Entity Pattern Version
            ConceptEntity referencedComponentMeaning = mock(ConceptEntity.class);
            PublicId referencedComponentMeaningPublicId = PublicIds.newRandom();
            when(referencedComponentMeaning.publicId()).thenReturn(referencedComponentMeaning);

            PatternVersionRecord mockPatternVersion = mock(PatternVersionRecord.class);
            when(mockPatternVersion.semanticMeaning()).thenReturn(referencedComponentMeaning);
            when(mockPatternVersion.semanticMeaning().publicId()).thenReturn(referencedComponentMeaningPublicId);

            EntityToTinkarSchemaTransformer entityToTinkarSchemaTransformer = spy(EntityToTinkarSchemaTransformer.getInstance());

            doReturn(PBStampChronology.getDefaultInstance()).when(entityToTinkarSchemaTransformer).createPBStampChronology(any());
            doReturn(List.of(PBFieldDefinition.getDefaultInstance())).when(entityToTinkarSchemaTransformer).createPBFieldDefinitions(any());

            // When we transform our Entity Pattern Version into a PBPatternVersion

            // Then the resulting PBPatternVersion should match the original entity value
            assertThrows(Throwable.class, () -> EntityToTinkarSchemaTransformer.getInstance().createPBPatternVersions(RecordListBuilder.make().add(mockPatternVersion).addAndBuild(mockPatternVersion)), "Not allowed to have an empty Semantic Purpose for a Pattern Version.");
        });
    }
}
