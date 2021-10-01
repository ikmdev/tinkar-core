package org.hl7.tinkar.entity.forremoval;

import io.activej.bytebuf.ByteBuf;
import org.hl7.tinkar.component.*;
import org.hl7.tinkar.entity.PatternEntity;
import org.hl7.tinkar.entity.PatternEntityVersion;

public class PatternEntityClass<T extends PatternEntityVersion>
        extends EntityClass<T>
        implements PatternEntity<T> {

    public PatternEntityClass() {
    }

    public static PatternEntityClass make(ByteBuf readBuf, byte entityFormatVersion) {
        PatternEntityClass patternEntity = new PatternEntityClass();
        patternEntity.fill(readBuf, entityFormatVersion);
        return patternEntity;
    }

    public static PatternEntityClass make(PatternChronology other) {
        PatternEntityClass patternEntity = new PatternEntityClass();
        patternEntity.fill(other);
        return patternEntity;
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
    protected void finishEntityRead(ByteBuf readBuf, byte formatVersion) {
        // no additional fields
    }

    @Override
    protected PatternEntityVersion makeVersion(ByteBuf readBuf, byte formatVersion) {
        return PatternEntityVersionClass.make(this, readBuf, formatVersion);
    }

    @Override
    protected void finishEntityRead(Chronology chronology) {
        // no additional fields
    }

    @Override
    protected PatternEntityVersion makeVersion(Version version) {
        return PatternEntityVersionClass.make(this, (PatternVersion) version);
    }

}
