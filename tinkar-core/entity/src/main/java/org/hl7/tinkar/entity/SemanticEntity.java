package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.component.*;
import org.hl7.tinkar.terms.SemanticFacade;

import java.util.Arrays;

public class SemanticEntity
        extends Entity<SemanticEntityVersion>
        implements SemanticFacade, SemanticChronology<SemanticEntityVersion> {

    protected int referencedComponentNid;

    protected int patternNid;

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
    protected void finishEntityRead(ByteBuf readBuf, byte formatVersion) {
        this.referencedComponentNid = readBuf.readInt();
        this.patternNid = readBuf.readInt();
    }

    @Override
    protected SemanticEntityVersion makeVersion(ByteBuf readBuf, byte formatVersion) {
        return SemanticEntityVersion.make(this, readBuf, formatVersion);
    }

    @Override
    protected void finishEntityRead(Chronology chronology) {
        if (chronology instanceof SemanticChronology semanticChronology) {
            referencedComponentNid = EntityService.get().nidForComponent(semanticChronology.referencedComponent());
            patternNid = EntityService.get().nidForComponent(semanticChronology.pattern());
        }
    }

    @Override
    protected SemanticEntityVersion makeVersion(Version version) {
        return SemanticEntityVersion.make(this, (SemanticVersion) version);
    }

    @Override
    public FieldDataType dataType() {
        return FieldDataType.SEMANTIC_CHRONOLOGY;
    }

    @Override
    protected void finishEntityWrite(ByteBuf byteBuf) {
        byteBuf.writeInt(referencedComponentNid);
        byteBuf.writeInt(patternNid);
    }

    @Override
    protected int subclassFieldBytesSize() {
        return 4; // referenced component
    }

    @Override
    public ImmutableList<SemanticEntityVersion> versions() {
        return super.versions();
    }

    @Override
    public String toString() {
        return "SemanticEntity{<" +
                nid +
                "> " + Arrays.toString(publicId().asUuidArray()) + " of pattern: «" + PrimitiveData.text(patternNid) +
                " <" +
                nid +
                "> " + Entity.getFast(patternNid).publicId().asUuidList() +
                "», rc: «" + PrimitiveData.text(referencedComponentNid) +
                " <" +
                referencedComponentNid +
                "> " + Entity.getFast(referencedComponentNid).publicId().asUuidList() +
                "», v: " + versions +
                '}';
    }

    @Override
    public Entity referencedComponent() {
        return EntityService.get().getEntityFast(this.referencedComponentNid);
    }

    @Override
    public PatternEntity pattern() {
        return EntityService.get().getEntityFast(patternNid);
    }

    public int referencedComponentNid() {
        return this.referencedComponentNid;
    }

    public int topEnclosingComponentNid() {
        return topEnclosingComponent().nid();
    }

    public Entity<? extends EntityVersion> topEnclosingComponent() {
        Entity<? extends EntityVersion> referencedComponent = Entity.getFast(this.referencedComponentNid);
        while (referencedComponent instanceof SemanticEntity parentSemantic) {
            referencedComponent = Entity.getFast(parentSemantic.referencedComponentNid);
        }
        return referencedComponent;
    }

    public int patternNid() {
        return this.patternNid;
    }
}
