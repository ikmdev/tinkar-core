package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.hl7.tinkar.component.*;
import org.hl7.tinkar.component.PatternChronology;
import org.hl7.tinkar.dto.StampDTO;

public class EntityFactory {

    public static Entity make(Chronology chronology) {
        if (chronology instanceof ConceptChronology conceptChronology) {
            return ConceptEntity.make(conceptChronology);
        } else if (chronology instanceof SemanticChronology semanticChronology) {
            return SemanticEntity.make(semanticChronology);
        } else if (chronology instanceof PatternChronology patternChronology) {
            return PatternEntity.make(patternChronology);
        } else if (chronology instanceof Stamp stamp) {
            return StampEntity.make(stamp);
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

            case PATTERN_CHRONOLOGY:
                return (T) PatternEntity.make(readBuf, entityFormatVersion);

            case STAMP:
                return (T) StampEntity.make(readBuf, entityFormatVersion);

            default:
                throw new UnsupportedOperationException("Can't handle fieldDataType: " + fieldDataType);

        }
    }

    public static StampEntity makeStamp(byte[] data) {
        return makeStamp(ByteBuf.wrapForReading(data));
    }

    public static StampEntity makeStamp(ByteBuf readBuf) {
        int numberOfArrays = readBuf.readInt();
        int sizeOfFirstArray = readBuf.readInt();
        byte entityFormatVersion = readBuf.readByte();
        FieldDataType fieldDataType = FieldDataType.fromToken(readBuf.readByte());
        switch (fieldDataType) {
            case STAMP:
                return StampEntity.make(readBuf, entityFormatVersion);

            default:
                throw new UnsupportedOperationException("Can't handle fieldDataType: " + fieldDataType);

        }
    }
    public static StampEntity makeStamp(StampDTO stampDTO) {
        return StampEntity.make(stampDTO);
    }
}
