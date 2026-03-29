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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.ConceptRecordBuilder;
import dev.ikm.tinkar.entity.ConceptVersionRecord;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.PatternRecord;
import dev.ikm.tinkar.entity.PatternRecordBuilder;
import dev.ikm.tinkar.entity.PatternVersionRecord;
import dev.ikm.tinkar.entity.PatternVersionRecordBuilder;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.StampRecordBuilder;
import dev.ikm.tinkar.entity.StampVersionRecord;
import dev.ikm.tinkar.entity.StampVersionRecordBuilder;
import dev.ikm.tinkar.schema.StampVersion;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.State;
import org.eclipse.collections.api.factory.Lists;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Test Helper for integration transform tests. Uses a real ephemeral provider
 * (EntityService) to assign NIDs and store entities, instead of mocks or
 * an in-memory NID registry.
 */
public class TransformTestHelper {
    public static final String TEST_TINKAR_STARTER_JSON = "test-tinkar-starter-data.json";
    public static final String JSON_CONCEPTS_ARRAY_PROP = "concepts";
    public static final String JSON_CONCEPT_NAME_PROP   = "name";
    public static final String JSON_CONCEPT_UUID_PROP   = "uuid";

    public static final String MODULE_CONCEPT_NAME      = "moduleConcept";
    public static final String PATH_CONCEPT_NAME        = "pathConcept";
    public static final String AUTHOR_CONCEPT_NAME      = "authorConcept";
    public static final String STATUS_CONCEPT_NAME      = "statusConcept";
    public static final String TEST_CONCEPT_NAME        = "testConcept";
    public static final String REF_COMP_PURPOSE_CONCEPT_NAME = "referencedComponentPurposeConcept";
    public static final String REF_COMP_MEANING_CONCEPT_NAME = "referencedComponentMeaningConcept";
    public static final String MEANING_CONCEPT_NAME          = "meaningConcept";
    public static final String DATATYPE_CONCEPT_NAME         = "dataTypeConcept";
    public static final String PURPOSE_CONCEPT_NAME          = "purposeConcept";

    public static dev.ikm.tinkar.schema.PublicId createPBPublicId() {
        return dev.ikm.tinkar.schema.PublicId.newBuilder().build();
    }

    public static dev.ikm.tinkar.schema.PublicId createPBPublicId(Concept concept) {
        return createPBPublicId(concept.publicId());
    }

    public static dev.ikm.tinkar.schema.PublicId createPBPublicId(PublicId publicId) {
        return dev.ikm.tinkar.schema.PublicId.newBuilder()
                .addUuids(publicId.asUuidList().get(0).toString()).build();
    }

    public static long nowEpochMillis() {
        return Instant.now().toEpochMilli();
    }

    public static long nowEpochMillis(long millisToAdd) {
        return Instant.now().plusMillis(millisToAdd).toEpochMilli();
    }

    public static long nowTimestamp() {
        return nowEpochMillis();
    }

    public static long nowTimestamp(long millisToAdd) {
        return nowEpochMillis(millisToAdd);
    }

    protected static Concept createSimpleConcept(String conceptName, String uuidStr) {
        return EntityProxy.Concept.make(conceptName, UUID.fromString(uuidStr));
    }

