package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.entity.internal.Get;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.component.*;

import java.util.Arrays;

public class SemanticEntity
        extends Entity<SemanticEntityVersion>
        implements SemanticChronology<SemanticEntityVersion> {

    protected int referencedComponentNid;

    @Override
    protected int subclassFieldBytesSize() {
        return 4; // referenced component
    }

    @Override
    public FieldDataType dataType() {
        return FieldDataType.SEMANTIC_CHRONOLOGY;
    }

    @Override
    public ImmutableList<SemanticEntityVersion> versions() {
        return super.versions();
    }

    @Override
    protected void finishEntityWrite(ByteBuf byteBuf) {
        byteBuf.writeInt(referencedComponentNid);
    }

    @Override
    protected void finishEntityRead(ByteBuf readBuf, byte formatVersion) {
        this.referencedComponentNid = readBuf.readInt();
    }

    @Override
    protected void finishEntityRead(Chronology chronology) {
        if (chronology instanceof SemanticChronology semanticChronology) {
            referencedComponentNid = Get.entityService().nidForComponent(semanticChronology.referencedComponent());
        }
    }

    @Override
    protected SemanticEntityVersion makeVersion(ByteBuf readBuf, byte formatVersion) {
        return SemanticEntityVersion.make(this, readBuf, formatVersion);
    }

    @Override
    protected SemanticEntityVersion makeVersion(Version version) {
        return SemanticEntityVersion.make(this, (SemanticVersion) version);
    }

    @Override
    public Entity referencedComponent() {
        return Get.entityService().getEntityFast(this.referencedComponentNid);
    }

    public int referencedComponentNid() {
        return this.referencedComponentNid;
    }

    @Override
    public PatternForSemantic patternForSemantic() {
        return Get.entityService().getEntityFast(definitionNid);
    }


    public static SemanticEntity make(ByteBuf readBuf, byte entityFormatVersion) {
        SemanticEntity semanticEntity = new SemanticEntity();
        semanticEntity.fill(readBuf, entityFormatVersion);
        return semanticEntity;
    }

    public static SemanticEntity make(SemanticChronology other) {
        SemanticEntity semanticEntity = new SemanticEntity();
        semanticEntity.fill(other);
        return semanticEntity;
    }

    @Override
    public String toString() {
        return "SemanticEntity<" +
                nid +
                "> " + Arrays.toString(publicId().asUuidArray()) +
                ", definitionNid=" + definitionNid +
                ", referencedComponentNid=" + referencedComponentNid +
                ", versions=" + versions +
                '}';
    }
}
