package dev.ikm.tinkar.integration.snomed.concept;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.integration.snomed.core.MockEntity;
import dev.ikm.tinkar.integration.snomed.core.TinkarStarterConceptUtil;
import dev.ikm.tinkar.integration.snomed.core.TinkarStarterDataHelper;

import java.time.Instant;
import java.util.*;

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

    /*
    Generating Stamp Chronology for Concepts
    */

    public static UUID getStampUUID(String row) {
        Concept textConcept = new Concept(row);
        UUID nameSpaceUUID = TinkarStarterConceptUtil.SNOMED_CT_NAMESPACE;

        UUID stampUUID = UuidT5Generator.get(nameSpaceUUID, textConcept.toString());
        MockEntity.populateMockData(stampUUID.toString(), TinkarStarterDataHelper.MockDataType.ENTITYREF);
        return stampUUID;
    }


    public static StampRecord createStampChronology(String row){

        UUID stampUUID = getStampUUID(row);

        StampRecord record = StampRecordBuilder.builder()
                .mostSignificantBits(stampUUID.getMostSignificantBits())
                .leastSignificantBits(stampUUID.getLeastSignificantBits())
                .nid(EntityService.get().nidForUuids(stampUUID))
                .versions(RecordListBuilder.make())
                .build();

        StampVersionRecord versionsRecord = createStampVersion(record, row);

        return record.withAndBuild(versionsRecord);

    }

   public static StampVersionRecord createStampVersion(StampRecord record, String row)
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

    /*
    Generating Concepts for Concept File
    */

    public static UUID getConceptUUID(String row) {
        Concept textConcept = new Concept(row);
        UUID conceptUUID = UuidT5Generator.get(SNOMED_CT_NAMESPACE, textConcept.id);
        MockEntity.populateMockData(conceptUUID.toString(), TinkarStarterDataHelper.MockDataType.ENTITYREF);
        return conceptUUID;
    }

    public static ConceptRecord createConceptChronology(String row) {
        // Get ConceptUUID from row
        UUID conceptUUID = getConceptUUID(row);
        ConceptRecord existingConceptRecord = (ConceptRecord)MockEntity.getEntity(conceptUUID);

        // Create record builder based on conceptUUID (if it exists or else it creates new)
        ConceptRecordBuilder recordBuilder = existingConceptRecord == null ?
                ConceptRecordBuilder.builder().versions(RecordListBuilder.make()) :
                ConceptRecordBuilder.builder(existingConceptRecord).versions(existingConceptRecord.versions());

        recordBuilder.leastSignificantBits(conceptUUID.getLeastSignificantBits())
                .mostSignificantBits(conceptUUID.getMostSignificantBits())
                .nid(EntityService.get().nidForUuids(conceptUUID));

        ConceptVersionRecord conceptVersionRecord = createConceptVersion(recordBuilder.build(), row);

        ConceptRecord newConceptRecord = ConceptRecordBuilder
                .builder(recordBuilder.build())
                .versions(recordBuilder.versions().newWith(conceptVersionRecord))
                .build();

        MockEntity.putEntity(conceptUUID, newConceptRecord);

        return newConceptRecord;
    }

    public static ConceptVersionRecord createConceptVersion(ConceptRecord conceptRecord, String row) {
        StampRecord stampRecord = createStampChronology(row);

        return ConceptVersionRecordBuilder.builder()
                .chronology(conceptRecord)
                .stampNid(stampRecord.nid())
                .build();
    }

    public static List<ConceptRecord> createConceptFromMultipleVersions(Class<?> aClass, String fileName) {
        Set<PublicId> uniquePublicIds = new HashSet<>();
        List<ConceptRecord> conceptRecords = new ArrayList<>();

        List<String> rows = loadSnomedFile(aClass, fileName);
            for(String row: rows) {
                ConceptRecord conceptRecord = createConceptChronology(row);
                if (uniquePublicIds.contains(conceptRecord.publicId())) {
                    conceptRecords.add(conceptRecord);
                } else {
                    uniquePublicIds.add(conceptRecord.publicId());
                }
            }
            return conceptRecords;

        }

}
