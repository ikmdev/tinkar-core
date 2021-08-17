package org.hl7.tinkar.entity;

import org.hl7.tinkar.component.FieldDataType;

public record FieldRecord<T>(T value, int purposeNid, int meaningNid,
                             FieldDataType fieldDataType,
                             SemanticEntityVersion enclosingSemanticVersion) implements Field<T> {

}
