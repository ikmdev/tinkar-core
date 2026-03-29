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
public class TestEntityToProtobufFieldDefinitionTransformIT {

    private Map<String, Concept> conceptMap;

    @BeforeAll
    public void setUp() {
        conceptMap = loadTestConcepts(this);
    }

    @Test
    @DisplayName("Transform a Field Definition Transform With All Fields Present - requires entity service")
    public void testEntityFieldDefinitionTransformWithAllFieldsPresent() {
        // Given a PatternRecord with a FieldDefinitionRecord with all real NIDs
        Concept meaningConcept = conceptMap.get(MEANING_CONCEPT_NAME);
        Concept dataTypeConcept = conceptMap.get(DATATYPE_CONCEPT_NAME);
        Concept purposeConcept = conceptMap.get(PURPOSE_CONCEPT_NAME);

        PublicId patternPublicId = PublicIds.newRandom();
        int patternNid = Entity.nid(patternPublicId);
        UUID patternUuid = patternPublicId.asUuidArray()[0];
        int stampNid = createAndStoreStamp();

        FieldDefinitionRecord fieldDef = FieldDefinitionRecordBuilder.builder()
                .dataTypeNid(Entity.nid(dataTypeConcept.publicId()))
                .purposeNid(Entity.nid(purposeConcept.publicId()))
                .meaningNid(Entity.nid(meaningConcept.publicId()))
                .patternVersionStampNid(stampNid)
                .patternNid(patternNid)
                .indexInPattern(0)
                .build();

        RecordListBuilder<PatternVersionRecord> patternVersions = RecordListBuilder.make();
        PatternRecord patternRecord = PatternRecordBuilder.builder()
                .mostSignificantBits(patternUuid.getMostSignificantBits())
                .leastSignificantBits(patternUuid.getLeastSignificantBits())
                .nid(patternNid)
                .versions(patternVersions)
                .build();

        PatternVersionRecord pvr = PatternVersionRecordBuilder.builder()
                .chronology(patternRecord)
                .stampNid(stampNid)
                .semanticPurposeNid(Entity.nid(conceptMap.get(REF_COMP_PURPOSE_CONCEPT_NAME).publicId()))
                .semanticMeaningNid(Entity.nid(conceptMap.get(REF_COMP_MEANING_CONCEPT_NAME).publicId()))
                .fieldDefinitions(Lists.immutable.of(fieldDef))
                .build();
        patternVersions.add(pvr);
        PatternRecord finalPattern = PatternRecordBuilder.builder(patternRecord).versions(patternVersions).build();
        EntityService.get().putEntity(finalPattern);

        // When we transform via the public transform() method
        TinkarMsg msg = EntityToTinkarSchemaTransformer.getInstance().transform(finalPattern);

        // Then
        assertNotNull(msg);
        assertTrue(msg.hasPatternChronology());
        var fieldDefs = msg.getPatternChronology().getPatternVersions(0).getFieldDefinitionsList();
        assertEquals(1, fieldDefs.size());
        assertFalse(fieldDefs.get(0).getMeaningPublicId().getUuidsList().isEmpty());
        assertFalse(fieldDefs.get(0).getDataTypePublicId().getUuidsList().isEmpty());
        assertFalse(fieldDefs.get(0).getPurposePublicId().getUuidsList().isEmpty());
    }

    @Test
    @DisplayName("Transform a Field Definition Transform With Missing DataType - requires entity service")
    public void testEntityFieldDefinitionTransformWithMissingDataType() {
        assertThrows(Throwable.class, () -> {
            int stampNid = createAndStoreStamp();
            FieldDefinitionRecordBuilder.builder()
                    .dataTypeNid(0)
                    .purposeNid(Entity.nid(conceptMap.get(PURPOSE_CONCEPT_NAME).publicId()))
                    .meaningNid(Entity.nid(conceptMap.get(MEANING_CONCEPT_NAME).publicId()))
                    .patternVersionStampNid(stampNid)
                    .patternNid(Entity.nid(PublicIds.newRandom()))
                    .indexInPattern(0)
                    .build();
        }, "Should not allow zero dataTypeNid");
    }

    @Test
    @DisplayName("Transform a Field Definition Transform With Missing Meaning - requires entity service")
    public void testEntityFieldDefinitionTransformWithMissingMeaning() {
        assertThrows(Throwable.class, () -> {
            int stampNid = createAndStoreStamp();
            FieldDefinitionRecordBuilder.builder()
                    .dataTypeNid(Entity.nid(conceptMap.get(DATATYPE_CONCEPT_NAME).publicId()))
                    .purposeNid(Entity.nid(conceptMap.get(PURPOSE_CONCEPT_NAME).publicId()))
                    .meaningNid(0)
                    .patternVersionStampNid(stampNid)
                    .patternNid(Entity.nid(PublicIds.newRandom()))
                    .indexInPattern(0)
                    .build();
        }, "Should not allow zero meaningNid");
    }

    @Test
    @DisplayName("Transform a Field Definition Transform With Missing Purpose - requires entity service")
    public void testEntityFieldDefinitionTransformWithMissingPurpose() {
        assertThrows(Throwable.class, () -> {
            int stampNid = createAndStoreStamp();
            FieldDefinitionRecordBuilder.builder()
                    .dataTypeNid(Entity.nid(conceptMap.get(DATATYPE_CONCEPT_NAME).publicId()))
                    .purposeNid(0)
                    .meaningNid(Entity.nid(conceptMap.get(MEANING_CONCEPT_NAME).publicId()))
                    .patternVersionStampNid(stampNid)
                    .patternNid(Entity.nid(PublicIds.newRandom()))
                    .indexInPattern(0)
                    .build();
        }, "Should not allow zero purposeNid");
    }
}
