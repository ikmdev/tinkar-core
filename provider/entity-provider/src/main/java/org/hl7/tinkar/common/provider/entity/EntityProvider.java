package org.hl7.tinkar.common.provider.entity;

import com.google.auto.service.AutoService;
import io.activej.bytebuf.ByteBuf;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.util.id.PublicId;
import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.component.Version;
import org.hl7.tinkar.common.provider.entity.internal.Get;
import org.hl7.tinkar.entity.*;

import java.util.Optional;
import java.util.UUID;

@AutoService(EntityService.class)
public class EntityProvider implements EntityService {

    public EntityProvider() {
        System.out.println("Constructing EntityProvider");
    }

    @Override
    public void putChronology(Chronology chronology) {
        if (chronology instanceof Entity) {
            Entity entity = (Entity) chronology;
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
        ByteBuf buf = ByteBuf.wrapForReading(bytes);
        byte formatVersion = buf.readByte();
        return EntityFactory.make(buf, formatVersion);
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
        Get.dataService().merge(entity.nid(), entity.getBytes());
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

}
