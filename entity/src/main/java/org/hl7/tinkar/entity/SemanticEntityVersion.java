package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.component.Component;
import org.hl7.tinkar.component.DefinitionForSemantic;
import org.hl7.tinkar.component.SemanticVersion;
import org.hl7.tinkar.dto.FieldDataType;
import org.hl7.tinkar.entity.internal.Get;

public class SemanticEntityVersion
        extends EntityVersion
        implements SemanticVersion {


    @Override
    public FieldDataType dataType() {
        return FieldDataType.SEMANTIC_VERSION;
    }

    private SemanticEntity getSemanticEntity() {
        return (SemanticEntity) this.chronology;
    }

    @Override
    protected void finishVersionFill(ByteBuf readBuf) {

    }

    @Override
    protected void writeVersionFields(ByteBuf writeBuf) {

    }

    @Override
    public Component referencedComponent() {
        return Get.entityService().getEntityFast(getSemanticEntity().referencedComponentNid);
    }

    @Override
    public DefinitionForSemantic definitionForSemantic() {
        return Get.entityService().getEntityFast(getSemanticEntity().setNid);
    }

    @Override
    public ImmutableList<Object> fields() {
        return null;
    }

    public static SemanticEntityVersion make(SemanticEntity semanticEntity, ByteBuf readBuf) {
        SemanticEntityVersion version = new SemanticEntityVersion();
        version.fill(semanticEntity, readBuf);
        return version;
    }

    public static SemanticEntityVersion make(SemanticEntity semanticEntity, SemanticVersion versionToCopy) {
        SemanticEntityVersion version = new SemanticEntityVersion();
        version.fill(semanticEntity, versionToCopy);
        return version;
    }
}
