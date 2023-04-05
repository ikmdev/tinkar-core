package dev.ikm.tinkar.entity.transfom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.terms.EntityProxy;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;

public class AbstractTestProtobufTransform {
    public static final String TEST_TINKAR_STARTER_JSON = "test-tinkar-starter-data.json";
    public static final String JSON_CONCEPTS_ARRAY_PROP = "concepts";
    public static final String JSON_CONCEPT_NAME_PROP   = "name";
    public static final String JSON_CONCEPT_UUID_PROP   = "uuid";

    protected Concept statusConcept;
    protected  Concept authorConcept;
    protected  Concept pathConcept;
    protected  Concept moduleConcept;
    protected  Concept testConcept;

    protected MockedStatic<Entity> mockedEntityService;
    protected StampRecord mockStampRecord;
    protected static Map<String, Concept> CONCEPT_MAP = new HashMap<>();

    protected static Concept createSimpleConcept(String conceptName, String uuidStr) {
        return EntityProxy.Concept.make(conceptName, UUID.fromString(uuidStr));
    }

    protected int nid(Concept concept) {
        return Entity.nid(concept.publicId());
    }

    protected JsonNode loadJsonFile(String fileName)  {
        InputStream inputStream = this.getClass().getResourceAsStream(fileName);
        try {
            String jsonText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readTree(jsonText);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public void init() {
        JsonNode jsonNode = loadJsonFile(TEST_TINKAR_STARTER_JSON);
        JsonNode concepts = jsonNode.get(JSON_CONCEPTS_ARRAY_PROP);
        concepts.forEach(c -> {
            Concept concept = createSimpleConcept(c.get(JSON_CONCEPT_NAME_PROP).asText(),
                    c.get(JSON_CONCEPT_UUID_PROP).asText());
            CONCEPT_MAP.put(c.get(JSON_CONCEPT_UUID_PROP).asText(), concept);
            CONCEPT_MAP.put(c.get(JSON_CONCEPT_NAME_PROP).asText(), concept);
        });

        moduleConcept = CONCEPT_MAP.get("moduleConcept");
        pathConcept   = CONCEPT_MAP.get("pathConcept");
        authorConcept = CONCEPT_MAP.get("authorConcept");
        statusConcept = CONCEPT_MAP.get("statusConcept");
        testConcept   = CONCEPT_MAP.get("testConcept");

        mockedEntityService = Mockito.mockStatic(Entity.class);
        mockedEntityService.when(() -> Entity.nid(statusConcept.publicId())).thenReturn(10);
        mockedEntityService.when(() -> Entity.nid(authorConcept.publicId())).thenReturn(20);
        mockedEntityService.when(() -> Entity.nid(moduleConcept.publicId())).thenReturn(30);
        mockedEntityService.when(() -> Entity.nid(pathConcept.publicId())).thenReturn(40);
        mockedEntityService.when(() -> Entity.nid(testConcept.publicId())).thenReturn(50);

        mockStampRecord = mock(StampRecord.class);

    }

}
