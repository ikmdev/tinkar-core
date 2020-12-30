package org.hl7.tinkar.entity;


import io.activej.bytebuf.ByteBuf;
import org.hl7.tinkar.component.FieldDefinition;
import org.hl7.tinkar.entity.internal.Get;

public class FieldDefinitionForEntity
    implements FieldDefinition {

    protected DefinitionForSemanticEntityVersion enclosingVersion;
    protected int dataTypeNid;
    protected int purposeNid;
    protected int identityNid;

    @Override
    public ConceptEntity getDataType() {
        return Get.entityService().getEntityFast(dataTypeNid);
    }

    @Override
    public ConceptEntity getPurpose() {
        return Get.entityService().getEntityFast(purposeNid);
    }

    @Override
    public ConceptEntity getIdentity() {
        return Get.entityService().getEntityFast(identityNid);
    }

    /**
     * TODO interface for write, fill, etc.
     * @param readBuf
     */
    public void fill(DefinitionForSemanticEntityVersion enclosingVersion, ByteBuf readBuf) {
        this.enclosingVersion = enclosingVersion;
        dataTypeNid = readBuf.readInt();
        purposeNid = readBuf.readInt();
        identityNid = readBuf.readInt();
    }

    public void fill(DefinitionForSemanticEntityVersion enclosingVersion, FieldDefinition fieldDefinition) {
        this.enclosingVersion = enclosingVersion;
        dataTypeNid = Get.entityService().nidForUuids(fieldDefinition.getDataType());
        purposeNid = Get.entityService().nidForUuids(fieldDefinition.getPurpose());
        identityNid = Get.entityService().nidForUuids(fieldDefinition.getIdentity());
    }

    public void write(ByteBuf writeBuf) {
        writeBuf.writeInt(dataTypeNid);
        writeBuf.writeInt(purposeNid);
        writeBuf.writeInt(identityNid);
    }

    public static FieldDefinitionForEntity make(DefinitionForSemanticEntityVersion enclosingVersion, ByteBuf readBuf) {
        FieldDefinitionForEntity fieldDefinitionForEntity = new FieldDefinitionForEntity();
        fieldDefinitionForEntity.fill(enclosingVersion, readBuf);
        return fieldDefinitionForEntity;
    }

    public static FieldDefinitionForEntity make(DefinitionForSemanticEntityVersion enclosingVersion, FieldDefinition fieldDefinition) {
        FieldDefinitionForEntity fieldDefinitionForEntity = new FieldDefinitionForEntity();
        fieldDefinitionForEntity.fill(enclosingVersion, fieldDefinition);
        return fieldDefinitionForEntity;
    }

}
