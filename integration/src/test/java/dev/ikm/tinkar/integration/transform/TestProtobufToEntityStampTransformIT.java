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
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampEntityVersion;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.StampVersionRecord;
import dev.ikm.tinkar.entity.transform.TinkarSchemaToEntityTransformer;
import dev.ikm.tinkar.integration.NewEphemeralKeyValueProvider;
import dev.ikm.tinkar.schema.StampChronology;
import dev.ikm.tinkar.schema.StampVersion;
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
public class TestProtobufToEntityStampTransformIT {

    private Map<String, Concept> conceptMap;

    @BeforeAll
    public void setUp() {
        conceptMap = loadTestConcepts(this);
    }

    private StampRecord transformStampViaPublicApi(StampChronology pbStampChronology) {
        TinkarMsg msg = TinkarMsg.newBuilder()
                .setStampChronology(pbStampChronology)
                .build();

        AtomicReference<Entity<? extends EntityVersion>> entityResult = new AtomicReference<>();
        Consumer<Entity<? extends EntityVersion>> entityConsumer = entityResult::set;
        Consumer<StampEntity<StampEntityVersion>> stampConsumer = (s) -> {};
        TinkarSchemaToEntityTransformer.getInstance().transform(msg, entityConsumer, stampConsumer);
        return (StampRecord) entityResult.get();
    }

    @Test
    @DisplayName("Transform a Stamp Version With All Fields Present - requires entity service")
    public void stampVersionTransformWithStatusTimeAuthorModulePathPresent() {
        // Given a PBStampVersion with all fields
        long expectedTime = nowTimestamp();
        Concept statusConcept = conceptMap.get(STATUS_CONCEPT_NAME);
        Concept authorConcept = conceptMap.get(AUTHOR_CONCEPT_NAME);
        Concept moduleConcept = conceptMap.get(MODULE_CONCEPT_NAME);
        Concept pathConcept = conceptMap.get(PATH_CONCEPT_NAME);

        StampVersion pbStampVersion = createPbStampVersion(expectedTime, statusConcept, authorConcept, moduleConcept, pathConcept);

        StampChronology pbStampChronology = StampChronology.newBuilder()
                .setPublicId(createPBPublicId(conceptMap.get(TEST_CONCEPT_NAME)))
                .setFirstStampVersion(pbStampVersion)
                .build();

        // When we transform
        StampRecord stampRecord = transformStampViaPublicApi(pbStampChronology);

        // Then the resulting StampRecord should have one version with matching fields
        assertNotNull(stampRecord);
        assertEquals(1, stampRecord.versions().size());
        StampVersionRecord svr = stampRecord.versions().get(0);
        assertEquals(expectedTime, svr.time());
        assertEquals(Entity.nid(statusConcept.publicId()), svr.stateNid());
        assertEquals(Entity.nid(authorConcept.publicId()), svr.authorNid());
        assertEquals(Entity.nid(moduleConcept.publicId()), svr.moduleNid());
        assertEquals(Entity.nid(pathConcept.publicId()), svr.pathNid());
    }

    @Test
    @DisplayName("Transform a Stamp Version With All Fields Present Two - requires entity service")
    public void stampVersionTransformWithStatusTimeAuthorModulePathPresent2() {
        // Given a PBStampVersion with all fields (using conceptMap-based helper)
        long expectedTime = nowTimestamp();
        StampVersion pbStampVersion = createPbStampVersion(conceptMap, expectedTime);

        StampChronology pbStampChronology = StampChronology.newBuilder()
                .setPublicId(createPBPublicId(conceptMap.get(TEST_CONCEPT_NAME)))
                .setFirstStampVersion(pbStampVersion)
                .build();

        // When we transform
        StampRecord stampRecord = transformStampViaPublicApi(pbStampChronology);

        // Then
        assertNotNull(stampRecord);
        assertEquals(1, stampRecord.versions().size());
        assertEquals(expectedTime, stampRecord.versions().get(0).time());
    }

    @Test
    @DisplayName("Transform a Stamp Chronology With One Version - requires entity service")
    public void stampChronologyTransformWithOneVersion() {
        // Given a PBStampChronology with one stamp version
        long expectedTime = nowTimestamp();
        Concept testConcept = conceptMap.get(TEST_CONCEPT_NAME);

        StampVersion pbStampVersion = createPbStampVersion(conceptMap, expectedTime);
        StampChronology pbStampChronology = StampChronology.newBuilder()
                .setPublicId(createPBPublicId(testConcept))
                .setFirstStampVersion(pbStampVersion)
                .build();

        // When we transform
        StampRecord stampRecord = transformStampViaPublicApi(pbStampChronology);

        // Then
        assertNotNull(stampRecord);
        assertEquals(1, stampRecord.versions().size());
        assertEquals(testConcept.publicId().asUuidArray()[0],
                stampRecord.publicId().asUuidArray()[0]);
    }

    @Test
    @DisplayName("Transform a Stamp Chronology With Two Versions - requires entity service")
    public void stampChronologyTransformWithTwoVersions() {
        // Given a PBStampChronology with two stamp versions
        long time1 = nowTimestamp();
        long time2 = nowTimestamp(1000);
        Concept testConcept = conceptMap.get(TEST_CONCEPT_NAME);

        StampVersion pbStampVersion1 = createPbStampVersion(conceptMap, time1);
        StampVersion pbStampVersion2 = createPbStampVersion(conceptMap, time2);

        StampChronology pbStampChronology = StampChronology.newBuilder()
                .setPublicId(createPBPublicId(testConcept))
                .setFirstStampVersion(pbStampVersion1)
                .setSecondStampVersion(pbStampVersion2)
                .build();

        // When we transform
        StampRecord stampRecord = transformStampViaPublicApi(pbStampChronology);

        // Then
        assertNotNull(stampRecord);
        assertEquals(2, stampRecord.versions().size());
    }
}
