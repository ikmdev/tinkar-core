package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.hl7.tinkar.component.ConceptVersion;
import org.hl7.tinkar.component.FieldDataType;

public class ConceptEntityVersion
        extends EntityVersion
        implements ConceptVersion {

    protected ConceptEntityVersion() {}

    @Override
    public FieldDataType dataType() {
        return FieldDataType.CONCEPT_VERSION;
    }

    @Override
    protected void finishVersionFill(ByteBuf readBuf, byte formatVersion) {
        // no additional fields to read.
    }

    @Override
    protected int subclassFieldBytesSize() {
        return 0;
    }

    @Override
    protected void writeVersionFields(ByteBuf byteBuf) {
        // no additional fields to write.
    }

    public static ConceptEntityVersion make(ConceptEntity conceptEntity, ByteBuf readBuf, byte formatVersion) {
        ConceptEntityVersion version = new ConceptEntityVersion();
        version.fill(conceptEntity, readBuf, formatVersion);
        return version;
    }

    public static ConceptEntityVersion make(ConceptEntity conceptEntity, ConceptVersion versionToCopy) {
        ConceptEntityVersion version = new ConceptEntityVersion();
        version.fill(conceptEntity, versionToCopy);
        return version;
    }
}
