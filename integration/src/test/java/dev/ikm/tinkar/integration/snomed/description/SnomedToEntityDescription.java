package dev.ikm.tinkar.integration.snomed.description;

import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static dev.ikm.tinkar.integration.snomed.core.TinkarStarterConceptUtil.*;

public class SnomedToEntityDescription {

    public static final int EFFECTIVE_TIME_INDEX = 1;
    public static final int ACTIVE_INDEX = 2;
    public static final int DEV_PATH_INDEX = 3;

    public StampRecord createSTAMPChronology(String row){

        UUID stampUUID = generateStampUuid(row);
        StampRecord record = StampRecordBuilder.builder()
                .mostSignificantBits(stampUUID.getMostSignificantBits())
                .leastSignificantBits(stampUUID.getLeastSignificantBits())
                .nid(EntityService.get().nidForUuids(stampUUID))
                .versions(RecordListBuilder.make())
                .build();

        StampVersionRecord versionsRecord = createSTAMPVersion(stampUUID, record, row);

        return record.withVersions(RecordListBuilder.make().newWith(versionsRecord));

    }

    StampVersionRecord createSTAMPVersion(UUID stampUUID, StampRecord record, String row){
        String[] values = row.split("\t");

        StampVersionRecordBuilder recordBuilder = StampVersionRecordBuilder.builder();
        if(Integer.parseInt(values[ACTIVE_INDEX]) == 1){
            recordBuilder.stateNid(EntityService.get().nidForUuids(ACTIVE));
        }
        else {
            recordBuilder.stateNid(EntityService.get().nidForUuids(INACTIVE));
        }
        return recordBuilder
                .chronology(record)
                .time(LocalDate.parse(values[EFFECTIVE_TIME_INDEX], DateTimeFormatter.ofPattern("yyyyMMdd")).atTime(12,0,0).toInstant(ZoneOffset.UTC).toEpochMilli())
                .authorNid(EntityService.get().nidForUuids(SNOMED_CT_AUTHOR))
                .moduleNid(EntityService.get().nidForUuids(UuidT5Generator.get(SNOMED_CT_NAMESPACE,values[DEV_PATH_INDEX])))
                .pathNid(EntityService.get().nidForUuids(DEVELOPMENT_PATH))
                .build();

    }

    private UUID generateStampUuid(String row) {
        return UuidT5Generator.get(SNOMED_CT_NAMESPACE, row.replaceAll("\t",""));
    }


}
