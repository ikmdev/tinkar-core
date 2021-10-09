package org.hl7.tinkar.entity.load;

import com.google.protobuf.ByteString;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.common.id.PublicIds;
import org.hl7.tinkar.common.util.uuid.UuidUtil;
import org.hl7.tinkar.component.*;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.protobuf.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public final class ProtocolBuffersEntityFactory {

    protected static final Logger LOG = Logger.getLogger(ProtocolBuffersEntityFactory.class.getName());

    public static Chronology<? extends Version> make(PBTinkarMsg pbTinkarMsg){

        try {
            if(!pbTinkarMsg.isInitialized())
                throw new IllegalStateException(pbTinkarMsg.getInitializationErrorString());

            switch (pbTinkarMsg.getValueCase()) {
                case CONCEPTCHRONOLOGYVALUE -> make(pbTinkarMsg.getConceptChronologyValue());
                case SEMANTICCHRONOLOGYVALUE -> make(pbTinkarMsg.getSemanticChronologyValue());
                case PATTERNCHRONOLOGYVALUE -> make(pbTinkarMsg.getPatternChronologyValue());
                default -> throw new IllegalStateException("not expecting " + pbTinkarMsg.getValueCase());
            }
        }catch (IllegalStateException e){
            LOG.warning(e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    private static ConceptChronology<ConceptEntityVersion> make(PBConceptChronology pbConceptChronology){
        ConceptEntity<ConceptEntityVersion> conceptEntity = createConceptEntity(pbConceptChronology.getPublicId());
        MutableList<ConceptEntityVersion> conceptEntityVersions;
        conceptEntityVersions = Lists.mutable.ofInitialCapacity(pbConceptChronology.getConceptVersionsCount());
        createVersionEntities(pbConceptChronology, conceptEntity, conceptEntityVersions);
        return ConceptRecordBuilder.builder((ConceptRecord) conceptEntity)
                .versionRecords(conceptEntityVersions).build();
    }

    private static SemanticChronology<SemanticEntityVersion> make(PBSemanticChronology pbSemanticChronology){
        SemanticEntity semanticEntity = createSemanticEntity(pbSemanticChronology);

        MutableList<SemanticEntityVersion> semanticEntityVersions = Lists.mutable
                .ofInitialCapacity(pbSemanticChronology.getVersionsCount());


        pbSemanticChronology.getVersionsList();


        return null;

    }

    private static PatternChronology<PatternEntityVersion> make(PBPatternChronology pbPatternChronology){
        return null;
    }

    private static UUID[] processPBPublicID(PBPublicId publicId){
        List<ByteString> byteStringList = publicId.getIdList();
        UUID[] uuids = new UUID[byteStringList.size()];

        for(int i = 0; i < byteStringList.size();i++){
            uuids[i] = UUID.nameUUIDFromBytes(byteStringList.get(i).toByteArray());
        }

        return uuids;
    }

    private static ConceptEntity<ConceptEntityVersion> createConceptEntity(PBConcept pbConcept){
        return createConceptEntity(pbConcept.getPublicId());
    }

    private static ConceptEntity<ConceptEntityVersion> createConceptEntity(PBPublicId publicId) throws IllegalStateException{
        UUID[] uuids = processPBPublicID(publicId);
        if(uuids.length > 0) {
            int conceptNid = EntityService.get().nidForUuids(uuids);
            if (uuids.length > 1) {
                return ConceptRecordBuilder.builder()
                        .leastSignificantBits(uuids[0].getLeastSignificantBits())
                        .mostSignificantBits(uuids[0].getMostSignificantBits())
                        .additionalUuidLongs(UuidUtil.asArray(Arrays.copyOfRange(uuids, 1, uuids.length)))
                        .nid(conceptNid)
                        .build();
            } else {
                return ConceptRecordBuilder.builder()
                        .leastSignificantBits(uuids[0].getLeastSignificantBits())
                        .mostSignificantBits(uuids[0].getMostSignificantBits())
                        .nid(conceptNid)
                        .build();
            }
        }else {
            throw new IllegalStateException("missing primordial UUID");
        }
    }

    private static SemanticEntity createSemanticEntity(PBSemanticChronology pbSemanticChronology){
        UUID[] uuids = processPBPublicID(pbSemanticChronology.getPublicId());
        int patternNid = EntityService.get().nidForUuids(processPBPublicID(pbSemanticChronology.getPublicId()));
        int referencedComponentNid = EntityService.get().nidForUuids(processPBPublicID(pbSemanticChronology.getPublicId()));
        if(uuids.length > 0) {
            int semanticNid = EntityService.get().nidForUuids(uuids);
            if (uuids.length > 1) {
                return SemanticRecordBuilder.builder()
                        .leastSignificantBits(uuids[0].getLeastSignificantBits())
                        .mostSignificantBits(uuids[0].getMostSignificantBits())
                        .additionalUuidLongs(UuidUtil.asArray(Arrays.copyOfRange(uuids, 1, uuids.length)))
                        .nid(semanticNid)
                        .patternNid(patternNid)
                        .referencedComponentNid(referencedComponentNid)
                        .build();
            } else {
                return SemanticRecordBuilder.builder()
                        .leastSignificantBits(uuids[0].getLeastSignificantBits())
                        .mostSignificantBits(uuids[0].getMostSignificantBits())
                        .nid(semanticNid)
                        .patternNid(patternNid)
                        .referencedComponentNid(referencedComponentNid)
                        .build();
            }
        }else {
            throw new IllegalStateException("missing primordial UUID");
        }
    }



    private static StampEntity<StampEntityVersion> createStampEntity(PBStamp pbStamp){
        UUID[] uuids = processPBPublicID(pbStamp.getPublicId());
        if(uuids.length > 0) {
            int stampNid = EntityService.get().nidForUuids(uuids);
            if (uuids.length > 1) {
                return StampRecordBuilder.builder()
                        .leastSignificantBits(uuids[0].getLeastSignificantBits())
                        .mostSignificantBits(uuids[0].getMostSignificantBits())
                        .nid(stampNid)
                        .additionalUuidLongs(UuidUtil.asArray(Arrays.copyOfRange(uuids, 1, uuids.length)))
                        .build();
            } else {
                return StampRecordBuilder.builder()
                        .leastSignificantBits(uuids[0].getLeastSignificantBits())
                        .mostSignificantBits(uuids[0].getMostSignificantBits())
                        .nid(stampNid)
                        .build();
            }
        }else {
            throw new IllegalStateException("missing primordial UUID");
        }
    }

    private static MutableList<ConceptEntityVersion> createVersionEntities(PBConceptChronology pbConceptChronology,
                                                                           ConceptEntity<ConceptEntityVersion> conceptEntity,
                                                                           MutableList<ConceptEntityVersion> versions) {
        for (PBConceptVersion pbConceptVersion : pbConceptChronology.getConceptVersionsList()) {
            versions.add(createConceptVersionEntity(pbConceptVersion, conceptEntity));
        }
        return versions;
    }

    private static MutableList<SemanticEntityVersion> createVersionEntities(PBSemanticChronology pbSemanticChronology,
                                                                            SemanticEntity<SemanticEntityVersion> semanticEntity,
                                                                            MutableList<SemanticEntityVersion> versions) {
        for (PBSemanticVersion pbSemanticVersion : pbSemanticChronology.getVersionsList()) {
            versions.add(createSemanticVersionEntity(pbSemanticVersion, semanticEntity));
        }
        return versions;
    }

    private static MutableList<PatternEntityVersion> createVersionEntities(PBPatternChronology pbPatternChronology,
                                                                           PatternEntity<PatternEntityVersion> patternEntity,
                                                                           MutableList<PatternEntityVersion> versions) {
        for (PBPatternVersion pbPatternVersion : pbPatternChronology.getVersionsList()) {
            versions.add(createPatternVersionEntity(pbPatternVersion, patternEntity));
        }
        return versions;
    }

    private static ConceptEntityVersion createConceptVersionEntity(PBConceptVersion pbConceptVersion,
                                                                   ConceptEntity<ConceptEntityVersion> conceptRecord){
        StampEntityVersion stampEntityVersion = createStampEntityVersion(pbConceptVersion.getStamp());
        return ConceptVersionRecordBuilder.builder()
                .chronology(conceptRecord)
                .stampNid(stampEntityVersion.stampNid())
                .build();
    }

    private static SemanticEntityVersion createSemanticVersionEntity(PBSemanticVersion pbSemanticVersion,
                                                                     SemanticEntity<SemanticEntityVersion> semanticEntity){
        StampEntityVersion stampEntityVersion = createStampEntityVersion(pbSemanticVersion.getStamp());
        List<PBField> pbFields = pbSemanticVersion.getFieldValuesList();
        ArrayList<Object> fields = new ArrayList<>();

        for(PBField pbField : pbFields){
            switch (pbField.getValueCase()){
                case BYTESVALUE -> fields.add(pbField.getBytesValue());
                case INTVALUE -> fields.add(pbField.getIntValue());
                case FLOATVALUE -> fields.add(pbField.getFloatValue());
                case BOOLVALUE -> fields.add(pbField.getBoolValue());
                case STRINGVALUE -> fields.add(pbField.getStringValue());
                case PUBLICIDVALUE -> PublicIds.of(processPBPublicID(pbField.getPublicIdValue()));
                case TIMEVALUE -> fields.add(pbField.getTimeValue().getSeconds()); //TODO(aks8m): Is this right?
                case CONCEPTVALUE -> fields.add(createConceptEntity(pbField.getConceptValue()));
                case STAMPVALUE -> fields.add(createStampEntityVersion(pbField.getStampValue())); //TODO(aks8m): Is this right?
                case DIGRAPHVALUE -> {
                    PBDiGraph pbDiGraph = pbField.getDiGraphValue();

                }
                case GRAPHVALUE -> {}
                case default -> throw new IllegalStateException("unknown field type found");
            }
        }

        return SemanticVersionRecordBuilder.builder()
                .chronology(semanticEntity)
                .stampNid(stampEntityVersion.stampNid())
                .fields(null)
                .build();

    }

    private static PatternEntityVersion createPatternVersionEntity(PBPatternVersion pbPatternVersion,
                                                                   PatternEntity<PatternEntityVersion> patternEntity){
        StampEntityVersion stampEntityVersion = createStampEntityVersion(pbPatternVersion.getStamp());
        int semanticPurposeNid = EntityService.get()
                .nidForUuids(processPBPublicID(pbPatternVersion.getReferencedComponentPurpose()));
        int semanticMeaningNid = EntityService.get()
                .nidForUuids(processPBPublicID(pbPatternVersion.getReferencedComponentMeaning()));
        return PatternVersionRecordBuilder.builder()
                .chronology(patternEntity)
                .stampNid(stampEntityVersion.stampNid())
                .semanticPurposeNid(semanticPurposeNid)
                .semanticMeaningNid(semanticMeaningNid)
                .build();
    }

    private static StampEntityVersion createStampEntityVersion(PBStamp pbStamp){
        StampEntity<StampEntityVersion> stampEntity = createStampEntity(pbStamp);
        int stateNid = EntityService.get().nidForUuids(processPBPublicID(pbStamp.getStatus().getPublicId()));
        long time = pbStamp.getTime().getSeconds(); //TODO(aks8m): Is this right?
        int authorNid = EntityService.get().nidForUuids(processPBPublicID(pbStamp.getAuthor().getPublicId()));
        int moduleNid = EntityService.get().nidForUuids(processPBPublicID(pbStamp.getModule().getPublicId()));
        int pathNid = EntityService.get().nidForUuids(processPBPublicID(pbStamp.getPath().getPublicId()));
        return StampVersionRecordBuilder.builder()
                .stateNid(stateNid)
                .time(time)
                .authorNid(authorNid)
                .moduleNid(moduleNid)
                .pathNid(pathNid)
                .chronology(stampEntity)
                .build();
    }
}
