package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.id.IntIdSet;
import org.hl7.tinkar.common.util.time.DateTimeUtil;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.FieldDefinition;
import org.hl7.tinkar.component.TypePatternVersion;
import org.hl7.tinkar.entity.internal.Get;
import org.hl7.tinkar.component.FieldDataType;

import java.time.Instant;

public class TypePatternEntityVersion
        extends EntityVersion
        implements TypePatternVersion<FieldDefinitionForEntity> {

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
        return FieldDataType.TYPE_PATTERN_VERSION;
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

    public static TypePatternEntityVersion make(TypePatternEntity definitionEntity, ByteBuf readBuf, byte formatVersion) {
        TypePatternEntityVersion version = new TypePatternEntityVersion();
        version.fill(definitionEntity, readBuf, formatVersion);
        return version;
    }

    public static TypePatternEntityVersion make(TypePatternEntity definitionEntity, TypePatternVersion<FieldDefinition> definitionVersion) {
        TypePatternEntityVersion version = new TypePatternEntityVersion();
        version.fill(definitionEntity, definitionVersion);
        version.referencedComponentPurposeNid = Get.entityService().nidForComponent(definitionVersion.referencedComponentPurpose());
        version.referencedComponentMeaningNid = Get.entityService().nidForComponent(definitionVersion.referencedComponentMeaning());
        version.fieldDefinitionForEntities.clear();
        for (FieldDefinition fieldDefinition: definitionVersion.fieldDefinitions()) {
            version.fieldDefinitionForEntities.add(FieldDefinitionForEntity.make(version, fieldDefinition));
        }
        return version;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append(Entity.getStamp(stampNid).describe());

        sb.append(" rcp: ");
        sb.append(DefaultDescriptionText.get(referencedComponentPurposeNid));
        sb.append(" rcm: ");
        sb.append(DefaultDescriptionText.get(referencedComponentMeaningNid));
        sb.append(" f: [");
             // TODO get proper version after relative position computer available.
            // Maybe put stamp coordinate on thread, or relative position computer on thread
            for (int i = 0; i < fieldDefinitionForEntities.size(); i++) {
                if (i > 0) {
                    sb.append("; ");
                }
                sb.append(i);
                sb.append(": ");
                FieldDefinitionForEntity fieldDefinitionForEntity = fieldDefinitionForEntities.get(i);
                sb.append(fieldDefinitionForEntity);
            }
        sb.append("]}");

        return sb.toString();
    }
}
