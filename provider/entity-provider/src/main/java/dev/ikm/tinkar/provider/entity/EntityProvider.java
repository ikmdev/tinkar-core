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
package dev.ikm.tinkar.provider.entity;

import dev.ikm.tinkar.common.id.Nid;
import java.util.stream.LongStream;

import org.eclipse.collections.api.list.primitive.MutableLongList;
import org.eclipse.collections.api.set.primitive.ImmutableLongSet;

import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.common.service.internal.EntityStore;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.ikm.tinkar.common.alert.AlertObject;
import dev.ikm.tinkar.common.alert.AlertStreams;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.DataActivity;
import dev.ikm.tinkar.common.service.DefaultDescriptionForNidService;
import dev.ikm.tinkar.common.service.DiagnosticText;
import dev.ikm.tinkar.common.service.PluggableService;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceProperties;
import dev.ikm.tinkar.common.service.ServiceKeys;
import org.eclipse.collections.api.list.primitive.MutableLongList;
import org.eclipse.collections.api.factory.primitive.LongLists;
import dev.ikm.tinkar.common.service.PrimitiveDataRepair;
import dev.ikm.tinkar.common.service.ProviderController;
import dev.ikm.tinkar.common.service.PublicIdService;
import dev.ikm.tinkar.common.service.SearchService;
import dev.ikm.tinkar.common.service.ServiceLifecycleManager;
import dev.ikm.tinkar.common.service.ServiceExclusionGroup;
import dev.ikm.tinkar.common.service.ServiceLifecyclePhase;
import dev.ikm.tinkar.common.service.TinkExecutor;
import dev.ikm.tinkar.common.util.broadcast.Broadcaster;
import dev.ikm.tinkar.common.util.broadcast.SimpleBroadcaster;
import dev.ikm.tinkar.common.util.broadcast.Subscriber;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import dev.ikm.tinkar.component.Chronology;
import dev.ikm.tinkar.component.Version;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.entity.EntityText;
import dev.ikm.tinkar.entity.internal.EntityLookup;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.State;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.factory.primitive.LongSets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.primitive.ImmutableLongSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.IntStream;

import static dev.ikm.tinkar.terms.KernelTerm.DESCRIPTION_PATTERN;

//@AutoService({EntityService.class, PublicIdService.class, DefaultDescriptionForNidService.class})
public class EntityProvider implements EntityService, EntityLookup, PublicIdService, DefaultDescriptionForNidService, EntityDataRepair {

    private static final Logger LOG = LoggerFactory.getLogger(EntityProvider.class);
    private static final Cache<Long, String> STRING_CACHE = Caffeine.newBuilder().maximumSize(1024).build();
    private static final Cache<Long, Entity> ENTITY_CACHE = Caffeine.newBuilder().maximumSize(10240).build();
    /**
     * One put of a nid at a time: the merge and the cache entry made from it land in the order
     * of the merges, so the cache never holds an older union than the store does. Two puts of
     * one nid from two threads could otherwise cache the earlier merge last.
     */
    private static final Object[] PUT_LOCKS = new Object[1024];

    static {
        for (int i = 0; i < PUT_LOCKS.length; i++) {
            PUT_LOCKS[i] = new Object();
        }
    }

    private static Object putLock(long nid) {
        return PUT_LOCKS[(int) (Long.hashCode(nid) & (PUT_LOCKS.length - 1))];
    }
    private static final Cache<Long, StampEntity> STAMP_CACHE = Caffeine.newBuilder().maximumSize(1024).build();


    //Multi<Entity<? extends EntityVersion>> chronologyBroadcaster = BroadcastProcessor.create().toHotStream();
    //  <T extends Entity<? extends EntityVersion>>
    final Broadcaster<Long> processor;

    private boolean loadPhase = false;

    private final dev.ikm.tinkar.entity.LoadPhaseSearchPolicy loadPhaseSearchPolicy =
            new dev.ikm.tinkar.entity.LoadPhaseSearchPolicy();

    /**
     * TODO elegant shutdown of entityStream and others
     */
    public EntityProvider() {
        LOG.info("Constructing EntityProvider");
        this.processor = new SimpleBroadcaster<>();
    }

    @Override
    public dev.ikm.tinkar.entity.LoadPhaseSearchPolicy loadPhaseSearchPolicy() {
        return loadPhaseSearchPolicy;
    }

