package dev.ikm.tinkar.entity.transfom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.schema.PBPublicId;
import dev.ikm.tinkar.schema.PBStampVersion;
import dev.ikm.tinkar.terms.EntityProxy;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test Helper utility class helps the developer & tester to do the following:
 * <pre>
 *     1. Open a session to perform tests and mock entity related services.
 *     2. Create Protobuffer objects
 *     3. Any utility functions to assist in the test cases.
 * </pre>
 * This allows you to create unit tests without wiring up a full entity service.
 *
 */
public class ProtobufToEntityTestHelper {
    /** Primative starter data */
    public static final String TEST_TINKAR_STARTER_JSON = "test-tinkar-starter-data.json";

    /** JSON property concepts an array of concept objects. */
    public static final String JSON_CONCEPTS_ARRAY_PROP = "concepts";

    /** JSON property name of a concept object. */
    public static final String JSON_CONCEPT_NAME_PROP   = "name";

    /** JSON property uuid of a concept object. */
    public static final String JSON_CONCEPT_UUID_PROP   = "uuid";

    /** A module concept object. */
    public static final String MODULE_CONCEPT_NAME      = "moduleConcept";
    /** A path concept object. */
    public static final String PATH_CONCEPT_NAME        = "pathConcept";
    /** An author concept object. */
    public static final String AUTHOR_CONCEPT_NAME      = "authorConcept";
    /** A status concept object. */
    public static final String STATUS_CONCEPT_NAME      = "statusConcept";
    /** A simple test concept object. */
    public static final String TEST_CONCEPT_NAME        = "testConcept";

    /** A simple referencedComponentPurposeConcept concept object. */
    public static final String REF_COMP_PURPOSE_CONCEPT_NAME = "referencedComponentPurposeConcept";
    /** A referencedComponentMeaningConcept concept object. */
    public static final String REF_COMP_MEANING_CONCEPT_NAME = "referencedComponentMeaningConcept";
    /** A meaningConcept concept object. */
    public static final String MEANING_CONCEPT_NAME          = "meaningConcept";
    /** A dataTypeConcept object. */
    public static final String DATATYPE_CONCEPT_NAME         = "dataTypeConcept";
    /** A purposeConcept concept object. */
    public static final String PURPOSE_CONCEPT_NAME          = "purposeConcept";


    public static PBPublicId createPBPublicId(){
        return PBPublicId.newBuilder().build();
    }
    public static PBPublicId createPBPublicId(Concept concept){
        return createPBPublicId(concept.publicId());
    }
    public static PBPublicId createPBPublicId(PublicId publicId){
        ByteString byteString = ByteString.copyFrom(UuidUtil.getRawBytes(publicId.asUuidList().get(0)));
        return PBPublicId.newBuilder().addId(byteString).build();
    }

    /**
     * Returns the current epoch time in seconds as a long.
     * @return long current epoch time in seconds.
     */
    public static long nowEpochSeconds() {
        return Instant.now().getEpochSecond();
    }
    public static long nowEpochSeconds(long secondsToAdd) {
        return Instant.now().plusSeconds(secondsToAdd).getEpochSecond();
    }
    public static Timestamp createTimestamp(long epochSeconds) {
        return Timestamp.newBuilder().setSeconds(epochSeconds).build();
    }

    /**
     * Returns a Google's protocal buffers Timestamp object based on epoch time in seconds.
     * @return
     */
    public static Timestamp nowTimestamp() {
        return createTimestamp(nowEpochSeconds());
    }
    public static Timestamp nowTimestamp(long secondsToAdd) {
        return createTimestamp(nowEpochSeconds(secondsToAdd));
    }

    /**
     * This will create a simple concept having a name and uuid. Becareful the concept returned will not have a nid inside the PublicId.
     * @param conceptName Name of the unique concept
     * @param uuidStr UUID hash string
     * @return Concept The concept.
     */
    protected static Concept createSimpleConcept(String conceptName, String uuidStr) {
        return EntityProxy.Concept.make(conceptName, UUID.fromString(uuidStr));
    }

