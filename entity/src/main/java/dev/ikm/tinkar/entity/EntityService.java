/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.common.id.IntIdList;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.TinkExecutor;
import dev.ikm.tinkar.common.util.broadcast.Broadcaster;
import dev.ikm.tinkar.component.Chronology;
import dev.ikm.tinkar.component.ChronologyService;
import dev.ikm.tinkar.component.Component;
import dev.ikm.tinkar.component.Version;
import dev.ikm.tinkar.entity.export.ExportEntitiesToProtobufFile;
import dev.ikm.tinkar.entity.internal.EntityServiceFinder;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.terms.ComponentWithNid;
import dev.ikm.tinkar.terms.EntityFacade;
import org.eclipse.collections.api.list.ImmutableList;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public interface EntityService extends ChronologyService, Broadcaster<Integer> {
    static EntityService get() {
        return EntityServiceFinder.INSTANCE.get();
    }

    default CompletableFuture<EntityCountSummary> fullExport(File file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return TinkExecutor.ioThreadPool().submit(new ExportEntitiesToProtobufFile(file)).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
    }

    default CompletableFuture<EntityCountSummary> temporalExport(File file, long fromEpoch, long toEpoch) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return TinkExecutor.ioThreadPool().submit(new ExportEntitiesToProtobufFile(file, fromEpoch, toEpoch)).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
    }

    default CompletableFuture<EntityCountSummary> membershipExport(File file, List<PublicId> membershipTags) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return TinkExecutor.ioThreadPool().submit(new ExportEntitiesToProtobufFile(file, membershipTags)).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
    }

    default CompletableFuture<EntityCountSummary> loadData(File file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return TinkExecutor.ioThreadPool().submit(new LoadEntitiesFromProtobufFile(file)).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
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
    default <T extends Entity<V>, V extends EntityVersion> Optional<T> getEntity(PublicId publicId) {
        return getEntity(nidForPublicId(publicId));
    }

    default <T extends Entity<V>, V extends EntityVersion> Optional<T> getEntity(int nid) {
        T entity = getEntityFast(nid);
        if (entity == null || entity.canceled()) {
            return Optional.empty();
        }
        return Optional.of(entity);
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
        return getEntity(entityFacade.nid());
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
        StampEntity entity = getEntityFast(nid);
        if (entity == null || entity.canceled()) {
            return Optional.empty();
        }
        return Optional.of(entity);
    }

    default Optional<StampEntity<StampEntityVersion>> getStamp(ImmutableList<UUID> uuidList) {
        return getStamp(nidForUuids(uuidList));
    }

    default Optional<StampEntity<StampEntityVersion>> getStamp(UUID... uuids) {
        return getStamp(nidForUuids(uuids));
    }

    default StampEntity<StampEntityVersion> getStampFast(ImmutableList<UUID> uuidList) {
        return getStampFast(nidForUuids(uuidList));
    }

    <T extends StampEntity<? extends StampEntityVersion>> T getStampFast(int nid);

    default StampEntity<StampEntityVersion> getStampFast(UUID... uuids) {
        return getStampFast(nidForUuids(uuids));
    }

    /**
     * Each time an entity is put via this method, each Flow.Subscriber
     * is notified that the entity may have changed by publishing the
     * nid of the entity.
     *
     * @param entity
     */
    void putEntity(Entity entity);

    /**
     * Each time an entity is put via this method, Flow.Subscriber
     * is not notified that the entity may have changed.
     * @param entity
     */
    void putEntityQuietly(Entity entity);

    /**
     * @param stampEntity
     * @deprecated Use putEntity instead
     */
    @Deprecated
    void putStamp(StampEntity stampEntity);

    default int nidForComponent(Component component) {
        if (component instanceof ComponentWithNid) {
            return ((ComponentWithNid) component).nid();
        }
        return nidForPublicId(component.publicId());
    }

    void invalidateCaches(Entity entity);

    void invalidateCaches(int... nids);

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

    void notifyRefreshRequired(Transaction transaction);
}
