package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.hl7.tinkar.component.*;
import org.hl7.tinkar.component.PatternChronology;
import org.hl7.tinkar.terms.PatternFacade;

public class PatternEntity
        extends Entity<PatternEntityVersion>
        implements PatternChronology<PatternEntityVersion>, PatternFacade {

    public PatternEntity() {
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
        return FieldDataType.PATTERN_CHRONOLOGY;
    }

    @Override
    protected void finishEntityWrite(ByteBuf writeBuf) {
        // no additional fields
    }

    @Override
    protected PatternEntityVersion makeVersion(ByteBuf readBuf, byte formatVersion) {
        return PatternEntityVersion.make(this, readBuf, formatVersion);
    }

    @Override
    protected PatternEntityVersion makeVersion(Version version) {
        return PatternEntityVersion.make(this, (PatternVersion) version);
    }

    public static PatternEntity make(ByteBuf readBuf, byte entityFormatVersion) {
        PatternEntity patternEntity = new PatternEntity();
        patternEntity.fill(readBuf, entityFormatVersion);
        return patternEntity;
    }

    public static PatternEntity make(PatternChronology other) {
        PatternEntity patternEntity = new PatternEntity();
        patternEntity.fill(other);
        return patternEntity;
    }

}
