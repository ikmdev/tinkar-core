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

import dev.ikm.tinkar.common.id.PublicId;
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
public class TestEntityToProtobufPublicIdTransformIT {

    private Map<String, Concept> conceptMap;

    @BeforeAll
    public void setUp() {
        conceptMap = loadTestConcepts(this);
    }

    @Test
    @DisplayName("Transform a Entity Public ID into a Protobuf message with a list of public ID's.")
    public void publicIdEntityTransformWithPublicIDList() {
        // Given a concept with two UUIDs in its PublicId
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        PublicId combinedPublicId = PublicIds.of(uuid1, uuid2);
        int nid = Entity.nid(combinedPublicId);

        // Create a ConceptRecord with this multi-UUID public ID and transform via public API
        int stampNid = createAndStoreStamp();
        RecordListBuilder<ConceptVersionRecord> versions = RecordListBuilder.make();
        ConceptRecord conceptRecord = ConceptRecordBuilder.builder()
                .mostSignificantBits(uuid1.getMostSignificantBits())
                .leastSignificantBits(uuid1.getLeastSignificantBits())
                .nid(nid)
                .versions(versions)
                .build();
        versions.add(new ConceptVersionRecord(conceptRecord, stampNid));
        ConceptRecord finalRecord = ConceptRecordBuilder.builder(conceptRecord).versions(versions).build();

        // When we transform
        TinkarMsg msg = EntityToTinkarSchemaTransformer.getInstance().transform(finalRecord);

        // Then the resulting protobuf PublicId should contain the first UUID
        assertNotNull(msg);
        assertTrue(msg.hasConceptChronology());
        var pbPublicId = msg.getConceptChronology().getPublicId();
        assertFalse(pbPublicId.getUuidsList().isEmpty(), "Should have at least one UUID");
        assertEquals(uuid1.toString(), pbPublicId.getUuids(0), "First UUID should match");
    }
}
