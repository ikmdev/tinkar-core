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
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.StampRecordBuilder;
import dev.ikm.tinkar.entity.StampVersionRecord;
import dev.ikm.tinkar.entity.StampVersionRecordBuilder;
import dev.ikm.tinkar.entity.transform.EntityToTinkarSchemaTransformer;
import dev.ikm.tinkar.integration.NewEphemeralKeyValueProvider;
import dev.ikm.tinkar.schema.TinkarMsg;
import dev.ikm.tinkar.terms.State;
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
public class TestEntityToProtobufStampTransformIT {

    private Map<String, Concept> conceptMap;

    @BeforeAll
    public void setUp() {
        conceptMap = loadTestConcepts(this);
    }

    private StampRecord createStampRecordWithOneVersion(long time) {
        Concept authorConcept = conceptMap.get(AUTHOR_CONCEPT_NAME);
        Concept moduleConcept = conceptMap.get(MODULE_CONCEPT_NAME);
        Concept pathConcept = conceptMap.get(PATH_CONCEPT_NAME);

        PublicId stampPublicId = PublicIds.newRandom();
        int stampNid = Entity.nid(stampPublicId);
        UUID stampUuid = stampPublicId.asUuidArray()[0];

        RecordListBuilder<StampVersionRecord> stampVersions = RecordListBuilder.make();
        StampRecord stampRecord = StampRecordBuilder.builder()
                .mostSignificantBits(stampUuid.getMostSignificantBits())
                .leastSignificantBits(stampUuid.getLeastSignificantBits())
                .nid(stampNid)
                .versions(stampVersions)
                .build();

        StampVersionRecord svr = StampVersionRecordBuilder.builder()
                .chronology(stampRecord)
                .stateNid(State.ACTIVE.nid())
                .time(time)
                .authorNid(Entity.nid(authorConcept.publicId()))
                .moduleNid(Entity.nid(moduleConcept.publicId()))
                .pathNid(Entity.nid(pathConcept.publicId()))
                .build();
        stampVersions.add(svr);
        return StampRecordBuilder.builder(stampRecord).versions(stampVersions).build();
    }

    @Test
    @DisplayName("Transform a Entity Stamp Version With all Values Present - requires entity service")
    public void testEntitytoProtobufStampVersionTransformWithValuesPresent() {
        long expectedTime = nowEpochMillis();
        StampRecord stampRecord = createStampRecordWithOneVersion(expectedTime);

        // When we transform via the public transform() method
        TinkarMsg msg = EntityToTinkarSchemaTransformer.getInstance().transform(stampRecord);

        // Then
        assertNotNull(msg);
        assertTrue(msg.hasStampChronology());
        assertTrue(msg.getStampChronology().hasFirstStampVersion());
        assertEquals(expectedTime, msg.getStampChronology().getFirstStampVersion().getTime());
        assertFalse(msg.getStampChronology().getFirstStampVersion().getStatusPublicId().getUuidsList().isEmpty());
        assertFalse(msg.getStampChronology().getFirstStampVersion().getAuthorPublicId().getUuidsList().isEmpty());
        assertFalse(msg.getStampChronology().getFirstStampVersion().getModulePublicId().getUuidsList().isEmpty());
        assertFalse(msg.getStampChronology().getFirstStampVersion().getPathPublicId().getUuidsList().isEmpty());
    }

    @Test
    @DisplayName("Transform a Entity Stamp Version With Status being Blank - requires entity service")
    public void stampVersionTransformWithStatusBeingBlank() {
        // StampVersionRecord constructor validates stateNid is non-zero
        assertThrows(Throwable.class, () -> {
            StampRecord stampRecord = createStampRecordWithOneVersion(nowEpochMillis());
            StampVersionRecordBuilder.builder()
                    .chronology(stampRecord)
                    .stateNid(0)
                    .time(nowEpochMillis())
                    .authorNid(Entity.nid(conceptMap.get(AUTHOR_CONCEPT_NAME).publicId()))
                    .moduleNid(Entity.nid(conceptMap.get(MODULE_CONCEPT_NAME).publicId()))
                    .pathNid(Entity.nid(conceptMap.get(PATH_CONCEPT_NAME).publicId()))
                    .build();
        }, "Should not allow zero stateNid");
    }

    @Test
    @DisplayName("Transform a Entity Stamp Version With Author being Blank - requires entity service")
    public void stampVersionTransformWithAuthorBeingBlank() {
        assertThrows(Throwable.class, () -> {
            StampRecord stampRecord = createStampRecordWithOneVersion(nowEpochMillis());
            StampVersionRecordBuilder.builder()
                    .chronology(stampRecord)
                    .stateNid(State.ACTIVE.nid())
                    .time(nowEpochMillis())
                    .authorNid(0)
                    .moduleNid(Entity.nid(conceptMap.get(MODULE_CONCEPT_NAME).publicId()))
                    .pathNid(Entity.nid(conceptMap.get(PATH_CONCEPT_NAME).publicId()))
                    .build();
        }, "Should not allow zero authorNid");
    }

