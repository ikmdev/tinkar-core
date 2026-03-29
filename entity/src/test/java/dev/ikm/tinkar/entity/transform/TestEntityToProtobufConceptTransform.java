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
import dev.ikm.tinkar.entity.ConceptVersionRecord;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.StampVersionRecord;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.schema.ConceptVersion;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.ikm.tinkar.entity.transform.ProtobufToEntityTestHelper.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestEntityToProtobufConceptTransform {

    private Map<String, Concept> conceptMap;

    @BeforeEach
    public void setUp() {
        conceptMap = loadTestConcepts(this);
    }

    @AfterEach
    public void tearDown() {
        clearRegistry();
    }

    @Test
    @DisplayName("createPBStampChronology function throws error When StampEntity is empty")
    public void testStampEntityWithEmptyVersions() {
        // Given a StampEntity with empty versions
        PublicId randomPublicID = PublicIds.newRandom();
        UUID uuid = randomPublicID.asUuidArray()[0];
        RecordListBuilder<StampVersionRecord> emptyVersions = RecordListBuilder.make();
        emptyVersions.build();
        StampRecord stampRecord = new StampRecord(
                uuid.getMostSignificantBits(),
                uuid.getLeastSignificantBits(),
                null,
                -1,
                emptyVersions);

        // When/Then - should throw because versions is empty
        assertThrows(RuntimeException.class, () -> EntityToTinkarSchemaTransformer.getInstance()
                .createPBStampChronology(stampRecord), "Unexpected number of version size: 0 " +
                " for stamp entity: " + randomPublicID);
    }

    @Test
    @DisplayName("createPBStampChronology function throws error When StampEntity has more than 2 Versions")
    public void testStampEntityWithMoreThanTwoVersions() {
        // Given a StampEntity with three versions
        // We need a StampRecord with 3 versions. StampVersionRecord requires non-zero nids.
        PublicId randomPublicID = PublicIds.newRandom();
        UUID uuid = randomPublicID.asUuidArray()[0];
        RecordListBuilder<StampVersionRecord> versions = RecordListBuilder.make();
        StampRecord stampRecord = new StampRecord(
                uuid.getMostSignificantBits(),
                uuid.getLeastSignificantBits(),
                null,
                -1,
                versions);
        // Add three versions with arbitrary non-zero nids
        versions.add(new StampVersionRecord(stampRecord, -10, nowEpochMillis(), -20, -30, -40));
        versions.add(new StampVersionRecord(stampRecord, -10, nowEpochMillis(1000), -20, -30, -40));
        versions.add(new StampVersionRecord(stampRecord, -10, nowEpochMillis(2000), -20, -30, -40));
        versions.build();

        // When/Then - should throw because versions > 2
        assertThrows(RuntimeException.class, () -> EntityToTinkarSchemaTransformer.getInstance()
                .createPBStampChronology(stampRecord), "Unexpected number of version size: 3" +
                " for stamp entity: " + randomPublicID);
    }

    @Test
    @DisplayName("Transform a Entity Concept Chronology With Zero Versions/Values Present")
    public void conceptChronologyTransformWithZeroVersion() {
        // Given an Entity Concept Version
        // When we transform our Entity Concept Version into a PBConceptVersion
        // Then the resulting PBConceptVersion should match the original entity value
        assertThrows(Throwable.class, () -> EntityToTinkarSchemaTransformer.getInstance()
                .createPBConceptVersions(RecordListBuilder.make().build()), "Not allowed to have an empty Concept Version.");
    }

}
