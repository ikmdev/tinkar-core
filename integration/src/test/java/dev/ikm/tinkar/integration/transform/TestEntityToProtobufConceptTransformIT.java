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

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.ConceptRecordBuilder;
import dev.ikm.tinkar.entity.ConceptVersionRecord;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.transform.EntityToTinkarSchemaTransformer;
import dev.ikm.tinkar.integration.NewEphemeralKeyValueProvider;
import dev.ikm.tinkar.schema.TinkarMsg;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;
import java.util.UUID;

import static dev.ikm.tinkar.integration.transform.TransformTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(NewEphemeralKeyValueProvider.class)
public class TestEntityToProtobufConceptTransformIT {

    private Map<String, Concept> conceptMap;

    @BeforeAll
    public void setUp() {
        conceptMap = loadTestConcepts(this);
    }

    @Test
    @DisplayName("Transform a Entity Concept Chronology with all values - requires entity service")
    public void conceptChronologyTransformWithOneVersion() {
        // Given a ConceptRecord with one version that has a real stamp stored in the provider
        Concept testConcept = conceptMap.get(TEST_CONCEPT_NAME);
        int conceptNid = Entity.nid(testConcept.publicId());
        UUID uuid = testConcept.publicId().asUuidArray()[0];

        int stampNid = createAndStoreStamp();

        RecordListBuilder<ConceptVersionRecord> versions = RecordListBuilder.make();
        ConceptRecord conceptRecord = ConceptRecordBuilder.builder()
                .mostSignificantBits(uuid.getMostSignificantBits())
                .leastSignificantBits(uuid.getLeastSignificantBits())
                .nid(conceptNid)
                .versions(versions)
                .build();
        versions.add(new ConceptVersionRecord(conceptRecord, stampNid));
        ConceptRecord finalRecord = ConceptRecordBuilder.builder(conceptRecord).versions(versions).build();

        // When we transform the full ConceptEntity via the public transform() method
        // stamp().publicId() now works because the stamp is stored in the ephemeral provider
        TinkarMsg msg = EntityToTinkarSchemaTransformer.getInstance().transform(finalRecord);

        // Then
        assertNotNull(msg);
        assertTrue(msg.hasConceptChronology());
        assertEquals(1, msg.getConceptChronology().getConceptVersionsCount());
        assertFalse(msg.getConceptChronology().getConceptVersions(0)
                .getStampChronologyPublicId().getUuidsList().isEmpty(),
                "Stamp public ID should not be empty");
    }

    @Test
    @DisplayName("Transform a Entity Concept Version with two versions present - requires entity service")
    public void conceptVersionTransformWithTwoVersions() {
        // Given a ConceptRecord with two versions, each with a stored stamp
        Concept testConcept = conceptMap.get(TEST_CONCEPT_NAME);
        int conceptNid = Entity.nid(testConcept.publicId());
        UUID uuid = testConcept.publicId().asUuidArray()[0];

        int stampNid1 = createAndStoreStamp();
        int stampNid2 = createAndStoreStamp();

        RecordListBuilder<ConceptVersionRecord> versions = RecordListBuilder.make();
        ConceptRecord conceptRecord = ConceptRecordBuilder.builder()
                .mostSignificantBits(uuid.getMostSignificantBits())
                .leastSignificantBits(uuid.getLeastSignificantBits())
                .nid(conceptNid)
                .versions(versions)
                .build();
        versions.add(new ConceptVersionRecord(conceptRecord, stampNid1));
        versions.add(new ConceptVersionRecord(conceptRecord, stampNid2));
        ConceptRecord finalRecord = ConceptRecordBuilder.builder(conceptRecord).versions(versions).build();

        // When we transform
        TinkarMsg msg = EntityToTinkarSchemaTransformer.getInstance().transform(finalRecord);

        // Then
        assertNotNull(msg);
        assertTrue(msg.hasConceptChronology());
        assertEquals(2, msg.getConceptChronology().getConceptVersionsCount());
    }
}
