package org.hl7.tinkar.integration.snomed.core;

import org.eclipse.collections.api.list.ImmutableList;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.util.uuid.UuidT5Generator;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.terms.State;
import org.hl7.tinkar.terms.TinkarTerm;

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
//        TinkarTerm.DESCRIPTION_PATTERN.nid();
//        EntityService.get().nidForUuids(UUID.fromString("a4de0039-2625-5842-8a4c-d1ce6aebf021"));


        String snomedCtNamespace = snomedData.get("namespace").asText();
        UUID nameSpaceUUID = UuidT5Generator.get(snomedCtNamespace);
        String status = snomedData.get("status").asText();
        State state = State.valueOf(status);
        long effectiveTime = snomedData.get("time").asLong();
        String module = snomedData.get("module").asText();
        String author = snomedData.get("author").asText();
        String path = snomedData.get("path").asText();
        // TODO how to determine nids for entity

        UUID authorUuid = UuidT5Generator.get(author);
        UUID moduleUuid = UuidT5Generator.get(module);
        UUID pathUuid = UuidT5Generator.get(path);

        ConceptRecord authorConceptRecord = ConceptRecordBuilder.builder()
                .leastSignificantBits(authorUuid.getLeastSignificantBits())
                .mostSignificantBits(authorUuid.getMostSignificantBits())
                .nid(EntityService.get().nidForUuids(authorUuid))
                .build();

        ConceptRecord moduleConceptRecord = ConceptRecordBuilder.builder()
                .leastSignificantBits(moduleUuid.getLeastSignificantBits())
                .mostSignificantBits(moduleUuid.getMostSignificantBits())
                .nid(EntityService.get().nidForUuids(moduleUuid))
                .build();

        ConceptRecord pathConceptRecord = ConceptRecordBuilder.builder()
                .leastSignificantBits(pathUuid.getLeastSignificantBits())
                .mostSignificantBits(pathUuid.getMostSignificantBits())
                .nid(EntityService.get().nidForUuids(pathUuid))
                .build();

        StampVersionRecord stampVersionRecord = StampVersionRecordBuilder.builder()
                .authorNid(authorConceptRecord.nid())
                .moduleNid(moduleConceptRecord.nid())
                .pathNid(pathConceptRecord.nid())
                .build();

        ImmutableList<StampVersionRecord> stampVersionRecords = new RecordListBuilder<>();
        stampVersionRecords.newWith(stampVersionRecord);
        
        UUID stampUuid = generateStampUuid()
//        UUID stamp
        StampRecord stampRecord = StampRecordBuilder.builder()
                .versions(stampVersionRecords)
                .leastSignificantBits()
                .build();
    }

    public UUID generateStampUuid(UUID namespaceUuid, String status, long effectiveTime, String moduleId, String author, String path) {
        return UuidT5Generator.get(namespaceUuid, status+effectiveTime+moduleId+author+path);
    }

}
