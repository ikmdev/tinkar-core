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
package dev.ikm.tinkar.integration.transform;

import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampEntityVersion;
import dev.ikm.tinkar.entity.transform.TinkarSchemaToEntityTransformer;
import dev.ikm.tinkar.integration.NewEphemeralKeyValueProvider;
import dev.ikm.tinkar.schema.Field;
import dev.ikm.tinkar.schema.SemanticChronology;
import dev.ikm.tinkar.schema.SemanticVersion;
import dev.ikm.tinkar.schema.TinkarMsg;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static dev.ikm.tinkar.integration.transform.TransformTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(NewEphemeralKeyValueProvider.class)
public class TestProtobufToEntitySemanticTransformIT {

    private Map<String, Concept> conceptMap;

    @BeforeAll
    public void setUp() {
        conceptMap = loadTestConcepts(this);
    }

    @Test
    @DisplayName("Transform a Semantic Chronology With Zero Versions Present - requires entity service")
    public void semanticChronologyTransformWithZeroVersion() {
        // Given a PBSemanticChronology with no Semantic Versions present
        SemanticChronology pbSemanticChronology = SemanticChronology.newBuilder()
                .setPublicId(createPBPublicId(conceptMap.get(TEST_CONCEPT_NAME)))
                .setReferencedComponentPublicId(createPBPublicId(conceptMap.get(MODULE_CONCEPT_NAME)))
                .setPatternForSemanticPublicId(createPBPublicId(conceptMap.get(PATH_CONCEPT_NAME)))
                .build();

        TinkarMsg msg = TinkarMsg.newBuilder()
                .setSemanticChronology(pbSemanticChronology)
                .build();

        // When we transform - should throw because zero versions
        assertThrows(Throwable.class, () -> TinkarSchemaToEntityTransformer.getInstance()
                .transform(msg, null, null), "Not allowed to have no semantic versions.");
    }

    @Test
    @DisplayName("Transform a Semantic Chronology With One Version Present - requires entity service")
    public void semanticChronologyTransformWithOneVersion() {
        // Given a PBSemanticChronology with one version
        Concept testConcept = conceptMap.get(TEST_CONCEPT_NAME);

        String expectedStringValue = "Testing Field Transformation with a string.";
        Field pbFieldString = Field.newBuilder()
                .setStringValue(expectedStringValue)
                .build();

        SemanticVersion pbSemanticVersion = SemanticVersion.newBuilder()
                .setStampChronologyPublicId(createPBPublicId(testConcept))
                .addFields(pbFieldString)
                .build();

        SemanticChronology pbSemanticChronology = SemanticChronology.newBuilder()
                .setPublicId(createPBPublicId(testConcept))
                .setReferencedComponentPublicId(createPBPublicId(conceptMap.get(MODULE_CONCEPT_NAME)))
                .setPatternForSemanticPublicId(createPBPublicId(conceptMap.get(PATH_CONCEPT_NAME)))
                .addSemanticVersions(pbSemanticVersion)
                .build();

        TinkarMsg msg = TinkarMsg.newBuilder()
                .setSemanticChronology(pbSemanticChronology)
                .build();

        // When we transform
        AtomicReference<Entity<? extends EntityVersion>> result = new AtomicReference<>();
        Consumer<Entity<? extends EntityVersion>> entityConsumer = result::set;
        Consumer<StampEntity<StampEntityVersion>> stampConsumer = (s) -> {};
        TinkarSchemaToEntityTransformer.getInstance().transform(msg, entityConsumer, stampConsumer);

        // Then the resulting SemanticEntity should have one version with one field
        assertNotNull(result.get());
        assertInstanceOf(SemanticEntity.class, result.get());
        SemanticEntity<?> semanticEntity = (SemanticEntity<?>) result.get();
        assertEquals(1, semanticEntity.versions().size());
        assertEquals(1, semanticEntity.versions().get(0).fieldValues().size());
        assertEquals(expectedStringValue, semanticEntity.versions().get(0).fieldValues().get(0));
    }
}
