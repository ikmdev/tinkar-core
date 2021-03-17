package org.hl7.tinkar.provider.entity;

import io.activej.bytebuf.ByteBuf;
import org.eclipse.collections.api.block.procedure.primitive.IntProcedure;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.component.Version;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.provider.entity.internal.Get;

import java.util.Optional;
import java.util.UUID;

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
        byte[] bytes = Get.dataService().getBytes(nid);
        if (bytes == null) {
            return null;
        }
       return EntityFactory.make(bytes);
    }

    @Override
    public StampEntity getStampFast(int nid) {
        byte[] bytes = Get.dataService().getBytes(nid);
        if (bytes == null) {
            return null;
        }
        return EntityFactory.makeStamp(ByteBuf.wrapForReading(bytes));
    }

    @Override
    public void putEntity(Entity entity) {
        if (entity instanceof SemanticEntity semanticEntity) {
            Get.dataService().merge(entity.nid(),
                    semanticEntity.typePatternNid(),
                    semanticEntity.referencedComponentNid(),
                    entity.getBytes());
        } else {
            Get.dataService().merge(entity.nid(), Integer.MAX_VALUE, Integer.MAX_VALUE, entity.getBytes());
        }
    }

    @Override
    public int nidForPublicId(ImmutableList<UUID> uuidList) {
        return Get.dataService().nidForUuids(uuidList);
    }

    @Override
    public int nidForPublicId(UUID... uuids) {
        return Get.dataService().nidForUuids(uuids);
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
        return Get.dataService().nidForUuids(publicId.asUuidArray());
    }

    @Override
    public Entity unmarshalChronology(byte[] bytes) {
        return EntityFactory.make(bytes);
    }

    @Override
    public void forEachEntityOfType(int typeDefinitionNid, IntProcedure procedure) {
        Get.dataService().forEachEntityOfType(typeDefinitionNid, procedure);
    }

    @Override
    public int[] entityNidsOfType(int setNid) {
        return Get.dataService().entityNidsOfType(setNid);
    }

    @Override
    public void forEachSemanticForComponent(int componentNid, IntProcedure procedure) {
        Get.dataService().forEachSemanticForComponent(componentNid, procedure);
    }

    @Override
    public int[] semanticNidsForComponent(int componentNid) {
        return Get.dataService().semanticNidsForComponent(componentNid);
    }

    @Override
    public void forEachSemanticForComponentOfType(int componentNid, int typeDefinitionNid, IntProcedure procedure) {
        Get.dataService().forEachSemanticForComponentOfType(componentNid, typeDefinitionNid, procedure);
    }

    @Override
    public int[] semanticNidsForComponentOfType(int componentNid, int typeDefinitionNid) {
        return Get.dataService().semanticNidsForComponentOfType(componentNid, typeDefinitionNid);
    }
}
