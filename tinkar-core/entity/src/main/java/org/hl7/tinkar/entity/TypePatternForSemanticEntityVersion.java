package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.FieldDefinition;
import org.hl7.tinkar.component.TypePatternForSemanticVersion;
import org.hl7.tinkar.entity.internal.Get;
import org.hl7.tinkar.component.FieldDataType;

public class TypePatternForSemanticEntityVersion
        extends EntityVersion
        implements TypePatternForSemanticVersion<FieldDefinitionForEntity> {

    // TODO should we have a referenced component "identity" or "what" field as well?
    protected int referencedComponentPurposeNid;
    protected int referencedComponentMeaningNid;
    protected final MutableList<FieldDefinitionForEntity> fieldDefinitionForEntities = Lists.mutable.empty();


    @Override
    public ImmutableList<FieldDefinitionForEntity> fieldDefinitions() {
        return fieldDefinitionForEntities.toImmutable();
    }

    @Override
    public Concept referencedComponentPurpose() {
        return Get.entityService().getEntityFast(referencedComponentPurposeNid);
    }

    @Override
    public Concept referencedComponentMeaning() {
        return Get.entityService().getEntityFast(referencedComponentMeaningNid);
    }

    @Override
    public FieldDataType dataType() {
        return FieldDataType.PATTERN_FOR_SEMANTIC_VERSION;
    }

    @Override
    protected void finishVersionFill(ByteBuf readBuf, byte formatVersion) {
        this.referencedComponentPurposeNid = readBuf.readInt();
        this.referencedComponentMeaningNid = readBuf.readInt();
        int fieldCount = readBuf.readInt();
        fieldDefinitionForEntities.clear();
        for (int i = 0; i < fieldCount; i++) {
            fieldDefinitionForEntities.add(FieldDefinitionForEntity.make(this, readBuf));
        }
    }

    @Override
    protected void writeVersionFields(ByteBuf writeBuf) {
        writeBuf.writeInt(this.referencedComponentPurposeNid);
        writeBuf.writeInt(this.referencedComponentMeaningNid);
        writeBuf.writeInt(fieldDefinitionForEntities.size());
        for (FieldDefinitionForEntity field: fieldDefinitionForEntities) {
            field.write(writeBuf);
        }
    }


    @Override
    protected int subclassFieldBytesSize() {
        int size = 4; // referenced component purpose nid
        size += 4; // length of component meaning...
        size += 4; // length of field definitions...
        size += (fieldDefinitionForEntities.size() * 12); // 4 * 3 for each field.
        return size;
    }

    public static TypePatternForSemanticEntityVersion make(TypePatternForSemanticEntity definitionForSemanticEntity, ByteBuf readBuf, byte formatVersion) {
        TypePatternForSemanticEntityVersion version = new TypePatternForSemanticEntityVersion();
        version.fill(definitionForSemanticEntity, readBuf, formatVersion);
        return version;
    }

    public static TypePatternForSemanticEntityVersion make(TypePatternForSemanticEntity definitionForSemanticEntity, TypePatternForSemanticVersion<FieldDefinition> definitionForSemanticVersion) {
        TypePatternForSemanticEntityVersion version = new TypePatternForSemanticEntityVersion();
        version.fill(definitionForSemanticEntity, definitionForSemanticVersion);
        version.referencedComponentPurposeNid = Get.entityService().nidForComponent(definitionForSemanticVersion.referencedComponentPurpose());
        version.referencedComponentMeaningNid = Get.entityService().nidForComponent(definitionForSemanticVersion.referencedComponentMeaning());
        version.fieldDefinitionForEntities.clear();
        for (FieldDefinition fieldDefinition: definitionForSemanticVersion.fieldDefinitions()) {
            version.fieldDefinitionForEntities.add(FieldDefinitionForEntity.make(version, fieldDefinition));
        }
        return version;
    }

}
