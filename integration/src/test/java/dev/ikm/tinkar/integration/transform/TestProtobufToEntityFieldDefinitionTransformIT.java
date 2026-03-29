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
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampEntityVersion;
import dev.ikm.tinkar.entity.transform.TinkarSchemaToEntityTransformer;
import dev.ikm.tinkar.integration.NewEphemeralKeyValueProvider;
import dev.ikm.tinkar.schema.FieldDefinition;
import dev.ikm.tinkar.schema.PatternChronology;
import dev.ikm.tinkar.schema.PatternVersion;
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
public class TestProtobufToEntityFieldDefinitionTransformIT {

    private Map<String, Concept> conceptMap;

    @BeforeAll
    public void setUp() {
        conceptMap = loadTestConcepts(this);
    }

    private PatternEntity<?> createPatternWithFieldDef(FieldDefinition fieldDef) {
        Concept testConcept = conceptMap.get(TEST_CONCEPT_NAME);
        PatternVersion pbPatternVersion = PatternVersion.newBuilder()
                .setStampChronologyPublicId(createPBPublicId(testConcept))
                .setReferencedComponentPurposePublicId(createPBPublicId(conceptMap.get(REF_COMP_PURPOSE_CONCEPT_NAME)))
                .setReferencedComponentMeaningPublicId(createPBPublicId(conceptMap.get(REF_COMP_MEANING_CONCEPT_NAME)))
                .addFieldDefinitions(fieldDef)
                .build();

        PatternChronology pbPatternChronology = PatternChronology.newBuilder()
                .setPublicId(createPBPublicId(testConcept))
                .addPatternVersions(pbPatternVersion)
                .build();

        TinkarMsg msg = TinkarMsg.newBuilder()
                .setPatternChronology(pbPatternChronology)
                .build();

        AtomicReference<Entity<? extends EntityVersion>> result = new AtomicReference<>();
        Consumer<Entity<? extends EntityVersion>> entityConsumer = result::set;
        Consumer<StampEntity<StampEntityVersion>> stampConsumer = (s) -> {};
        TinkarSchemaToEntityTransformer.getInstance().transform(msg, entityConsumer, stampConsumer);
        return (PatternEntity<?>) result.get();
    }

    @Test
    @DisplayName("Transform a Field Definition Transform With All Fields Present - requires entity service")
    public void testEntityFieldDefinitionTransformWithAllFieldPresent() {
        Concept meaningConcept = conceptMap.get(MEANING_CONCEPT_NAME);
        Concept dataTypeConcept = conceptMap.get(DATATYPE_CONCEPT_NAME);
        Concept purposeConcept = conceptMap.get(PURPOSE_CONCEPT_NAME);

        FieldDefinition pbFieldDef = FieldDefinition.newBuilder()
                .setMeaningPublicId(createPBPublicId(meaningConcept))
                .setDataTypePublicId(createPBPublicId(dataTypeConcept))
                .setPurposePublicId(createPBPublicId(purposeConcept))
                .build();

        // When we transform via PatternChronology
        PatternEntity<?> patternEntity = createPatternWithFieldDef(pbFieldDef);

        // Then the field definition should have the correct NIDs
        assertNotNull(patternEntity);
        var fieldDefs = patternEntity.versions().get(0).fieldDefinitions();
        assertEquals(1, fieldDefs.size());
        assertEquals(Entity.nid(meaningConcept.publicId()), fieldDefs.get(0).meaningNid());
        assertEquals(Entity.nid(dataTypeConcept.publicId()), fieldDefs.get(0).dataTypeNid());
        assertEquals(Entity.nid(purposeConcept.publicId()), fieldDefs.get(0).purposeNid());
    }

    @Test
    @DisplayName("Transform a Field Definition Transform With a Missing Data Type - requires entity service")
    public void testEntityFieldDefinitionTransformWithMissingDataType() {
        FieldDefinition pbFieldDef = FieldDefinition.newBuilder()
                .setMeaningPublicId(createPBPublicId(conceptMap.get(MEANING_CONCEPT_NAME)))
                .setDataTypePublicId(createPBPublicId())
                .setPurposePublicId(createPBPublicId(conceptMap.get(PURPOSE_CONCEPT_NAME)))
                .build();

        assertThrows(Throwable.class, () -> createPatternWithFieldDef(pbFieldDef),
                "Should throw when data type has empty public id");
    }

    @Test
    @DisplayName("Transform a Field Definition Transform With a Missing Meaning - requires entity service")
    public void testEntityFieldDefinitionTransformWithMissingMeaning() {
        FieldDefinition pbFieldDef = FieldDefinition.newBuilder()
                .setMeaningPublicId(createPBPublicId())
                .setDataTypePublicId(createPBPublicId(conceptMap.get(DATATYPE_CONCEPT_NAME)))
                .setPurposePublicId(createPBPublicId(conceptMap.get(PURPOSE_CONCEPT_NAME)))
                .build();

        assertThrows(Throwable.class, () -> createPatternWithFieldDef(pbFieldDef),
                "Should throw when meaning has empty public id");
    }

    @Test
    @DisplayName("Transform a Field Definition Transform With a Missing Purpose - requires entity service")
    public void testEntityFieldDefinitionTransformWithMissingPurpose() {
        FieldDefinition pbFieldDef = FieldDefinition.newBuilder()
                .setMeaningPublicId(createPBPublicId(conceptMap.get(MEANING_CONCEPT_NAME)))
                .setDataTypePublicId(createPBPublicId(conceptMap.get(DATATYPE_CONCEPT_NAME)))
                .setPurposePublicId(createPBPublicId())
                .build();

        assertThrows(Throwable.class, () -> createPatternWithFieldDef(pbFieldDef),
                "Should throw when purpose has empty public id");
    }
}
