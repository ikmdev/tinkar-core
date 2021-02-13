package org.hl7.tinkar.entity;


import io.activej.bytebuf.ByteBuf;
import org.hl7.tinkar.component.FieldDefinition;
import org.hl7.tinkar.entity.internal.Get;

public class FieldDefinitionForEntity
    implements FieldDefinition {

    protected PatternForSemanticEntityVersion enclosingVersion;
    protected int dataTypeNid;
    protected int purposeNid;
    protected int identityNid;

    @Override
    public ConceptEntity dataType() {
        return Get.entityService().getEntityFast(dataTypeNid);
    }

    @Override
    public ConceptEntity purpose() {
        return Get.entityService().getEntityFast(purposeNid);
    }

    @Override
    public ConceptEntity meaning() {
        return Get.entityService().getEntityFast(identityNid);
    }

    /**
     * TODO interface for write, fill, etc.
     * @param readBuf
     */
    public void fill(PatternForSemanticEntityVersion enclosingVersion, ByteBuf readBuf) {
        this.enclosingVersion = enclosingVersion;
        dataTypeNid = readBuf.readInt();
        purposeNid = readBuf.readInt();
        identityNid = readBuf.readInt();
    }

    public void fill(PatternForSemanticEntityVersion enclosingVersion, FieldDefinition fieldDefinition) {
        this.enclosingVersion = enclosingVersion;
        dataTypeNid = Get.entityService().nidForPublicId(fieldDefinition.dataType());
        purposeNid = Get.entityService().nidForPublicId(fieldDefinition.purpose());
        identityNid = Get.entityService().nidForPublicId(fieldDefinition.meaning());
    }

    public void write(ByteBuf writeBuf) {
        writeBuf.writeInt(dataTypeNid);
        writeBuf.writeInt(purposeNid);
        writeBuf.writeInt(identityNid);
    }

    public static FieldDefinitionForEntity make(PatternForSemanticEntityVersion enclosingVersion, ByteBuf readBuf) {
        FieldDefinitionForEntity fieldDefinitionForEntity = new FieldDefinitionForEntity();
        fieldDefinitionForEntity.fill(enclosingVersion, readBuf);
        return fieldDefinitionForEntity;
    }

    public static FieldDefinitionForEntity make(PatternForSemanticEntityVersion enclosingVersion, FieldDefinition fieldDefinition) {
        FieldDefinitionForEntity fieldDefinitionForEntity = new FieldDefinitionForEntity();
        fieldDefinitionForEntity.fill(enclosingVersion, fieldDefinition);
        return fieldDefinitionForEntity;
    }

}
