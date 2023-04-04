package org.hl7.tinkar.integration.snomed.description;

import org.eclipse.collections.api.factory.Lists;
import org.hl7.tinkar.common.util.uuid.UuidT5Generator;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.integration.snomed.core.EntityService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class TestSnomedToEntity {

    public static final UUID NAMESPACE = new UUID(1l,2l); //TODO: placeholder for SNOMED CT namespace UUID
    EntityService entityService;

    public TestSnomedToEntity() {
        setupMockEntityService();
    }

    public void setupMockEntityService() {
        // TODO
        // This is Mocked Entity Service in test package with proxy data
        entityService = org.hl7.tinkar.integration.snomed.core.EntityService.get();
    }
    public SemanticRecord createDescriptionSemantic(String row) {

        String[] values = row.split("\t");

        UUID patternUUID = new UUID(8l, 2l); //TODO: reference existing concept 'Description Pattern'
        UUID semanticUUID = UuidT5Generator.get(patternUUID.toString()+values[0]);
        UUID referencedComponentUUID = UuidT5Generator.get(NAMESPACE,values[4]);
        int nidValue = entityService.get().nidForUuids(semanticUUID);
        SemanticRecord record = SemanticRecordBuilder.builder()
                .leastSignificantBits(semanticUUID.getLeastSignificantBits())
                .mostSignificantBits(semanticUUID.getMostSignificantBits())
                .nid(nidValue)
                .patternNid(entityService.get().nidForUuids(patternUUID))
                .referencedComponentNid(entityService.get().nidForUuids(referencedComponentUUID))
                .versions(Lists.immutable.with())
                .build();

        SemanticVersionRecord versionRecord = createSemanticVersion(semanticUUID, record, values);

        return record.withVersions(Lists.immutable.with(versionRecord));
    }

    SemanticVersionRecord createSemanticVersion(UUID semanticUUID, SemanticRecord record, String[] values){
        UUID language = null;
        if (values[5].equals("en")){
            language = new UUID(1l,2l); //TODO: reference existing concept 'English language'
        }
        Object[] fields = {
                language,
                values[7],
                UuidT5Generator.get(NAMESPACE, values[8]),
                UuidT5Generator.get(NAMESPACE, values[6])};

        return SemanticVersionRecordBuilder.builder()
                .chronology(record)
                .stampNid(createSTAMPChronology(values).nid())
                .fieldValues(Lists.immutable.ofAll(Arrays.asList(fields)))
                .build();
    }

    public StampRecord createSTAMPChronology(String[] values){

        String template =  "%s%s%s%s%s%s%s%s%s".formatted(
                values[0], //id
                values[1], //effective time
                values[2], //active
                values[3], //module id
                values[4], //concept id
                values[5], //language code
                values[6], //type id
                values[7], //term
                values[8]); //case significance id

        UUID stampUUID = UuidT5Generator.get(NAMESPACE, template); //TODO: reference SNOMED CT Namespace UUID

        StampRecord record = StampRecordBuilder.builder()
                .mostSignificantBits(stampUUID.getMostSignificantBits())
                .leastSignificantBits(stampUUID.getLeastSignificantBits())
                .nid(entityService.get().nidForUuids(stampUUID))
                .versions(Lists.immutable.with())
                .build();

        StampVersionRecord versionsRecord = createSTAMPVersion(stampUUID, record, values);

        return record.withVersions(Lists.immutable.with(versionsRecord));

    }
    //TODO: reference existing concepts for state, author, module, path
    StampVersionRecord createSTAMPVersion(UUID stampUUID, StampRecord record, String[] values){
        int state =1; //TODO: need state concept
        if(Integer.parseInt(values[2]) == 1){
            state = 2;
        }
        return StampVersionRecordBuilder.builder()
                .chronology(record)
                .stateNid(state)
                .time(LocalDate.parse(values[1], DateTimeFormatter.ofPattern("yyyyMMdd")).atTime(12,0,0).toInstant(ZoneOffset.UTC).toEpochMilli())
                .authorNid(entityService.get().nidForUuids()))
                .moduleNid(3)
                .pathNid(4)
                .build();

    }
    public List<String> loadSnomedFile(String fileName){
        List<String> lines = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream(fileName)));
            reader.readLine();
            String line = reader.readLine();
            while(line!=null){
                lines.add(line);
                line = reader.readLine();
            }
        } catch (Exception e) {
        }
        return lines;
    }


}
