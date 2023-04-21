package dev.ikm.tinkar.integration.snomed.core;

import com.fasterxml.jackson.databind.JsonNode;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import static dev.ikm.tinkar.integration.snomed.core.TinkarStarterConceptUtil.*;

public class MockEntity {

    private static int nidCount = 1;
    private static final Map<UUID, Integer> mockDataMap = new HashMap<>();

    static {
        init();
    }

    private static void init() {
        JsonNode primitiveData = loadJsonData(TinkarStarterDataHelper.class, TEST_SNOMEDCT_MOCK_DATA_JSON);
        Iterator itr = primitiveData.get("data").iterator();

        while(itr.hasNext()) {
            JsonNode mockData = (JsonNode) itr.next();
            String value = mockData.get("value").asText();
            TinkarStarterDataHelper.MockDataType type = TinkarStarterDataHelper.MockDataType.getEnumType(mockData.get("type").asText());
            Integer nid = mockData.get("nid").asInt();
            MockEntity.populateMockData(value, type, nid);
        }
    }

    public static void populateMockData(String textValue, TinkarStarterDataHelper.MockDataType type) {
        populateMockData(textValue, type, nidCount);
        nidCount+=1;
    }

    private static void populateMockData(String textValue, TinkarStarterDataHelper.MockDataType type, int nid) {
        UUID value;
        switch(type) {
            case MODULE: {
                value = UuidT5Generator.get(SNOMED_CT_NAMESPACE, textValue);
            }
            break;
            case CONCEPT: {
                value = UuidT5Generator.get(textValue);
            }
            break;
            case ENTITYREF: {
                value = UUID.fromString(textValue);
            }
            break;
            default: {
                value = UUID.randomUUID();
            };
            break;
        }
        mockDataMap.putIfAbsent(value, nid);
    }

    public static int getNid(UUID key) {
        return mockDataMap.get(key);
    }
}
