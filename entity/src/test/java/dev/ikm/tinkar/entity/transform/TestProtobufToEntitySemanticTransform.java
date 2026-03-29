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

import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.schema.Field;
import dev.ikm.tinkar.schema.SemanticChronology;
import dev.ikm.tinkar.schema.SemanticVersion;
import dev.ikm.tinkar.schema.StampChronology;
import dev.ikm.tinkar.schema.StampVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Map;

import static dev.ikm.tinkar.entity.transform.ProtobufToEntityTestHelper.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestProtobufToEntitySemanticTransform {

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
    @DisplayName("Transform a Semantic Chronology With Missing Public ID's")
    public void semanticChronologyTransformWithAMissingPublicId() {
        // Given a PBSemanticChronology with no Semantic Public ID's present
        Concept testConcept = conceptMap.get(TEST_CONCEPT_NAME);

        StampVersion pbStampVersion = StampVersion.newBuilder()
                .setStatusPublicId(createPBPublicId(conceptMap.get(STATUS_CONCEPT_NAME)))
                .setTime(nowTimestamp())
                .setAuthorPublicId(createPBPublicId(conceptMap.get(AUTHOR_CONCEPT_NAME)))
                .setModulePublicId(createPBPublicId(conceptMap.get(MODULE_CONCEPT_NAME)))
                .setPathPublicId(createPBPublicId(conceptMap.get(PATH_CONCEPT_NAME)))
                .build();

        StampChronology pbStampChronology = StampChronology.newBuilder()
                .setPublicId(createPBPublicId(testConcept))
                .setFirstStampVersion(pbStampVersion)
                .build();

        String expectedStringValue = "Testing Field Transformation with a string.";
        Field pbFieldString = Field.newBuilder()
                .setStringValue(expectedStringValue)
                .build();

        SemanticVersion pbSemanticVersion = SemanticVersion.newBuilder()
                .setStampChronologyPublicId(pbStampChronology.getPublicId())
                .addFields(pbFieldString)
                .build();

        SemanticChronology pbSemanticChronology = SemanticChronology.newBuilder()
                .setReferencedComponentPublicId(createPBPublicId(conceptMap.get(MODULE_CONCEPT_NAME)))
                .setPatternForSemanticPublicId(createPBPublicId(conceptMap.get(PATH_CONCEPT_NAME)))
                .addSemanticVersions(pbSemanticVersion)
                .build();

        // When we transform PBSemanticChronology
        // Then we will throw a Runtime exception (missing public ID)
        assertThrows(Throwable.class, () -> TinkarSchemaToEntityTransformer.getInstance()
                .transformSemanticChronology(pbSemanticChronology), "Not allowed to have no semantic versions.");
    }
    //TODO: Add more coverage to Semantic missing fields
}
