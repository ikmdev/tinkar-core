package org.hl7.tinkar.entity.forremoval;

import io.activej.bytebuf.ByteBuf;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.component.*;
import org.hl7.tinkar.entity.*;

import java.util.Arrays;

public class SemanticEntityClass
        extends EntityClass<SemanticEntityVersion>
        implements SemanticEntity<SemanticEntityVersion> {

    protected int referencedComponentNid;

    protected int patternNid;

    public static SemanticEntityClass make(ByteBuf readBuf, byte entityFormatVersion) {
        SemanticEntityClass semanticEntity = new SemanticEntityClass();
        semanticEntity.fill(readBuf, entityFormatVersion);
        return semanticEntity;
    }

    public static SemanticEntityClass make(SemanticChronology other) {
        SemanticEntityClass semanticEntity = new SemanticEntityClass();
        semanticEntity.fill(other);
        return semanticEntity;
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
    public ImmutableList<SemanticEntityVersion> versions() {
        return super.versions();
    }

    @Override
    protected void finishEntityRead(ByteBuf readBuf, byte formatVersion) {
        this.referencedComponentNid = readBuf.readInt();
        this.patternNid = readBuf.readInt();
    }

    @Override
    protected SemanticEntityVersion makeVersion(ByteBuf readBuf, byte formatVersion) {
        return SemanticEntityVersionClass.make(this, readBuf, formatVersion);
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
        return SemanticEntityVersionClass.make(this, (SemanticVersion) version);
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
    public EntityClass referencedComponent() {
        return EntityService.get().getEntityFast(this.referencedComponentNid);
    }

    @Override
    public int referencedComponentNid() {
        return this.referencedComponentNid;
    }

    @Override
    public PatternEntityClass pattern() {
        return EntityService.get().getEntityFast(patternNid);
    }

    @Override
    public int patternNid() {
        return this.patternNid;
    }

    @Override
    public int topEnclosingComponentNid() {
        return topEnclosingComponent().nid();
    }

    @Override
    public Entity<? extends EntityVersion> topEnclosingComponent() {
        Entity<? extends EntityVersion> referencedComponent = Entity.getFast(this.referencedComponentNid);
        while (referencedComponent instanceof SemanticEntityClass parentSemantic) {
            referencedComponent = Entity.getFast(parentSemantic.referencedComponentNid);
        }
        return referencedComponent;
    }
}
