package org.hl7.tinkar.entity.transfom;

import com.google.protobuf.ByteString;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.util.uuid.UuidUtil;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.protobuf.*;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class EntityToProtocolBuffersTransform implements EntityTransform<Entity, byte[]> {

    Logger LOG = Logger.getLogger(EntityToProtocolBuffersTransform.class.getName());

    public byte[] transform(Entity entity){
        return switch (entity.versionDataType()){
            case CONCEPT_CHRONOLOGY -> makeConceptChronology((ConceptEntity<ConceptEntityVersion>) entity);
            case SEMANTIC_CHRONOLOGY -> makeSemanticChronology((SemanticEntity<SemanticEntityVersion>) entity);
            case PATTERN_CHRONOLOGY -> makePatternChronology((PatternEntity<PatternEntityVersion>) entity);
            default -> throw new IllegalStateException("not expecting" + entity.versionDataType());
        };
    }

    public byte[] makeConceptChronology(ConceptEntity<ConceptEntityVersion> conceptEntity){

        return PBConceptChronology.newBuilder()
                .setPublicId(createPBPublicId(conceptEntity.publicId()))
                .build()
                .toByteArray();
    }

    public byte[] makeSemanticChronology(SemanticEntity<SemanticEntityVersion> conceptEntity){



        return PBSemanticChronology.newBuilder()
                .setPublicId(createPBPublicId(conceptEntity.publicId()))
                .build()
                .toByteArray();
    }

    public byte[] makePatternChronology(PatternEntity<PatternEntityVersion> conceptEntity){

        return PBPatternChronology.newBuilder()
                .setPublicId(createPBPublicId(conceptEntity.publicId()))
                .build()
                .toByteArray();
    }

    public PBPublicId createPBPublicId(PublicId publicId){
        return PBPublicId.newBuilder()
                .addAllId(publicId.asUuidList().stream()
                        .map(UuidUtil::getRawBytes)
                        .map(ByteString::copyFrom)
                        .collect(Collectors.toList()))
                .build();
    }

    public PBStamp createPBStamp(StampEntity stampEntity){
        return PBStamp.newBuilder()
                .setStatus(createPBConcept(stampEntity.state().publicId()))
                .setAuthor(createPBConcept(stampEntity.author().publicId()))
                .setModule(createPBConcept(stampEntity.module().publicId()))
                .setPath(createPBConcept(stampEntity.path().publicId()))
                .build();
    }

    public PBConcept createPBConcept(PublicId publicId){
        return PBConcept.newBuilder()
                .setPublicId(createPBPublicId(publicId))
                .build();
    }

    public List<PBConceptVersion> createVersions(ConceptEntityVersion conceptEntityVersion){

        return null;
    }

    public List<PBSemanticVersion> createVersions(SemanticEntityVersion semanticEntityVersion){

        return null;
    }

    public List<PBPatternVersion> createVersions(PatternEntityVersion patternEntityVersion) {

        return null;
    }
}
