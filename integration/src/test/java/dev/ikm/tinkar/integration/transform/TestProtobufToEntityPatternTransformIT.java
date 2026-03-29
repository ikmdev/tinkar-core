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
public class TestProtobufToEntityPatternTransformIT {

    private Map<String, Concept> conceptMap;

    @BeforeAll
    public void setUp() {
        conceptMap = loadTestConcepts(this);
    }

    private PatternEntity<?> transformPatternViaPublicApi(PatternChronology pbPatternChronology) {
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
    @DisplayName("Transform a Pattern Chronology With Zero Versions Present - requires entity service")
    public void patternChronologyTransformWithZeroVersion() {
        // Given a PBPatternChronology with no Pattern Versions present
        PatternChronology pbPatternChronology = PatternChronology.newBuilder()
                .setPublicId(createPBPublicId(conceptMap.get(TEST_CONCEPT_NAME)))
                .build();

        TinkarMsg msg = TinkarMsg.newBuilder()
                .setPatternChronology(pbPatternChronology)
                .build();

        // When we transform PBPatternChronology - should throw
        assertThrows(Throwable.class, () -> TinkarSchemaToEntityTransformer.getInstance()
                .transform(msg, null, null), "Not allowed to have no pattern versions.");
    }

    @Test
    @DisplayName("Transform a Pattern Chronology With One Version Present - requires entity service")
    public void patternChronologyTransformWithOneVersion() {
        Concept testConcept = conceptMap.get(TEST_CONCEPT_NAME);
        Concept purposeConcept = conceptMap.get(PURPOSE_CONCEPT_NAME);
        Concept meaningConcept = conceptMap.get(MEANING_CONCEPT_NAME);
        Concept dataTypeConcept = conceptMap.get(DATATYPE_CONCEPT_NAME);

        FieldDefinition pbFieldDef = FieldDefinition.newBuilder()
                .setMeaningPublicId(createPBPublicId(meaningConcept))
                .setDataTypePublicId(createPBPublicId(dataTypeConcept))
                .setPurposePublicId(createPBPublicId(purposeConcept))
                .build();

        PatternVersion pbPatternVersion = PatternVersion.newBuilder()
                .setStampChronologyPublicId(createPBPublicId(testConcept))
                .setReferencedComponentPurposePublicId(createPBPublicId(conceptMap.get(REF_COMP_PURPOSE_CONCEPT_NAME)))
                .setReferencedComponentMeaningPublicId(createPBPublicId(conceptMap.get(REF_COMP_MEANING_CONCEPT_NAME)))
                .addFieldDefinitions(pbFieldDef)
                .build();

        PatternChronology pbPatternChronology = PatternChronology.newBuilder()
                .setPublicId(createPBPublicId(testConcept))
                .addPatternVersions(pbPatternVersion)
                .build();

        // When we transform
        PatternEntity<?> patternEntity = transformPatternViaPublicApi(pbPatternChronology);

        // Then
        assertNotNull(patternEntity);
        assertEquals(1, patternEntity.versions().size());
        assertEquals(1, patternEntity.versions().get(0).fieldDefinitions().size());
    }

    @Test
    @DisplayName("Transform a Pattern Chronology With Two Versions Present - requires entity service")
    public void patternChronologyTransformWithTwoVersions() {
        Concept testConcept = conceptMap.get(TEST_CONCEPT_NAME);
        Concept purposeConcept = conceptMap.get(PURPOSE_CONCEPT_NAME);
        Concept meaningConcept = conceptMap.get(MEANING_CONCEPT_NAME);
        Concept dataTypeConcept = conceptMap.get(DATATYPE_CONCEPT_NAME);

        FieldDefinition pbFieldDef = FieldDefinition.newBuilder()
                .setMeaningPublicId(createPBPublicId(meaningConcept))
                .setDataTypePublicId(createPBPublicId(dataTypeConcept))
                .setPurposePublicId(createPBPublicId(purposeConcept))
                .build();

        PatternVersion pbPatternVersion1 = PatternVersion.newBuilder()
                .setStampChronologyPublicId(createPBPublicId(testConcept))
                .setReferencedComponentPurposePublicId(createPBPublicId(conceptMap.get(REF_COMP_PURPOSE_CONCEPT_NAME)))
                .setReferencedComponentMeaningPublicId(createPBPublicId(conceptMap.get(REF_COMP_MEANING_CONCEPT_NAME)))
                .addFieldDefinitions(pbFieldDef)
                .build();

        PatternVersion pbPatternVersion2 = PatternVersion.newBuilder()
                .setStampChronologyPublicId(createPBPublicId(conceptMap.get(MODULE_CONCEPT_NAME)))
                .setReferencedComponentPurposePublicId(createPBPublicId(conceptMap.get(REF_COMP_PURPOSE_CONCEPT_NAME)))
                .setReferencedComponentMeaningPublicId(createPBPublicId(conceptMap.get(REF_COMP_MEANING_CONCEPT_NAME)))
                .addFieldDefinitions(pbFieldDef)
                .build();

        PatternChronology pbPatternChronology = PatternChronology.newBuilder()
                .setPublicId(createPBPublicId(testConcept))
                .addPatternVersions(pbPatternVersion1)
                .addPatternVersions(pbPatternVersion2)
                .build();

        // When we transform
        PatternEntity<?> patternEntity = transformPatternViaPublicApi(pbPatternChronology);

        // Then
        assertNotNull(patternEntity);
        assertEquals(2, patternEntity.versions().size());
    }
}
