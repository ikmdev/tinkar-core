package org.hl7.tinkar.entity;


import io.activej.bytebuf.ByteBuf;
import org.hl7.tinkar.component.FieldDefinition;
import org.hl7.tinkar.entity.internal.Get;

public class FieldDefinitionForEntity
    implements FieldDefinition {

    protected TypePatternEntityVersion enclosingVersion;
    protected int dataTypeNid;
    protected int purposeNid;
    protected int meaningNid;

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
        return Get.entityService().getEntityFast(meaningNid);
    }

    /**
     * TODO interface for write, fill, etc.
     * @param readBuf
     */
    public void fill(TypePatternEntityVersion enclosingVersion, ByteBuf readBuf) {
        this.enclosingVersion = enclosingVersion;
        dataTypeNid = readBuf.readInt();
        purposeNid = readBuf.readInt();
        meaningNid = readBuf.readInt();
    }

    public void fill(TypePatternEntityVersion enclosingVersion, FieldDefinition fieldDefinition) {
        this.enclosingVersion = enclosingVersion;
        dataTypeNid = Get.entityService().nidForComponent(fieldDefinition.dataType());
        purposeNid = Get.entityService().nidForComponent(fieldDefinition.purpose());
        meaningNid = Get.entityService().nidForComponent(fieldDefinition.meaning());
    }

    public void write(ByteBuf writeBuf) {
        writeBuf.writeInt(dataTypeNid);
        writeBuf.writeInt(purposeNid);
        writeBuf.writeInt(meaningNid);
    }

    public static FieldDefinitionForEntity make(TypePatternEntityVersion enclosingVersion, ByteBuf readBuf) {
        FieldDefinitionForEntity fieldDefinitionForEntity = new FieldDefinitionForEntity();
        fieldDefinitionForEntity.fill(enclosingVersion, readBuf);
        return fieldDefinitionForEntity;
    }

    public static FieldDefinitionForEntity make(TypePatternEntityVersion enclosingVersion, FieldDefinition fieldDefinition) {
        FieldDefinitionForEntity fieldDefinitionForEntity = new FieldDefinitionForEntity();
        fieldDefinitionForEntity.fill(enclosingVersion, fieldDefinition);
        return fieldDefinitionForEntity;
    }

    @Override
    public String toString() {
        return "FieldDef{t: " +
                DefaultDescriptionText.get(dataTypeNid) + " p: " +
                DefaultDescriptionText.get(purposeNid) + " m: " +
                DefaultDescriptionText.get(meaningNid) +
                '}';
    }
}
