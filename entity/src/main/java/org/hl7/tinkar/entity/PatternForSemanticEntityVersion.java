package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.FieldDefinition;
import org.hl7.tinkar.component.PatternForSemanticVersion;
import org.hl7.tinkar.entity.internal.Get;
import org.hl7.tinkar.lombok.dto.FieldDataType;

public class PatternForSemanticEntityVersion
        extends EntityVersion
        implements PatternForSemanticVersion<FieldDefinitionForEntity> {

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

    public static PatternForSemanticEntityVersion make(PatternForSemanticEntity definitionForSemanticEntity, ByteBuf readBuf, byte formatVersion) {
        PatternForSemanticEntityVersion version = new PatternForSemanticEntityVersion();
        version.fill(definitionForSemanticEntity, readBuf, formatVersion);
        return version;
    }

    public static PatternForSemanticEntityVersion make(PatternForSemanticEntity definitionForSemanticEntity, PatternForSemanticVersion<FieldDefinition> definitionForSemanticVersion) {
        PatternForSemanticEntityVersion version = new PatternForSemanticEntityVersion();
        version.fill(definitionForSemanticEntity, definitionForSemanticVersion);
        version.referencedComponentPurposeNid = Get.entityService().nidForPublicId(definitionForSemanticVersion.referencedComponentPurpose());
        version.referencedComponentMeaningNid = Get.entityService().nidForPublicId(definitionForSemanticVersion.referencedComponentMeaning());
        version.fieldDefinitionForEntities.clear();
        for (FieldDefinition fieldDefinition: definitionForSemanticVersion.fieldDefinitions()) {
            version.fieldDefinitionForEntities.add(FieldDefinitionForEntity.make(version, fieldDefinition));
        }
        return version;
    }

}
