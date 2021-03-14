package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.component.ConceptChronology;
import org.hl7.tinkar.component.PatternForSemanticChronology;
import org.hl7.tinkar.component.SemanticChronology;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.lombok.dto.StampDTO;

public class EntityFactory {

    public static Entity make(Chronology chronology) {
        if (chronology instanceof ConceptChronology) {
            ConceptChronology conceptChronology = (ConceptChronology) chronology;
            ConceptEntity conceptEntity = ConceptEntity.make(conceptChronology);
            return conceptEntity;
        } else if (chronology instanceof SemanticChronology) {
            SemanticChronology semanticChronology = (SemanticChronology) chronology;
            SemanticEntity semanticEntity =  SemanticEntity.make(semanticChronology);
           return semanticEntity;
        } else if (chronology instanceof PatternForSemanticChronology) {
            PatternForSemanticChronology definitionForSemanticChronology = (PatternForSemanticChronology) chronology;
            PatternForSemanticEntity patternForSemanticEntity = PatternForSemanticEntity.make(definitionForSemanticChronology);
            return patternForSemanticEntity;

        }
        throw new UnsupportedOperationException("Can't convert: " + chronology);
    }

    public static <T extends Entity<V>, V extends EntityVersion> T make(byte[] data) {
        // TODO change to use DecoderInput instead of ByteBuf directly.
        // TODO remove the parts where it computes size.
        ByteBuf buf = ByteBuf.wrapForReading(data);
        // bytes starts with number of arrays (int = 4 bytes), then size of first array (int = 4 bytes), then type token, -1 since index starts at 0...
        int numberOfArrays = buf.readInt();
        int sizeOfFirstArray = buf.readInt();
        byte formatVersion = buf.readByte();
        return make(buf, formatVersion);
    }

    public static <T extends Entity<V>, V extends EntityVersion> T make(ByteBuf readBuf, byte entityFormatVersion) {

        FieldDataType fieldDataType = FieldDataType.fromToken(readBuf.readByte());
        switch (fieldDataType) {
            case CONCEPT_CHRONOLOGY:
                return (T) ConceptEntity.make(readBuf, entityFormatVersion);

            case SEMANTIC_CHRONOLOGY:
                return (T) SemanticEntity.make(readBuf, entityFormatVersion);

            case PATTERN_FOR_SEMANTIC_CHRONOLOGY:
                return (T) PatternForSemanticEntity.make(readBuf, entityFormatVersion);

            default:
                throw new UnsupportedOperationException("Can't handle fieldDataType: " + fieldDataType);

        }
    }

    public static StampEntity makeStamp(byte[] data) {
        return makeStamp(ByteBuf.wrapForReading(data));
    }

    public static StampEntity makeStamp(ByteBuf readBuf) {

        FieldDataType fieldDataType = FieldDataType.fromToken(readBuf.readByte());
        switch (fieldDataType) {
            case STAMP:
                return StampEntity.make(readBuf);

            default:
                throw new UnsupportedOperationException("Can't handle fieldDataType: " + fieldDataType);

        }
    }
    public static StampEntity makeStamp(StampDTO stampDTO) {
        return StampEntity.make(stampDTO);
    }
}
