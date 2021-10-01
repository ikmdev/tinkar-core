package org.hl7.tinkar.entity;


import io.activej.bytebuf.ByteBuf;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.component.FieldDefinition;
import org.hl7.tinkar.terms.ConceptFacade;
// TODO convert to record
public class FieldDefinitionForEntity
        implements FieldDefinition {
    public FieldDefinitionForEntity() {
    }

    public FieldDefinitionForEntity(FieldDefinition fieldDefinition) {
        dataTypeNid = EntityService.get().nidForComponent(fieldDefinition.dataType());
        purposeNid = EntityService.get().nidForComponent(fieldDefinition.purpose());
        meaningNid = EntityService.get().nidForComponent(fieldDefinition.meaning());
    }

    protected int dataTypeNid;
    protected int purposeNid;
    protected int meaningNid;

    public static FieldDefinitionForEntity make(ByteBuf readBuf) {
        FieldDefinitionForEntity fieldDefinitionForEntity = new FieldDefinitionForEntity();
        fieldDefinitionForEntity.fill(readBuf);
        return fieldDefinitionForEntity;
    }

    /**
     * TODO interface for write, fill, etc.
     *
     * @param readBuf
     */
    public void fill(ByteBuf readBuf) {
        dataTypeNid = readBuf.readInt();
        purposeNid = readBuf.readInt();
        meaningNid = readBuf.readInt();
    }

    public static FieldDefinitionForEntity make(PatternEntityVersion enclosingVersion, FieldDefinition fieldDefinition) {
        FieldDefinitionForEntity fieldDefinitionForEntity = new FieldDefinitionForEntity();
        fieldDefinitionForEntity.fill(fieldDefinition);
        return fieldDefinitionForEntity;
    }

    public void fill(FieldDefinition fieldDefinition) {
        dataTypeNid = EntityService.get().nidForComponent(fieldDefinition.dataType());
        purposeNid = EntityService.get().nidForComponent(fieldDefinition.purpose());
        meaningNid = EntityService.get().nidForComponent(fieldDefinition.meaning());
    }

    @Override
    public ConceptFacade dataType() {
        return EntityService.get().getEntityFast(dataTypeNid);
    }

    @Override
    public ConceptFacade purpose() {
        return EntityService.get().getEntityFast(purposeNid);
    }

    @Override
    public ConceptFacade meaning() {
        return EntityService.get().getEntityFast(meaningNid);
    }

    public int dataTypeNid() {
        return dataTypeNid;
    }

    public int purposeNid() {
        return purposeNid;
    }

    public int meaningNid() {
        return meaningNid;
    }

    public void write(ByteBuf writeBuf) {
        writeBuf.writeInt(dataTypeNid);
        writeBuf.writeInt(purposeNid);
        writeBuf.writeInt(meaningNid);
    }

    @Override
    public String toString() {
        return "FieldDef{t: " +
                PrimitiveData.text(dataTypeNid) + " p: " +
                PrimitiveData.text(purposeNid) + " m: " +
                PrimitiveData.text(meaningNid) +
                '}';
    }
}
