package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.component.*;
import org.hl7.tinkar.dto.FieldDataType;
import org.hl7.tinkar.entity.internal.Get;

public class SemanticEntity
        extends Entity<SemanticEntityVersion>
        implements SemanticChronology<SemanticEntityVersion> {

    protected int referencedComponentNid;

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
    protected void finishEntityRead(ByteBuf readBuf) {
        this.referencedComponentNid = readBuf.readInt();
    }

    @Override
    protected void finishEntityRead(Chronology chronology) {
        if (chronology instanceof SemanticChronology semanticChronology) {
            referencedComponentNid = Get.entityService().nidForUuids(semanticChronology.referencedComponent().componentUuids());
        }
    }

    @Override
    protected SemanticEntityVersion makeVersion(ByteBuf readBuf) {
        return SemanticEntityVersion.make(this, readBuf);
    }

    @Override
    protected SemanticEntityVersion makeVersion(Version version) {
        return SemanticEntityVersion.make(this, (SemanticVersion) version);
    }

    @Override
    public Entity referencedComponent() {
        return Get.entityService().getEntityFast(this.referencedComponentNid);
    }

    @Override
    public DefinitionForSemantic definitionForSemantic() {
        return Get.entityService().getEntityFast(setNid);
    }


    public static SemanticEntity make(ByteBuf readBuf) {
        SemanticEntity semanticEntity = new SemanticEntity();
        semanticEntity.fill(readBuf);
        return semanticEntity;
    }

    public static SemanticEntity make(SemanticChronology other) {
        SemanticEntity semanticEntity = new SemanticEntity();
        semanticEntity.fill(other);
        return semanticEntity;
    }

}
