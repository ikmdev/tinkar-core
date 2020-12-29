package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.ConceptVersion;
import org.hl7.tinkar.component.DefinitionForSemanticVersion;
import org.hl7.tinkar.component.FieldDefinition;
import org.hl7.tinkar.dto.FieldDataType;

public class DefinitionForSemanticEntityVersion extends EntityVersion implements DefinitionForSemanticVersion {

    @Override
    public ImmutableList<FieldDefinition> fieldDefinitions() {
        return null;
    }

    @Override
    public Concept referencedComponentPurpose() {
        return null;
    }

    @Override
    public FieldDataType dataType() {
        return null;
    }

    @Override
    protected void finishVersionFill(ByteBuf readBuf) {

    }

    @Override
    protected void writeVersionFields(ByteBuf writeBuf) {

    }


    public static DefinitionForSemanticEntityVersion make(DefinitionForSemanticEntity definitionForSemanticEntity, ByteBuf readBuf) {
        DefinitionForSemanticEntityVersion version = new DefinitionForSemanticEntityVersion();
        version.fill(definitionForSemanticEntity, readBuf);
        return version;
    }

    public static DefinitionForSemanticEntityVersion make(DefinitionForSemanticEntity definitionForSemanticEntity, ConceptVersion versionToCopy) {
        DefinitionForSemanticEntityVersion version = new DefinitionForSemanticEntityVersion();
        version.fill(definitionForSemanticEntity, versionToCopy);
        return version;
    }

}
