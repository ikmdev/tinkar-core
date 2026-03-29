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
import dev.ikm.tinkar.schema.ConceptChronology;
import dev.ikm.tinkar.schema.ConceptVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Map;

import static dev.ikm.tinkar.entity.transform.ProtobufToEntityTestHelper.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestProtobufToEntityConceptTransform {

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
    @DisplayName("Transform a Concept Chronology With Zero Public Id's")
    public void conceptChronologyTransformWithZeroPublicIds() {
        // Given a PBConceptChronology with no public ID
        Concept testConcept = conceptMap.get(TEST_CONCEPT_NAME);

        ConceptVersion pbConceptVersion = ConceptVersion.newBuilder()
                .setStampChronologyPublicId(createPBPublicId(testConcept))
                .build();

        ConceptChronology pbConceptChronology = ConceptChronology.newBuilder()
                .addConceptVersions(pbConceptVersion)
                .build();

        // When we transform PBConceptChronology
        // Then we will throw a Runtime exception
        assertThrows(Throwable.class, () -> TinkarSchemaToEntityTransformer.getInstance()
                .transformConceptChronology(pbConceptChronology), "Not allowed to have no public id's.");
    }
    // TODO write test to fail when stamp version is the same (time, author, etc.)
}
