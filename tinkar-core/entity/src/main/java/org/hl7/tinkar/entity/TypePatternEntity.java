package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.hl7.tinkar.component.*;
import org.hl7.tinkar.component.TypePatternChronology;

import java.util.Arrays;
import java.util.Optional;

public class TypePatternEntity
        extends Entity<TypePatternEntityVersion>
        implements TypePatternChronology<TypePatternEntityVersion>, TypePatternFacade {

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
        return FieldDataType.TYPE_PATTERN_CHRONOLOGY;
    }

    @Override
    protected void finishEntityWrite(ByteBuf writeBuf) {
        // no additional fields
    }

    @Override
    protected TypePatternEntityVersion makeVersion(ByteBuf readBuf, byte formatVersion) {
        return TypePatternEntityVersion.make(this, readBuf, formatVersion);
    }

    @Override
    protected TypePatternEntityVersion makeVersion(Version version) {
        return TypePatternEntityVersion.make(this, (TypePatternVersion) version);
    }

    public static TypePatternEntity make(ByteBuf readBuf, byte entityFormatVersion) {
        TypePatternEntity definitionEntity = new TypePatternEntity();
        definitionEntity.fill(readBuf, entityFormatVersion);
        return definitionEntity;
    }

    public static TypePatternEntity make(TypePatternChronology other) {
        TypePatternEntity definitionEntity = new TypePatternEntity();
        definitionEntity.fill(other);
        return definitionEntity;
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TypePatternEntity{");
        Optional<String> stringOptional = DefaultDescriptionText.getOptional(this.nid);
        if (stringOptional.isPresent()) {
            sb.append(stringOptional.get());
            sb.append(' ');
        }
        sb.append("<");
        sb.append(nid);
        sb.append("> ");
        sb.append(Arrays.toString(publicId().asUuidArray()));
        sb.append(", v: ");
        sb.append(versions);
        sb.append('}');
        return sb.toString();
    }

}
