package org.hl7.tinkar.entity;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.hl7.tinkar.component.FieldDefinition;

/**
 * TODO, create an entity data type that combines concept and FieldDataType like the Status enum?
 *
 * @param <T>
 */
@RecordBuilder
public record FieldRecord<T>(T value, int semanticVersionStampNid,
                             FieldDefinitionRecord fieldDefinition) implements FieldDefinition, Field<T>, FieldRecordBuilder.With {

    @Override
    public int meaningNid() {
        return fieldDefinition.meaningNid();
    }

    @Override
    public int purposeNid() {
        return fieldDefinition.purposeNid();
    }

    @Override
    public int dataTypeNid() {
        return fieldDefinition.dataTypeNid();
    }


    @Override
    public String toString() {
        return "FieldRecord{" +
                "value: " + value +
                ", for semantic version: " + Entity.getStamp(semanticVersionStampNid).lastVersion().describe() +
                ", with " + fieldDefinition +
                '}';
    }

}