    @Test
    @DisplayName("Transform a Entity Stamp Version With Module being Blank - requires entity service")
    public void stampVersionTransformWithModuleBeingBlank() {
        assertThrows(Throwable.class, () -> {
            StampRecord stampRecord = createStampRecordWithOneVersion(nowEpochMillis());
            StampVersionRecordBuilder.builder()
                    .chronology(stampRecord)
                    .stateNid(State.ACTIVE.nid())
                    .time(nowEpochMillis())
                    .authorNid(Entity.nid(conceptMap.get(AUTHOR_CONCEPT_NAME).publicId()))
                    .moduleNid(0)
                    .pathNid(Entity.nid(conceptMap.get(PATH_CONCEPT_NAME).publicId()))
                    .build();
        }, "Should not allow zero moduleNid");
    }

    @Test
    @DisplayName("Transform a Entity Stamp Version With Path being Blank - requires entity service")
    public void stampVersionTransformWithPathBeingBlank() {
        assertThrows(Throwable.class, () -> {
            StampRecord stampRecord = createStampRecordWithOneVersion(nowEpochMillis());
            StampVersionRecordBuilder.builder()
                    .chronology(stampRecord)
                    .stateNid(State.ACTIVE.nid())
                    .time(nowEpochMillis())
                    .authorNid(Entity.nid(conceptMap.get(AUTHOR_CONCEPT_NAME).publicId()))
                    .moduleNid(Entity.nid(conceptMap.get(MODULE_CONCEPT_NAME).publicId()))
                    .pathNid(0)
                    .build();
        }, "Should not allow zero pathNid");
    }

    @Test
    @DisplayName("Transform a Entity Stamp Chronology With One Version - requires entity service")
    public void stampChronologyTransformWithOneVersion() {
        long expectedTime = nowEpochMillis();
        StampRecord stampRecord = createStampRecordWithOneVersion(expectedTime);

        TinkarMsg msg = EntityToTinkarSchemaTransformer.getInstance().transform(stampRecord);

        assertNotNull(msg);
        assertTrue(msg.hasStampChronology());
        assertTrue(msg.getStampChronology().hasFirstStampVersion());
        assertFalse(msg.getStampChronology().hasSecondStampVersion());
        assertEquals(expectedTime, msg.getStampChronology().getFirstStampVersion().getTime());
    }

    @Test
    @DisplayName("Transform a Entity Stamp Version With Two Versions - requires entity service")
    public void stampVersionTransformWithTwoVersions() {
        long time1 = nowEpochMillis();
        long time2 = nowEpochMillis(1000);

        Concept authorConcept = conceptMap.get(AUTHOR_CONCEPT_NAME);
        Concept moduleConcept = conceptMap.get(MODULE_CONCEPT_NAME);
        Concept pathConcept = conceptMap.get(PATH_CONCEPT_NAME);

        PublicId stampPublicId = PublicIds.newRandom();
        int stampNid = Entity.nid(stampPublicId);
        UUID stampUuid = stampPublicId.asUuidArray()[0];

        RecordListBuilder<StampVersionRecord> stampVersions = RecordListBuilder.make();
        StampRecord stampRecord = StampRecordBuilder.builder()
                .mostSignificantBits(stampUuid.getMostSignificantBits())
                .leastSignificantBits(stampUuid.getLeastSignificantBits())
                .nid(stampNid)
                .versions(stampVersions)
                .build();

        stampVersions.add(StampVersionRecordBuilder.builder()
                .chronology(stampRecord)
                .stateNid(State.ACTIVE.nid())
                .time(time1)
                .authorNid(Entity.nid(authorConcept.publicId()))
                .moduleNid(Entity.nid(moduleConcept.publicId()))
                .pathNid(Entity.nid(pathConcept.publicId()))
                .build());
        stampVersions.add(StampVersionRecordBuilder.builder()
                .chronology(stampRecord)
                .stateNid(State.ACTIVE.nid())
                .time(time2)
                .authorNid(Entity.nid(authorConcept.publicId()))
                .moduleNid(Entity.nid(moduleConcept.publicId()))
                .pathNid(Entity.nid(pathConcept.publicId()))
                .build());

        StampRecord finalStamp = StampRecordBuilder.builder(stampRecord).versions(stampVersions).build();

        TinkarMsg msg = EntityToTinkarSchemaTransformer.getInstance().transform(finalStamp);

        assertNotNull(msg);
        assertTrue(msg.hasStampChronology());
        assertTrue(msg.getStampChronology().hasFirstStampVersion());
        assertTrue(msg.getStampChronology().hasSecondStampVersion());
        assertEquals(time1, msg.getStampChronology().getFirstStampVersion().getTime());
        assertEquals(time2, msg.getStampChronology().getSecondStampVersion().getTime());
    }
}
