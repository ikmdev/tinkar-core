package org.hl7.tinkar.entity.forremoval;

import io.activej.bytebuf.ByteBuf;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.component.FieldDefinition;
import org.hl7.tinkar.component.PatternVersion;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.terms.ConceptFacade;

public class PatternEntityVersionClass
        extends EntityVersionClass
        implements PatternEntityVersion {

    protected final MutableList<FieldDefinitionForEntity> fieldDefinitionForEntities = Lists.mutable.empty();
    protected int semanticPurposeNid;
    protected int semanticMeaningNid;

    public static PatternEntityVersion make(PatternEntity definitionEntity, ByteBuf readBuf, byte formatVersion) {
        throw new UnsupportedOperationException();
        //PatternEntityVersionClass version = new PatternEntityVersionClass();
        //version.fill(definitionEntity, readBuf, formatVersion);
        //return version;
    }

    public static PatternEntityVersionClass make(PatternEntity patternEntity, PatternVersion<FieldDefinition> patternVersion) {
        throw new UnsupportedOperationException();
        /*PatternEntityVersionClass version = new PatternEntityVersionClass();
        version.fill(patternEntity, patternVersion);
        version.semanticPurposeNid = EntityService.get().nidForComponent(patternVersion.semanticPurpose());
        version.semanticMeaningNid = EntityService.get().nidForComponent(patternVersion.semanticMeaning());
        version.fieldDefinitionForEntities.clear();
        for (FieldDefinition fieldDefinition : patternVersion.fieldDefinitions()) {
            version.fieldDefinitionForEntities.add(FieldDefinitionForEntity.make(version, fieldDefinition));
        }
        return version;*/
    }

    @Override
    public int semanticPurposeNid() {
        return semanticPurposeNid;
    }

    @Override
    public int semanticMeaningNid() {
        return semanticMeaningNid;
    }

    @Override
    public <T> T getFieldWithMeaning(ConceptFacade fieldMeaning, SemanticEntityVersion version) {
        return (T) version.fields().get(indexForMeaning(fieldMeaning));
    }

    @Override
    public ImmutableList<FieldDefinitionForEntity> fieldDefinitions() {
        return fieldDefinitionForEntities.toImmutable();
    }

    @Override
    public <T> T getFieldWithPurpose(ConceptFacade fieldPurpose, SemanticEntityVersion version) {
        return (T) version.fields().get(indexForPurpose(fieldPurpose));
    }

    // TODO: should allow more than one index for purpose?
    // TODO: Note the stamp calculator caches these indexes. Consider how to optimize, and eliminate unoptimized calls?
    @Override
    public int indexForPurpose(int purposeNid) {
        for (int i = 0; i < fieldDefinitions().size(); i++) {
            if (fieldDefinitions().get(i).purposeNid() == purposeNid) {
                return i;
            }
        }
        return -1;
    }

    // TODO: should allow more than one index for meaning?
    // TODO: Note the stamp calculator caches these indexes. Consider how to optimize, and eliminate unoptimized calls?
    @Override
    public int indexForMeaning(int meaningNid) {
        for (int i = 0; i < fieldDefinitions().size(); i++) {
            if (fieldDefinitions().get(i).meaningNid() == meaningNid) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public ConceptEntity semanticPurpose() {
        return EntityService.get().getEntityFast(semanticPurposeNid);
    }

    @Override
    public ConceptEntity semanticMeaning() {
        return EntityService.get().getEntityFast(semanticMeaningNid);
    }

    @Override
    public int indexForMeaning(ConceptFacade meaning) {
        return indexForMeaning(meaning.nid());
    }

    @Override
    public int indexForPurpose(ConceptFacade purpose) {
        return indexForPurpose(purpose.nid());
    }

    @Override
    public FieldDataType dataType() {
        return FieldDataType.PATTERN_VERSION;
    }

    @Override
    protected void writeVersionFields(ByteBuf writeBuf) {
        writeBuf.writeInt(this.semanticPurposeNid);
        writeBuf.writeInt(this.semanticMeaningNid);
        writeBuf.writeInt(fieldDefinitionForEntities.size());
        for (FieldDefinitionForEntity field : fieldDefinitionForEntities) {
            field.write(writeBuf);
        }
    }

    @Override
    protected void finishVersionFill(ByteBuf readBuf, byte formatVersion) {
        this.semanticPurposeNid = readBuf.readInt();
        this.semanticMeaningNid = readBuf.readInt();
        int fieldCount = readBuf.readInt();
        fieldDefinitionForEntities.clear();
        for (int i = 0; i < fieldCount; i++) {
            fieldDefinitionForEntities.add(FieldDefinitionForEntity.make(readBuf));
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append(Entity.getStamp(stampNid).describe());

        sb.append(" rcp: ");
        sb.append(PrimitiveData.text(semanticPurposeNid));
        sb.append(" rcm: ");
        sb.append(PrimitiveData.text(semanticMeaningNid));
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