    public void addSubscriberWithWeakReference(Subscriber<Long> subscriber) {
        this.processor.addSubscriberWithWeakReference(subscriber);
    }

    @Override
    public String textFast(long nid) {

        // TODO use a default language coordinate instead of this hardcode routine.
        return STRING_CACHE.get(nid, integer -> {
            long[] semanticNids = EntityStore.current().semanticNidsForComponentOfPattern(nid, DESCRIPTION_PATTERN.nid());
            String anyString = null;
            String fqnString = null;
            for (long semanticNid : semanticNids) {
                EntityHandle descriptionSemanticHandle = EntityHandle.get(semanticNid);
                if (descriptionSemanticHandle.isSemantic() && descriptionSemanticHandle.expectEntity() instanceof SemanticEntity<?> descriptionSemantic) {
                    EntityHandle patternHandle = EntityHandle.get(descriptionSemantic.patternNid());
                    if (patternHandle.isPattern() && patternHandle.expectEntity() instanceof PatternEntity<?> pattern) {
                        // TODO: use version computer to get version
                        PatternEntityVersion patternEntityVersion = pattern.versions().get(0);
                        SemanticEntityVersion version = (SemanticEntityVersion) descriptionSemantic.versions().get(0);
                        int indexForMeaning = patternEntityVersion.indexForMeaning(KernelTerm.DESCRIPTION_TYPE);
                        int indexForText = patternEntityVersion.indexForMeaning(KernelTerm.TEXT_FOR_DESCRIPTION);
                        if (indexForMeaning == -1 || indexForText == -1) {
                            throw new IllegalStateException("Expecting a pattern entity with description and text fields. Found: " + EntityText.diagnostic(patternEntityVersion));
                        }
                        if (version.fieldValues().get(indexForMeaning).equals(KernelTerm.REGULAR_NAME_DESCRIPTION_TYPE)) {
                            return (String) version.fieldValues().get(indexForText);
                        }
                        if (version.fieldValues().get(indexForMeaning).equals(KernelTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE)) {
                            fqnString = (String) version.fieldValues().get(indexForText);
                        }
                        anyString = (String) version.fieldValues().get(indexForText);
                    } else {
                        // Pattern entity is not a PatternEntity. If it is absent (e.g. in gRPC/ephemeral-store mode
                        // before all entities are loaded) skip silently. Otherwise report the type mismatch.
                        if (patternHandle.isAbsent()) {
                            LOG.warn("Pattern entity absent for NID {} while resolving text for NID {} — skipping (gRPC mode)",
                                    descriptionSemantic.patternNid(), nid);
                        } else {
                            Entity<?> entity = patternHandle.expectEntity();
                            anyString = " <" + entity.nid() + ">" + entity.asUuidList().toString();
                            AlertStreams.getRoot().dispatch(AlertObject.makeError(new IllegalStateException("Expecting a pattern entity. Found: " + EntityText.diagnostic(entity))));
                        }
                    }
                } else {
                    // Description semantic handle is not a SemanticEntity. If absent (gRPC/ephemeral-store mode
                    // before all entities are loaded) skip silently. Otherwise report the type mismatch.
                    if (descriptionSemanticHandle.isAbsent()) {
                        LOG.warn("Description semantic entity absent for NID {} while resolving text for NID {} — skipping (gRPC mode)",
                                semanticNid, nid);
                    } else {
                        Entity<?> entity = descriptionSemanticHandle.expectEntity();
                        anyString = " <" + entity.nid() + "> " + entity.asUuidList().toString();
                        LOG.error("ERROR getting string for nid: " + anyString);
                        LOG.error("ERROR Nid - 2: <" + (nid - 2) + "> " + getChronology(nid - 2));
                        LOG.error("ERROR Nid - 1: <" + (nid - 1) + "> " + getChronology(nid - 1));
                        LOG.error("ERROR Nid: <" + nid + "> " + getChronology(nid));
                        LOG.error("ERROR Nid + 1: <" + (nid + 1) + "> " + getChronology(nid + 1));
                        LOG.error("ERROR Nid + 2: <" + (nid + 2) + "> " + getChronology(nid + 2));

                        AlertStreams.getRoot().dispatch(AlertObject.makeError(new IllegalStateException("Expecting a description semantic of "
                                + DiagnosticText.component(nid) + ". Found: " + EntityText.diagnostic(entity))));
                    }
                }
            }
            if (fqnString != null) {
                return fqnString;
            }
            return anyString;
        });

    }

