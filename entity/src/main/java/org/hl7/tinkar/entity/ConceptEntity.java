package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.hl7.tinkar.component.*;
import org.hl7.tinkar.dto.FieldDataType;

public final class ConceptEntity
        extends Entity<ConceptEntityVersion>
        implements ConceptChronology<ConceptEntityVersion> {

    private ConceptEntity() {
        super();
    }

    @Override
    protected ConceptEntityVersion makeVersion(ByteBuf readBuf) {
        return ConceptEntityVersion.make(this, readBuf);
    }

    @Override
    protected ConceptEntityVersion makeVersion(Version version) {
        return ConceptEntityVersion.make(this, (ConceptVersion) version);
    }

    @Override
    public FieldDataType dataType() {
        return FieldDataType.CONCEPT_CHRONOLOGY;
    }

    @Override
    protected void finishEntityWrite(ByteBuf readBuf) {
        // No additional fields to write.
    }

    @Override
    protected void finishEntityRead(ByteBuf readBuf) {
        // no extra fields to read.
    }

    @Override
    protected void finishEntityRead(Chronology chronology) {
        // no extra fields to read.
    }

    public static ConceptEntity make(ByteBuf readBuf) {
        ConceptEntity conceptEntity = new ConceptEntity();
        conceptEntity.fill(readBuf);
        return conceptEntity;
    }

    public static ConceptEntity make(ConceptChronology other) {
        ConceptEntity conceptEntity = new ConceptEntity();
        conceptEntity.fill(other);
        return conceptEntity;
    }

}
