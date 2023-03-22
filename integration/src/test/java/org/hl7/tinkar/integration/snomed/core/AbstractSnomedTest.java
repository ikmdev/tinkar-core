package org.hl7.tinkar.integration.snomed.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.util.uuid.UuidT5Generator;
import org.hl7.tinkar.entity.StampEntity;
import org.hl7.tinkar.entity.StampRecord;
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
        String snomedCtNamespace = snomedData.get("namespace").asText();
        UUID nameSpaceUUID = UuidT5Generator.get(snomedCtNamespace);
        String status = snomedData.get("status").asText();
        State state = State.valueOf(status);
        long effectiveTime = snomedData.get("time").asLong();
        String moduleId = snomedData.get("module").asText();
        String author = snomedData.get("author").asText();
        String path = snomedData.get("path").asText();
        // TODO how to determine nids for entity
        StampRecord stampRecord = StampRecord.make(nameSpaceUUID, state, effectiveTime, PrimitiveData.publicId(moduleId), author, path);
        return stampRecord.stamp();
    }

    public UUID getStampUuid(UUID namespaceUuid, String status, long effectiveTime, String moduleId, String author, String path) {
        return UuidT5Generator.get(namespaceUuid, status+effectiveTime+moduleId+author+path);
    }

}
