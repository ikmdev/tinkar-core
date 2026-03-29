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
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.SemanticRecordBuilder;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import dev.ikm.tinkar.entity.SemanticVersionRecordBuilder;
import dev.ikm.tinkar.entity.transform.EntityToTinkarSchemaTransformer;
import dev.ikm.tinkar.integration.NewEphemeralKeyValueProvider;
import dev.ikm.tinkar.schema.TinkarMsg;
import org.eclipse.collections.api.factory.Lists;
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
public class TestEntityToProtobufSemanticTransformIT {

    private Map<String, Concept> conceptMap;

    @BeforeAll
    public void setUp() {
        conceptMap = loadTestConcepts(this);
    }

    private SemanticRecord createSemanticWithFields(int numVersions, int fieldsPerVersion) {
        PublicId semanticPublicId = PublicIds.newRandom();
        int semanticNid = Entity.nid(semanticPublicId);
        UUID semanticUuid = semanticPublicId.asUuidArray()[0];

        int patternNid = createAndStorePattern(conceptMap);
        int referencedComponentNid = Entity.nid(conceptMap.get(MODULE_CONCEPT_NAME).publicId());

        RecordListBuilder<SemanticVersionRecord> semanticVersions = RecordListBuilder.make();
        SemanticRecord semanticRecord = SemanticRecordBuilder.builder()
                .mostSignificantBits(semanticUuid.getMostSignificantBits())
                .leastSignificantBits(semanticUuid.getLeastSignificantBits())
                .nid(semanticNid)
                .patternNid(patternNid)
                .referencedComponentNid(referencedComponentNid)
                .versions(semanticVersions)
                .build();

        for (int v = 0; v < numVersions; v++) {
            int stampNid = createAndStoreStamp();
            Object[] fields = new Object[fieldsPerVersion];
            for (int f = 0; f < fieldsPerVersion; f++) {
                fields[f] = "Field value " + v + "-" + f;
            }

            SemanticVersionRecord svr = SemanticVersionRecordBuilder.builder()
                    .chronology(semanticRecord)
                    .stampNid(stampNid)
                    .fieldValues(Lists.immutable.of(fields))
                    .build();
            semanticVersions.add(svr);
        }

        return SemanticRecordBuilder.builder(semanticRecord).versions(semanticVersions).build();
    }

    @Test
    @DisplayName("Transform a Entity Semantic Version with all values present - requires entity service")
    public void semanticChronologyTransformWithOneVersion() {
        SemanticRecord semanticRecord = createSemanticWithFields(1, 1);
        EntityService.get().putEntity(semanticRecord);

        TinkarMsg msg = EntityToTinkarSchemaTransformer.getInstance().transform(semanticRecord);

        assertNotNull(msg);
        assertTrue(msg.hasSemanticChronology());
        assertEquals(1, msg.getSemanticChronology().getSemanticVersionsCount());
        assertEquals(1, msg.getSemanticChronology().getSemanticVersions(0).getFieldsCount());
    }

    @Test
    @DisplayName("Transform a Entity Semantic Version with all values present and two Fields - requires entity service")
    public void semanticChronologyTransformWithTwoFields() {
        SemanticRecord semanticRecord = createSemanticWithFields(1, 2);
        EntityService.get().putEntity(semanticRecord);

        TinkarMsg msg = EntityToTinkarSchemaTransformer.getInstance().transform(semanticRecord);

        assertNotNull(msg);
        assertTrue(msg.hasSemanticChronology());
        assertEquals(1, msg.getSemanticChronology().getSemanticVersionsCount());
        assertEquals(2, msg.getSemanticChronology().getSemanticVersions(0).getFieldsCount());
    }

    @Test
    @DisplayName("Transform a Entity Semantic Version With a Missing Stamp - requires entity service")
    public void semanticVersionTransformWithAMissingStamp() {
        assertThrows(Throwable.class, () -> {
            PublicId semanticPublicId = PublicIds.newRandom();
            int semanticNid = Entity.nid(semanticPublicId);
            UUID semanticUuid = semanticPublicId.asUuidArray()[0];

            RecordListBuilder<SemanticVersionRecord> semanticVersions = RecordListBuilder.make();
            SemanticRecord semanticRecord = SemanticRecordBuilder.builder()
                    .mostSignificantBits(semanticUuid.getMostSignificantBits())
                    .leastSignificantBits(semanticUuid.getLeastSignificantBits())
                    .nid(semanticNid)
                    .patternNid(Entity.nid(conceptMap.get(PATH_CONCEPT_NAME).publicId()))
                    .referencedComponentNid(Entity.nid(conceptMap.get(MODULE_CONCEPT_NAME).publicId()))
                    .versions(semanticVersions)
                    .build();

            SemanticVersionRecordBuilder.builder()
                    .chronology(semanticRecord)
                    .stampNid(0)
                    .fieldValues(Lists.immutable.of("test"))
                    .build();
        }, "Should not allow zero stampNid");
    }

    @Test
    @DisplayName("Transform a Entity Semantic Version With a Missing Field - requires entity service")
    public void semanticVersionTransformWithAMissingField() {
        assertThrows(Throwable.class, () -> {
            PublicId semanticPublicId = PublicIds.newRandom();
            int semanticNid = Entity.nid(semanticPublicId);
            UUID semanticUuid = semanticPublicId.asUuidArray()[0];

            RecordListBuilder<SemanticVersionRecord> semanticVersions = RecordListBuilder.make();
            SemanticRecord semanticRecord = SemanticRecordBuilder.builder()
                    .mostSignificantBits(semanticUuid.getMostSignificantBits())
                    .leastSignificantBits(semanticUuid.getLeastSignificantBits())
                    .nid(semanticNid)
                    .patternNid(Entity.nid(conceptMap.get(PATH_CONCEPT_NAME).publicId()))
                    .referencedComponentNid(Entity.nid(conceptMap.get(MODULE_CONCEPT_NAME).publicId()))
                    .versions(semanticVersions)
                    .build();

            int stampNid = createAndStoreStamp();
            SemanticVersionRecordBuilder.builder()
                    .chronology(semanticRecord)
                    .stampNid(stampNid)
                    .fieldValues(null)
                    .build();
        }, "Should not allow null fieldValues");
    }
}