    @Override
    public <T extends Chronology<V>, V extends Version> Optional<T> getChronology(long nid) {
        Entity entity = getEntityFast(nid);
        if (entity == null || entity.canceled()) {
            return Optional.empty();
        }
        return Optional.of((T) entity);
    }

    /**
     * Example call when resolving via RocksDB:
     *
     * <pre>{@code
     * long nid = ScopedValue
     *         .where(SCOPED_PATTERN_PUBLICID_FOR_NID, patternFacade.publicId())
     *         .call(() -> PrimitiveData.nid(semanticUUID));
     * }</pre>
     *
     * @param uuids one or more UUIDs that identify the component
     * @return the nid corresponding to the provided UUIDs
     */
    @Override
    public long nidForUuids(UUID... uuids) {
        return PrimitiveData.get().nidForUuids(uuids);
    }

    /**
     * Example call when resolving via RocksDB:
     *
     * <pre>{@code
     * long nid = ScopedValue
     *         .where(SCOPED_PATTERN_PUBLICID_FOR_NID, patternFacade.publicId())
     *         .call(() -> PrimitiveData.nid(semanticUUID));
     * }</pre>
     *
     * @param publicId for the component to obtain the nid for.
     * @return the nid corresponding to the provided UUIDs
     */
    @Override
    public long nidForPublicId(PublicId publicId) {
        return PrimitiveData.get().nidForUuids(publicId.asUuidArray());
    }

    /**
     * The lookup {@link EntityHandle} reaches through {@link EntityLookup}.
     *
     * @param nid the entity's nid
     * @return the entity, or {@code null} if the store holds none for the nid
     */
    @Override
    public Entity<?> entityOrNull(long nid) {
        return getEntityFast(nid);
    }

    /**
     * Reads an entity through the cache, cast unchecked to the type the caller in this class
     * expects. Private: outside the provider, entities are looked up through {@link EntityHandle}.
     */
    private <T extends Entity<V>, V extends EntityVersion> T getEntityFast(long nid) {
        return (T) ENTITY_CACHE.get(nid, entityNid -> {
            byte[] bytes = EntityStore.current().getBytes(nid);
            if (bytes == null) {
                return null;
            }
            return EntityRecordFactory.make(bytes);
        });
    }

    @Override
    public long nidForUuids(ImmutableList<UUID> uuidList) {
        return PrimitiveData.get().nidForUuids(uuidList);
    }

    @Override
    public StampEntity getStampFast(long nid) {
        return STAMP_CACHE.get(nid, stampNid -> {
                    byte[] bytes = EntityStore.current().getBytes(nid);
                    if (bytes == null) {
                        return null;
                    }
                    return EntityRecordFactory.make(bytes);
                }
        );
    }

    @Override
    public void putEntity(Entity entity, DataActivity activity) {
        putEntity(entity, activity, true, true);
    }

    @Override
    public void putEntityQuietly(Entity entity, DataActivity activity) {
        putEntity(entity, activity, false, true);
    }

    public void putEntityNoCache(Entity entity, DataActivity activity) {
        putEntityNoCache(entity, activity, true);
    }

    private void putEntityNoCache(Entity entity, DataActivity activity, boolean dispatch) {
        putEntity(entity, activity, dispatch, false);
    }

