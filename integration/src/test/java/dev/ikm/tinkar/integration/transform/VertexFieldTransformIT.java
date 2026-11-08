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

import dev.ikm.tinkar.common.id.LongIdList;
import dev.ikm.tinkar.common.id.LongIds;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.SemanticRecordBuilder;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import dev.ikm.tinkar.entity.SemanticVersionRecordBuilder;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.entity.transform.EntityToTinkarSchemaTransformer;
import dev.ikm.tinkar.entity.transform.TinkarSchemaToEntityTransformer;
import dev.ikm.tinkar.fixtures.NewEphemeralKeyValueProvider;
import dev.ikm.tinkar.integration.helper.NidsByKind;
import dev.ikm.tinkar.schema.Field;
import dev.ikm.tinkar.schema.TinkarMsg;
import dev.ikm.tinkar.terms.EntityFacade;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import org.eclipse.collections.impl.factory.primitive.LongObjectMaps;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static dev.ikm.tinkar.integration.transform.TransformTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Semantic fields that are a vertex, or a one-UUID public id, through a change set and back.
 *
 * <p>Both are also a {@link dev.ikm.tinkar.common.id.VertexId}: an {@link EntityVertex} is one,
 * and so is {@code PublicId1}. The writer once took that case first, so a vertex field (a stamped
 * document's vertices, the Komet Assistant's conversation journal) went out as its bare UUID,
 * and the importer refused every such change set: "Unexpected value: &lt;uuid&gt;".
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(NewEphemeralKeyValueProvider.class)
class VertexFieldTransformIT {

    private Map<String, Concept> conceptMap;

    @BeforeAll
    void setUp() {
        conceptMap = loadTestConcepts(this);
    }

    private long nid(String conceptName) {
        return NidsByKind.any(conceptMap.get(conceptName).publicId());
    }

    /** A semantic of one version whose only field is the value given. */
    private SemanticRecord semanticWithField(Object fieldValue) {
        UUID semanticUuid = UUID.randomUUID();
        PublicId semanticPublicId = PublicIds.of(semanticUuid);
        long patternNid = createAndStorePattern(conceptMap);
        long semanticNid = EntityService.get().nidForSemantic(PrimitiveData.publicId(patternNid), semanticPublicId);
        RecordListBuilder<SemanticVersionRecord> versions = RecordListBuilder.make();
        SemanticRecord semantic = SemanticRecordBuilder.builder()
                .mostSignificantBits(semanticUuid.getMostSignificantBits())
                .leastSignificantBits(semanticUuid.getLeastSignificantBits())
                .nid(semanticNid)
                .patternNid(patternNid)
                .referencedComponentNid(nid(MODULE_CONCEPT_NAME))
                .versions(versions)
                .build();
        versions.add(SemanticVersionRecordBuilder.builder()
                .chronology(semantic)
                .stampNid(createAndStoreStamp())
                .fieldValues(Lists.immutable.of(fieldValue))
                .build());
        return SemanticRecordBuilder.builder(semantic).versions(versions).build();
    }

    private static Field onlyField(TinkarMsg msg) {
        assertEquals(1, msg.getSemanticChronology().getSemanticVersionsCount());
        return msg.getSemanticChronology().getSemanticVersions(0).getFields(0);
    }

    /** The message read back, as the change-set importer reads it. */
    private static Object readBackOnlyField(TinkarMsg msg) {
        AtomicReference<Entity<? extends EntityVersion>> read = new AtomicReference<>();
        TinkarSchemaToEntityTransformer.getInstance().transform(msg, read::set, stamp -> {});
        SemanticEntity<?> semantic = assertInstanceOf(SemanticEntity.class, read.get());
        SemanticEntityVersion version = (SemanticEntityVersion) semantic.versions().get(0);
        return version.fieldValues().get(0);
    }

    @Test
    @DisplayName("A vertex field is written as the vertex, properties and all, and read back as one")
    void vertexFieldRoundTrips() {
        UUID vertexUuid = UUID.randomUUID();
        EntityVertex vertex = EntityVertex.make(vertexUuid, nid(MEANING_CONCEPT_NAME));
        MutableLongObjectMap<Object> properties = LongObjectMaps.mutable.empty();
        properties.put(nid(TEST_CONCEPT_NAME), "Which statins lower LDL?");
        properties.put(nid(PURPOSE_CONCEPT_NAME), 2);
        properties.put(nid(DATATYPE_CONCEPT_NAME), LongIds.list.of(nid(AUTHOR_CONCEPT_NAME), nid(PATH_CONCEPT_NAME)));
        vertex.setProperties(properties);
        SemanticRecord semantic = semanticWithField(vertex);
        EntityService.get().putEntity(semantic);

        TinkarMsg msg = EntityToTinkarSchemaTransformer.getInstance().transform(semantic);

        Field field = onlyField(msg);
        assertEquals(Field.ValueCase.VERTEX, field.getValueCase(),
                "A vertex field must be written as the vertex, not as its UUID alone");
        assertEquals(3, field.getVertex().getPropertiesCount(), "Every property of the vertex must be written");

        EntityVertex readBack = assertInstanceOf(EntityVertex.class, readBackOnlyField(msg));
        assertEquals(vertexUuid, readBack.asUuid());
        assertEquals(nid(MEANING_CONCEPT_NAME), readBack.getMeaningNid());
        assertEquals("Which statins lower LDL?", readBack.properties().get(nid(TEST_CONCEPT_NAME)));
        assertEquals(2, readBack.properties().get(nid(PURPOSE_CONCEPT_NAME)));
        LongIdList members = assertInstanceOf(LongIdList.class, readBack.properties().get(nid(DATATYPE_CONCEPT_NAME)),
                "A vertex's id list must come back by nid, as document readers expect it");
        assertArrayEquals(new long[]{nid(AUTHOR_CONCEPT_NAME), nid(PATH_CONCEPT_NAME)}, members.toArray());
    }

    @Test
    @DisplayName("A one-UUID public id field is written as a public id and read back as a reference")
    void oneUuidPublicIdFieldRoundTrips() {
        PublicId referenced = conceptMap.get(TEST_CONCEPT_NAME).publicId();
        PublicId oneUuid = PublicIds.of(referenced.asUuidArray()[0]);
        SemanticRecord semantic = semanticWithField(oneUuid);
        EntityService.get().putEntity(semantic);

        TinkarMsg msg = EntityToTinkarSchemaTransformer.getInstance().transform(semantic);

        assertEquals(Field.ValueCase.PUBLIC_ID, onlyField(msg).getValueCase(),
                "A public id field must be written as a public id, never as a vertex UUID");
        EntityFacade readBack = assertInstanceOf(EntityFacade.class, readBackOnlyField(msg));
        assertEquals(nid(TEST_CONCEPT_NAME), readBack.nid());
    }
}
