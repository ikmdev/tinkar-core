/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.entity.transform;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.ConceptVersionRecord;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.schema.ConceptVersion;
import dev.ikm.tinkar.schema.StampChronology;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Disabled;

import java.util.List;

import static dev.ikm.tinkar.entity.transform.ProtobufToEntityTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// @Disabled("Java 23")
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
            //Given a concept chronology with all values
            ConceptEntity conceptPublic = mock(ConceptEntity.class);
            StampRecord stampRecord = mock(StampRecord.class);
            PublicId conceptPublicId = PublicIds.newRandom();
            when(conceptPublic.publicId()).thenReturn(conceptPublic);
            Concept testConcept = conceptMap.get(TEST_CONCEPT_NAME);
            PublicId expectedPublicId = testConcept.publicId();

            ConceptVersionRecord mockConceptVersion = mock(ConceptVersionRecord.class);
            when(mockConceptVersion.publicId()).thenReturn(conceptPublic);
            when(mockConceptVersion.stamp()).thenReturn(stampRecord);
            when(mockConceptVersion.stamp().publicId()).thenReturn(expectedPublicId);

            EntityToTinkarSchemaTransformer entityTransformer = spy(EntityToTinkarSchemaTransformer.getInstance());

            // When we transform our Entity Pattern Version into a PBPatternVersion
            List<ConceptVersion> actualPBConceptVersion = entityTransformer.createPBConceptVersions(RecordListBuilder.make().with(mockConceptVersion).build());

            // Then the resulting PBConceptVersion should match the original entity value
            assertEquals(1, actualPBConceptVersion.size(), "The size of the Concept Chronology does not match the expected.");
            assertEquals(createPBPublicId(expectedPublicId), actualPBConceptVersion.get(0).getStampChronologyPublicId(), "The Concept Chronology is missing a STAMP public ID.");
            assertFalse(actualPBConceptVersion.isEmpty(), "The Concept Version is empty.");
            assertTrue(actualPBConceptVersion.get(0).hasStampChronologyPublicId(), "The Concept Chronology is missing a STAMP public ID.");
        });
    }

    @Test
    @DisplayName("Transform a Entity Concept Version with two versions present")
    public void conceptVersionTransformWithTwoVersions() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            ConceptEntity conceptPublic = mock(ConceptEntity.class);
            StampRecord stampRecord = mock(StampRecord.class);
            PublicId conceptPublicId = PublicIds.newRandom();
            when(conceptPublic.publicId()).thenReturn(conceptPublic);
            Concept testConcept = conceptMap.get(TEST_CONCEPT_NAME);
            PublicId expectedPublicId = testConcept.publicId();

            ConceptVersionRecord mockConceptVersion = mock(ConceptVersionRecord.class);
            when(mockConceptVersion.publicId()).thenReturn(conceptPublic);
            when(mockConceptVersion.stamp()).thenReturn(stampRecord);
            when(mockConceptVersion.stamp().publicId()).thenReturn(expectedPublicId);

            EntityToTinkarSchemaTransformer entityTransformer = spy(EntityToTinkarSchemaTransformer.getInstance());

            doReturn(StampChronology.getDefaultInstance()).when(entityTransformer).createPBStampChronology(any());
            // When we transform our Entity Pattern Version into a PBPatternVersion
            List<ConceptVersion> actualPBConceptVersion = entityTransformer.createPBConceptVersions(RecordListBuilder.make().with(mockConceptVersion).addAndBuild(mockConceptVersion));

            // Then the resulting PBConceptVersion should match the original entity value
            assertEquals(2, actualPBConceptVersion.size(), "The size of the Concept Chronology does not match the expected.");
            assertFalse(actualPBConceptVersion.isEmpty(), "The Concept Version is empty.");
            assertTrue(actualPBConceptVersion.get(0).hasStampChronologyPublicId(), "The Concept Chronology is missing a STAMP public ID.");
            assertTrue(actualPBConceptVersion.get(1).hasStampChronologyPublicId(), "The Concept Chronology is missing a STAMP public ID.");
        });
    }
}
