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
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.StampVersionRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Map;
import java.util.UUID;

import static dev.ikm.tinkar.entity.transform.ProtobufToEntityTestHelper.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestEntityToProtobufStampTransform {

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
    @DisplayName("Transform a Entity Stamp Chronology With No Versions")
    public void stampChronologyTransformWithZeroVersions() {
        // Given a StampEntity with zero versions
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

        // When we transform our StampVersion into a PBStampVersion
        // Then the resulting PBStampVersion should throw an exception because there is an empty stamp version.
        assertThrows(Throwable.class, () -> EntityToTinkarSchemaTransformer.getInstance()
                .createPBStampChronology(stampRecord), "Not allowed to have an empty stamp version in a StampChronology.");
    }
    //TODO: Add test to check if a stamp chronology can be created with two stamp version of the same type (and time).
}
