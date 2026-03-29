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
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampEntityVersion;
import dev.ikm.tinkar.entity.transform.TinkarSchemaToEntityTransformer;
import dev.ikm.tinkar.integration.NewEphemeralKeyValueProvider;
import dev.ikm.tinkar.schema.ConceptChronology;
import dev.ikm.tinkar.schema.ConceptVersion;
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
public class TestProtobufToEntityConceptTransformIT {

    private Map<String, Concept> conceptMap;

    @BeforeAll
    public void setUp() {
        conceptMap = loadTestConcepts(this);
    }

    @Test
    @DisplayName("Transform a Concept Chronology With Zero Version - requires entity service")
    public void conceptChronologyTransformWithZeroVersion() {
        // Given a PBConceptChronology with a public ID but no versions
        ConceptChronology pbConceptChronology = ConceptChronology.newBuilder()
                .setPublicId(createPBPublicId(conceptMap.get(TEST_CONCEPT_NAME)))
                .build();

        TinkarMsg msg = TinkarMsg.newBuilder()
                .setConceptChronology(pbConceptChronology)
                .build();

        // When we transform - should throw because zero versions
        assertThrows(Throwable.class, () -> TinkarSchemaToEntityTransformer.getInstance()
                .transform(msg, null, null), "Not allowed to have no stamp versions.");
    }

    @Test
    @DisplayName("Transform a Concept Chronology With One Version - requires entity service")
    public void conceptChronologyTransformWithOneVersion() {
        // Given a PBConceptChronology with one Stamp Version present
        Concept testConcept = conceptMap.get(TEST_CONCEPT_NAME);

        ConceptVersion pbConceptVersion = ConceptVersion.newBuilder()
                .setStampChronologyPublicId(createPBPublicId(testConcept))
                .build();

        ConceptChronology pbConceptChronology = ConceptChronology.newBuilder()
                .setPublicId(createPBPublicId(testConcept))
                .addConceptVersions(pbConceptVersion)
                .build();

        TinkarMsg msg = TinkarMsg.newBuilder()
                .setConceptChronology(pbConceptChronology)
                .build();

        // When we transform - Entity.nid() works with ephemeral provider
        AtomicReference<Entity<? extends EntityVersion>> result = new AtomicReference<>();
        Consumer<Entity<? extends EntityVersion>> entityConsumer = result::set;
        Consumer<StampEntity<StampEntityVersion>> stampConsumer = (s) -> {};
        TinkarSchemaToEntityTransformer.getInstance().transform(msg, entityConsumer, stampConsumer);

        // Then the resulting ConceptEntity should have the right public ID and one version
        assertNotNull(result.get());
        assertInstanceOf(ConceptEntity.class, result.get());
        ConceptEntity<?> conceptEntity = (ConceptEntity<?>) result.get();
        assertEquals(testConcept.publicId().asUuidArray()[0],
                conceptEntity.publicId().asUuidArray()[0],
                "Public IDs should match");
        assertEquals(1, conceptEntity.versions().size(), "Should have one version");
    }

    @Test
    @DisplayName("Transform a Concept Chronology With Two Versions - requires entity service")
    public void conceptChronologyTransformWithTwoVersions() {
        // Given a PBConceptChronology with two Stamp Versions present
        Concept testConcept = conceptMap.get(TEST_CONCEPT_NAME);

        ConceptVersion pbConceptVersionOne = ConceptVersion.newBuilder()
                .setStampChronologyPublicId(createPBPublicId(testConcept))
                .build();

        ConceptVersion pbConceptVersionTwo = ConceptVersion.newBuilder()
                .setStampChronologyPublicId(createPBPublicId(conceptMap.get(MODULE_CONCEPT_NAME)))
                .build();

        ConceptChronology pbConceptChronology = ConceptChronology.newBuilder()
                .setPublicId(createPBPublicId(testConcept))
                .addConceptVersions(pbConceptVersionOne)
                .addConceptVersions(pbConceptVersionTwo)
                .build();

        TinkarMsg msg = TinkarMsg.newBuilder()
                .setConceptChronology(pbConceptChronology)
                .build();

        // When we transform
        AtomicReference<Entity<? extends EntityVersion>> result = new AtomicReference<>();
        Consumer<Entity<? extends EntityVersion>> entityConsumer = result::set;
        Consumer<StampEntity<StampEntityVersion>> stampConsumer = (s) -> {};
        TinkarSchemaToEntityTransformer.getInstance().transform(msg, entityConsumer, stampConsumer);

        // Then the resulting ConceptEntity should have two versions
        assertNotNull(result.get());
        ConceptEntity<?> conceptEntity = (ConceptEntity<?>) result.get();
        assertEquals(2, conceptEntity.versions().size(), "Should have two versions");
    }
}
