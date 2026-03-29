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
import dev.ikm.tinkar.entity.FieldDefinitionRecord;
import dev.ikm.tinkar.entity.FieldDefinitionRecordBuilder;
import dev.ikm.tinkar.entity.PatternRecord;
import dev.ikm.tinkar.entity.PatternRecordBuilder;
import dev.ikm.tinkar.entity.PatternVersionRecord;
import dev.ikm.tinkar.entity.PatternVersionRecordBuilder;
import dev.ikm.tinkar.entity.RecordListBuilder;
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
public class TestEntityToProtobufPatternTransformIT {

    private Map<String, Concept> conceptMap;

    @BeforeAll
    public void setUp() {
        conceptMap = loadTestConcepts(this);
    }

    private PatternRecord createPatternWithVersions(int numVersions) {
        PublicId patternPublicId = PublicIds.newRandom();
        int patternNid = Entity.nid(patternPublicId);
        UUID patternUuid = patternPublicId.asUuidArray()[0];

        Concept purposeConcept = conceptMap.get(REF_COMP_PURPOSE_CONCEPT_NAME);
        Concept meaningConcept = conceptMap.get(REF_COMP_MEANING_CONCEPT_NAME);
        Concept dataTypeConcept = conceptMap.get(DATATYPE_CONCEPT_NAME);
        Concept fieldMeaningConcept = conceptMap.get(MEANING_CONCEPT_NAME);
        Concept fieldPurposeConcept = conceptMap.get(PURPOSE_CONCEPT_NAME);

        RecordListBuilder<PatternVersionRecord> patternVersions = RecordListBuilder.make();
        PatternRecord patternRecord = PatternRecordBuilder.builder()
                .mostSignificantBits(patternUuid.getMostSignificantBits())
                .leastSignificantBits(patternUuid.getLeastSignificantBits())
                .nid(patternNid)
                .versions(patternVersions)
                .build();

        for (int i = 0; i < numVersions; i++) {
            int stampNid = createAndStoreStamp();
            FieldDefinitionRecord fieldDef = FieldDefinitionRecordBuilder.builder()
                    .dataTypeNid(Entity.nid(dataTypeConcept.publicId()))
                    .purposeNid(Entity.nid(fieldPurposeConcept.publicId()))
                    .meaningNid(Entity.nid(fieldMeaningConcept.publicId()))
                    .patternVersionStampNid(stampNid)
                    .patternNid(patternNid)
                    .indexInPattern(0)
                    .build();

            PatternVersionRecord pvr = PatternVersionRecordBuilder.builder()
                    .chronology(patternRecord)
                    .stampNid(stampNid)
                    .semanticPurposeNid(Entity.nid(purposeConcept.publicId()))
                    .semanticMeaningNid(Entity.nid(meaningConcept.publicId()))
                    .fieldDefinitions(Lists.immutable.of(fieldDef))
                    .build();
            patternVersions.add(pvr);
        }

        return PatternRecordBuilder.builder(patternRecord).versions(patternVersions).build();
    }

    @Test
    @DisplayName("Transform one Entity Pattern Version with all values present - requires entity service")
    public void patternVersionTransformWithOneVersion() {
        PatternRecord patternRecord = createPatternWithVersions(1);
        EntityService.get().putEntity(patternRecord);

        TinkarMsg msg = EntityToTinkarSchemaTransformer.getInstance().transform(patternRecord);

        assertNotNull(msg);
        assertTrue(msg.hasPatternChronology());
        assertEquals(1, msg.getPatternChronology().getPatternVersionsCount());
        assertEquals(1, msg.getPatternChronology().getPatternVersions(0).getFieldDefinitionsCount());
        assertFalse(msg.getPatternChronology().getPatternVersions(0)
                .getReferencedComponentPurposePublicId().getUuidsList().isEmpty());
        assertFalse(msg.getPatternChronology().getPatternVersions(0)
                .getReferencedComponentMeaningPublicId().getUuidsList().isEmpty());
    }

    @Test
    @DisplayName("Transform two Entity Pattern Version with all values present - requires entity service")
    public void patternVersionTransformWithTwoVersions() {
        PatternRecord patternRecord = createPatternWithVersions(2);
        EntityService.get().putEntity(patternRecord);

        TinkarMsg msg = EntityToTinkarSchemaTransformer.getInstance().transform(patternRecord);

        assertNotNull(msg);
        assertTrue(msg.hasPatternChronology());
        assertEquals(2, msg.getPatternChronology().getPatternVersionsCount());
    }

    @Test
    @DisplayName("Transform one Entity Pattern Version with Meaning missing and Purpose present - requires entity service")
    public void patternVersionTransformWithOneVersionWithMeaningMissingPurposePresent() {
        assertThrows(Throwable.class, () -> {
            PublicId patternPublicId = PublicIds.newRandom();
            int patternNid = Entity.nid(patternPublicId);
            UUID patternUuid = patternPublicId.asUuidArray()[0];

            RecordListBuilder<PatternVersionRecord> patternVersions = RecordListBuilder.make();
            PatternRecord patternRecord = PatternRecordBuilder.builder()
                    .mostSignificantBits(patternUuid.getMostSignificantBits())
                    .leastSignificantBits(patternUuid.getLeastSignificantBits())
                    .nid(patternNid)
                    .versions(patternVersions)
                    .build();

            int stampNid = createAndStoreStamp();
            PatternVersionRecordBuilder.builder()
                    .chronology(patternRecord)
                    .stampNid(stampNid)
                    .semanticPurposeNid(Entity.nid(conceptMap.get(REF_COMP_PURPOSE_CONCEPT_NAME).publicId()))
                    .semanticMeaningNid(0)
                    .fieldDefinitions(Lists.immutable.empty())
                    .build();
        }, "Should not allow zero semanticMeaningNid");
    }

    @Test
    @DisplayName("Transform one Entity Pattern Version with Purpose missing and Meaning present - requires entity service")
    public void patternVersionTransformWithOneVersionWithPurposeMissingMeaningPresent() {
        assertThrows(Throwable.class, () -> {
            PublicId patternPublicId = PublicIds.newRandom();
            int patternNid = Entity.nid(patternPublicId);
            UUID patternUuid = patternPublicId.asUuidArray()[0];

            RecordListBuilder<PatternVersionRecord> patternVersions = RecordListBuilder.make();
            PatternRecord patternRecord = PatternRecordBuilder.builder()
                    .mostSignificantBits(patternUuid.getMostSignificantBits())
                    .leastSignificantBits(patternUuid.getLeastSignificantBits())
                    .nid(patternNid)
                    .versions(patternVersions)
                    .build();

            int stampNid = createAndStoreStamp();
            PatternVersionRecordBuilder.builder()
                    .chronology(patternRecord)
                    .stampNid(stampNid)
                    .semanticPurposeNid(0)
                    .semanticMeaningNid(Entity.nid(conceptMap.get(REF_COMP_MEANING_CONCEPT_NAME).publicId()))
                    .fieldDefinitions(Lists.immutable.empty())
                    .build();
        }, "Should not allow zero semanticPurposeNid");
    }
}
