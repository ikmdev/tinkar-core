package dev.ikm.tinkar.integration.snomed.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.*;
import org.eclipse.collections.api.list.ImmutableList;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AbstractSnomedUtil {

    protected static EntityService EntityService;
    private static JsonNode snomedData;

    static Map<UUID, Entity> uuidToEntity = new HashMap<>();

    public AbstractSnomedUtil() {
        setupMockEntityService();
    }

    public void setupMockEntityService() {
        // TODO
        // This is Mocked Entity Service in test package with proxy data
        EntityService = dev.ikm.tinkar.integration.snomed.core.EntityService.get();
    }
    protected void loadJsonFile(String fileName) throws IOException {
        InputStream inputStream = this.getClass().getResourceAsStream(fileName);
        String jsonText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        ObjectMapper objectMapper = new ObjectMapper();
        snomedData = objectMapper.readTree(jsonText);
    }

    protected JsonNode getSnomedData() {
        return snomedData;
    }

    // This method will recursively parse fields passed as arguments from JSONNode.
    protected JsonNode getSnomedFieldData(String ... nestedFields)
    {
        JsonNode tempNode = null;
        boolean inloop = false;
        for (String field: nestedFields) {
            if (!inloop) {
                tempNode = snomedData.get(field);
                inloop = true;
            } else {
                tempNode = tempNode.get(field);
            }
        }
        return tempNode;
    }

    protected String getSnomedFieldDataAsText(String ... fields)
    {
        return getSnomedFieldData(fields).asText();
    }
    protected Long getSnomedFieldDataAsLong(String ... fields)
    {
        return getSnomedFieldData(fields).asLong();
    }

    protected void cleanUpSnomedData() {
       snomedData = null;
    }

    public ConceptRecord conceptBuilder(UUID conceptUuid) {
        return ConceptRecordBuilder.builder()
                .leastSignificantBits(conceptUuid.getLeastSignificantBits())
                .mostSignificantBits(conceptUuid.getMostSignificantBits())
                .versions(RecordListBuilder.make().build())
                .nid(EntityService.get().nidForUuids(conceptUuid))
                .build();
    }

    public SemanticRecord semanticBuilder(UUID semanticUuid, UUID conceptUUID) {
        return SemanticRecordBuilder.builder()
                .nid(EntityService.get().nidForUuids(semanticUuid))
                .mostSignificantBits(semanticUuid.getMostSignificantBits())
                .leastSignificantBits(semanticUuid.getLeastSignificantBits())
                .referencedComponentNid(EntityService.get().nidForUuids(conceptUUID))
                //.patternNid() // TODO how to get pattern nid
                .build();
    }

    protected ConceptRecord generateAndPublishConcept(String conceptFieldValue) {
        // Creating uuid for all the fields to convert them in concepts.
        UUID conceptUuid = UuidT5Generator.get(conceptFieldValue);
        // this could have been a random uuid, but to provide more meaningful, uuid - doing it this way
        ConceptRecord concept = conceptBuilder(conceptUuid);
//        EntityService.get().putEntity(concept); // i have added map in Abstract
        return concept;
    }

    protected int getNid(String ... nestedFields) {
        // Everything is a concept in Tinkar. Create nids for all concepts to populate in builders
        return generateAndPublishConcept(getSnomedFieldDataAsText(nestedFields)).nid();
    }

    protected int getInt(String ... nestedFields) {
        // Everything is a concept in Tinkar. Create nids for all concepts to populate in builders
        return getSnomedFieldData(nestedFields).asInt();
    }

    protected int getNid(JsonNode node, String fieldName) {
        // Everything is a concept in Tinkar. Create nids for all concepts to populate in builders
        return generateAndPublishConcept(node.get(fieldName).asText()).nid();
    }

    /*
       Stamp Chronology Methods
   */
    public UUID generateStampUuid() {
        UUID nameSpaceUUID = UUID.fromString(getSnomedFieldDataAsText("namespace"));
        String status = getSnomedFieldDataAsText("STAMP", "status");
        long effectiveTime = getSnomedFieldDataAsLong("STAMP","time");
        String module = getSnomedFieldDataAsText("STAMP","module");
        String author = getSnomedFieldDataAsText("STAMP","author");
        String path = getSnomedFieldDataAsText("STAMP","path");

        UUID stampUUID = UuidT5Generator.get(nameSpaceUUID, status+effectiveTime+module+author+path);
        return stampUUID;
    }

    public StampVersionRecord getStampVersionRecord(long effectiveTime, StampRecord stampRecord) {
        // Create stamp version with the record specific values
        return StampVersionRecordBuilder.builder()
                .stateNid(getNid("STAMP", "status"))
                .time(effectiveTime)
                .authorNid(getNid("STAMP", "author"))
                .moduleNid(getNid("STAMP", "module"))
                .pathNid(getNid("STAMP", "path"))
                .chronology(stampRecord)
                .build();
    }

    public StampEntity buildStampChronology(JsonNode snomedData) throws IOException
    {
        // Get stamp Uuid and effective time
        UUID stampUuid = generateStampUuid();
        long effectiveTime = getSnomedFieldDataAsLong("STAMP", "time");
        // Create stamp version with the record specific values
        RecordListBuilder<StampVersionRecord> stampVersionRecords =
                RecordListBuilder.make();
        // create Stamp Record with those versions created above.
        StampRecord stampRecord = StampRecordBuilder.builder()
                .versions(stampVersionRecords)
                .leastSignificantBits(stampUuid.getLeastSignificantBits())
                .mostSignificantBits(stampUuid.getMostSignificantBits())
                // TODO check with Andrew - StampUUID for multiple text file rows..
                //  are we going to put it in unit test and then read it?
                .nid(EntityService.get().nidForUuids(stampUuid))
                .build();

        StampVersionRecord stampVersionRecord = getStampVersionRecord(effectiveTime, stampRecord);

        StampRecord updatedStampRecord = StampRecordBuilder.builder(stampRecord)
                .versions(RecordListBuilder.make().newWith(stampVersionRecord))
                .build();
        return updatedStampRecord;
    }

    /*
        Concept Chronology Methods
    */
    public UUID generateConceptUuid(JsonNode conceptNode) throws IOException{
        ObjectMapper mapper = new ObjectMapper();
        String description = conceptNode.get("description").asText();
        String originsArray = conceptNode.get("origins").asText();
        String destinationsArray = conceptNode.get("destinations").asText();
        List<String> origins = Arrays.stream(mapper.readValue(originsArray, String[].class)).toList();
        List<String> destinations = Arrays.stream(mapper.readValue(destinationsArray , String[].class)).toList();
        return UuidT5Generator.get(description+origins.stream().reduce((str1, str2) -> str1 + str2)+destinations.stream().reduce((str1, str2) -> str1+str2));
    }

    public UUID generateNavigationUuid(JsonNode conceptNode)
            throws JsonProcessingException
    {
        ObjectMapper mapper = new ObjectMapper();
        String originsArray = conceptNode.get("origins").asText();
        String destinationsArray = conceptNode.get("destinations").asText();
        List<String> origins = Arrays.stream(mapper.readValue(originsArray, String[].class)).toList();
        List<String> destinations = Arrays.stream(mapper.readValue(destinationsArray , String[].class)).toList();
        return UuidT5Generator.get(origins.stream().reduce((str1, str2) -> str1 + str2)+""+destinations.stream().reduce((str1, str2) -> str1+str2));
    }

    public List<ConceptEntity> buildConceptChronology(JsonNode snomedData) throws IOException {
        List<ConceptEntity> conceptEntities = new ArrayList<>();
        Iterator itr = snomedData.get("concepts").iterator();

        while(itr.hasNext()) {
            JsonNode conceptNode = (JsonNode) itr.next();
            UUID conceptUUID = generateConceptUuid(conceptNode);
            UUID descriptionUUID = UuidT5Generator.get(conceptNode.get("description").asText());
            UUID navigationUUID = generateNavigationUuid(conceptNode);
            UUID stampUUID = generateStampUuid();

            SemanticEntity descriptionSemantic = semanticBuilder(descriptionUUID, conceptUUID);
            SemanticEntity navigationSemantic = semanticBuilder(navigationUUID, conceptUUID);
            RecordListBuilder<ConceptVersionRecord> conceptVersionRecords =
                    RecordListBuilder.make();

            ConceptRecordBuilder conceptRecordBuilder = ConceptRecordBuilder.builder()
                    .nid(EntityService.get().nidForUuids(conceptUUID))
                    .versions(conceptVersionRecords)
                    .leastSignificantBits(conceptUUID.getLeastSignificantBits())
                    .mostSignificantBits(conceptUUID.getMostSignificantBits());

            // TODO : which one goes before versionrecord or record..both refernce each other
            ConceptVersionRecord conceptVersionRecord = ConceptVersionRecordBuilder.builder()
                    .stampNid(EntityService.get().nidForUuids(stampUUID))
                    .chronology(conceptRecordBuilder.build()) // Todo Check with Andrew on cyclic dependencies
                    .build();

            conceptVersionRecords.add(conceptVersionRecord);
            conceptEntities.add(ConceptRecordBuilder
                    .builder(conceptRecordBuilder.build())
                    .versions(conceptVersionRecords)
                    .build());
        }

        return conceptEntities;
    }

    /*
      Pattern Chronology Methods
  */
    public UUID generatePatternUuid() {

        String description = getSnomedFieldDataAsText("patterns", "description");
        String meaning = getSnomedFieldDataAsText("patterns", "meaning");
        String purpose = getSnomedFieldDataAsText("patterns", "purpose");

        UUID patternUuid = UuidT5Generator.get(description+meaning+purpose);
        return patternUuid;
    }

    public PatternVersionRecord getPatternVersionRecord(PatternRecord patternRecord) {
        // Create pattern version with the record specific and field definition values
        ImmutableList<FieldDefinitionRecord> fieldDefinitions = RecordListBuilder.make()
                .add(FieldDefinitionRecordBuilder
                        .builder()
                        .meaningNid(getNid("patterns", "field-description", "meaning"))
                        .purposeNid(getNid("patterns", "field-description", "purpose"))
                        .dataTypeNid(getNid("patterns", "field-description", "datatype"))
                        .indexInPattern(getInt("patterns", "field-description", "index"))
                        .build()
                ).build();

        return PatternVersionRecordBuilder.builder()
                .semanticMeaningNid(getNid("patterns", "meaning"))
                .semanticPurposeNid(getNid("patterns", "purpose"))
                .fieldDefinitions(fieldDefinitions)
                .chronology(patternRecord)
                .build();
    }

    public PatternEntity buildPatternChronology(JsonNode snomedData)
    {
        // Get stamp Uuid and effective time
        UUID patternUuid = generatePatternUuid();
        RecordListBuilder<PatternVersionRecord> patternVersionRecords =
                RecordListBuilder.make();

        // create Stamp Record with those versions created above.
        PatternRecord patternRecord = PatternRecordBuilder.builder()
                .versions(patternVersionRecords)
                .leastSignificantBits(patternUuid.getLeastSignificantBits())
                .mostSignificantBits(patternUuid.getMostSignificantBits())
                .nid(EntityService.get().nidForUuids(patternUuid))
                .build();

        PatternVersionRecord patternVersionRecord = getPatternVersionRecord(patternRecord);

        PatternRecord updatedPatternRecord = PatternRecordBuilder.builder(patternRecord)
                .versions(RecordListBuilder.make().newWith(patternVersionRecord))
                .build();

        return updatedPatternRecord;
    }

    public Entity getReference(String fieldValue) {
        return uuidToEntity.get(UuidT5Generator.get(fieldValue));
    }

}
