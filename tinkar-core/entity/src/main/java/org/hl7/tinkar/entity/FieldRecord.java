package org.hl7.tinkar.entity;

import io.soabase.recordbuilder.core.RecordBuilder;

import java.util.Optional;

/**
 * TODO, create an entity data type that combines concept and FieldDataType like the Status enum?
 *
 * @param <T>
 */
@RecordBuilder
public record FieldRecord<T>(T value, String narrativeValue, int dataTypeNid, int purposeNid, int meaningNid,
                             SemanticEntityVersion enclosingSemanticVersion) implements Field<T>, FieldRecordBuilder.With {
    @Override
    public Optional<String> narrativeOptional() {
        return Optional.ofNullable(narrativeValue);
    }
}
