package org.hl7.tinkar.integration.snomed.core;

import org.eclipse.collections.api.list.ImmutableList;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.tinkar.common.util.uuid.UuidT5Generator;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.terms.State;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public abstract class AbstractSnomedTest {

    // Utility function to parse and load json file in JsonNode object
    public JsonNode loadJsonFile(String fileName) throws IOException {
        InputStream inputStream = this.getClass().getResourceAsStream(fileName);
        String jsonText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(jsonText);
        return jsonNode;
    }

    public StampEntity loadStampEntity(JsonNode snomedData) throws IOException {

        // TODO Delete this
        // Concept - Deloitte's version of Stamp not SNOMED's

        // TinkarTerm.DESCRIPTION_PATTERN.nid();
        // EntityService.get().nidForUuids(UUID.fromString("a4de0039-2625-5842-8a4c-d1ce6aebf021"));

        UUID nameSpaceUUID = UUID.fromString(snomedData.get("namespace").asText());
        String status = snomedData.get("status").asText();
        State state = State.valueOf(status);
        long effectiveTime = snomedData.get("time").asLong();
        String module = snomedData.get("module").asText();
        String author = snomedData.get("author").asText();
        String path = snomedData.get("path").asText();

        UUID stampUuid = generateStampUuid(nameSpaceUUID, status, effectiveTime, module, author, path);

        // TODO - cleanup: how to determine nids for entity
        // Everything is a concept & we need to create nids for all concepts to populate in builders
        ConceptRecord authorConcept = generateConcept(author);
        ConceptRecord moduleConcept = generateConcept(module);
        ConceptRecord pathConcept = generateConcept(path);
        ConceptRecord stateConcept = generateConcept(status);

        StampVersionRecord stampVersionRecord = StampVersionRecordBuilder.builder()
                .stateNid(stateConcept.nid())
                .time(effectiveTime)
                .authorNid(authorConcept.nid())
                .moduleNid(moduleConcept.nid())
                .pathNid(pathConcept.nid())
                .build();

        ImmutableList<StampVersionRecord> stampVersionRecords =
                RecordListBuilder.make().newWith(stampVersionRecord);

        StampRecord stampRecord = StampRecordBuilder.builder()
                .versions(stampVersionRecords)
                .leastSignificantBits(stampUuid.getLeastSignificantBits())
                .mostSignificantBits(stampUuid.getMostSignificantBits())
                .build();

        return stampRecord.stamp();
    }

    public UUID generateStampUuid(UUID namespaceUuid, String status, long effectiveTime, String moduleId, String author, String path) {
        // this could have been a random uuid, but to provide more meaningful, uuid - doing it this way
        return UuidT5Generator.get(namespaceUuid, status+effectiveTime+moduleId+author+path);
    }

    public ConceptRecord generateConcept(String conceptField) {
        
        // Creating uuid for all the fields to convert them in concepts.
        UUID conceptUuid = UuidT5Generator.get(conceptField);

        // this could have been a random uuid, but to provide more meaningful, uuid - doing it this way
        return ConceptRecordBuilder.builder()
                .leastSignificantBits(conceptUuid.getLeastSignificantBits())
                .mostSignificantBits(conceptUuid.getMostSignificantBits())
                .nid(EntityService.get().nidForUuids(conceptUuid))
                .build();
    }

}
