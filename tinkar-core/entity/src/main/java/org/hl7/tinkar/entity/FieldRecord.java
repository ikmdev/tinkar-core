package org.hl7.tinkar.entity;

public record FieldRecord<T>(T value, int purposeNid, int meaningNid) implements Field<T> {

}
