package org.hl7.tinkar.entity;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.component.ChronologyService;
import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.component.Component;
import org.hl7.tinkar.component.Version;

import java.util.Optional;
import java.util.UUID;

public interface EntityService extends ChronologyService {

    @Override
    default <T extends Chronology<V>, V extends Version> Optional<T> getChronology(Component component) {
        return getChronology(nidForUuids(component.componentUuids()));
    }
    @Override
    default <T extends Chronology<V>, V extends Version> Optional<T> getChronology(ImmutableList<UUID> uuidList) {
        return getChronology(nidForUuids(uuidList));
    }
    @Override
    default <T extends Chronology<V>, V extends Version> Optional<T> getChronology(UUID... uuids) {
        return getChronology(nidForUuids(uuids));
    }
    <T extends Chronology<V>, V extends Version> Optional<T> getChronology(int nid);


    default <T extends Entity<V>, V extends EntityVersion> Optional<T> getEntity(Component component) {
        return getEntity(nidForUuids(component.componentUuids()));
    }
    default <T extends Entity<V>, V extends EntityVersion> Optional<T> getEntity(ImmutableList<UUID> uuidList) {
        return getEntity(nidForUuids(uuidList));
    }
    default <T extends Entity<V>, V extends EntityVersion> Optional<T> getEntity(UUID... uuids) {
        return getEntity(nidForUuids(uuids));
    }
    default <T extends Entity<V>, V extends EntityVersion> Optional<T> getEntity(int nid) {
        return Optional.ofNullable(getEntityFast(nid));
    }
    default <T extends Entity<V>, V extends EntityVersion> T getEntityFast(ImmutableList<UUID> uuidList) {
        return getEntityFast(nidForUuids(uuidList));
    }
    default <T extends Entity<V>, V extends EntityVersion> T getEntityFast(UUID... uuids) {
        return getEntityFast(nidForUuids(uuids));
    }
    <T extends Entity<V>, V extends EntityVersion> T getEntityFast(int nid);


    default Optional<StampEntity> getStamp(Component component) {
        return getStamp(nidForUuids(component.componentUuids()));
    }
    default Optional<StampEntity> getStamp(ImmutableList<UUID> uuidList) {
        return getStamp(nidForUuids(uuidList));
    }
    default Optional<StampEntity> getStamp(UUID... uuids) {
        return getStamp(nidForUuids(uuids));
    }
    default Optional<StampEntity> getStamp(int nid) {
        return Optional.ofNullable(getStampFast(nid));
    }
    default StampEntity getStampFast(ImmutableList<UUID> uuidList) {
        return getStampFast(nidForUuids(uuidList));
    }
    default StampEntity getStampFast(UUID... uuids) {
        return getStampFast(nidForUuids(uuids));
    }
    StampEntity getStampFast(int nid);

    void putEntity(Entity entity);

    default int nidForUuids(Component component) {
        return nidForUuids(component.componentUuids());
    }

    int nidForUuids(ImmutableList<UUID> uuidList);
    int nidForUuids(UUID... uuids);
}
