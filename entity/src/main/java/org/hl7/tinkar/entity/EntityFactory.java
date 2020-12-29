package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.component.ConceptChronology;
import org.hl7.tinkar.component.DefinitionForSemanticChronology;
import org.hl7.tinkar.component.SemanticChronology;
import org.hl7.tinkar.dto.FieldDataType;
import org.hl7.tinkar.dto.StampDTO;

public class EntityFactory {

    public static Entity make(Chronology chronology) {
        if (chronology instanceof ConceptChronology conceptChronology) {
            return ConceptEntity.make(conceptChronology);
        } else if (chronology instanceof SemanticChronology semanticChronology) {
            return SemanticEntity.make(semanticChronology);
        } else if (chronology instanceof DefinitionForSemanticChronology definitionForSemanticChronology) {
            return DefinitionForSemanticEntity.make(definitionForSemanticChronology);
        }
        throw new UnsupportedOperationException("Can't convert: " + chronology);
    }

    public static <T extends Entity<V>, V extends EntityVersion> T make(byte[] data) {
        return make(ByteBuf.wrapForReading(data));
    }

    public static <T extends Entity<V>, V extends EntityVersion> T make(ByteBuf readBuf) {

        FieldDataType fieldDataType = FieldDataType.fromToken(readBuf.readByte());
        switch (fieldDataType) {
            case CONCEPT_CHRONOLOGY:
                return (T) ConceptEntity.make(readBuf);

            case SEMANTIC_CHRONOLOGY:
                return (T) SemanticEntity.make(readBuf);

            case DEFINITION_FOR_SEMANTIC_CHRONOLOGY:
                return (T) DefinitionForSemanticEntity.make(readBuf);

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