    private void putEntity(Entity entity, DataActivity activity, boolean dispatch, boolean addToCache) {
        invalidateCaches(entity);
        // Only a semantic has a pattern and a referenced component; every other entity passes
        // the not-applicable sentinel, Integer.MAX_VALUE (Nid.NOT_APPLICABLE), for both.
        synchronized (putLock(entity.nid())) {
            byte[] mergedEntityBytes = switch (entity) {
                case ConceptEntity conceptEntity -> {
                    STRING_CACHE.put(conceptEntity.nid(), conceptEntity.asUuidList().toString());
                    yield EntityStore.current().merge(entity.nid(), Integer.MAX_VALUE, Integer.MAX_VALUE,
                            entity.getBytes(), entity, activity);
                }
                case PatternEntity patternEntity -> {
                    STRING_CACHE.put(patternEntity.nid(), patternEntity.asUuidList().toString());
                    yield EntityStore.current().merge(entity.nid(), Integer.MAX_VALUE, Integer.MAX_VALUE,
                            entity.getBytes(), entity, activity);
                }
                case SemanticEntity semanticEntity -> {
                    STRING_CACHE.put(semanticEntity.nid(), semanticEntity.asUuidList().toString());
                    yield EntityStore.current().merge(entity.nid(),
                            semanticEntity.patternNid(),
                            semanticEntity.referencedComponentNid(),
                            entity.getBytes(), entity, activity);
                }
                case StampEntity stampEntity -> {
                    if (stampEntity.lastVersion().stateNid() == State.CANCELED.nid()) {
                        PrimitiveData.get().addCanceledStampNid(stampEntity.nid());
                    }
                    yield EntityStore.current().merge(entity.nid(), Integer.MAX_VALUE, Integer.MAX_VALUE,
                            entity.getBytes(), entity, activity);
                }
                default -> throw new IllegalStateException("Unexpected value: " + EntityText.diagnostic(entity));
            };

            if (addToCache) {
                ENTITY_CACHE.put(entity.nid(),  EntityRecordFactory.make(mergedEntityBytes));
            }
        }
        if (dispatch) {
            processor.dispatch(entity.nid());
            if (entity instanceof SemanticEntity semanticEntity) {
                processor.dispatch(semanticEntity.referencedComponentNid());
            }
        }
    }

    @Override
    public void invalidateCaches(Entity entity) {
        invalidateCaches(entity.nid());
        if (entity instanceof SemanticEntity semanticEntity) {
            invalidateCaches(semanticEntity.referencedComponentNid(), semanticEntity.patternNid());
            Entity parent = getEntityFast(semanticEntity.referencedComponentNid());
            while (parent != null) {
                switch (parent) {
                    case ConceptEntity conceptEntity -> {
                        parent = null;
                        STRING_CACHE.invalidate(conceptEntity.nid());
                    }
                    case PatternEntity patternEntity -> {
                        parent = null;
                        STRING_CACHE.invalidate(patternEntity.nid());
                    }
                    case SemanticEntity semantic -> {
                        // If semantic is a dialect, might invalidate preferred description,
                        // so need to go up to concept or pattern to invalidate strings in cache.
                        parent = getEntityFast(semantic.referencedComponentNid());
                        STRING_CACHE.invalidate(semantic.nid());
                    }
                    case StampEntity stampEntity -> {
                        // A semantic can reference a STAMP (e.g. a commit-provenance comment); terminate the
                        // walk and invalidate the stamp's string cache, like the concept/pattern cases (ike-issues#757).
                        parent = null;
                        STRING_CACHE.invalidate(stampEntity.nid());
                    }
                    default -> throw new IllegalStateException("Unexpected value: " + EntityText.diagnostic(parent));
                }
            }
        }
    }

    @Override
    public void invalidateCaches(long... nids) {
        for (long nid : nids) {
            STRING_CACHE.invalidate(nid);
            ENTITY_CACHE.invalidate(nid);
            STAMP_CACHE.invalidate(nid);
        }
    }

    @Override
    public Entity unmarshalChronology(byte[] bytes) {
        return EntityRecordFactory.make(bytes);
    }

    @Override
    public void forEachSemanticOfPattern(long patternNid, Consumer<SemanticEntity<SemanticEntityVersion>> procedure) {
        if (EntityService.keysNoSemantics(patternNid)) {
            return;
        }
        EntityStore.current().forEachSemanticNidOfPattern(patternNid, (long nid) -> acceptSemantic(nid, procedure));
    }

    @Override
    public Stream<SemanticEntity<SemanticEntityVersion>> semanticsOfPattern(long patternNid) {
        if (EntityService.keysNoSemantics(patternNid)) {
            return Stream.empty();
        }
        return semantics(EntityStore.current().semanticNidsOfPattern(patternNid));
    }

    @Override
    public Stream<SemanticEntity<SemanticEntityVersion>> semanticsForComponent(long componentNid) {
        return semantics(EntityStore.current().semanticNidsForComponent(componentNid));
    }

