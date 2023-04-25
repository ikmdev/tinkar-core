package dev.ikm.tinkar.integration.snomed.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TinkarStarterConceptUtil {
    public static final String TEST_SNOMEDCT_MOCK_DATA_JSON ="mock-data.json";
    public static final UUID SNOMED_CT_NAMESPACE = UUID.fromString("48b004d4-6457-4648-8d58-e3287126d96b");
    public static final UUID DEVELOPMENT_PATH = UuidT5Generator.get("Development Path");
    public static final UUID SNOMED_CT_AUTHOR = UuidT5Generator.get("SNOMED CT Author");
    public static final UUID SNOMED_CT_STARTER_DATA_MODULE = UuidT5Generator.get("SNOMED CT Starter Data Module");
    public static final UUID ACTIVE = UuidT5Generator.get("Active");
    public static final UUID INACTIVE = UuidT5Generator.get("Inactive");
    public static final UUID DELOITTE_USER = UuidT5Generator.get("Deloitte User");
    public static final UUID IDENTIFIER_PATTERN = UuidT5Generator.get("Identifier Pattern");
    public static final UUID SNOMED_CT_IDENTIFIER = UuidT5Generator.get("SNOMED CT identifier");
    public static final UUID SNOMED_TEXT_MODULE_ID = UuidT5Generator.get(SNOMED_CT_NAMESPACE , "900000000000207008");

    public static JsonNode loadJsonData(Class<?> aClass, String fileName) {
        InputStream inputStream = aClass.getResourceAsStream(fileName);
        try {
            String jsonText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readTree(jsonText);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<String> loadSnomedFile(Class<?> aClass, String fileName) {
        List<String> lines = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(aClass.getResourceAsStream(fileName)));
            reader.readLine();
            String line = reader.readLine();
            while(line!=null){
                lines.add(line);
                line = reader.readLine();
            }
        } catch (Exception e) {
            System.out.println("Empty file");
        }
        return lines;
    }
}