    /**
     * Based on the location of the class file is where text json files will reside.
     * @param clazz The test class being run lets this method know the resource path of the json file.
     * @param fileName The json file name
     * @return JsonNode which allows you to read and parse values.
     */
    public static JsonNode loadJsonFile(Class clazz, String fileName)  {
        InputStream inputStream = clazz.getResourceAsStream(fileName);
        try {
            String jsonText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readTree(jsonText);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This can only be used when the Entity.nid(PublicId) is mocked.
     * @param concept
     * @return
     */
    public static int nid(Concept concept) {
        return Entity.nid(concept.publicId());
    }

    /**
     * Create PB stamp Version
     * @param expectedTime
     * @param statusConcept
     * @param authorConcept
     * @param moduleConcept
     * @param pathConcept
     * @return
     */
    public static PBStampVersion createPbStampVersion(Timestamp expectedTime, Concept statusConcept, Concept authorConcept, Concept moduleConcept, Concept pathConcept) {
        PBStampVersion pbStampVersion = PBStampVersion.newBuilder()
                .setStatus(createPBPublicId(statusConcept))
                .setTime(expectedTime)
                .setAuthor(createPBPublicId(authorConcept))
                .setModule(createPBPublicId(moduleConcept))
                .setPath(createPBPublicId(pathConcept))
                .build();
        return pbStampVersion;
    }

    public static PBStampVersion createPbStampVersion(Map<String, Concept> conceptMap, Timestamp expectedTime) {
        PBStampVersion pbStampVersion = PBStampVersion.newBuilder()
                .setStatus(createPBPublicId(conceptMap.get(STATUS_CONCEPT_NAME)))
                .setTime(expectedTime)
                .setAuthor(createPBPublicId(conceptMap.get(AUTHOR_CONCEPT_NAME)))
                .setModule(createPBPublicId(conceptMap.get(MODULE_CONCEPT_NAME)))
                .setPath(createPBPublicId(conceptMap.get(PATH_CONCEPT_NAME)))
                .build();
        return pbStampVersion;
    }
    /**
     * A statically mocked Entity class when nid(PublicId) and nid(Component) is called.
     * Also map containing the concept name and concept instance for easy lookup.
     * @param test The test class enabling the core concepts to be loaded from a json file
     * @param session A scope for the test developer to perform testing so Mokito can unregister statically mocked objects.
     */
    public static void openSession(Object test, BiConsumer<MockedStatic<Entity>, Map<String, Concept>> session) {
        Map<String, Concept> conceptMap = new HashMap<>();
        JsonNode jsonNode = loadJsonFile(test.getClass(), TEST_TINKAR_STARTER_JSON);
        JsonNode concepts = jsonNode.get(JSON_CONCEPTS_ARRAY_PROP);

        // Convert to a list of json concepts
        List<JsonNode> conceptList = new ArrayList<>();
        concepts.iterator().forEachRemaining(conceptList::add);

        try (MockedStatic<Entity> mockedEntity = Mockito.mockStatic(Entity.class)) {
            // Mock the EntityService provider
            EntityService entityService = mock(EntityService.class);

            for(int i=0; i<conceptList.size(); i++) {
                int nid = (i+1) * 10;
                // Each JSON concept from the json file (resources)
                JsonNode conceptJson = conceptList.get(i);
                Concept concept = createSimpleConcept(conceptJson.get(JSON_CONCEPT_NAME_PROP).asText(),
                        conceptJson.get(JSON_CONCEPT_UUID_PROP).asText());
                conceptMap.put(conceptJson.get(JSON_CONCEPT_UUID_PROP).asText(), concept);
                conceptMap.put(conceptJson.get(JSON_CONCEPT_NAME_PROP).asText(), concept);

                // Mock the nid(PublicId) method
                mockedEntity.when(() -> Entity.nid(concept.publicId())).thenReturn(nid);

                // Mock the nid(Concept) method
                mockedEntity.when(() -> Entity.nid(concept)).thenReturn(nid);

                // put entity and store nid
                when(entityService.nidForComponent(concept)).thenReturn(nid);
                when(entityService.nidForPublicId(concept.publicId())).thenReturn(nid);
                mockedEntity.when(() -> Entity.provider()).thenReturn(entityService);
            }

            // Pass mocked entity service and conc
            session.accept(mockedEntity, conceptMap);

        } // closable (unregisters statically mocked object)

    }

}