    @Override
    public Stream<SemanticEntity<SemanticEntityVersion>> semanticsForComponentOfPattern(long componentNid, long patternNid) {
        return semantics(EntityStore.current().semanticNidsForComponentOfPattern(componentNid, patternNid));
    }

    /**
     * The semantics with these nids, read as the stream reaches them; a nid with no entity, or
     * whose entity is not a semantic, is passed over.
     */
    @SuppressWarnings("unchecked")
    private Stream<SemanticEntity<SemanticEntityVersion>> semantics(long[] nids) {
        return LongStream.of(nids)
                .mapToObj(nid -> (Entity<?>) getEntityFast(nid))
                .filter(entity -> entity instanceof SemanticEntity)
                .map(entity -> (SemanticEntity<SemanticEntityVersion>) entity);
    }

    /** Gives the consumer the semantic with this nid, unless no entity has it or it is not a semantic. */
    @SuppressWarnings("unchecked")
    private void acceptSemantic(long nid, Consumer<SemanticEntity<SemanticEntityVersion>> procedure) {
        if ((Entity<?>) getEntityFast(nid) instanceof SemanticEntity semantic) {
            procedure.accept((SemanticEntity<SemanticEntityVersion>) semantic);
        }
    }

    @Override
    public void forEachSemanticForComponent(long componentNid, Consumer<SemanticEntity<SemanticEntityVersion>> procedure) {
        EntityStore.current().forEachSemanticNidForComponent(componentNid, (long nid) -> acceptSemantic(nid, procedure));
    }

    @Override
    public void forEachSemanticForComponentOfPattern(long componentNid, long patternNid, Consumer<SemanticEntity<SemanticEntityVersion>> procedure) {
        EntityStore.current().forEachSemanticNidForComponentOfPattern(componentNid, patternNid, (long nid) -> acceptSemantic(nid, procedure));
    }

    @Override
    public void notifyRefreshRequired(Transaction transaction) {
        transaction.forEachComponentInTransaction(nid -> {
            EntityHandle.get(nid).ifPresent(entity -> invalidateCaches(entity));
            this.processor.dispatch(nid);
        });
    }

    @Override
    public PublicId publicId(long nid) {
        Entity<?> entity = getEntityFast(nid);
        if (entity != null) {
            return entity.publicId();
        }
        // Referenced-but-absent component: resolve from the primitive store's
        // identity map — the nid was minted from a public id even if no entity
        // was ever written for it.
        return PrimitiveData.get().publicIdForNid(nid);
    }

    @Override
    public <T extends Chronology<V>, V extends Version> Optional<T> getChronology(PublicId publicId) {
        Entity entity;
        if (publicId instanceof EntityFacade entityFacade) {
            entity = getEntityFast(entityFacade.nid());
        } else {
            entity = getEntityFast(nidForPublicId(publicId));
        }
        if (entity == null || entity.canceled()) {
            return Optional.empty();
        }
        return Optional.of((T) entity);
    }

    public static class CacheProvider implements CachingService {

        @Override
        public void reset() {
            LOG.info("Resetting Entity Caches");
            STRING_CACHE.invalidateAll();
            ENTITY_CACHE.invalidateAll();
            STAMP_CACHE.invalidateAll();
        }
    }

    @Override
    public void dispatch(Long item) {
        this.processor.dispatch(item);
    }

    @Override
    public void removeSubscriber(Subscriber<Long> subscriber) {
        this.processor.removeSubscriber(subscriber);
    }

    @Override
    public void erase(Entity entity) {
        if (PrimitiveData.get() instanceof PrimitiveDataRepair primitiveDataRepair) {
            primitiveDataRepair.erase(entity.nid());
        } else {
            throw new UnsupportedOperationException("PrimitiveDataRepair is not supported by: " +
                    PrimitiveData.get().getClass().getName());
        }
    }

