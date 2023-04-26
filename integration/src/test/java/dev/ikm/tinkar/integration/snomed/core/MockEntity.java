package dev.ikm.tinkar.integration.snomed.core;

import com.fasterxml.jackson.databind.JsonNode;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import static dev.ikm.tinkar.integration.snomed.core.TinkarStarterConceptUtil.*;

public class MockEntity {
    private static int nidCount = 100;
    private static final Map<UUID, Integer> mockDataMap = new HashMap<>();

    // universal cache of type Object to persist entities (stamp, concept, semantics) to create multiple versions
    private static final Map<UUID, Object> entityCache = new HashMap();

    static {
        init();
    }

    public static void init() {
        JsonNode primitiveData = loadJsonData(MockEntity.class, TEST_SNOMEDCT_MOCK_DATA_JSON);
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

    public static void putEntity(UUID uuid, Object entity) {
        entityCache.put(uuid, entity);
    }

    public static Object getEntity(UUID key) {
        return entityCache.get(key);
    }
}
