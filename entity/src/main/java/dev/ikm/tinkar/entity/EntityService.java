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

import dev.ikm.tinkar.common.service.internal.EntityStore;
import dev.ikm.tinkar.common.id.IntIdList;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.DataActivity;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceLifecycleManager;
import dev.ikm.tinkar.common.service.TinkExecutor;
import dev.ikm.tinkar.common.util.broadcast.Broadcaster;
import dev.ikm.tinkar.component.Chronology;
import dev.ikm.tinkar.component.ChronologyService;
import dev.ikm.tinkar.component.Component;
import dev.ikm.tinkar.component.Version;
import dev.ikm.tinkar.entity.export.ExportEntitiesToProtobufFile;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.entity.EntityStringUtil;
import dev.ikm.tinkar.terms.ComponentWithNid;
import dev.ikm.tinkar.terms.EntityBinding;
import dev.ikm.tinkar.terms.EntityFacade;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static dev.ikm.tinkar.common.service.PrimitiveData.SCOPED_PATTERN_PUBLICID_FOR_NID;
import static dev.ikm.tinkar.entity.Entity.LOG;

public interface EntityService extends ChronologyService, Broadcaster<Integer> {
    static EntityService get() {
        return ServiceLifecycleManager.get()
                .getRunningService(EntityService.class)
                .orElseThrow(() -> new NoSuchElementException(
                        "No EntityService found. Ensure ServiceLifecycleManager has started services."));
    }