    public static JsonNode loadJsonFile(Class clazz, String fileName) {
        InputStream inputStream = clazz.getResourceAsStream(fileName);
        try {
            String jsonText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readTree(jsonText);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static StampVersion createPbStampVersion(long expectedTime, Concept statusConcept,
                                                     Concept authorConcept, Concept moduleConcept,
                                                     Concept pathConcept) {
        return StampVersion.newBuilder()
                .setStatusPublicId(createPBPublicId(statusConcept))
                .setTime(expectedTime)
                .setAuthorPublicId(createPBPublicId(authorConcept))
                .setModulePublicId(createPBPublicId(moduleConcept))
                .setPathPublicId(createPBPublicId(pathConcept))
                .build();
    }

    public static StampVersion createPbStampVersion(Map<String, Concept> conceptMap, long expectedTime) {
        return createPbStampVersion(expectedTime,
                conceptMap.get(STATUS_CONCEPT_NAME),
                conceptMap.get(AUTHOR_CONCEPT_NAME),
                conceptMap.get(MODULE_CONCEPT_NAME),
                conceptMap.get(PATH_CONCEPT_NAME));
    }

    /**
     * Loads test concepts from JSON, assigns real NIDs via Entity.nid(),
     * and stores ConceptRecord entities in the ephemeral provider so
     * getEntityFast(nid) works for entity-to-protobuf tests.
     */
    public static Map<String, Concept> loadTestConcepts(Object test) {
        Map<String, Concept> conceptMap = new HashMap<>();
        JsonNode jsonNode = loadJsonFile(test.getClass(), TEST_TINKAR_STARTER_JSON);
        JsonNode concepts = jsonNode.get(JSON_CONCEPTS_ARRAY_PROP);
        List<JsonNode> conceptList = new ArrayList<>();
        concepts.iterator().forEachRemaining(conceptList::add);

        for (JsonNode conceptJson : conceptList) {
            String name = conceptJson.get(JSON_CONCEPT_NAME_PROP).asText();
            String uuidStr = conceptJson.get(JSON_CONCEPT_UUID_PROP).asText();
            Concept concept = createSimpleConcept(name, uuidStr);

            // Entity.nid() assigns a real NID via the running provider
            int nid = Entity.nid(concept.publicId());

            // Store a real ConceptRecord so Entity.provider().getEntityFast(nid) works
            storeConceptEntity(concept.publicId(), nid);

            conceptMap.put(name, concept);
            conceptMap.put(uuidStr, concept);
        }
        return conceptMap;
    }

    /**
     * Creates and stores a minimal ConceptRecord entity in the provider.
     */
    public static void storeConceptEntity(PublicId publicId, int nid) {
        UUID uuid = publicId.asUuidArray()[0];
        RecordListBuilder<ConceptVersionRecord> versions = RecordListBuilder.make();

        // Build a stamp for this concept version using State.ACTIVE
        int stampNid = createAndStoreStamp();

        ConceptRecord conceptRecord = ConceptRecordBuilder.builder()
                .mostSignificantBits(uuid.getMostSignificantBits())
                .leastSignificantBits(uuid.getLeastSignificantBits())
                .nid(nid)
                .versions(versions)
                .build();

        versions.add(new ConceptVersionRecord(conceptRecord, stampNid));
        ConceptRecord finalRecord = ConceptRecordBuilder.builder(conceptRecord).versions(versions).build();
        EntityService.get().putEntity(finalRecord);
    }

    /**
     * Creates and stores a StampRecord in the provider, returning the stamp NID.
     */
    public static int createAndStoreStamp() {
        PublicId stampPublicId = PublicIds.newRandom();
        int stampNid = Entity.nid(stampPublicId);
        UUID stampUuid = stampPublicId.asUuidArray()[0];

        RecordListBuilder<StampVersionRecord> stampVersions = RecordListBuilder.make();
        StampRecord stampRecord = StampRecordBuilder.builder()
                .mostSignificantBits(stampUuid.getMostSignificantBits())
                .leastSignificantBits(stampUuid.getLeastSignificantBits())
                .nid(stampNid)
                .versions(stampVersions)
                .build();

        // Use real concept NIDs from known TinkarTerm UUIDs for STAMP fields
        int stateNid = State.ACTIVE.nid();
        int authorNid = Entity.nid(PublicIds.of(UUID.fromString("76fdab49-b0ee-4c83-900e-8064103ef3b0")));
        int moduleNid = Entity.nid(PublicIds.of(UUID.fromString("840928b5-480c-4e8d-af77-7c817e880aed")));
        int pathNid = Entity.nid(PublicIds.of(UUID.fromString("4fa15e05-5c48-470a-a6f0-2080e725e6fb")));

        StampVersionRecord svr = StampVersionRecordBuilder.builder()
                .chronology(stampRecord)
                .stateNid(stateNid)
                .time(Instant.now().toEpochMilli())
                .authorNid(authorNid)
                .moduleNid(moduleNid)
                .pathNid(pathNid)
                .build();
        stampVersions.add(svr);
        StampRecord finalStamp = StampRecordBuilder.builder(stampRecord).versions(stampVersions).build();
        EntityService.get().putEntity(finalStamp);
        return stampNid;
    }

    /**
     * Creates and stores a StampRecord with specific concept NIDs for STAMP fields,
     * returning the stamp NID.
     */
    public static int createAndStoreStamp(int stateNid, int authorNid, int moduleNid, int pathNid) {
        PublicId stampPublicId = PublicIds.newRandom();
        int stampNid = Entity.nid(stampPublicId);
        UUID stampUuid = stampPublicId.asUuidArray()[0];

        RecordListBuilder<StampVersionRecord> stampVersions = RecordListBuilder.make();
        StampRecord stampRecord = StampRecordBuilder.builder()
                .mostSignificantBits(stampUuid.getMostSignificantBits())
                .leastSignificantBits(stampUuid.getLeastSignificantBits())
                .nid(stampNid)
                .versions(stampVersions)
                .build();

        StampVersionRecord svr = StampVersionRecordBuilder.builder()
                .chronology(stampRecord)
                .stateNid(stateNid)
                .time(Instant.now().toEpochMilli())
                .authorNid(authorNid)
                .moduleNid(moduleNid)
                .pathNid(pathNid)
                .build();
        stampVersions.add(svr);
        StampRecord finalStamp = StampRecordBuilder.builder(stampRecord).versions(stampVersions).build();
        EntityService.get().putEntity(finalStamp);
        return stampNid;
    }

    /**
     * Creates and stores a StampRecord with specific concept NIDs and a specific time,
     * returning the stamp NID.
     */
    public static int createAndStoreStamp(int stateNid, long time, int authorNid, int moduleNid, int pathNid) {
        PublicId stampPublicId = PublicIds.newRandom();
        int stampNid = Entity.nid(stampPublicId);
        UUID stampUuid = stampPublicId.asUuidArray()[0];

        RecordListBuilder<StampVersionRecord> stampVersions = RecordListBuilder.make();
        StampRecord stampRecord = StampRecordBuilder.builder()
                .mostSignificantBits(stampUuid.getMostSignificantBits())
                .leastSignificantBits(stampUuid.getLeastSignificantBits())
                .nid(stampNid)
                .versions(stampVersions)
                .build();

        StampVersionRecord svr = StampVersionRecordBuilder.builder()
                .chronology(stampRecord)
                .stateNid(stateNid)
                .time(time)
                .authorNid(authorNid)
                .moduleNid(moduleNid)
                .pathNid(pathNid)
                .build();
        stampVersions.add(svr);
        StampRecord finalStamp = StampRecordBuilder.builder(stampRecord).versions(stampVersions).build();
        EntityService.get().putEntity(finalStamp);
        return stampNid;
    }

    /**
     * Returns the stored StampRecord for a given stamp NID.
     */
    public static StampRecord getStamp(int stampNid) {
        return (StampRecord) Entity.getStamp(stampNid);
    }

    /**
     * Creates and stores a minimal PatternRecord entity in the provider, returning the pattern NID.
     * This is needed for Semantic entities which reference a pattern.
     */
    public static int createAndStorePattern(Map<String, Concept> conceptMap) {
        PublicId patternPublicId = PublicIds.newRandom();
        int patternNid = Entity.nid(patternPublicId);
        UUID patternUuid = patternPublicId.asUuidArray()[0];

        int stampNid = createAndStoreStamp();
        Concept purposeConcept = conceptMap.get(PURPOSE_CONCEPT_NAME);
        Concept meaningConcept = conceptMap.get(MEANING_CONCEPT_NAME);

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
                .semanticPurposeNid(Entity.nid(purposeConcept.publicId()))
                .semanticMeaningNid(Entity.nid(meaningConcept.publicId()))
                .fieldDefinitions(Lists.immutable.empty())
                .build();
        patternVersions.add(pvr);

        PatternRecord finalPattern = PatternRecordBuilder.builder(patternRecord).versions(patternVersions).build();
        EntityService.get().putEntity(finalPattern);
        return patternNid;
    }
}
