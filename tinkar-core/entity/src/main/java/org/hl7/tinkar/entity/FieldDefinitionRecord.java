package org.hl7.tinkar.entity;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.component.FieldDefinition;

@RecordBuilder
public record FieldDefinitionRecord(int dataTypeNid, int purposeNid, int meaningNid, int patternVersionStampNid,
                                    int patternNid)
        implements FieldDefinitionForEntity, FieldDefinitionRecordBuilder.With {
    public FieldDefinitionRecord(FieldDefinition fieldDefinition, PatternEntityVersion patternVersion) {
        this(EntityService.get().nidForComponent(fieldDefinition.dataType()),
                EntityService.get().nidForComponent(fieldDefinition.purpose()),
                EntityService.get().nidForComponent(fieldDefinition.meaning()),
                patternVersion.stampNid(),
                patternVersion.nid()
        );
    }

    public FieldDefinitionRecord(FieldDefinition fieldDefinition, int patternVersionStampNid, int patternNid) {
        this(EntityService.get().nidForComponent(fieldDefinition.dataType()),
                EntityService.get().nidForComponent(fieldDefinition.purpose()),
                EntityService.get().nidForComponent(fieldDefinition.meaning()),
                patternVersionStampNid,
                patternNid
        );
    }

    @Override
    public String toString() {
        return "FieldDefinitionRecord{" +
                "dataType: " + PrimitiveData.text(dataTypeNid) +
                ", purpose: " + PrimitiveData.text(purposeNid) +
                ", meaning: " + PrimitiveData.text(meaningNid) +
                ", for pattern version: " + Entity.getStamp(patternVersionStampNid).lastVersion().describe() +
                ", for pattern: " + PrimitiveData.text(patternNid) +
                '}';
    }
}
