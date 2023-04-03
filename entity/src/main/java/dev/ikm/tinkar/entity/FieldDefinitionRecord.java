package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.Validator;
import dev.ikm.tinkar.component.FieldDefinition;
import io.soabase.recordbuilder.core.RecordBuilder;

@RecordBuilder
public record FieldDefinitionRecord(int dataTypeNid, int purposeNid, int meaningNid, int patternVersionStampNid,
                                    int patternNid, int indexInPattern)
        implements FieldDefinitionForEntity, FieldDefinitionRecordBuilder.With {

    public FieldDefinitionRecord {
        Validator.notZero(dataTypeNid);
        Validator.notZero(purposeNid);
        Validator.notZero(meaningNid);
        Validator.notZero(patternVersionStampNid);
        Validator.notZero(patternNid);
    }
    public FieldDefinitionRecord(FieldDefinition fieldDefinition, PatternEntityVersion patternVersion, int indexInPattern) {
        this(Entity.nid(fieldDefinition.dataType()),
                Entity.nid(fieldDefinition.purpose()),
                Entity.nid(fieldDefinition.meaning()),
                patternVersion.stampNid(),
                patternVersion.nid(),
                indexInPattern
        );
    }

    public FieldDefinitionRecord(FieldDefinition fieldDefinition, int patternVersionStampNid, int patternNid, int indexInPattern) {
        this(Entity.nid(fieldDefinition.dataType()),
                Entity.nid(fieldDefinition.purpose()),
                Entity.nid(fieldDefinition.meaning()),
                patternVersionStampNid,
                patternNid,
                indexInPattern
        );
    }

    @Override
    public String toString() {
        return "FieldDefinitionRecord{" +
                "dataType: " + PrimitiveData.text(dataTypeNid) +
                ", purpose: " + PrimitiveData.text(purposeNid) +
                ", meaning: " + PrimitiveData.text(meaningNid) +
                ", for pattern version: " + Entity.getStamp(patternVersionStampNid).lastVersion().describe() +
                ", for pattern: " + PrimitiveData.text(patternNid) + " [" + indexInPattern +
                "]}";
    }
}
