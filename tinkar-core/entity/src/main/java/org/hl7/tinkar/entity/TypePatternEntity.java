package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.hl7.tinkar.component.*;
import org.hl7.tinkar.component.TypePatternForSemanticChronology;

public class TypePatternEntity
        extends Entity<TypePatternForSemanticEntityVersion>
        implements TypePatternForSemanticChronology<TypePatternForSemanticEntityVersion> {

    public TypePatternEntity() {
    }
    @Override
    protected int subclassFieldBytesSize() {
        return 0; // no additional fields
    }

    @Override
    protected void finishEntityRead(ByteBuf readBuf, byte formatVersion) {
        // no additional fields
    }

    @Override
    protected void finishEntityRead(Chronology chronology) {
        // no additional fields
    }

    @Override
    public FieldDataType dataType() {
        return FieldDataType.PATTERN_FOR_SEMANTIC_CHRONOLOGY;
    }

    @Override
    protected void finishEntityWrite(ByteBuf writeBuf) {
        // no additional fields
    }

    @Override
    protected TypePatternForSemanticEntityVersion makeVersion(ByteBuf readBuf, byte formatVersion) {
        return TypePatternForSemanticEntityVersion.make(this, readBuf, formatVersion);
    }

    @Override
    protected TypePatternForSemanticEntityVersion makeVersion(Version version) {
        return TypePatternForSemanticEntityVersion.make(this, (TypePatternForSemanticVersion) version);
    }

    public static TypePatternForSemanticEntity make(ByteBuf readBuf, byte entityFormatVersion) {
        TypePatternForSemanticEntity definitionForSemanticEntity = new TypePatternForSemanticEntity();
        definitionForSemanticEntity.fill(readBuf, entityFormatVersion);
        return definitionForSemanticEntity;
    }

    public static TypePatternForSemanticEntity make(TypePatternForSemanticChronology other) {
        TypePatternForSemanticEntity definitionForSemanticEntity = new TypePatternForSemanticEntity();
        definitionForSemanticEntity.fill(other);
        return definitionForSemanticEntity;
    }
}
