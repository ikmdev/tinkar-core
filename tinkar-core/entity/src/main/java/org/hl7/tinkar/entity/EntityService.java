package org.hl7.tinkar.entity;

import org.eclipse.collections.api.block.procedure.primitive.IntProcedure;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.id.PublicIds;
import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.component.ChronologyService;
import org.hl7.tinkar.component.Component;
import org.hl7.tinkar.component.Version;
import org.hl7.tinkar.entity.internal.Get;

import java.util.*;
import java.util.function.Consumer;

public interface EntityService extends ChronologyService {
    static EntityService get() {
        return Get.entityService();
    }

    @Override
    default <T extends Chronology<V>, V extends Version> Optional<T> getChronology(Component component) {
        return getChronology(nidForPublicId(component.publicId()));
    }

    @Override
    default <T extends Chronology<V>, V extends Version> Optional<T> getChronology(UUID... uuids) {
        return getChronology(nidForPublicId(uuids));
    }

    <T extends Chronology<V>, V extends Version> Optional<T> getChronology(int nid);


    default <T extends Entity<V>, V extends EntityVersion> Optional<T> getEntity(Component component) {
        return getEntity(nidForPublicId(component.publicId()));
    }

    default <T extends Entity<V>, V extends EntityVersion> Optional<T> getEntity(ImmutableList<UUID> uuidList) {
        return getEntity(nidForPublicId(uuidList));
    }

    default <T extends Entity<V>, V extends EntityVersion> Optional<T> getEntity(UUID... uuids) {
        return getEntity(nidForPublicId(uuids));
    }

    default <T extends Entity<V>, V extends EntityVersion> Optional<T> getEntity(int nid) {
        return Optional.ofNullable(getEntityFast(nid));
    }

    default <T extends Entity<V>, V extends EntityVersion> T getEntityFast(ImmutableList<UUID> uuidList) {
        return getEntityFast(nidForPublicId(uuidList));
    }

    default <T extends Entity<V>, V extends EntityVersion> T getEntityFast(UUID... uuids) {
        return getEntityFast(nidForPublicId(uuids));
    }

    <T extends Entity<V>, V extends EntityVersion> T getEntityFast(int nid);


    default Optional<StampEntity> getStamp(Component component) {
        return getStamp(nidForPublicId(component.publicId()));
    }

    default Optional<StampEntity> getStamp(ImmutableList<UUID> uuidList) {
        return getStamp(nidForPublicId(uuidList));
    }

    default Optional<StampEntity> getStamp(UUID... uuids) {
        return getStamp(nidForPublicId(uuids));
    }

    default Optional<StampEntity> getStamp(int nid) {
        return Optional.ofNullable(getStampFast(nid));
    }

    default StampEntity getStampFast(ImmutableList<UUID> uuidList) {
        return getStampFast(nidForPublicId(uuidList));
    }

    default StampEntity getStampFast(UUID... uuids) {
        return getStampFast(nidForPublicId(uuids));
    }

    StampEntity getStampFast(int nid);

    void putEntity(Entity entity);

    void putStamp(StampEntity stampEntity);

    default int nidForComponent(Component component) {
        if (component instanceof ComponentWithNid) {
            return ((ComponentWithNid) component).nid();
        }
        return nidForPublicId(component.publicId());
    }

    default int nidForPublicId(ImmutableList<UUID> uuidList) {
        return nidForPublicId(PublicIds.of(uuidList.toArray(new UUID[uuidList.size()])));
    }

    default int nidForPublicId(UUID... uuids) {
        return nidForPublicId(PublicIds.of(uuids));
    }

    int nidForPublicId(PublicId publicId);

    <T extends Chronology<V>, V extends Version> T unmarshalChronology(byte[] bytes);


    /**
     * Note, this method does not sort the provided uuidList,
     * it only ensures that the UUIDs assigned to each nid are added to the existing list
     * in a sorted order. This method is to create reproducible identifiers for objects.
     *
     * @param uuidList
     * @param nids
     * @throws NoSuchElementException
     */
    default void addSortedUuids(List<UUID> uuidList, int... nids) throws NoSuchElementException {
        for (int nid : nids) {
            UUID[] uuids = getEntityFast(nid).publicId().asUuidArray();
            Arrays.sort(uuids);
            for (UUID nidUuid : uuids) {
                uuidList.add(nidUuid);
            }
        }
    }

    void forEachEntityOfType(int typeDefinitionNid, Consumer<SemanticEntity> procedure);

    int[] entityNidsOfType(int setNid);

    void forEachSemanticForComponent(int componentNid, Consumer<SemanticEntity> procedure);

    int[] semanticNidsForComponent(int componentNid);

    void forEachSemanticForComponentOfType(int componentNid, int typeDefinitionNid, Consumer<SemanticEntity> procedure);

    int[] semanticNidsForComponentOfType(int componentNid, int typeDefinitionNid);
}