    @Override
    public Future<Entity> mergeThenErase(Entity entityToMergeInto, Entity entityToErase) {
        if (entityToMergeInto.getClass().equals(entityToMergeInto.getClass())) {
            if (PrimitiveData.get() instanceof PrimitiveDataRepair primitiveDataRepair) {
                FutureTask<Entity> mergeThenEraseTask = new FutureTask<>(() -> {
                    Future<Entity> mergedEntity = mergeEntities(entityToMergeInto, entityToErase);
                    Entity entityToKeep = mergedEntity.get();
                    erase(entityToErase);
                    primitiveDataRepair.put(entityToMergeInto.nid(), mergedEntity.get().getBytes());
                    return entityToKeep;
                });
                return (Future<Entity>) TinkExecutor.threadPool().submit(mergeThenEraseTask);
            } else {
                throw new UnsupportedOperationException("PrimitiveDataRepair is not supported by: " +
                        PrimitiveData.get().getClass().getName());
            }
        } else {
            throw new IllegalStateException("Cannot merge entities of different types: \n" +
                    EntityText.diagnostic(entityToErase) + "\n\n" + EntityText.diagnostic(entityToMergeInto));
        }
    }

    private static Future<Entity> mergeEntities(Entity<?> entityToMergeInto, Entity<?> entityToMergeFrom) {
        // TODO Need to handle different IDs. ?
        ImmutableLongSet entityOneStampNidSet = LongSets.immutable.ofAll(entityToMergeInto.versions().stream().mapToLong(version -> version.stampNid()));
        ImmutableLongSet entityTwoStampNidSet = LongSets.immutable.ofAll(entityToMergeFrom.versions().stream().mapToLong(version -> version.stampNid()));
        ImmutableLongSet stampDifferenceNids = entityOneStampNidSet.difference(entityTwoStampNidSet);
        ImmutableLongSet stampUnionNids = entityOneStampNidSet.intersect(entityTwoStampNidSet);
        ImmutableLongSet allStampNids = stampDifferenceNids.newWithAll(stampUnionNids);

        for (long unionStamp : stampUnionNids.toArray()) {
            if (!entityToMergeInto.getVersion(unionStamp).equals(entityToMergeFrom.getVersion(unionStamp))) {
                return EntityMergeServiceFinder.adjudicatedMerge(entityToMergeInto, entityToMergeFrom);
            }
        }
        // At this point, all versions with the same stamps have equal fields.
        if (stampDifferenceNids.isEmpty()) {
            return new FutureTask<>(() -> entityToMergeInto);
        }

        // Check to see if any of the stampDifferenceNids versions have the same time, module, and path
        for (long differenceStampNid : stampDifferenceNids.toArray()) {
            StampEntity differenceStamp = EntityService.get().getStampFast(differenceStampNid);
            for (long anyStampNid : allStampNids.toArray()) {
                if (differenceStampNid != anyStampNid) {
                    StampEntity anyStamp = EntityService.get().getStampFast(anyStampNid);
                    if (differenceStamp.time() == anyStamp.time() &&
                            differenceStamp.moduleNid() == anyStamp.moduleNid() &&
                            differenceStamp.pathNid() == anyStamp.pathNid()) {
                        // Can't have 2 changes at the same virtual point of time, module, path
                        return EntityMergeServiceFinder.adjudicatedMerge(entityToMergeInto, entityToMergeFrom);
                    }
                }
            }
        }
        // At this point, all stamps represent distinct time, module, and path. We can just merge the versions.
        return switch (entityToMergeInto) {
            case ConceptRecord conceptOneRecord when entityToMergeFrom instanceof ConceptRecord conceptTwoRecord
                    -> mergeAllConceptVersions(conceptOneRecord, conceptTwoRecord);
            case PatternRecord patternOneRecord when entityToMergeFrom instanceof PatternRecord patternTwoRecord
                -> mergeAllPatternVersions(patternOneRecord, patternTwoRecord);
            case SemanticRecord semanticOneRecord when entityToMergeFrom instanceof SemanticRecord semanticTwoRecord
                    -> mergeAllSemanticVersions(semanticOneRecord, semanticTwoRecord);
            default -> throw new IllegalStateException("Can't merge:\n" + EntityText.diagnostic(entityToMergeInto) + "\nand:\n" + EntityText.diagnostic(entityToMergeFrom));
        };
    }

