package dev.ikm.tinkar.integration.snomed.core;

import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static dev.ikm.tinkar.integration.snomed.core.SnomedCTConstants.*;
import static dev.ikm.tinkar.integration.snomed.core.SnomedCTConstants.DEVELOPMENT_PATH_UUID;
import static dev.ikm.tinkar.integration.snomed.core.SnomedCTHelper.*;
import static dev.ikm.tinkar.integration.snomed.core.MockDataType.ENTITYREF;
import static dev.ikm.tinkar.integration.snomed.core.MockDataType.MODULE;

public class SnomedCTStampChronology {

    public static List<StampRecord> createSTAMPChronologyForAllRecords(Object test, String snomedCTDataFile) {
        SnomedCTData snomedCTData = loadSnomedFile(test.getClass(), snomedCTDataFile);
        List<StampRecord> stampRecords = new ArrayList<>();
        int totalValueRows = snomedCTData.getTotalRows();
        for(int rowNumber =0 ; rowNumber < totalValueRows ; rowNumber++ ){
            StampRecord stampRecord = createSTAMPChronology(rowNumber, snomedCTData);
            stampRecords.add(stampRecord);
        }
        return stampRecords;
    }

    public static StampRecord createSTAMPChronology(int rowNumber, SnomedCTData snomedCTData) {
        UUID stampUUID = getStampUUID(snomedCTData.toString(rowNumber));
        StampRecord record = StampRecordBuilder.builder()
                .mostSignificantBits(stampUUID.getMostSignificantBits())
                .leastSignificantBits(stampUUID.getLeastSignificantBits())
                .nid(EntityService.get().nidForUuids(stampUUID))
                .versions(RecordListBuilder.make())
                .build();
        StampVersionRecord versionsRecord = createSTAMPVersion(record,rowNumber, snomedCTData);
        return record.withVersions(RecordListBuilder.make().newWith(versionsRecord));
    }

    public static StampVersionRecord createSTAMPVersion(StampRecord record, int rowNumber, SnomedCTData snomedCTData) {
        StampVersionRecordBuilder recordBuilder = StampVersionRecordBuilder.builder();
        if(snomedCTData.getActive(rowNumber) == 1){
            recordBuilder.stateNid(EntityService.get().nidForUuids(ACTIVE_UUID));
        }
        else if(snomedCTData.getActive(rowNumber) == 0){
            recordBuilder.stateNid(EntityService.get().nidForUuids(INACTIVE_UUID));
        }
        return recordBuilder
                .chronology(record)
                .time(snomedCTData.getEffectiveTime(rowNumber))
                .authorNid(EntityService.get().nidForUuids(SNOMED_CT_AUTHOR_UUID))
                .moduleNid(EntityService.get().nidForUuids(getSnomedTextModuleId(snomedCTData,rowNumber)))
                .pathNid(EntityService.get().nidForUuids(DEVELOPMENT_PATH_UUID))
                .build();
    }

    public static UUID getStampUUID(String uniqueString ) {
        UUID stampUUID = UuidT5Generator.get(SNOMED_CT_NAMESPACE_UUID, uniqueString);
        MockEntity.populateMockData(stampUUID.toString(), ENTITYREF);
        return stampUUID;
    }

    public static UUID getSnomedTextModuleId(SnomedCTData snomedCTData, int rowNumber){
        UUID moduleUUID = UuidT5Generator.get(snomedCTData.getNamespaceUUID(), snomedCTData.getModuleId(rowNumber));
        MockEntity.populateMockData(moduleUUID.toString(), MODULE);
        return moduleUUID;
    }
}
