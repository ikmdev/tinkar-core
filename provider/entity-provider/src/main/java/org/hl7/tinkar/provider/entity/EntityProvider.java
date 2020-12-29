package org.hl7.tinkar.provider.entity;

import com.google.auto.service.AutoService;
import io.activej.bytebuf.ByteBuf;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.component.Version;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.provider.entity.internal.Get;

import java.util.Optional;
import java.util.UUID;

@AutoService(EntityService.class)
public class EntityProvider implements EntityService {
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
        return EntityFactory.make(ByteBuf.wrapForReading(bytes));
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
        Get.dataService().merge(entity.nid(), entity.getBytes(), Entity::merge);
    }

    @Override
    public int nidForUuids(ImmutableList<UUID> uuidList) {
        return Get.dataService().nidForUuids(uuidList);
    }

    @Override
    public int nidForUuids(UUID... uuids) {
        return Get.dataService().nidForUuids(uuids);
    }

    @Override
    public ImmutableList<UUID> uuidListForNid(int nid) {
        return Get.dataService().uuidListForNid(nid);
    }
}
