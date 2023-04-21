package dev.ikm.tinkar.integration.snomed.concept;

import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.integration.snomed.core.MockEntity;
import dev.ikm.tinkar.integration.snomed.core.TinkarStarterConceptUtil;
import dev.ikm.tinkar.integration.snomed.core.TinkarStarterDataHelper;

import java.time.Instant;
import java.util.UUID;

import static dev.ikm.tinkar.integration.snomed.core.TinkarStarterConceptUtil.*;

public class SnomedTextToConcept {

    protected static class Concept {
        String id;
        long effectiveTime;
        int active;
        String moduleId;
        String definitionStatusId;

        public Concept(String input)
        {
            String[] row = input.split(("\t"));
            this.id = row[0];
            this.effectiveTime = Long.parseLong(row[1]);
            this.active = Integer.parseInt(row[2]);
            this.moduleId = row[3];
            this.definitionStatusId = row[4];
        }

        @Override public String toString() {
            return id+effectiveTime+active+moduleId+definitionStatusId;
        }

    }

    SnomedTextToConcept() {
    }



    public static UUID getStampUUID(Concept textConcept) {
        UUID nameSpaceUUID = TinkarStarterConceptUtil.SNOMED_CT_NAMESPACE;
        String status = textConcept.active == 1 ? "Active":"Inactive";
        long effectiveTime = textConcept.effectiveTime;
        String module = textConcept.moduleId;
        String definitionStatusId = textConcept.definitionStatusId;

        UUID stampUUID = UuidT5Generator.get(nameSpaceUUID, status + effectiveTime + module + definitionStatusId);
        MockEntity.populateMockData(stampUUID.toString(), TinkarStarterDataHelper.MockDataType.ENTITYREF);
        return stampUUID;
    }


    public static StampRecord createSTAMPChronology(String row){
        Concept textConceptEntity = new Concept(row);

        UUID stampUUID = getStampUUID(textConceptEntity);

        StampRecord record = StampRecordBuilder.builder()
                .mostSignificantBits(stampUUID.getMostSignificantBits())
                .leastSignificantBits(stampUUID.getLeastSignificantBits())
                .nid(EntityService.get().nidForUuids(stampUUID))
                .versions(RecordListBuilder.make())
                .build();

        StampVersionRecord versionsRecord = createSTAMPVersion(stampUUID, record, row);

        return record.withAndBuild(versionsRecord);

    }

   public static StampVersionRecord createSTAMPVersion(UUID stampUUID,
                                                           StampRecord record,
                                                           String row)
    {
        Concept textConceptEntity = new Concept(row);

        StampVersionRecordBuilder recordBuilder = StampVersionRecordBuilder.builder();
        if (textConceptEntity.active == 1){
            recordBuilder.stateNid(EntityService.get().nidForUuids(ACTIVE));
        } else {
            recordBuilder.stateNid(EntityService.get().nidForUuids(INACTIVE));
        }
        return recordBuilder
                .chronology(record)
                .time(Instant.ofEpochSecond(textConceptEntity.effectiveTime).toEpochMilli())
                .authorNid(EntityService.get().nidForUuids(SNOMED_CT_AUTHOR))
                .moduleNid(EntityService.get().nidForUuids(UuidT5Generator.get(SNOMED_CT_NAMESPACE, textConceptEntity.moduleId)))
                .pathNid(EntityService.get().nidForUuids(DEVELOPMENT_PATH))
                .build();

    }
}
