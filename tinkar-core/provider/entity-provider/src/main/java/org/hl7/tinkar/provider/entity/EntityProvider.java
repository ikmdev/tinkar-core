package org.hl7.tinkar.provider.entity;

import io.activej.bytebuf.ByteBuf;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.component.Version;
import org.hl7.tinkar.entity.*;

import com.google.auto.service.AutoService;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@AutoService(EntityService.class)
public class EntityProvider implements EntityService {

    public EntityProvider() {
        System.out.println("Constructing EntityProvider");
    }

    @Override
    public void putChronology(Chronology chronology) {
        if (chronology instanceof Entity entity) {
            putEntity(entity);
        } else {
            putEntity(EntityFactory.make(chronology));
        }
    }

    @Override
    public <T extends Chronology<V>, V extends Version> Optional<T> getChronology(int nid) {
        return Optional.ofNullable((T) getEntityFast(nid));
    }

    @Override
    public <T extends Entity<V>, V extends EntityVersion> T getEntityFast(int nid) {
        byte[] bytes = PrimitiveData.get().getBytes(nid);
        if (bytes == null) {
            return null;
        }
       return EntityFactory.make(bytes);
    }

    @Override
    public StampEntity getStampFast(int nid) {
        byte[] bytes = PrimitiveData.get().getBytes(nid);
        if (bytes == null) {
            return null;
        }
        return EntityFactory.makeStamp(ByteBuf.wrapForReading(bytes));
    }

    @Override
    public void putEntity(Entity entity) {
        if (entity instanceof SemanticEntity semanticEntity) {
            PrimitiveData.get().merge(entity.nid(),
                    semanticEntity.typePatternNid(),
                    semanticEntity.referencedComponentNid(),
                    entity.getBytes());
        } else {
            PrimitiveData.get().merge(entity.nid(), Integer.MAX_VALUE, Integer.MAX_VALUE, entity.getBytes());
        }
    }

    @Override
    public int nidForPublicId(ImmutableList<UUID> uuidList) {
        return PrimitiveData.get().nidForUuids(uuidList);
    }

    @Override
    public int nidForPublicId(UUID... uuids) {
        return PrimitiveData.get().nidForUuids(uuids);
    }

    @Override
    public <T extends Chronology<V>, V extends Version> Optional<T> getChronology(PublicId publicId) {
        if (publicId instanceof Entity) {
            return Optional.ofNullable((T) getEntityFast(((Entity) publicId).nid()));
        }
        return Optional.ofNullable((T) getEntityFast(nidForPublicId(publicId)));
    }

    @Override
    public int nidForPublicId(PublicId publicId) {
        return PrimitiveData.get().nidForUuids(publicId.asUuidArray());
    }

    @Override
    public Entity unmarshalChronology(byte[] bytes) {
        return EntityFactory.make(bytes);
    }

    @Override
    public void forEachEntityOfType(int typeDefinitionNid, Consumer<SemanticEntity> procedure) {
        PrimitiveData.get().forEachEntityOfType(typeDefinitionNid, (int nid) -> procedure.accept(getEntityFast(nid)));
    }

    @Override
    public int[] entityNidsOfType(int setNid) {
        return PrimitiveData.get().entityNidsOfType(setNid);
    }

    @Override
    public void forEachSemanticForComponent(int componentNid, Consumer<SemanticEntity> procedure) {
        PrimitiveData.get().forEachSemanticNidForComponent(componentNid, (int nid) -> procedure.accept(getEntityFast(nid)));
    }

    @Override
    public int[] semanticNidsForComponent(int componentNid) {
        return PrimitiveData.get().semanticNidsForComponent(componentNid);
    }

    @Override
    public void forEachSemanticForComponentOfType(int componentNid, int typeDefinitionNid, Consumer<SemanticEntity> procedure) {
        PrimitiveData.get().forEachSemanticNidForComponentOfType(componentNid, typeDefinitionNid, (int nid) -> procedure.accept(getEntityFast(nid)));
    }

    @Override
    public int[] semanticNidsForComponentOfType(int componentNid, int typeDefinitionNid) {
        return PrimitiveData.get().semanticNidsForComponentOfType(componentNid, typeDefinitionNid);
    }
}
