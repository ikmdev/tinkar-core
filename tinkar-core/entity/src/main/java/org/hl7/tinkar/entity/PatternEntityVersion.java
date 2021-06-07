package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.FieldDefinition;
import org.hl7.tinkar.component.PatternVersion;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.terms.ConceptFacade;

public class PatternEntityVersion
        extends EntityVersion
        implements PatternVersion<FieldDefinitionForEntity> {

    protected int referencedComponentPurposeNid;
    protected int referencedComponentMeaningNid;
    protected final MutableList<FieldDefinitionForEntity> fieldDefinitionForEntities = Lists.mutable.empty();


    @Override
    public ImmutableList<FieldDefinitionForEntity> fieldDefinitions() {
        return fieldDefinitionForEntities.toImmutable();
    }

    @Override
    public Concept referencedComponentPurpose() {
        return EntityService.get().getEntityFast(referencedComponentPurposeNid);
    }

    @Override
    public Concept referencedComponentMeaning() {
        return EntityService.get().getEntityFast(referencedComponentMeaningNid);
    }

    @Override
    public FieldDataType dataType() {
        return FieldDataType.PATTERN_VERSION;
    }

    public <T> T getFieldWithMeaning(ConceptFacade fieldMeaning, SemanticEntityVersion version) {
        return (T) version.fields.get(indexForMeaning(fieldMeaning));
    }
    public <T> T getFieldWithPurpose(ConceptFacade fieldPurpose, SemanticEntityVersion version) {
        return (T) version.fields.get(indexForPurpose(fieldPurpose));
    }

    public int indexForMeaning(ConceptFacade meaning) {
        return indexForMeaning(meaning.nid());
    }

    public int indexForPurpose(ConceptFacade purpose) {
        return indexForPurpose(purpose.nid());
    }

    public int indexForMeaning(int meaningNid) {
        for (int i = 0; i < fieldDefinitions().size(); i++) {
            if (fieldDefinitions().get(i).meaningNid == meaningNid) {
                return i;
            }
        }
        return -1;
    }

    public int indexForPurpose(int purposeNid) {
        for (int i = 0; i < fieldDefinitions().size(); i++) {
            if (fieldDefinitions().get(i).purposeNid == purposeNid) {
                return i;
            }
        }
        return -1;
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

    public static PatternEntityVersion make(PatternEntity definitionEntity, ByteBuf readBuf, byte formatVersion) {
        PatternEntityVersion version = new PatternEntityVersion();
        version.fill(definitionEntity, readBuf, formatVersion);
        return version;
    }

    public static PatternEntityVersion make(PatternEntity patternEntity, PatternVersion<FieldDefinition> patternVersion) {
        PatternEntityVersion version = new PatternEntityVersion();
        version.fill(patternEntity, patternVersion);
        version.referencedComponentPurposeNid = EntityService.get().nidForComponent(patternVersion.referencedComponentPurpose());
        version.referencedComponentMeaningNid = EntityService.get().nidForComponent(patternVersion.referencedComponentMeaning());
        version.fieldDefinitionForEntities.clear();
        for (FieldDefinition fieldDefinition: patternVersion.fieldDefinitions()) {
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
        sb.append(PrimitiveData.text(referencedComponentPurposeNid));
        sb.append(" rcm: ");
        sb.append(PrimitiveData.text(referencedComponentMeaningNid));
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
