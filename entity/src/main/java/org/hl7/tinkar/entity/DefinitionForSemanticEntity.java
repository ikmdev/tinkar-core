package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.component.DefinitionForSemanticChronology;
import org.hl7.tinkar.component.Version;
import org.hl7.tinkar.dto.FieldDataType;

public class DefinitionForSemanticEntity
        extends Entity<DefinitionForSemanticEntityVersion>
        implements DefinitionForSemanticChronology<DefinitionForSemanticEntityVersion> {

    public DefinitionForSemanticEntity() {
    }

    @Override
    protected void finishEntityRead(ByteBuf readBuf) {
        // no additional fields
    }

    @Override
    protected void finishEntityRead(Chronology chronology) {
        // no additional fields
    }

    @Override
    public FieldDataType dataType() {
        return FieldDataType.DEFINITION_FOR_SEMANTIC_CHRONOLOGY;
    }

    @Override
    protected void finishEntityWrite(ByteBuf writeBuf) {
        // no additional fields
    }

    @Override
    protected DefinitionForSemanticEntityVersion makeVersion(ByteBuf readBuf) {
        return null;
    }

    @Override
    protected DefinitionForSemanticEntityVersion makeVersion(Version version) {
        return null;
    }

    public static DefinitionForSemanticEntity make(ByteBuf readBuf) {
        DefinitionForSemanticEntity definitionForSemanticEntity = new DefinitionForSemanticEntity();
        definitionForSemanticEntity.fill(readBuf);
        return definitionForSemanticEntity;
    }

    public static DefinitionForSemanticEntity make(DefinitionForSemanticChronology other) {
        DefinitionForSemanticEntity definitionForSemanticEntity = new DefinitionForSemanticEntity();
        definitionForSemanticEntity.fill(other);
        return definitionForSemanticEntity;
    }
}
