package dev.ikm.tinkar.integration.snomed.language;

import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.integration.snomed.core.MockEntity;
import dev.ikm.tinkar.integration.snomed.core.TinkarStarterConceptUtil;
import dev.ikm.tinkar.integration.snomed.core.TinkarStarterDataHelper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static dev.ikm.tinkar.integration.snomed.core.TinkarStarterConceptUtil.*;

public class SnomedToLanguageTransform {
    protected static class Language {
        String id;
        long effectiveTime;
        int active;
        String moduleId;
        String refsetId;
        String referencedComponentId;
        String acceptabilityId;

        public Language(String input)
        {
            String[] row = input.split(("\t"));
            this.id = row[0];
            this.effectiveTime = Long.parseLong(row[1]);
            this.active = Integer.parseInt(row[2]);
            this.moduleId = row[3];
            this.refsetId = row[4];
            this.referencedComponentId = row[5];
            this.acceptabilityId = row[6];
        }

        @Override public String toString() {
            return id+effectiveTime+active+moduleId+refsetId+referencedComponentId+acceptabilityId;
        }

    }

    /*
    Generating Semantic Chronology for LanguageFile
    */

    public static UUID getStampUUID(String row) {
        Language textLanguage = new Language(row);
        UUID nameSpaceUUID = TinkarStarterConceptUtil.SNOMED_CT_NAMESPACE;

        UUID stampUUID = UuidT5Generator.get(nameSpaceUUID, textLanguage.toString());
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

        return StampRecordBuilder.builder(record)
                .versions(record.versions().newWith(versionsRecord))
                .build();

    }

   public static StampVersionRecord createStampVersion(StampRecord record, String row)
    {
        Language textLanguageEntity = new Language(row);

        StampVersionRecordBuilder recordBuilder = StampVersionRecordBuilder.builder();
        if (textLanguageEntity.active == 1){
            recordBuilder.stateNid(EntityService.get().nidForUuids(ACTIVE));
        } else {
            recordBuilder.stateNid(EntityService.get().nidForUuids(INACTIVE));
        }

        return recordBuilder
                .chronology(record)
                .time(Instant.ofEpochSecond(textLanguageEntity.effectiveTime).toEpochMilli())
                .authorNid(EntityService.get().nidForUuids(SNOMED_CT_AUTHOR))
                .moduleNid(EntityService.get().nidForUuids(UuidT5Generator.get(SNOMED_CT_NAMESPACE, textLanguageEntity.moduleId)))
                .pathNid(EntityService.get().nidForUuids(DEVELOPMENT_PATH))
                .build();

    }

    // creates identifier semantic for language
    public static SemanticRecord createLanguageIdentifierSemantic(String row) {
        UUID patternUUID = getIdentifierPatternUUID();
        UUID semanticUUID = getIdentifierSemanticUUID(row);
        UUID referencedComponentUUID = getReferenceComponentUUID(row);

        Language textLanguage = new Language(row);
        Object[] fields = new Object[] {textLanguage.id, SNOMED_CT_IDENTIFIER};
        StampRecord stampRecord = createStampChronology(row);

        SemanticRecord identifierSemanticRecord = SemanticRecordBuilder.builder()
                .versions(RecordListBuilder.make())
                .nid(EntityService.get().nidForUuids(semanticUUID))
                .referencedComponentNid(EntityService.get().nidForUuids(referencedComponentUUID))
                .patternNid(EntityService.get().nidForUuids(patternUUID))
                .leastSignificantBits(semanticUUID.getLeastSignificantBits())
                .mostSignificantBits(semanticUUID.getMostSignificantBits())
                .build();

        SemanticVersionRecord semanticVersionRecord = SemanticVersionRecordBuilder.builder()
                .chronology(identifierSemanticRecord)
                .stampNid(stampRecord.nid())
                .fieldValues(RecordListBuilder.make().newWithAll(List.of(fields)))
                .build();

        return SemanticRecordBuilder.builder(identifierSemanticRecord)
                .versions(identifierSemanticRecord.versions().newWith(semanticVersionRecord))
                .build();
    }

    // generate and return identifier semantic UUID
    public static UUID getIdentifierSemanticUUID(String row) {
        Language textLanguage = new Language(row);
        UUID semanticUUID = UuidT5Generator.get(SNOMED_CT_NAMESPACE, IDENTIFIER_PATTERN.toString() + textLanguage.id);
        MockEntity.populateMockData(semanticUUID.toString(), TinkarStarterDataHelper.MockDataType.ENTITYREF);
        return semanticUUID;
    }

    // generate and return identifier semantic pattern UUID
    public static UUID getIdentifierPatternUUID() {
        MockEntity.populateMockData(IDENTIFIER_PATTERN.toString(), TinkarStarterDataHelper.MockDataType.ENTITYREF);
        return IDENTIFIER_PATTERN;
    }

    // generate and return reference component UUID
    public static UUID getReferenceComponentUUID(String row) {
        Language textLanguage = new Language(row);
        UUID referenceComponentUUID = UuidT5Generator.get(SNOMED_CT_NAMESPACE, LANGUAGE_ACCEPTABILITY_PATTERN.toString()+textLanguage.id);
        MockEntity.populateMockData(referenceComponentUUID.toString(), TinkarStarterDataHelper.MockDataType.ENTITYREF);
        return referenceComponentUUID;
    }

}
