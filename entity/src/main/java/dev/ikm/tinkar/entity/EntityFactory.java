package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.component.*;
import dev.ikm.tinkar.dto.StampDTO;
import io.activej.bytebuf.ByteBuf;

public class EntityFactory {

    public static Entity make(Chronology chronology) {
        if (chronology instanceof ConceptChronology conceptChronology) {
            return EntityRecordFactory.make(conceptChronology);
        } else if (chronology instanceof SemanticChronology semanticChronology) {
            return EntityRecordFactory.make(semanticChronology);
        } else if (chronology instanceof PatternChronology patternChronology) {
            return EntityRecordFactory.make(patternChronology);
        } else if (chronology instanceof Stamp stamp) {
            return EntityRecordFactory.make(stamp);
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
                return (T) EntityRecordFactory.make(readBuf, entityFormatVersion, fieldDataType);

            case SEMANTIC_CHRONOLOGY:
                return (T) EntityRecordFactory.make(readBuf, entityFormatVersion, fieldDataType);

            case PATTERN_CHRONOLOGY:
                return (T) EntityRecordFactory.make(readBuf, entityFormatVersion, fieldDataType);

            case STAMP:
                return (T) EntityRecordFactory.make(readBuf, entityFormatVersion, fieldDataType);

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
                //return StampEntity.make(readBuf, entityFormatVersion);

            default:
                throw new UnsupportedOperationException("Can't handle fieldDataType: " + fieldDataType);

        }
    }

    public static StampEntity makeStamp(StampDTO stampDTO) {
        throw new UnsupportedOperationException("Can't makeStamp: " + stampDTO);
        //return StampEntity.make(stampDTO);
    }
}
