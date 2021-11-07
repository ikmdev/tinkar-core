package org.hl7.tinkar.entity;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.id.PublicIds;
import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.component.ChronologyService;
import org.hl7.tinkar.component.Component;
import org.hl7.tinkar.component.Version;
import org.hl7.tinkar.entity.internal.EntityServiceFinder;
import org.hl7.tinkar.terms.ComponentWithNid;
import org.hl7.tinkar.terms.EntityFacade;

import java.util.*;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

public interface EntityService extends ChronologyService, Flow.Publisher<Entity<? extends EntityVersion>> {
    static EntityService get() {
        return EntityServiceFinder.INSTANCE.get();
    }

    @Override
    default <T extends Chronology<V>, V extends Version> Optional<T> getChronology(UUID... uuids) {
        return getChronology(nidForUuids(uuids));
    }

    @Override
    default <T extends Chronology<V>, V extends Version> Optional<T> getChronology(Component component) {
        return getChronology(nidForPublicId(component.publicId()));
    }

    <T extends Chronology<V>, V extends Version> Optional<T> getChronology(int nid);

    default int nidForUuids(UUID... uuids) {
        return nidForPublicId(PublicIds.of(uuids));
    }

    int nidForPublicId(PublicId publicId);

    default <T extends Entity<V>, V extends EntityVersion> Optional<T> getEntity(Component component) {
        return getEntity(nidForPublicId(component.publicId()));
    }

    default <T extends Entity<V>, V extends EntityVersion> Optional<T> getEntity(int nid) {
        return Optional.ofNullable(getEntityFast(nid));
    }

    <T extends Entity<V>, V extends EntityVersion> T getEntityFast(int nid);

    default <T extends Entity<V>, V extends EntityVersion> Optional<T> getEntity(ImmutableList<UUID> uuidList) {
        return getEntity(nidForUuids(uuidList));
    }

    default int nidForUuids(ImmutableList<UUID> uuidList) {
        return nidForPublicId(PublicIds.of(uuidList.toArray(new UUID[uuidList.size()])));
    }

    default <T extends Entity<V>, V extends EntityVersion> Optional<T> getEntity(UUID... uuids) {
        return getEntity(nidForUuids(uuids));
    }

    default <T extends Entity<V>, V extends EntityVersion> Optional<T> getEntity(EntityFacade entityFacade) {
        return Optional.ofNullable(getEntityFast(entityFacade.nid()));
    }

    default <T extends Entity<V>, V extends EntityVersion> T getEntityFast(ImmutableList<UUID> uuidList) {
        return getEntityFast(nidForUuids(uuidList));
    }

    default <T extends Entity<V>, V extends EntityVersion> T getEntityFast(UUID... uuids) {
        return getEntityFast(nidForUuids(uuids));
    }

    default <T extends Entity<V>, V extends EntityVersion> T getEntityFast(EntityFacade entityFacade) {
        return getEntityFast(entityFacade.nid());
    }

    default Optional<StampEntity<StampEntityVersion>> getStamp(Component component) {
        return getStamp(nidForPublicId(component.publicId()));
    }

    default Optional<StampEntity<StampEntityVersion>> getStamp(int nid) {
        return Optional.ofNullable(getStampFast(nid));
    }

    <T extends StampEntity<? extends StampEntityVersion>> T getStampFast(int nid);

    default Optional<StampEntity<StampEntityVersion>> getStamp(ImmutableList<UUID> uuidList) {
        return getStamp(nidForUuids(uuidList));
    }

    default Optional<StampEntity<StampEntityVersion>> getStamp(UUID... uuids) {
        return getStamp(nidForUuids(uuids));
    }

    default StampEntity<StampEntityVersion> getStampFast(ImmutableList<UUID> uuidList) {
        return getStampFast(nidForUuids(uuidList));
    }

    default StampEntity<StampEntityVersion> getStampFast(UUID... uuids) {
        return getStampFast(nidForUuids(uuids));
    }

    void putEntity(Entity entity);

    void putStamp(StampEntity stampEntity);

    default int nidForComponent(Component component) {
        if (component instanceof ComponentWithNid) {
            return ((ComponentWithNid) component).nid();
        }
        return nidForPublicId(component.publicId());
    }

    <T extends Chronology<V>, V extends Version> T unmarshalChronology(byte[] bytes);

    default void addSortedUuids(List<UUID> uuidList, IntIdList idList) throws NoSuchElementException {
        addSortedUuids(uuidList, idList.toArray());
    }

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

    void forEachSemanticOfPattern(int patternNid, Consumer<SemanticEntity<SemanticEntityVersion>> procedure);

    int[] semanticNidsOfPattern(int patternNid);

    void forEachSemanticForComponent(int componentNid, Consumer<SemanticEntity<SemanticEntityVersion>> procedure);

    int[] semanticNidsForComponent(int componentNid);

    void forEachSemanticForComponentOfPattern(int componentNid, int patternNid, Consumer<SemanticEntity<SemanticEntityVersion>> procedure);

    int[] semanticNidsForComponentOfPattern(int componentNid, int patternNid);
}