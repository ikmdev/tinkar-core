package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.component.PatternForSemanticChronology;
import org.hl7.tinkar.component.PatternForSemanticVersion;
import org.hl7.tinkar.component.Version;
import org.hl7.tinkar.lombok.dto.FieldDataType;

public class PatternForSemanticEntity
        extends Entity<PatternForSemanticEntityVersion>
        implements PatternForSemanticChronology<PatternForSemanticEntityVersion> {

    public PatternForSemanticEntity() {
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
    protected PatternForSemanticEntityVersion makeVersion(ByteBuf readBuf, byte formatVersion) {
        return PatternForSemanticEntityVersion.make(this, readBuf, formatVersion);
    }

    @Override
    protected PatternForSemanticEntityVersion makeVersion(Version version) {
        return PatternForSemanticEntityVersion.make(this, (PatternForSemanticVersion) version);
    }

    public static PatternForSemanticEntity make(ByteBuf readBuf, byte entityFormatVersion) {
        PatternForSemanticEntity definitionForSemanticEntity = new PatternForSemanticEntity();
        definitionForSemanticEntity.fill(readBuf, entityFormatVersion);
        return definitionForSemanticEntity;
    }

    public static PatternForSemanticEntity make(PatternForSemanticChronology other) {
        PatternForSemanticEntity definitionForSemanticEntity = new PatternForSemanticEntity();
        definitionForSemanticEntity.fill(other);
        return definitionForSemanticEntity;
    }
}