    private static Future<Entity> mergeAllPatternVersions(PatternRecord patternOneRecord, PatternRecord patternTwoRecord) {
        // All versions are distinct. Merge using union...
        RecordListBuilder<PatternVersionRecord> versionList = RecordListBuilder.make();
        patternOneRecord.versions().forEach(versionRecord -> versionList.add(versionRecord));
        patternTwoRecord.versions().forEach(versionRecord -> versionList.add(versionRecord));
        MutableSet<UUID> additionalUuids = Sets.mutable.ofAll(patternOneRecord.asUuidList());
        additionalUuids.addAll(patternTwoRecord.asUuidList().castToList());
        additionalUuids.remove(new UUID(patternOneRecord.mostSignificantBits(), patternOneRecord.leastSignificantBits()));
        ImmutableLongList additionalUuidLongs = null;
        if (additionalUuids.notEmpty()) {
            additionalUuidLongs = UuidUtil.asImmutableLongList(additionalUuids.toArray(new UUID[additionalUuids.size()] ));
        }
        PatternRecordBuilder builder = patternOneRecord.with().versions(versionList).additionalUuidLongs(additionalUuidLongs);
        return new FutureTask<>(() -> builder.build());
    }

    private static Future<Entity> mergeAllSemanticVersions(SemanticRecord semanticOneRecord, SemanticRecord semanticTwoRecord) {
        // All versions are distinct. Merge using union...
        RecordListBuilder<SemanticVersionRecord> versionList = RecordListBuilder.make();
        semanticOneRecord.versions().forEach(versionRecord -> versionList.add(versionRecord));
        semanticTwoRecord.versions().forEach(versionRecord -> versionList.add(versionRecord));
        MutableSet<UUID> additionalUuids = Sets.mutable.ofAll(semanticOneRecord.asUuidList());
        additionalUuids.addAll(semanticTwoRecord.asUuidList().castToList());
        additionalUuids.remove(new UUID(semanticOneRecord.mostSignificantBits(), semanticOneRecord.leastSignificantBits()));
        ImmutableLongList additionalUuidLongs = null;
        if (additionalUuids.notEmpty()) {
            additionalUuidLongs = UuidUtil.asImmutableLongList(additionalUuids.toArray(new UUID[additionalUuids.size()] ));
        }
       SemanticRecordBuilder builder = semanticOneRecord.with().versions(versionList).additionalUuidLongs(additionalUuidLongs);
        return new FutureTask<>(() -> builder.build());
    }

    private static FutureTask<Entity> mergeAllConceptVersions(ConceptRecord conceptOneRecord, ConceptRecord conceptTwoRecord) {
        // All versions are distinct. Merge using union...
        RecordListBuilder<ConceptVersionRecord> versionList = RecordListBuilder.make();
        conceptOneRecord.versions().forEach(conceptVersionRecord -> versionList.add(conceptVersionRecord));
        conceptTwoRecord.versions().forEach(conceptVersionRecord -> versionList.add(conceptVersionRecord));
        MutableSet<UUID> additionalUuids = Sets.mutable.ofAll(conceptOneRecord.asUuidList());
        additionalUuids.addAll(conceptTwoRecord.asUuidList().castToList());
        additionalUuids.remove(new UUID(conceptOneRecord.mostSignificantBits(), conceptOneRecord.leastSignificantBits()));
        ImmutableLongList additionalUuidLongs = null;
        if (additionalUuids.notEmpty()) {
            additionalUuidLongs = UuidUtil.asImmutableLongList(additionalUuids.toArray(new UUID[additionalUuids.size()] ));
        }
        ConceptRecordBuilder builder = conceptOneRecord.with().versions(versionList).additionalUuidLongs(additionalUuidLongs);
        return new FutureTask<>(() -> builder.build());
    }

    @Override
    public boolean isLoadPhase() {
        return loadPhase;
    }

    @Override
    public void beginLoadPhase() {
        loadPhase = true;
        loadPhaseSearchPolicy.reset();
        PrimitiveData.get().setLoadPhase(true);
        runningSearchService().ifPresent(search -> search.setLoadPhase(true));
    }

    @Override
    public void endLoadPhase() {
        loadPhase = false;
        PrimitiveData.get().setLoadPhase(false);
        runningSearchService().ifPresent(search -> search.setLoadPhase(false));
        processor.dispatch((long) Nid.NONE); // everything changed: the none sentinel, widened
    }

    private static Optional<SearchService> runningSearchService() {
        return ServiceLifecycleManager.get().getRunningService(SearchService.class);
    }

