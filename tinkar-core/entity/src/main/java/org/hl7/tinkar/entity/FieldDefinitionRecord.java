package org.hl7.tinkar.entity;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.hl7.tinkar.component.FieldDefinition;

@RecordBuilder
public record FieldDefinitionRecord(int dataTypeNid, int purposeNid, int meaningNid, int patternVersionStampNid)
        implements FieldDefinitionForEntity, FieldDefinitionRecordBuilder.With {
    public FieldDefinitionRecord(FieldDefinition fieldDefinition, PatternEntityVersion patternVersion) {
        this(EntityService.get().nidForComponent(fieldDefinition.dataType()),
                EntityService.get().nidForComponent(fieldDefinition.purpose()),
                EntityService.get().nidForComponent(fieldDefinition.meaning()),
                patternVersion.stampNid()
        );
    }

    public FieldDefinitionRecord(FieldDefinition fieldDefinition, int patternVersionStampNid) {
        this(EntityService.get().nidForComponent(fieldDefinition.dataType()),
                EntityService.get().nidForComponent(fieldDefinition.purpose()),
                EntityService.get().nidForComponent(fieldDefinition.meaning()),
                patternVersionStampNid
        );
    }
}