    default CompletableFuture<dev.ikm.tinkar.common.service.EntityCountSummary> fullExport(File file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return TinkExecutor.ioThreadPool().submit(new ExportEntitiesToProtobufFile(file)).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }, TinkExecutor.ioThreadPool());
    }

    default CompletableFuture<dev.ikm.tinkar.common.service.EntityCountSummary> temporalExport(File file, long fromEpoch, long toEpoch) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return TinkExecutor.ioThreadPool().submit(new ExportEntitiesToProtobufFile(file, fromEpoch, toEpoch)).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }, TinkExecutor.ioThreadPool());
    }

    default CompletableFuture<dev.ikm.tinkar.common.service.EntityCountSummary> membershipExport(File file, List<PublicId> membershipTags) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return TinkExecutor.ioThreadPool().submit(new ExportEntitiesToProtobufFile(file, membershipTags)).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }, TinkExecutor.ioThreadPool());
    }

    default CompletableFuture<dev.ikm.tinkar.common.service.EntityCountSummary> loadData(File file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return TinkExecutor.ioThreadPool().submit(new LoadEntitiesFromProtobufFile(file)).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }, TinkExecutor.ioThreadPool());
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


        default int nidForUuids(ImmutableList<UUID> uuidList) {
            return nidForPublicId(PublicIds.of(uuidList.toArray(new UUID[uuidList.size()])));
        }

        default Optional<StampEntity<StampEntityVersion>> getStamp(Component component) {
        return getStamp(nidForPublicId(component.publicId()));
    }

    default Optional<StampEntity<StampEntityVersion>> getStamp(int nid) {
        StampEntity entity = (StampEntity) EntityHandle.get(nid).orNull();
        if (entity == null || entity.canceled()) {
            return Optional.empty();
        }
        return Optional.of(entity);
    }

    /**
     * Iterates through all stamp entities in the system and applies the given consumer function to each stamp entity.
     * The method retrieves all stamp NIDs, resolves them to their respective entities, and processes them if they
     * are instances of {@code StampEntity}. Any unexpected entity types are logged as errors.
     *
     * @param consumer a {@code Consumer} that processes each {@code StampEntity} encountered during the iteration.
     *                 The consumer is invoked for each valid {@code StampEntity} found.
     */
    default void forEachStampEntity(Consumer<StampEntity<StampEntityVersion>> consumer) {
        EntityStore.current().forEachStampNid(nid -> {
            EntityHandle.get(nid).ifPresent(entity -> {
                switch (entity) {
                    case StampEntity stampEntity -> consumer.accept(stampEntity);
                    case ConceptEntity conceptEntity -> LOG.error("Unexpected concept entity in stamp iteration: {}", conceptEntity);
                    case PatternEntity patternEntity -> LOG.error("Unexpected pattern entity in stamp iteration: {}", patternEntity);
                    case SemanticEntity semanticEntity -> LOG.error("Unexpected semantic entity in stamp iteration: {}", semanticEntity);
                    default -> throw new IllegalStateException("Unexpected value: " + EntityText.diagnostic(entity));
                }
            });
        });
    }

    /**
     * Iterates over all concept entities and applies the given consumer to each entity.
     * This method retrieves all concept entity identifiers, resolves them to entity objects,
     * and applies the specified processing logic via the provided {@code Consumer}.
     * Non-concept entities are logged as unexpected occurrences.
     *
     * @param consumer the consumer to process each {@code ConceptEntity}. The consumer will
     *                 receive each resolved concept entity during the iteration.
     */
    default void forEachConceptEntity(Consumer<ConceptEntity<ConceptEntityVersion>> consumer) {
        EntityStore.current().forEachConceptNid(nid -> {
            EntityHandle.get(nid).ifPresent(entity -> {
                switch (entity) {
                    case ConceptEntity conceptEntity -> consumer.accept(conceptEntity);
                    case StampEntity stampEntity -> LOG.error("Unexpected stamp entity in concept iteration: {}", stampEntity);
                    case PatternEntity patternEntity -> LOG.error("Unexpected pattern entity in concept iteration: {}", patternEntity);
                    case SemanticEntity semanticEntity -> LOG.error("Unexpected semantic entity in concept iteration: {}", semanticEntity);
                    default -> throw new IllegalStateException("Unexpected value: " + EntityText.diagnostic(entity));
                }
            });
        });
    }

    default void forEachEntity(ImmutableIntList entityNids, Consumer<Entity<?>> consumer) {
        EntityStore.current().forEach(entityNids, (bytes, _) -> {
            Entity<EntityVersion> entity = EntityRecordFactory.make(bytes);
            consumer.accept(entity);
        });
    }

    /**
     * Iterates over all pattern entities and applies the specified {@code consumer} function to each one.
     * This method retrieves pattern entities using their pattern NIDs and processes them if they are of the
     * correct entity type. It logs errors for unexpected entity types encountered during iteration.
     *
     * @param consumer a {@link Consumer} functional interface to process each {@link PatternEntity}
     *                 with its associated {@link PatternEntityVersion}
     */
    default void forEachPatternEntity(Consumer<PatternEntity<PatternEntityVersion>> consumer) {
        EntityStore.current().forEachPatternNid(nid -> {
            EntityHandle.get(nid).ifPresent(entity -> {
                switch (entity) {
                    case PatternEntity patternEntity -> consumer.accept(patternEntity);
                    case StampEntity stampEntity -> LOG.error("Unexpected stamp entity in pattern iteration: {}", stampEntity);
                    case ConceptEntity conceptEntity -> LOG.error("Unexpected concept entity in pattern iteration: {}", conceptEntity);
                    case SemanticEntity semanticEntity -> LOG.error("Unexpected semantic entity in pattern iteration: {}", semanticEntity);
                    default -> throw new IllegalStateException("Unexpected value: " + EntityText.diagnostic(entity));
                }
            });
        });
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
     * Defaults to an activity of DataActivity.SYNCHRONIZABLE_EDIT.
     *
     * @param entity
     */
    default void putEntity(Entity entity) {
        putEntity(entity, DataActivity.SYNCHRONIZABLE_EDIT);
    }

    /**
     * Inserts or updates a given entity and associates it with a specified data activity.
     * Each time an entity is put via this method, each Flow.Subscriber is notified that the
     * entity may have changed by publishing the nid of the entity.
     * TODO: convert to the event bus instead of the Flow.Subscriber
     * @param entity The entity to be put into the system.
     * @param activity The data activity associated with the entity.
     */
    void putEntity(Entity entity, DataActivity activity);

    default void putEntityNoCache(Entity entity) {
        putEntityNoCache(entity, DataActivity.SYNCHRONIZABLE_EDIT);
    }

    void putEntityNoCache(Entity entity, DataActivity activity);

    /**
     * Each time an entity is put via this method, Flow.Subscriber
     * is not notified that the entity may have changed.
     * @param entity
     */
    void putEntityQuietly(Entity entity, DataActivity activity);
    /**
     * Each time an entity is put via this method, Flow.Subscriber
     * is not notified that the entity may have changed.
     *
     * Defaults to an activity of DataActivity.SYNCHRONIZABLE_EDIT.
     *
     * @param entity
     */
    default void putEntityQuietly(Entity entity) {
        putEntityQuietly(entity, DataActivity.SYNCHRONIZABLE_EDIT);
    }

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
            UUID[] uuids = EntityHandle.get(nid).expectEntity().publicId().asUuidArray();
            Arrays.sort(uuids);
            for (UUID nidUuid : uuids) {
                uuidList.add(nidUuid);
            }
        }
    }

    void forEachSemanticOfPattern(int patternNid, Consumer<SemanticEntity<SemanticEntityVersion>> procedure);

    /**
     * The semantics of a pattern. Each is read as the stream reaches it, so a stream that stops
     * early ({@code findAny}, {@code anyMatch}, {@code limit}) reads only what it needed.
     *
     * @param patternNid the pattern
     * @return the pattern's semantics, in no particular order
     */
    Stream<SemanticEntity<SemanticEntityVersion>> semanticsOfPattern(int patternNid);

    /**
     * The semantics that reference a component, read as the stream reaches them.
     *
     * @param componentNid the referenced component
     * @return the semantics referencing it, in no particular order
     */
    Stream<SemanticEntity<SemanticEntityVersion>> semanticsForComponent(int componentNid);

    /**
     * The semantics of a pattern that reference a component, read as the stream reaches them:
     * a component's descriptions, its stated axioms, its membership in a pattern.
     *
     * @param componentNid the referenced component
     * @param patternNid   the pattern
     * @return the pattern's semantics referencing the component, in no particular order
     */
    Stream<SemanticEntity<SemanticEntityVersion>> semanticsForComponentOfPattern(int componentNid, int patternNid);

    /**
     * Every semantic in the store, of every pattern.
     *
     * @param consumer receives each semantic
     */
    default void forEachSemanticEntity(Consumer<SemanticEntity<SemanticEntityVersion>> consumer) {
        forEachPatternEntity(pattern -> forEachSemanticOfPattern(pattern.nid(), consumer));
    }

    /**
     * Every entity in the store: concepts, patterns, semantics and stamps.
     *
     * @param consumer receives each entity
     */
    default void forEachEntity(Consumer<Entity<?>> consumer) {
        EntityStore.current().forEach((bytes, _) -> consumer.accept(EntityRecordFactory.make(bytes)));
    }

    /**
     * Every entity in the store, given to the consumer from several threads at once, in no
     * particular order: for work over the whole store, such as rebuilding an index.
     *
     * @param consumer receives each entity; must be safe to call concurrently
     */
    default void forEachEntityParallel(Consumer<Entity<?>> consumer) {
        EntityStore.current().forEachParallel((bytes, _) -> {
            if (bytes != null && bytes.length > 0) {
                consumer.accept(EntityRecordFactory.make(bytes));
            }
        });
    }

    /**
     * How many entities the store holds, of every kind, counted without reading them.
     *
     * @return the number of entities in the store
     */
    default long countEntities() {
        java.util.concurrent.atomic.LongAdder count = new java.util.concurrent.atomic.LongAdder();
        EntityStore.current().forEachParallel((bytes, _) -> {
            if (bytes != null && bytes.length > 0) {
                count.increment();
            }
        });
        return count.sum();
    }

    /**
     * How many semantics of a pattern the store holds, counted without reading them: the length
     * of the pattern's index. A view that shows the first few semantics of a pattern and the
     * number of the rest uses this, with {@link #semanticsOfPattern(int)} limited, so it reads
     * only what it shows; counting with {@link #forEachSemanticOfPattern} reads every semantic.
     *
     * @param patternNid the pattern
     * @return the number of semantics its index lists
     */
    default int countSemanticsOfPattern(int patternNid) {
        if (keysNoSemantics(patternNid)) {
            return 0;
        }
        return EntityStore.current().semanticNidsOfPattern(patternNid).length;
    }

    /**
     * Whether a pattern is one whose elements are not semantics: the concept, stamp and pattern
     * binding patterns. A pattern-keyed store keys every concept, stamp and pattern under one of
     * them, so their index lists concepts, stamps and patterns; none of them has a semantic.
     *
     * @param patternNid the pattern
     * @return {@code true} for the concept, stamp and pattern binding patterns
     */
    static boolean keysNoSemantics(int patternNid) {
        // By nid: most stores keep no map from a nid back to its public id.
        for (PublicId binding : List.of(EntityBinding.Concept.pattern().publicId(),
                EntityBinding.Stamp.pattern().publicId(), EntityBinding.Pattern.pattern().publicId())) {
            if (PrimitiveData.get().hasPublicId(binding) && PrimitiveData.get().nidForPublicId(binding) == patternNid) {
                return true;
            }
        }
        return false;
    }

    void forEachSemanticForComponent(int componentNid, Consumer<SemanticEntity<SemanticEntityVersion>> procedure);

    void forEachSemanticForComponentOfPattern(int componentNid, int patternNid, Consumer<SemanticEntity<SemanticEntityVersion>> procedure);

    void notifyRefreshRequired(Transaction transaction);

    boolean isLoadPhase();

    void endLoadPhase();

    void beginLoadPhase();

    /**
     * The active {@link LoadPhaseSearchPolicy} for this load phase. Providers
     * consult it in their {@code merge(...)} hot path to decide whether each
     * incoming merge should be live-indexed (small change-sets) or skipped
     * with a fallback recreate at endLoadPhase (large change-sets).
     *
     * <p>The same instance is reused across loadPhases; {@code beginLoadPhase}
     * is responsible for resetting its state.
     *
     * @return never {@code null}
     */
    LoadPhaseSearchPolicy loadPhaseSearchPolicy();


    /**
     * Retrieves the NID for an entity of a specific pattern.
     * <p>     * <b>Why use this method:</b> This method supports the evolution toward making EntityKey more efficient
     * by incorporating a Pattern part and a sequence within that pattern part. This approach enables better
     * organization and performance optimization of entity identifiers.
     * <p>     * <b>Deprecation Note:</b> The {@code Entity.nid()} methods have been deprecated in favor of these
     * {@code nidForXxx} methods, which provide explicit pattern context and prepare the codebase for
     * enhanced identifier management.
     *
     * @param patternNid the NID of the pattern that defines the entity's structure
     * @param entityPublicId the public ID of the entity
     * @return the NID associated with the given entity within the specified pattern context
     */
    default int nidFor(int patternNid, PublicId entityPublicId) {
        PublicId patternPublicId = EntityHandle.get(patternNid).expectEntity().publicId();
        return ScopedValue
                .where(SCOPED_PATTERN_PUBLICID_FOR_NID, patternPublicId)
                .call(() -> nidForPublicId(entityPublicId));
    }

    /**
     * Retrieves the NID for a semantic entity associated with a specific pattern.
     * <p>     * <b>Why use this method:</b> This method supports the evolution toward making EntityKey more efficient
     * by incorporating a Pattern part and a sequence within that pattern part. By explicitly specifying the
     * pattern context, this method enables better performance and more accurate identifier resolution for semantics.
     * <p>     * <b>Deprecation Note:</b> The {@code Entity.nid()} methods have been deprecated in favor of these
     * {@code nidForXxx} methods, which provide explicit pattern context and prepare the codebase for
     * enhanced identifier management.
     *
     * @param patternPublicId the public ID of the pattern that defines the semantic's structure
     * @param semanticPublicId the public ID of the semantic entity
     * @return the NID associated with the given semantic within the specified pattern context
     */
    default int nidForSemantic(PublicId patternPublicId, PublicId semanticPublicId) {
        return ScopedValue
                .where(SCOPED_PATTERN_PUBLICID_FOR_NID, patternPublicId)
                .call(() -> nidForPublicId(semanticPublicId));
    }

    /**
     * Retrieves the NID for a pattern entity.
     * <p>     * <b>Why use this method:</b> This method supports the evolution toward making EntityKey more efficient
     * by incorporating a Pattern part and a sequence within that pattern part. By explicitly identifying
     * pattern entities through this method, the system can optimize identifier allocation and retrieval
     * for pattern-specific operations.
     * <p>     * <b>Deprecation Note:</b> The {@code Entity.nid()} methods have been deprecated in favor of these
     * {@code nidForXxx} methods, which provide explicit pattern context and prepare the codebase for
     * enhanced identifier management.
     *
     * @param patternPublicId the public ID of the pattern entity
     * @return the NID associated with the given pattern
     */
    default int nidForPattern(PublicId patternPublicId) {
        return ScopedValue
                .where(SCOPED_PATTERN_PUBLICID_FOR_NID, EntityBinding.Pattern.pattern())
                .call(() -> nidForPublicId(patternPublicId));
    }

    /**
     * Retrieves the NID for a STAMP (Status, Time, Author, Module, Path) entity.
     * <p>     * <b>Why use this method:</b> This method supports the evolution toward making EntityKey more efficient
     * by incorporating a Pattern part and a sequence within that pattern part. By explicitly handling
     * STAMP entities through this method, the system can optimize identifier management for versioning
     * and provenance tracking operations.
     * <p>     * <b>Deprecation Note:</b> The {@code Entity.nid()} methods have been deprecated in favor of these
     * {@code nidForXxx} methods, which provide explicit pattern context and prepare the codebase for
     * enhanced identifier management.
     *
     * @param stampPublicId the public ID of the STAMP entity
     * @return the NID associated with the given STAMP
     */
    default int nidForStamp(PublicId stampPublicId) {
        return ScopedValue
                .where(SCOPED_PATTERN_PUBLICID_FOR_NID, EntityBinding.Stamp.pattern())
                .call(() -> nidForPublicId(stampPublicId));
    }

    /**
     * Retrieves the NID for a concept entity.
     * <p>     * <b>Why use this method:</b> This method supports the evolution toward making EntityKey more efficient
     * by incorporating a Pattern part and a sequence within that pattern part. By explicitly identifying
     * concept entities through this method, the system can optimize identifier allocation and retrieval
     * for concept-specific operations.
     * <p>     * <b>Deprecation Note:</b> The {@code Entity.nid()} methods have been deprecated in favor of these
     * {@code nidForXxx} methods, which provide explicit pattern context and prepare the codebase for
     * enhanced identifier management.
     *
     * @param conceptPublicId the public ID of the concept entity
     * @return the NID associated with the given concept
     */
    default int nidForConcept(PublicId conceptPublicId) {
        return ScopedValue
                .where(SCOPED_PATTERN_PUBLICID_FOR_NID, EntityBinding.Concept.pattern())
                .call(() -> nidForPublicId(conceptPublicId));
    }

    /**
     * Lists and cancels uncommitted stamps during data provider shutdown.
     * This method is called by data providers during their close() sequence to ensure
     * data integrity by canceling any stamps that were left uncommitted outside of transactions.
     * <p>     * This method must be called while EntityService is still available, typically early
     * in the data provider's shutdown sequence before the data store is closed.
     *
     * @param stampNids array of stamp NIDs to check for uncommitted stamps
     */
    void listAndCancelUncommittedStamps(int[] stampNids);

    default String recursiveEntityToString(int nid) {
        return EntityStringUtil.recursiveEntityToString(nid);
    }

    default String recursiveEntityToString(EntityFacade entityFacade) {
        return EntityStringUtil.recursiveEntityToString(entityFacade);
    }
}