    /**
     * Lists and cancels uncommitted stamps during shutdown.
     * This method is called by data providers during their shutdown sequence to ensure
     * data integrity by canceling any stamps that were left uncommitted outside of transactions.
     *
     * @param stampNids the collection of stamp NIDs to check for uncommitted stamps
     */
    public void listAndCancelUncommittedStamps(long[] stampNids) {
        LOG.debug("Searching for canceled stamps in set of size {}", stampNids.length);
        for (long stampNid : stampNids) {
            try {
                StampEntity stamp = getStampFast(stampNid);
                if (stamp == null) {
                    LOG.debug("No stamp found for nid: {}", stampNid);
                    continue;
                }
                if (stamp.lastVersion() == null) {
                    LOG.info("Null last version for stamp with nid: {}", stampNid);
                } else {
                    if (stamp.time() == Long.MAX_VALUE && Transaction.forStamp(stamp).isEmpty()) {
                        // Uncommitted stamp found outside a transaction on restart. Set to canceled.
                        cancelUncommittedStamp(stampNid, (StampRecord) stamp);
                        PrimitiveData.get().addCanceledStampNid(stampNid);
                    } else if (stamp.lastVersion().stateNid() == State.CANCELED.nid()) {
                        PrimitiveData.get().addCanceledStampNid(stampNid);
                    }
                }
            } catch (Exception e) {
                LOG.error("Error processing stamp {}", stampNid, e);
            }
        }
    }

    /**
     * Cancels every stamp in the store left uncommitted outside a transaction: the
     * {@link ServiceKeys#CANCEL_UNCOMMITTED_STAMPS_AT_STARTUP} option, run as the entity service
     * starts, after the data store has opened and before anything has read from it.
     */
    void cancelUncommittedStamps() {
        MutableLongList stampNids = LongLists.mutable.empty().asSynchronized();
        EntityStore.current().forEachStampNid(stampNids::add);
        LOG.info("Canceling uncommitted stamps at startup, among {} stamps", stampNids.size());
        listAndCancelUncommittedStamps(stampNids.toSortedArray());
    }

    private void cancelUncommittedStamp(long stampNid, StampRecord stamp) {
        LOG.warn("Canceling uncommitted stamp: {}", stamp.publicId().asUuidList());
        StampVersionRecord lastVersion = stamp.lastVersion();
        StampVersionRecord canceledVersion = lastVersion.with()
                .time(Long.MIN_VALUE)
                .stateNid(State.CANCELED.nid())
                .build();
        StampEntity canceledStamp = stamp
                .without(lastVersion)
                .with(canceledVersion)
                .build();
        putEntity(canceledStamp, DataActivity.DATA_REPAIR);
    }

    /**
     * Controller for EntityProvider lifecycle management.
     * <p>     * Integrates with {@link dev.ikm.tinkar.common.service.ServiceLifecycleManager} and provides
     * service discovery for EntityService, PublicIdService, and DefaultDescriptionForNidService.
     */
    public static class Controller extends ProviderController<EntityProvider> {

        @Override
        protected EntityProvider createProvider() {
            return new EntityProvider();
        }

        @Override
        protected void startProvider(EntityProvider provider) {
            // EntityProvider starts upon construction. Uncommitted stamps survive a restart
            // unless the deployment asks for them to be canceled as it starts.
            if (ServiceProperties.get(ServiceKeys.CANCEL_UNCOMMITTED_STAMPS_AT_STARTUP, Boolean.FALSE)) {
                provider.cancelUncommittedStamps();
            }
        }

        @Override
        protected void stopProvider(EntityProvider provider) {
            LOG.info("Stopping EntityProvider");
            // Uncommitted stamps are kept: they survive a restart, and change sets share them.
        }

        @Override
        protected String getProviderName() {
            return "EntityProvider";
        }

        @Override
        public ImmutableList<Class<?>> serviceClasses() {
            // EntityProvider implements three service interfaces
            return Lists.immutable.of(
                    EntityService.class,
                    PublicIdService.class,
                    DefaultDescriptionForNidService.class
            );
        }

        @Override
        public ServiceLifecyclePhase getLifecyclePhase() {
            return ServiceLifecyclePhase.ENTITIES;
        }

        @Override
        public int getSubPriority() {
            return 10; // Start early in ENTITIES phase
        }

        @Override
        public Optional<ServiceExclusionGroup> getMutualExclusionGroup() {
            return Optional.empty(); // Not mutually exclusive
        }
    }
}
