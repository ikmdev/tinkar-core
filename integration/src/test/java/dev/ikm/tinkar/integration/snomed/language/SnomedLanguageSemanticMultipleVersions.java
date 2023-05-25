package dev.ikm.tinkar.integration.snomed.language;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.integration.snomed.core.MockEntity;
import dev.ikm.tinkar.integration.snomed.core.SnomedCTHelper;

import java.time.Instant;
import java.util.*;

import static dev.ikm.tinkar.integration.snomed.core.MockDataType.ENTITYREF;
import static dev.ikm.tinkar.integration.snomed.core.TinkarStarterConceptUtil.*;


public class SnomedLanguageSemanticMultipleVersions {
    public static List<SemanticRecord> createLanguageAcceptabilitySemantic(String input) {
        Set<PublicId> uniquePublicIds = new HashSet<>();
        List<SemanticRecord> semanticRecordList = new ArrayList<>();
        List<String> rows = loadSnomedFile(SnomedLanguageSemanticMultipleVersions.class, input);
        for (String row : rows) {
            SemanticRecord semanticRecord = createSemanticRecord(row);
            if (uniquePublicIds.contains(semanticRecord.publicId())) {
                semanticRecordList.add(semanticRecord);
            } else {
                uniquePublicIds.add(semanticRecord.publicId());
            }
        }
        return semanticRecordList;

    }

    public static SemanticRecord createSemanticRecord(String input) {
        LanguageData rowFile = new LanguageData(input);
        UUID patternUUID = LANGUAGE_ACCEPTABILITY_PATTERN;
        MockEntity.populateMockData(patternUUID.toString(), ENTITYREF);
        UUID semanticUUID = SnomedCTHelper.getSemanticUUID(patternUUID, rowFile.getId());
        UUID referencedComponentUUID = SnomedCTHelper.getReferenceComponentUUID(patternUUID, rowFile.getReferencedComponentId());

        SemanticRecord existingSemanticRecord = (SemanticRecord) MockEntity.getEntity(semanticUUID);
        SemanticRecordBuilder recordBuilder = existingSemanticRecord == null ?
                SemanticRecordBuilder.builder().versions(RecordListBuilder.make()) :
                SemanticRecordBuilder.builder(existingSemanticRecord).versions(existingSemanticRecord.versions());
        recordBuilder.leastSignificantBits(semanticUUID.getLeastSignificantBits())
                .mostSignificantBits(semanticUUID.getMostSignificantBits())
                .nid(EntityService.get().nidForUuids(semanticUUID))
                .patternNid(EntityService.get()
                        .nidForUuids(patternUUID))
                .referencedComponentNid(EntityService.get()
                        .nidForUuids(referencedComponentUUID));
        SemanticVersionRecord semanticVersionRecord = createLanguageAcceptabilitySemanticVersion(recordBuilder.build(), input, rowFile.getAcceptabilityId());

        SemanticRecord newSemanticRecord = SemanticRecordBuilder
                .builder(recordBuilder.build())
                .versions(recordBuilder.versions().newWith(semanticVersionRecord))
                .build();
        MockEntity.putEntity(semanticUUID, newSemanticRecord);
        return newSemanticRecord;
    }


    static SemanticVersionRecord createLanguageAcceptabilitySemanticVersion(SemanticRecord record, String input, String acceptabilityId) {
        Object[] fields = new Object[]{UuidT5Generator.get(SNOMED_CT_NAMESPACE, acceptabilityId)};
        StampRecord stampRecord = createStampChronology(input);
        return SemanticVersionRecordBuilder.builder()
                .chronology(record).
                stampNid(stampRecord.nid())
                .fieldValues(RecordListBuilder.make()
                        .newWithAll(List.of(fields))).build();
    }

    public static StampRecord createStampChronology(String input) {
        LanguageData rowFile = new LanguageData(input);
        UUID stampUUID = UuidT5Generator.get(SNOMED_CT_NAMESPACE, rowFile.getId() + rowFile.getEffectiveTime() + rowFile.getActive() + rowFile.getModuleId() + rowFile.getRefsetId() + rowFile.getReferencedComponentId() + rowFile.getAcceptabilityId());
        MockEntity.populateMockData(stampUUID.toString(), ENTITYREF);
        StampRecord stampRecord = StampRecordBuilder.builder().mostSignificantBits(stampUUID.getMostSignificantBits()).leastSignificantBits(stampUUID.getLeastSignificantBits()).nid(EntityService.get().nidForUuids(stampUUID)).versions(RecordListBuilder.make()).build();

        StampVersionRecord versionsRecord = createSTAMPVersion(stampRecord, input);
        return stampRecord.withVersions(RecordListBuilder.make().newWith(versionsRecord));

    }

    static StampVersionRecord createSTAMPVersion(StampRecord record, String row) {
        LanguageData input = new LanguageData(row);
        long timeStamp = Instant.ofEpochSecond(Long.parseLong(input.getEffectiveTime())).toEpochMilli();
        StampVersionRecordBuilder recordBuilder = StampVersionRecordBuilder.builder();
        if (input.getActive() == 1) recordBuilder.stateNid(EntityService.get().nidForUuids(ACTIVE));
        if (input.getActive() == 0) recordBuilder.stateNid(EntityService.get().nidForUuids(INACTIVE));
        return recordBuilder.authorNid(EntityService.get().nidForUuids(SNOMED_CT_AUTHOR)).pathNid(EntityService.get().nidForUuids(DEVELOPMENT_PATH)).moduleNid(EntityService.get().nidForUuids(UuidT5Generator.get(SNOMED_CT_NAMESPACE, String.valueOf(input.getModuleId())))).chronology(record).time(timeStamp).build();


    }


}



