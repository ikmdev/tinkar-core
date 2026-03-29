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
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampEntityVersion;
import dev.ikm.tinkar.schema.StampChronology;
import dev.ikm.tinkar.schema.StampVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Map;
import java.util.function.Consumer;

import static dev.ikm.tinkar.entity.transform.ProtobufToEntityTestHelper.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestProtobufToEntityStampTransform {

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
    @DisplayName("Transform a Stamp Version With Status being Blank")
    public void stampVersionTransformWithStatusBeingBlankPublicId() {
        // Given a PBStampVersion with a missing Public Id for Status
        StampVersion pbStampVersion = StampVersion.newBuilder()
                .setStatusPublicId(createPBPublicId())
                .setTime(nowTimestamp())
                .setAuthorPublicId(createPBPublicId(conceptMap.get(AUTHOR_CONCEPT_NAME)))
                .setModulePublicId(createPBPublicId(conceptMap.get(MODULE_CONCEPT_NAME)))
                .setPathPublicId(createPBPublicId(conceptMap.get(PATH_CONCEPT_NAME)))
                .build();

        // When we transform PBStampVersion
        // Then we will throw a Runtime exception
        assertThrows(Throwable.class, () -> TinkarSchemaToEntityTransformer.getInstance()
                .transformStampVersion(pbStampVersion, null), "Not allowed to have empty UUID for status.");
    }

    @Test
    @DisplayName("Transform a Stamp Version With Author being Blank")
    public void stampVersionTransformWithAuthorBeingBlankPublicId() {
        // Given a PBStampVersion with a missing Public Id for Author
        StampVersion pbStampVersion = StampVersion.newBuilder()
                .setStatusPublicId(createPBPublicId(conceptMap.get(STATUS_CONCEPT_NAME)))
                .setTime(nowTimestamp())
                .setAuthorPublicId(createPBPublicId())
                .setModulePublicId(createPBPublicId(conceptMap.get(MODULE_CONCEPT_NAME)))
                .setPathPublicId(createPBPublicId(conceptMap.get(PATH_CONCEPT_NAME)))
                .build();

        // When we transform PBStampVersion
        // Then we will throw a Runtime exception
        assertThrows(Throwable.class, () -> TinkarSchemaToEntityTransformer.getInstance()
                .transformStampVersion(pbStampVersion, null), "Not allowed to have empty UUID for author.");
    }

    @Test
    @DisplayName("Transform a Stamp Version With Module being Blank")
    public void stampVersionTransformWithModuleBeingBlankPublicId() {
        // Given a PBStampVersion with a missing Public Id for Module
        StampVersion pbStampVersion = StampVersion.newBuilder()
                .setStatusPublicId(createPBPublicId(conceptMap.get(STATUS_CONCEPT_NAME)))
                .setTime(nowTimestamp())
                .setAuthorPublicId(createPBPublicId(conceptMap.get(AUTHOR_CONCEPT_NAME)))
                .setModulePublicId(createPBPublicId())
                .setPathPublicId(createPBPublicId(conceptMap.get(PATH_CONCEPT_NAME)))
                .build();

        // When we transform PBStampVersion
        // Then we will throw a Runtime exception
        assertThrows(Throwable.class, () -> TinkarSchemaToEntityTransformer.getInstance()
                .transformStampVersion(pbStampVersion, null), "Not allowed to have empty UUID for module.");
    }

    @Test
    @DisplayName("Transform a Stamp Version With Path being Blank")
    public void stampVersionTransformWithPathBeingBlankPublicId() {
        // Given a PBStampVersion with a missing Public Id for Path
        StampVersion pbStampVersion = StampVersion.newBuilder()
                .setStatusPublicId(createPBPublicId(conceptMap.get(STATUS_CONCEPT_NAME)))
                .setTime(nowTimestamp())
                .setAuthorPublicId(createPBPublicId(conceptMap.get(AUTHOR_CONCEPT_NAME)))
                .setModulePublicId(createPBPublicId(conceptMap.get(MODULE_CONCEPT_NAME)))
                .setPathPublicId(createPBPublicId())
                .build();

        // When we transform PBStampVersion
        // Then we will throw a Runtime exception
        assertThrows(Throwable.class, () -> TinkarSchemaToEntityTransformer.getInstance()
                .transformStampVersion(pbStampVersion, null), "Not allowed to have empty UUID for path.");
    }

    @Test
    @DisplayName("Transform a Stamp Chronology With No Versions")
    public void stampChronologyTransformWithZeroVersion() {
        Consumer<StampEntity<StampEntityVersion>> stampConsumer = (c) -> { };
        // Given a PBStampChronology with a no Stamp Versions present
        StampChronology pbStampChronology = StampChronology.newBuilder()
                .setPublicId(createPBPublicId())
                .build();

        // When we transform PBStampChronology
        // Then we will throw a Runtime exception
        assertThrows(Throwable.class, () -> TinkarSchemaToEntityTransformer.getInstance()
                .transformStampChronology(pbStampChronology, stampConsumer), "Not allowed to have no stamp versions.");
    }
}
