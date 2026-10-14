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
package dev.ikm.tinkar.integration.integrity;

import network.ike.foundation.ike.bindings.IkeConcept;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import dev.ikm.tinkar.terms.PatternFacade;
import dev.ikm.tinkar.common.id.LongIdList;
import dev.ikm.tinkar.common.id.LongIdSet;
import dev.ikm.tinkar.common.id.LongIds;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.fixtures.DataIntegrity;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.State;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DataIntegrityIT {
    private static final Logger LOG = LoggerFactory.getLogger(DataIntegrity.class);
    private static final File DATASTORE_ROOT = TestConstants.createFilePathInTargetFromClassName.apply(DataIntegrityIT.class);
    public DataIntegrity dataIntegrity;

    @BeforeAll
    public static void beforeAll() {
        FileUtil.recursiveDelete(DATASTORE_ROOT);
        TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, DATASTORE_ROOT);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
    }

    @AfterAll
    public static void afterAll() {
        TestHelper.stopDatabase();
    }

    @Test
    public void correctDataIntegrityTest() {
        List<Long> aggregatedNullNidList = new ArrayList<>();
        Map<String, List<? extends Entity>> typeNameEntityMap = new HashMap<>();
        typeNameEntityMap.put("Stamp", dataIntegrity.validateStampReferences(aggregatedNullNidList));
        typeNameEntityMap.put("Concept", dataIntegrity.validateConceptReferences(aggregatedNullNidList));
        typeNameEntityMap.put("Semantic", dataIntegrity.validateSemanticReferences(aggregatedNullNidList));
        typeNameEntityMap.put("Pattern", dataIntegrity.validatePatternReferences(aggregatedNullNidList));
        typeNameEntityMap.put("Semantic Field Data Type", dataIntegrity.validateSemanticFieldDataTypes());

//        breakdown();

        LOG.info("Report for {} database:", PrimitiveData.get().name());
        typeNameEntityMap.forEach((typeString, misconfiguredList) -> {
            LOG.info("Found {} {}s containing incorrect references.", misconfiguredList.size(), typeString);
            if (!misconfiguredList.isEmpty()) {
                LOG.info("Misconfigured {} PublicIds: ", typeString);
                misconfiguredList.stream().map(Entity::publicId).map(PublicId::idString).forEach(LOG::info);
            }
        });
        LOG.info("Found {} Nids containing incorrect references.", aggregatedNullNidList.size());
        LOG.info("Misconfigured Nids:");
        aggregatedNullNidList.stream().map(String::valueOf).forEach(LOG::info);
        LOG.info("Misconfigured Nids:");
        aggregatedNullNidList.stream().map(String::valueOf).forEach(LOG::info);

        // ASSERTIONS
        assertEquals(0, aggregatedNullNidList.size());
    }

    @Test
    public void semanticFieldSample() {
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.COMPONENT_FIELD,
                KernelConcept.STRING
        );

        ImmutableList<Object> tooLongSemanticFieldValues = Lists.immutable.of(
                KernelConcept.IDENTIFIER_SOURCE,
                "Test-UUID",
                "This extra field value should throw an error"
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithTooManyFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), tooLongSemanticFieldValues, authoringStamp);

        assertFalse(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithTooManyFieldValues));
    }

    @Test
    public void semanticFieldsTooShort() {
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.COMPONENT_FIELD,
                KernelConcept.STRING
        );

        ImmutableList<Object> tooShortSemanticFieldValues = Lists.immutable.of(
                KernelConcept.IDENTIFIER_SOURCE
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithTooFewFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), tooShortSemanticFieldValues, authoringStamp);

        assertFalse(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithTooFewFieldValues));
    }

    @Test
    public void semanticFieldsMatchStamp() {
        UUID stampUUID = UUID.randomUUID();
        StampEntity stamp = StampRecord.make(stampUUID, State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        EntityService.get().putEntity(stamp);
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.COMPONENT_FIELD
        );

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                stamp
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertTrue(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }

    @Test
    public void semanticFieldsMatchString() {
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.STRING
        );

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                "String"
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertTrue(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }

    @Test
    public void semanticFieldsMatchInt() {
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.INTEGER_FIELD
        );

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                123
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertTrue(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }

    @Test
    public void semanticFieldsMatchFloat() {
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.FLOAT_FIELD
        );

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                1.0
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertTrue(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }

    @Test
    public void semanticFieldsMatchBoolean() {
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.BOOLEAN_FIELD
        );

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                true
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertTrue(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }

    @Test
    public void semanticFieldsMatchByteArray() {
        String str = "TEST";
        byte[] byteArr = str.getBytes();
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.BYTE_ARRAY_FIELD
        );

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                byteArr
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertTrue(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }

//    @Test
//    public void semanticFieldsMatchObjectArray() {
//        Instant[] instantArr = new Instant[1];
//        Instant inst1 = Instant.now();
//        instantArr[0] = inst1;
//        ImmutableList<EntityProxy.Concept> patternFieldDefinitions = Lists.immutable.of(
//                KernelConcept.ARRAY_FIELD
//        );
//
//        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
//                instantArr
//        );
//
//        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
//        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER, KernelConcept.SOLOR_OVERLAY_MODULE, KernelConcept.DEVELOPMENT_PATH);
//        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp , patternFieldDefinitions);
//        EntityService.get().putEntity(authoringStamp);
//        EntityService.get().putEntity(patternEntity);
//
//        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
//                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);
//
//        assertTrue(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
//    }

    @Test
    public void semanticFieldsMatchConceptPatternSemanticStamp() {
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.COMPONENT_FIELD,
                KernelConcept.COMPONENT_FIELD,
                KernelConcept.COMPONENT_FIELD,
                KernelConcept.COMPONENT_FIELD
        );


        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        AtomicReference<SemanticEntity> semanticFieldValue = new AtomicReference<SemanticEntity>();
        EntityService.get().forEachSemanticForComponentOfPattern(KernelConcept.ANONYMOUS_CONCEPT.nid(), KernelPattern.DESCRIPTION_PATTERN.nid(), semanticFieldValue::set);

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                KernelConcept.ANONYMOUS_CONCEPT,
                KernelPattern.DESCRIPTION_PATTERN,
                semanticFieldValue.get(),
                authoringStamp
        );


        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertTrue(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }

    @Test
    public void semanticFieldsMatchDitree() {
        // Initialize Vertices
        EntityVertex definitionRootVertex = EntityVertex.make(KernelConcept.DEFINITION_ROOT.nid());
        EntityVertex andVertex = EntityVertex.make(KernelConcept.AND.nid());
        EntityVertex necessarySetVertex = EntityVertex.make(KernelConcept.NECESSARY_SET.nid());

        EntityVertex conceptReferenceVertex = EntityVertex.make(KernelConcept.CONCEPT_REFERENCE.nid());
        conceptReferenceVertex.putUncommittedProperty(KernelConcept.CONCEPT_REFERENCE.nid(), KernelConcept.ANONYMOUS_CONCEPT);
        conceptReferenceVertex.commitProperties();

        // Build Sample DiTree
        DiTreeEntity.Builder expectedMergedDteBuilder = DiTreeEntity.builder();
        expectedMergedDteBuilder.setRoot(definitionRootVertex);
        expectedMergedDteBuilder.addVertex(andVertex);
        expectedMergedDteBuilder.addVertex(necessarySetVertex);
        expectedMergedDteBuilder.addEdge(necessarySetVertex.vertexIndex(), definitionRootVertex.vertexIndex());
        expectedMergedDteBuilder.addEdge(andVertex.vertexIndex(), necessarySetVertex.vertexIndex());
        expectedMergedDteBuilder.addVertex(conceptReferenceVertex);
        expectedMergedDteBuilder.addEdge(conceptReferenceVertex.vertexIndex(), andVertex.vertexIndex());
        DiTreeEntity expectedTree = expectedMergedDteBuilder.build();

        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.DITREE_FIELD
        );

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                expectedTree
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertTrue(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }

    @Test
    public void semanticFieldsMismatchDigraphDiTree() {
        // Initialize Vertices
        EntityVertex definitionRootVertex = EntityVertex.make(KernelConcept.DEFINITION_ROOT.nid());
        EntityVertex andVertex = EntityVertex.make(KernelConcept.AND.nid());
        EntityVertex necessarySetVertex = EntityVertex.make(KernelConcept.NECESSARY_SET.nid());

        EntityVertex conceptReferenceVertex = EntityVertex.make(KernelConcept.CONCEPT_REFERENCE.nid());
        conceptReferenceVertex.putUncommittedProperty(KernelConcept.CONCEPT_REFERENCE.nid(), KernelConcept.ANONYMOUS_CONCEPT);
        conceptReferenceVertex.commitProperties();

        // Build Sample DiTree
        DiTreeEntity.Builder expectedMergedDteBuilder = DiTreeEntity.builder();
        expectedMergedDteBuilder.setRoot(definitionRootVertex);
        expectedMergedDteBuilder.addVertex(andVertex);
        expectedMergedDteBuilder.addVertex(necessarySetVertex);
        expectedMergedDteBuilder.addEdge(necessarySetVertex.vertexIndex(), definitionRootVertex.vertexIndex());
        expectedMergedDteBuilder.addEdge(andVertex.vertexIndex(), necessarySetVertex.vertexIndex());
        expectedMergedDteBuilder.addVertex(conceptReferenceVertex);
        expectedMergedDteBuilder.addEdge(conceptReferenceVertex.vertexIndex(), andVertex.vertexIndex());
        DiTreeEntity expectedTree = expectedMergedDteBuilder.build();

        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.DIGRAPH_FIELD
        );

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                expectedTree
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertFalse(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }

    @Test
    public void semanticFieldsMatchInstant() {
        Instant currentTime = Instant.now();
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.INSTANT_LITERAL
        );

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                currentTime
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertTrue(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }

    @Test
    public void semanticFieldsMatchComponentIdList() {
        LongIdList list = LongIds.list.of(123, 456);
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.COMPONENT_ID_LIST_FIELD
        );

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                list
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertTrue(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }

    @Test
    public void semanticFieldsMatchComponentIdSet() {
        LongIdSet set = LongIds.set.of(123, 456);
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.COMPONENT_ID_SET_FIELD
        );

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                set
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertTrue(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }

    @Test
    public void semanticFieldsMatchLong() {
        Long longTest = 123321L;
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.LONG
        );

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                longTest
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertTrue(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }

    // TESTS FOR MISMATCH - TYPE INCOMPATABILITY
    @Test
    public void semanticFieldsMismatchString() {
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.BOOLEAN_FIELD
        );

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                "String"
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertFalse(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }

    @Test
    public void semanticFieldsMismatchInt() {
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.BOOLEAN_FIELD
        );

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                123
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertFalse(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }

    @Test
    public void semanticFieldsMismatchFloat() {
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.BOOLEAN_FIELD
        );

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                1.0
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertFalse(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }

    @Test
    public void semanticFieldsMismatchBoolean() {
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.STRING
        );

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                true
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertFalse(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }

    @Test
    public void semanticFieldsMismatchByteArray() {
        String str = "TEST";
        byte[] byteArr = str.getBytes();
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.BOOLEAN_FIELD
        );

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                byteArr
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertFalse(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }

    @Test
    public void semanticFieldsMismatchObjectArray() {
        Instant[] instantArr = new Instant[1];
        Instant inst1 = Instant.now();
        instantArr[0] = inst1;
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.BOOLEAN_FIELD
        );

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                (Object) instantArr
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertFalse(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }

    @Test
    public void semanticFieldsMismatchConceptPatternSemanticStamp() {
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.STRING,
                KernelConcept.STRING,
                KernelConcept.STRING,
                KernelConcept.STRING
        );


        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        AtomicReference<SemanticEntity> semanticFieldValue = new AtomicReference<SemanticEntity>();
        EntityService.get().forEachSemanticForComponentOfPattern(KernelConcept.ANONYMOUS_CONCEPT.nid(), KernelPattern.DESCRIPTION_PATTERN.nid(), semanticFieldValue::set);

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                KernelConcept.ANONYMOUS_CONCEPT,
                KernelPattern.DESCRIPTION_PATTERN,
                semanticFieldValue.get(),
                authoringStamp
        );


        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertFalse(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }

    @Test
    public void semanticFieldsMismatchInstant() {
        Instant currentTime = Instant.now();
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.STRING
        );

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                currentTime
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertFalse(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }

    @Test
    public void semanticFieldsMismatchComponentIdList() {
        LongIdList list = LongIds.list.of(123, 456);
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.STRING
        );

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                list
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertFalse(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }

    @Test
    public void semanticFieldsMismatchComponentIdSet() {
        LongIdSet set = LongIds.set.of(123, 456);
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.STRING
        );

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                set
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertFalse(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }

    @Test
    public void semanticFieldsMismatchLong() {
        Long longTest = 123321L;
        ImmutableList<ConceptFacade> patternFieldDefinitions = Lists.immutable.of(
                KernelConcept.BOOLEAN_FIELD
        );

        ImmutableList<Object> semanticFieldValues = Lists.immutable.of(
                longTest
        );

        long referencedComponentNid = KernelConcept.ANONYMOUS_CONCEPT.nid();
        StampEntity authoringStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, System.currentTimeMillis(), KernelConcept.USER.publicId(), KernelConcept.SOLOR_OVERLAY_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        Entity patternEntity = PatternTestHelper.createPattern(EntityProxy.Pattern.make("Test Pattern", UUID.randomUUID()), authoringStamp, patternFieldDefinitions);
        EntityService.get().putEntity(authoringStamp);
        EntityService.get().putEntity(patternEntity);

        SemanticEntity<? extends SemanticEntityVersion> semanticEntityWithFieldValues =
                SemanticTestHelper.createSemanticWithSupplier(referencedComponentNid, patternEntity.nid(), semanticFieldValues, authoringStamp);

        assertFalse(DataIntegrity.validateSemanticFieldDataType(semanticEntityWithFieldValues));
    }


    /*############# Semantic Helper Functions ##############*/
    private class SemanticTestHelper {
        public static SemanticEntity<? extends SemanticEntityVersion> createSemanticWithSupplier(long referencedComponentNid,
                                                                                                 long patternNid,
                                                                                                 ImmutableList<Object> fieldValues,
                                                                                                 Entity<? extends EntityVersion> authoringSTAMP) {
            RecordListBuilder<SemanticVersionRecord> versions = RecordListBuilder.make();

            UUID navigationSemanticUUID = UUID.randomUUID();
            SemanticRecord semanticRecord = SemanticRecordBuilder.builder()
                    .nid(EntityService.get().nidForUuids(navigationSemanticUUID))
                    .leastSignificantBits(navigationSemanticUUID.getLeastSignificantBits())
                    .mostSignificantBits(navigationSemanticUUID.getMostSignificantBits())
                    .additionalUuidLongs(null)
                    .patternNid(patternNid)
                    .referencedComponentNid(referencedComponentNid)
                    .versions(versions.toImmutable())
                    .build();

            versions.add(SemanticVersionRecordBuilder.builder()
                    .chronology(semanticRecord)
                    .stampNid(authoringSTAMP.nid())
                    .fieldValues(fieldValues)
                    .build());

            return SemanticRecordBuilder.builder(semanticRecord).versions(versions.toImmutable()).build();
        }

    }

    /*############# Pattern Helper Functions ##############*/
    private class PatternTestHelper {
        public static Entity<? extends EntityVersion> createPattern(PatternFacade pattern,
                                                                    Entity<? extends EntityVersion> authoringSTAMP,
                                                                    ImmutableList<ConceptFacade> fieldDefinitionConcepts) {
            return createPattern(pattern, IkeConcept.MEANING, IkeConcept.PURPOSE, authoringSTAMP, fieldDefinitionConcepts);
        }

        public static Entity<? extends EntityVersion> createPattern(PatternFacade pattern,
                                                                    ConceptFacade meaningConcept,
                                                                    ConceptFacade purposeConcept,
                                                                    Entity<? extends EntityVersion> authoringSTAMP,
                                                                    ImmutableList<ConceptFacade> fieldDefinitionConcepts) {

            MutableList<FieldDefinitionRecord> fieldDefinitions = Lists.mutable.empty();
            int i = 0;
            for (ConceptFacade fieldDefConcept : fieldDefinitionConcepts) {
                fieldDefinitions.add(fieldDefinition(pattern.nid(), fieldDefConcept, authoringSTAMP, i++));
            }

            RecordListBuilder<PatternVersionRecord> versions = RecordListBuilder.make();
            PublicIdentifierRecord patternIdRecord = PublicIdentifierRecord.make(pattern.publicId());
            PatternRecord patternRecord = PatternRecordBuilder.builder()
                    .nid(pattern.nid())
                    .leastSignificantBits(patternIdRecord.leastSignificantBits())
                    .mostSignificantBits(patternIdRecord.mostSignificantBits())
                    .additionalUuidLongs(patternIdRecord.additionalUuidLongs())
                    .versions(versions.toImmutable())
                    .build();

            versions.add(PatternVersionRecordBuilder.builder()
                    .chronology(patternRecord)
                    .stampNid(authoringSTAMP.nid())
                    .semanticMeaningNid(meaningConcept.nid())
                    .semanticPurposeNid(purposeConcept.nid())
                    .fieldDefinitions(fieldDefinitions.toImmutable())
                    .build());

            return PatternRecordBuilder.builder(patternRecord).versions(versions.toImmutable()).build();
        }

        public static FieldDefinitionRecord fieldDefinition(long patternNid, ConceptFacade dataType,
                                                            Entity<? extends EntityVersion> authoringSTAMP, int idx) {
            return fieldDefinition(patternNid, IkeConcept.MEANING, IkeConcept.PURPOSE, dataType, authoringSTAMP, idx);
        }

        public static FieldDefinitionRecord fieldDefinition(long patternNid, ConceptFacade meaning, ConceptFacade purpose,
                                                            ConceptFacade dataType, Entity<? extends EntityVersion> authoringSTAMP, int idx) {
            return FieldDefinitionRecordBuilder.builder()
                    .patternNid(patternNid)
                    .meaningNid(meaning.nid())
                    .purposeNid(purpose.nid())
                    .dataTypeNid(dataType.nid())
                    .indexInPattern(idx)
                    .patternVersionStampNid(authoringSTAMP.nid())
                    .build();
        }
    }

}
