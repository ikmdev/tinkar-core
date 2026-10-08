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
package dev.ikm.tinkar.provider.spinedarray;

import org.eclipse.collections.api.block.procedure.primitive.LongProcedure;
import java.util.function.ObjLongConsumer;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;

import dev.ikm.tinkar.common.id.Nid;
import dev.ikm.tinkar.common.service.SequentialNids;
import dev.ikm.tinkar.common.service.internal.EntityStore;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.util.SetOnce;
import dev.ikm.tinkar.collection.SpinedByteArrayMap;
import dev.ikm.tinkar.collection.SpinedIntLongArrayMap;
import dev.ikm.tinkar.common.alert.AlertStreams;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.impl.NidLayout;
import dev.ikm.tinkar.common.service.*;
import dev.ikm.tinkar.provider.search.DataStoreLockProbe;
import dev.ikm.tinkar.common.sets.ConcurrentHashSet;
import dev.ikm.tinkar.common.util.ints2long.IntsInLong;
import dev.ikm.tinkar.common.util.time.Stopwatch;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import dev.ikm.tinkar.common.validation.ValidationRecord;
import dev.ikm.tinkar.common.validation.ValidationSeverity;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.common.service.SearchService;
import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import org.eclipse.collections.api.block.procedure.primitive.IntProcedure;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.factory.primitive.LongSets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.list.primitive.MutableLongList;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.LongLists;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.ObjIntConsumer;

/**
 * Maybe a hybrid of SpinedArrayProvider and MVStoreProvider is worth considering.
 * <p>SpinedArrayProvider is performing horribly because of dependency on ConcurrentUuidIntHashMap serialization.
 * TODO: consider if we remove ConcurrentUuidIntHashMap, or improve.
 * <p>MVStore performs worse when iterating over entities.
 */
public class SpinedArrayProvider implements PrimitiveDataService, EntityStore, NidGenerator, PrimitiveDataRepair {
    private static final Logger LOG = LoggerFactory.getLogger(SpinedArrayProvider.class);
    protected static final File defaultDataDirectory = new File("target/spinedarrays/");

    public enum Lifecycle {
        UNINITIALIZED, STARTING, RUNNING, STOPPING, STOPPED
    }

    public static AtomicReference<Lifecycle> lifecycle = new AtomicReference<>(Lifecycle.UNINITIALIZED);

    private static final SetOnce<SpinedArrayProvider> spinedArrayProvider = new SetOnce<>();
    public static SpinedArrayProvider get() {
        // Set lifecycle to STARTING before attempting initialization
        lifecycle.compareAndSet(Lifecycle.UNINITIALIZED, Lifecycle.STARTING);

        return spinedArrayProvider.orElseSet(() -> {
            try {
                LOG.info("SetOnce.orElseSet: Creating new SpinedArrayProvider instance");
                return new SpinedArrayProvider();
            } catch (IOException | ExecutionException | InterruptedException e) {
                // Reset lifecycle on failure
                lifecycle.set(Lifecycle.UNINITIALIZED);
                LOG.error("Failed to create SpinedArrayProvider", e);
                throw new RuntimeException(e);
            }
        });
    }

    protected static LongAdder writeSequence = new LongAdder();
    protected final CountDownLatch uuidsLoadedLatch = new CountDownLatch(1);
    final AtomicInteger nextNid = new AtomicInteger(SequentialNids.FIRST_NID);

    final ConcurrentHashMap<UUID, Integer> uuidToNidMap = ConcurrentHashMap.newMap();
    final ConcurrentHashSet<Integer> patternNids = new ConcurrentHashSet();
    final ConcurrentHashSet<Integer> conceptNids = new ConcurrentHashSet();
    final ConcurrentHashSet<Integer> semanticNids = new ConcurrentHashSet();
    final ConcurrentHashSet<Integer> stampNids = new ConcurrentHashSet();
    final ConcurrentHashMap<Integer, ConcurrentHashSet<Integer>> patternElementNidsMap = ConcurrentHashMap.newMap();

    final SpinedByteArrayMap entityToBytesMap;
    /**
     * Using "citing" instead of "referencing" to make the field names more distinct.
     */
    final SpinedIntLongArrayMap nidToCitingComponentsNidMap;

    final File nidToByteArrayMapDirectory;
    final File nidToCitingComponentNidMapDirectory;
    final File nextNidKeyFile;

    /**
     * The UUIDs of nids that have no entity — components referenced but not present, such as a
     * child listed by an imported navigation semantic whose own record was not in the changeset.
     *
     * <p>The UUID map is rebuilt from entity bytes at startup, so without this file such a nid
     * would lose its identity on restart: nothing could say which component it is, and importing
     * that component later would give it a second nid, leaving the reference dangling for good.
     */
    final File absentIdentitiesFile;
    final SetOnce<SearchService> searchService = new SetOnce<>();
    private volatile boolean loadPhase = false;
    final String name;
    final ImmutableList<ChangeSetWriterService> changeSetWriterServices;

    private SpinedArrayProvider() throws IOException, ExecutionException, InterruptedException {
        Stopwatch stopwatch = new Stopwatch();
        LOG.info("Opening SpinedArrayProvider on thread: {}", Thread.currentThread().getName());
        NidLayout.activate(NidLayout.SEQUENTIAL);
        File configuredRoot = ServiceProperties.get(ServiceKeys.DATA_STORE_ROOT, defaultDataDirectory);
        boolean expectEmpty = ServiceProperties.get(ServiceKeys.DATA_STORE_EXPECT_EMPTY, Boolean.FALSE);
        if (expectEmpty) {
            assertEmptyDataRoot(configuredRoot);
            ServiceProperties.set(ServiceKeys.DATA_STORE_EXPECT_EMPTY, Boolean.FALSE);
        }
        name = configuredRoot.getName();
        configuredRoot.mkdirs();
        LOG.info("Datastore root: " + configuredRoot.getAbsolutePath());

        this.nidToByteArrayMapDirectory = new File(configuredRoot, "nidToByteArrayMap");
        this.nidToByteArrayMapDirectory.mkdirs();
        this.nidToCitingComponentNidMapDirectory = new File(configuredRoot, "nidToCitingComponentNidMap");
        this.nidToCitingComponentNidMapDirectory.mkdirs();
        this.nextNidKeyFile = new File(configuredRoot, "nextNidKeyFile");
        this.absentIdentitiesFile = new File(configuredRoot, "absentIdentities.txt");

        this.entityToBytesMap = new SpinedByteArrayMap(new ByteArrayFileStore(nidToByteArrayMapDirectory));
        this.nidToCitingComponentsNidMap = new SpinedIntLongArrayMap(new IntLongArrayFileStore(nidToCitingComponentNidMapDirectory));

        if (nextNidKeyFile.exists()) {
            String nextNidString = Files.readString(this.nextNidKeyFile.toPath());
            nextNid.set(Integer.valueOf(nextNidString));
        }
        // Nids minted before this opening were either given entities or recorded in
        // absentIdentitiesFile by an earlier save; only newer ones need checking.
        checkedBelowNid = nextNid.get();
        loadAbsentIdentities();
        LOG.info("Submitting UUID loading task to thread pool...");
        try {
            TinkExecutor.threadPool().submit(() -> {
                Stopwatch uuidNidMapFromEntitiesStopwatch = new Stopwatch();
                LOG.info("Starting UUID strategy 2 on thread: {}", Thread.currentThread().getName());
                UuidNidCollector uuidNidCollector = new UuidNidCollector(uuidToNidMap,
                        patternNids, conceptNids, semanticNids, stampNids, patternElementNidsMap);
                try {
                    LOG.info("Executing entityToBytesMap.forEachParallel...");
                    this.entityToBytesMap.forEachParallel(uuidNidCollector);
                    LOG.info("Completed entityToBytesMap.forEachParallel, counting down latch");
                    this.uuidsLoadedLatch.countDown();
                } catch (ExecutionException | InterruptedException e) {
                    LOG.error("Error during UUID loading: " + e.getLocalizedMessage(), e);
                } finally {
                    uuidNidMapFromEntitiesStopwatch.stop();
                    LOG.info("Finished UUID strategy 2 in: " + uuidNidMapFromEntitiesStopwatch.durationString());
                    LOG.info(uuidNidCollector.report());
                }
                LOG.info("UUID loading task completed");
            }).get();
            LOG.info("UUID loading task .get() returned successfully");
        } catch (Exception e) {
            LOG.error("Failed to complete UUID loading task", e);
            throw e;
        }

        ServiceLoader<ChangeSetWriterService> changeSetServiceLoader = PluggableService.load(ChangeSetWriterService.class);
        MutableList<ChangeSetWriterService> changeSetWriters = Lists.mutable.empty();
        changeSetServiceLoader.stream().forEach(changeSetProvider -> {
            changeSetWriters.add(changeSetProvider.get());
        });
        this.changeSetWriterServices = changeSetWriters.toImmutable();
        LOG.info("\n\nLoaded {} ChangeSetWriterService(s)\n\n", changeSetWriters.size());
        if (this.changeSetWriterServices.notEmpty()) {
            LOG.info("ChangeSetWriterService(s): ", changeSetWriters);
        }

        // Index recreation is now handled by SearchProvider in INDEXING phase
        stopwatch.stop();
        LOG.info("Opened SpinedArrayProvider in: " + stopwatch.durationString());
        lifecycle.set(Lifecycle.RUNNING);

    }

    private static void assertEmptyDataRoot(File configuredRoot) {
        if (!configuredRoot.exists()) {
            return;
        }
        if (!configuredRoot.isDirectory()) {
            throw new IllegalStateException("Configured DATA_STORE_ROOT is not a directory: " + configuredRoot.getAbsolutePath());
        }
        String[] entries = configuredRoot.list();
        if (entries != null && entries.length > 0) {
            throw new IllegalStateException("Expected empty DATA_STORE_ROOT but found contents: " + configuredRoot.getAbsolutePath());
        }
    }

    @Override
    public boolean hasUuid(UUID uuid) {
        try {
            this.uuidsLoadedLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return uuidToNidMap.containsKey(uuid);
    }


    @Override
    public long writeSequence() {
        return writeSequence.sum();
    }

     @Override
    public void close() {
        if (lifecycle.compareAndSet(Lifecycle.RUNNING, Lifecycle.STOPPING) ||
            lifecycle.compareAndSet(Lifecycle.STARTING, Lifecycle.STOPPING)) {
            Stopwatch stopwatch = new Stopwatch();
            LOG.info("Closing SpinedArrayProvider");
            try {
                this.changeSetWriterServices.forEach(ChangeSetWriterService::shutdown);
                save();


                entityToBytesMap.close();
            } catch (Exception e) {
                LOG.error("Error closing SpinedArrayProvider", e);
            } finally {
                lifecycle.set(Lifecycle.STOPPED);
                stopwatch.stop();
                LOG.info("Closed SpinedArrayProvider in: " + stopwatch.durationString());
            }
        } else {
            LOG.warn("SpinedArrayProvider is not running, cannot close: " + lifecycle.get());
        }
    }

    public void save() {
        Stopwatch stopwatch = new Stopwatch();
        LOG.info("Saving SpinedArrayProvider");
        try {
            Files.writeString(this.nextNidKeyFile.toPath(), Integer.toString(nextNid.get()));
            this.entityToBytesMap.write();
            this.nidToCitingComponentsNidMap.write();
            saveAbsentIdentities();
        } catch (Exception e) {
            LOG.error("Error saving SpinedArrayProvider", e);
        } finally {
            stopwatch.stop();
            LOG.info("Save SpinedArrayProvider in: " + stopwatch.durationString());
        }
    }

    /**
     * UUIDs of nids that have no entity: loaded from {@link #absentIdentitiesFile}, and found on
     * each save among the nids minted since the previous one. Small — such nids are rare.
     */
    private final java.util.concurrent.ConcurrentHashMap<Integer, List<UUID>> absentUuids =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Nids below this have been checked for missing entities by a save. Set when the store opens. */
    private volatile int checkedBelowNid = Integer.MIN_VALUE;

    /**
     * The public id {@code nid} was minted from — for a component that is referenced but has no
     * entity in this store.
     *
     * <p>Happens when a changeset refers to a component it does not carry: a time-range export
     * includes the whole history of each changed component, and a navigation semantic in it can
     * list a child created outside the range. Importing it mints the child a nid from its UUID with
     * no entity behind it. Without this the store could not say which component that nid is, and
     * anything that converts the referring semantic back to public ids — the gRPC service, an
     * export — failed.
     */
    @Override
    public PublicId publicIdForNid(long longNid) {
        int nid = Nid.narrowChecked(longNid);
        List<UUID> known = absentUuids.get(nid);
        if (known != null && !known.isEmpty()) {
            return PublicIds.of(known);
        }
        // Not yet saved as absent — a scan of the identity map, as the ephemeral store does.
        List<UUID> uuids = new ArrayList<>();
        uuidToNidMap.forEach((uuid, mappedNid) -> {
            if (mappedNid.intValue() == nid) { // ints, not two Integer references
                uuids.add(uuid);
            }
        });
        if (uuids.isEmpty()) {
            throw new IllegalStateException("No public id minted for nid " + nid + " in this store");
        }
        return PublicIds.of(uuids);
    }

    private void loadAbsentIdentities() throws IOException {
        if (!absentIdentitiesFile.exists()) {
            return;
        }
        int loaded = 0;
        for (String line : Files.readAllLines(absentIdentitiesFile.toPath())) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 2) {
                continue;
            }
            int nid = Integer.parseInt(parts[0]);
            List<UUID> uuids = new java.util.concurrent.CopyOnWriteArrayList<>();
            for (int i = 1; i < parts.length; i++) {
                UUID uuid = UUID.fromString(parts[i]);
                uuids.add(uuid);
                uuidToNidMap.putIfAbsent(uuid, nid);
            }
            absentUuids.put(nid, uuids);
            loaded++;
        }
        LOG.info("Loaded {} identities of referenced-but-absent components", loaded);
    }

    /**
     * Writes the UUIDs of every nid still without an entity. Checks only the nids minted since the
     * last save — nids are handed out in sequence — and finds the UUIDs of any new ones in a single
     * pass over the identity map, so a save costs little unless something was actually left absent.
     */
    private void saveAbsentIdentities() throws IOException {
        absentUuids.keySet().removeIf(nid -> entityToBytesMap.get(nid) != null);
        int mintedUpTo = nextNid.get();
        java.util.Set<Integer> newlyAbsent = new java.util.HashSet<>();
        for (int nid = checkedBelowNid; nid < mintedUpTo; nid++) {
            if (!absentUuids.containsKey(nid) && entityToBytesMap.get(nid) == null) {
                newlyAbsent.add(nid);
            }
        }
        if (!newlyAbsent.isEmpty()) {
            uuidToNidMap.forEach((uuid, nid) -> {
                if (newlyAbsent.contains(nid)) {
                    absentUuids.computeIfAbsent(nid, key -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(uuid);
                }
            });
        }
        checkedBelowNid = mintedUpTo;
        if (absentUuids.isEmpty()) {
            Files.deleteIfExists(absentIdentitiesFile.toPath());
            return;
        }
        StringBuilder lines = new StringBuilder();
        absentUuids.forEach((nid, uuids) -> {
            lines.append(nid);
            uuids.forEach(uuid -> lines.append(' ').append(uuid));
            lines.append('\n');
        });
        Files.writeString(absentIdentitiesFile.toPath(), lines);
    }

    @Override
    public long nidForUuids(UUID... uuids) {
        try {
            this.uuidsLoadedLatch.await();
            if (uuids.length == 1) {
                return uuidToNidMap.computeIfAbsent(uuids[0], uuidKey -> Nid.narrowChecked(newNid()));
            }

            OptionalInt optionalNid = optionalNid(uuids);

            // Integer.MAX_VALUE, which is never a nid, marks "no nid yet" until one of the UUIDs
            // has one or the first is given a new one.
            int nid = optionalNid.isPresent() ? optionalNid.getAsInt(): Integer.MAX_VALUE;

            for (UUID uuid : uuids) {
                if (Nid.isNotApplicable(nid)) {
                    nid = uuidToNidMap.computeIfAbsent(uuid, uuidKey -> Nid.narrowChecked(newNid()));
                } else {
                    uuidToNidMap.put(uuid, nid);
                }
            }
            if (nid == Integer.MIN_VALUE) {
                throw new IllegalStateException("nid cannot be Integer.MIN_VALUE");
            }
            return nid;
        } catch (InterruptedException e) {
            LOG.error(e.getLocalizedMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private OptionalInt optionalNid(UUID... uuids) {
        for (UUID uuid : uuids) {
            if (uuidToNidMap.containsKey(uuid)) {
                return OptionalInt.of(uuidToNidMap.get(uuid));
            }
        }
        return OptionalInt.empty();
    }

    @Override
    public long newNid() {
        return nextNid.getAndIncrement();
    }

    @Override
    public long nidForUuids(ImmutableList<UUID> uuidList) {
        try {
            this.uuidsLoadedLatch.await();
            if (uuidList.size() == 1) {
                return uuidToNidMap.computeIfAbsent(uuidList.get(0), uuidKey -> Nid.narrowChecked(newNid()));
            }

            OptionalInt optionalNid = optionalNid(uuidList.toArray(new UUID[uuidList.size()]));

            // Integer.MAX_VALUE, which is never a nid, marks "no nid yet" until one of the UUIDs
            // has one or the first is given a new one.
            int nid = optionalNid.isPresent() ? optionalNid.getAsInt(): Integer.MAX_VALUE;

            for (UUID uuid : uuidList) {
                if (Nid.isNotApplicable(nid)) {
                    nid = uuidToNidMap.computeIfAbsent(uuid, uuidKey -> Nid.narrowChecked(newNid()));
                } else {
                    uuidToNidMap.put(uuid, nid);
                }
            }
            if (nid == Integer.MIN_VALUE) {
                throw new IllegalStateException("nid cannot be Integer.MIN_VALUE");
            }
            return nid;
        } catch (InterruptedException e) {
            LOG.error(e.getLocalizedMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean hasPublicId(PublicId publicId) {
        try {
            this.uuidsLoadedLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return publicId.asUuidList().stream().anyMatch(uuidToNidMap::containsKey);
    }

    @Override
    public void forEach(ObjLongConsumer<byte[]> action) {
        this.entityToBytesMap.forEach((bytes, nid) -> action.accept(bytes, nid));
    }

    @Override
    public void forEachParallel(ObjLongConsumer<byte[]> action) {
        try {
            this.entityToBytesMap.forEachParallel((bytes, nid) -> action.accept(bytes, nid));
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void forEachParallel(ImmutableLongList nids, ObjLongConsumer<byte[]> action) {
        try {
            this.entityToBytesMap.forEachParallel(narrow(nids), (bytes, nid) -> action.accept(bytes, nid));
        } catch (ExecutionException | InterruptedException e) {
            AlertStreams.dispatchToRoot(e);
        }
    }

    @Override
    public void forEach(ImmutableLongList nids, ObjLongConsumer<byte[]> action) {
        try {
            this.entityToBytesMap.forEach(narrow(nids), (bytes, nid) -> action.accept(bytes, nid));
        } catch (ExecutionException | InterruptedException e) {
            AlertStreams.dispatchToRoot(e);
        }
    }


    /** The nids of a widened list, narrowed for the spined maps, which hold int nids. */
    private static ImmutableIntList narrow(ImmutableLongList nids) {
        int[] narrowed = new int[nids.size()];
        for (int i = 0; i < narrowed.length; i++) {
            narrowed[i] = Nid.narrowChecked(nids.get(i));
        }
        return IntLists.immutable.of(narrowed);
    }

    @Override
    public byte[] getBytes(long nid) {
        return this.entityToBytesMap.get(Nid.narrowChecked(nid));
    }

    @Override
    public byte[] merge(long nid, long patternNid, long referencedComponentNid, byte[] value, Object sourceObject, DataActivity activity) {
        if (Nid.isNone(nid)) {
            LOG.error("NID should not be Integer.MIN_VALUE");
            throw new IllegalStateException("NID should not be Integer.MIN_VALUE");
        }
        if (!this.entityToBytesMap.containsKey(Nid.narrowChecked(nid))) {
            // A concept, pattern or stamp comes with the not-applicable sentinel, Integer.MAX_VALUE
            // (Nid.NOT_APPLICABLE), as its pattern; only a semantic is indexed under its pattern and
            // referenced component. An entity's pattern is read from its bytes, never from a map.
            if (!Nid.isNotApplicable(patternNid)) {
                long citationLong = IntsInLong.ints2Long(Nid.narrowChecked(nid), Nid.narrowChecked(patternNid));
                this.nidToCitingComponentsNidMap.accumulateAndGet(Nid.narrowChecked(referencedComponentNid), new long[]{citationLong},
                        PrimitiveDataService::mergeCitations);
                addToPatternElementSet(Nid.narrowChecked(patternNid), Nid.narrowChecked(nid));
            }
            if (sourceObject instanceof ConceptEntity concept) {
                this.conceptNids.add(Nid.narrowChecked(concept.nid()));
            } else if (sourceObject instanceof SemanticEntity semanticEntity) {
                this.semanticNids.add(Nid.narrowChecked(semanticEntity.nid()));
            } else if (sourceObject instanceof PatternEntity patternEntity) {
                this.patternNids.add(Nid.narrowChecked(patternEntity.nid()));
            } else if (sourceObject instanceof StampEntity stampEntity) {
                this.stampNids.add(Nid.narrowChecked(stampEntity.nid()));
            }
        }
        byte[] mergedBytes = this.entityToBytesMap.accumulateAndGet(Nid.narrowChecked(nid), value, PrimitiveDataService::merge);
        this.writeSequence.increment();
        this.changeSetWriterServices.forEach(writerService -> writerService.writeToChangeSet((Entity) sourceObject, activity));

        // Delegate indexing to SearchProvider.
        //
        // TODO(temp): During loadPhase, live-index up to LoadPhaseSearchPolicy's
        // threshold; once exceeded, skip the rest and fall back to a full
        // recreate at endLoadPhase. Replace this two-mode shim with touched-nid
        // notification + per-nid catch-up when the proper design lands.
        // See LoadPhaseSearchPolicy javadoc for the full picture.
        if (!loadPhase
                || dev.ikm.tinkar.entity.EntityService.get().loadPhaseSearchPolicy().shouldIndexLive()) {
            try {
                getSearchService().index(sourceObject);
            } catch (Exception e) {
                // Search service may not be available yet during startup
                LOG.debug("SearchService not available for indexing", e);
            }
        }

        return mergedBytes;
    }

    private SearchService getSearchService() {
        return searchService.orElseSet(() ->
            ServiceLifecycleManager.get()
                .getRunningService(SearchService.class)
                .orElseThrow(() -> new IllegalStateException("SearchService not available - ensure services are started"))
        );
    }

    public boolean addToPatternElementSet(int patternNid, int elementNid) {

        return patternElementNidsMap.getIfAbsentPut(patternNid, integer -> new ConcurrentHashSet())
                .add(elementNid);
    }

    @Override
    public PrimitiveDataSearchResult[] search(String query, int maxResultSize) throws Exception {
        return getSearchService().search(query, maxResultSize);
    }

    @Override
    public String highlight(String query, String text) throws Exception {
        return getSearchService().highlight(query, text);
    }

    @Override
    public void setLoadPhase(boolean loadPhase) {
        this.loadPhase = loadPhase;
    }

    @Override
    public CompletableFuture<Void> recreateLuceneIndex() {
        return getSearchService().recreateIndex();
    }

    @Override
    public long[] semanticNidsOfPattern(long patternNid) {
        ConcurrentHashSet<Integer> elementNids = patternElementNidsMap.get(Nid.narrowChecked(patternNid));
        if (elementNids == null || elementNids.isEmpty()) {
            return new long[0];
        }
        // The set may grow while it is read; the list grows with it.
        MutableLongList elementNidList = LongLists.mutable.withInitialCapacity(elementNids.size());
        for (int elementNid : elementNids) {
            elementNidList.add(elementNid);
        }
        return elementNidList.toArray();
    }

    /**
     * Visits the elements of a pattern from the set the provider keeps for it. No copy is made:
     * a visit over a pattern with millions of elements costs its iteration and nothing more,
     * where it once built an immutable int set of them first, on every call. The set is
     * concurrent, so an element added while it is read may or may not be visited. A pattern no
     * semantic has been merged under has no set, and no elements.
     */
    private void forEachElementNid(int patternNid, IntProcedure procedure) {
        ConcurrentHashSet<Integer> elementNids = patternElementNidsMap.get(patternNid);
        if (elementNids != null) {
            for (int elementNid : elementNids) {
                procedure.accept(elementNid);
            }
        }
    }

    @Override
    public void forEachSemanticNidOfPattern(long patternNid, LongProcedure procedure) {
        EntityHandle.get(patternNid).expectPattern("Trying to iterate elements for entity that is not a pattern: ");
        forEachElementNid(Nid.narrowChecked(patternNid), procedure::value);
    }

    @Override
    public void forEachPatternNid(LongProcedure procedure) {
        try {
            this.uuidsLoadedLatch.await();
            this.patternNids.forEach(patternNid -> procedure.accept(patternNid));
        } catch (InterruptedException e) {
            LOG.error(e.getLocalizedMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void forEachConceptNid(LongProcedure procedure) {
        try {
            this.uuidsLoadedLatch.await();
            this.conceptNids.forEach(conceptNid -> procedure.accept(conceptNid));
        } catch (InterruptedException e) {
            LOG.error(e.getLocalizedMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void forEachStampNid(LongProcedure procedure) {
        try {
            this.uuidsLoadedLatch.await();
            this.stampNids.forEach(stampNid -> procedure.accept(stampNid));
        } catch (InterruptedException e) {
            LOG.error(e.getLocalizedMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void forEachSemanticNid(LongProcedure procedure) {
        try {
            this.uuidsLoadedLatch.await();
            this.semanticNids.forEach(semanticNid -> procedure.accept(semanticNid));
        } catch (InterruptedException e) {
            LOG.error(e.getLocalizedMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void forEachSemanticNidForComponent(long componentNid, LongProcedure procedure) {
        long[] citationLongs = this.nidToCitingComponentsNidMap.get(Nid.narrowChecked(componentNid));
        if (citationLongs != null) {
            for (long citationLong : citationLongs) {
                int citingComponentNid = (int) (citationLong >> 32);
                procedure.accept(citingComponentNid);
            }
        }
    }

    @Override
    public void forEachSemanticNidForComponentOfPattern(long componentNid, long patternNid, LongProcedure procedure) {
        long[] citationLongs = this.nidToCitingComponentsNidMap.get(Nid.narrowChecked(componentNid));
        if (citationLongs != null) {
            for (long citationLong : citationLongs) {
                int citingComponentNid = (int) (citationLong >> 32);
                int citingComponentPatternNid = (int) citationLong;
                if (patternNid == citingComponentPatternNid) {
                    procedure.accept(citingComponentNid);
                }
            }
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void erase(long nid) {
        this.entityToBytesMap.put(Nid.narrowChecked(nid), null);
        this.nidToCitingComponentsNidMap.put(Nid.narrowChecked(nid), null);
        this.conceptNids.remove(Nid.narrowChecked(nid));
        this.semanticNids.remove(Nid.narrowChecked(nid));
        this.patternNids.remove(Nid.narrowChecked(nid));
        this.stampNids.remove(Nid.narrowChecked(nid));
        this.nidToCitingComponentsNidMap.forEach((nidPatternsCitingComponent, referencedComponentNid) -> {
            MutableLongList nidPatternInLongToRemove = LongLists.mutable.withInitialCapacity(2);
            for (long nidPatternInLong : nidPatternsCitingComponent) {
                // The longs contain int nid, int patternNid in each long
                int nidCitingComponent = IntsInLong.int1FromLong(nidPatternInLong);
                if (nidCitingComponent == nid) {
                    nidPatternInLongToRemove.add(nidPatternInLong);
                }
            }
            if (nidPatternInLongToRemove.notEmpty()) {
                MutableLongSet longSet = LongSets.mutable.of(nidPatternsCitingComponent);
                longSet.removeAll(nidPatternInLongToRemove);
                this.nidToCitingComponentsNidMap.put(referencedComponentNid, longSet.toArray());
            }
        });

    }

    @Override
    public void mergeThenErase(long nidToErase, long nidToMergeInto) {


        byte[] mergedBytes = merge(getBytes(nidToMergeInto), getBytes(nidToErase), DataActivity.DATA_REPAIR);
        erase(nidToErase);
        put(nidToMergeInto, mergedBytes);
        EntityService.get().invalidateCaches(nidToErase, nidToMergeInto);
    }

    byte[] merge(byte[] bytesToMergeInto, byte[] bytesToBeErased, DataActivity activity) {
        if (bytesToBeErased == null) {
            return bytesToMergeInto;
        }
        ByteBuf readBufToMergeInto = ByteBuf.wrapForReading(bytesToMergeInto);
        int mergeIntoArrayCount = readBufToMergeInto.readInt();
        int mergeIntoChronicleArrayElementByteCount = readBufToMergeInto.readInt();
        byte mergeIntoEntityFormatVersion = readBufToMergeInto.readByte(); // Entity format version in a byte.
        byte mergeIntoEntityTypeToken = readBufToMergeInto.readByte(); // Entity type token

        int mergeIntoNid = readBufToMergeInto.readInt();
        ImmutableList<UUID> mergeIntoUuids = getUuidsFromBytes(readBufToMergeInto);

        ByteBuf readBufToBeErased = ByteBuf.wrapForReading(bytesToBeErased);
        int eraseComponentArrayCount = readBufToBeErased.readInt();
        int eraseComponentChronicleArrayElementByteCount = readBufToBeErased.readInt();
        byte eraseComponentEntityFormatVersion = readBufToBeErased.readByte(); // Entity format version in a byte.
        byte eraseComponentEntityTypeToken = readBufToBeErased.readByte(); // Entity type token
        int eraseComponentNid = readBufToBeErased.readInt();
        ImmutableList<UUID> uuidsFromBytesToBeErased = getUuidsFromBytes(readBufToBeErased);

        // Create final set of uuids...
        MutableSet<UUID> uuidSet = Sets.mutable.ofAll(mergeIntoUuids);
        uuidSet.addAll(uuidsFromBytesToBeErased.castToList());
        ImmutableList<UUID> mergedUuids = uuidSet.toImmutableList();

        // Note first array (the chronicle fields) will be larger by the number of additional UUIDs...

        // Create bytes for merge into...
        ByteBuf outputBytesToMergeInto = ByteBufPool.allocate(bytesToMergeInto.length + (mergedUuids.size() * 16));
        int additionalMergedBytes = (mergedUuids.size() - mergeIntoUuids.size()) * 16;
        outputBytesToMergeInto.writeInt(mergeIntoArrayCount);
        outputBytesToMergeInto.writeInt(mergeIntoChronicleArrayElementByteCount + additionalMergedBytes);
        outputBytesToMergeInto.writeByte(mergeIntoEntityFormatVersion);
        outputBytesToMergeInto.writeByte(mergeIntoEntityTypeToken);
        outputBytesToMergeInto.writeInt(mergeIntoNid);
        writeUuidsAndRemaining(mergedUuids, outputBytesToMergeInto, readBufToMergeInto);

        // Create bytes for to be erased
        ByteBuf outputBytesToBeErased = ByteBufPool.allocate(bytesToBeErased.length + (mergedUuids.size() * 16));
        int additionalErasedBytes = (mergedUuids.size() - uuidsFromBytesToBeErased.size()) * 16;
        outputBytesToBeErased.writeInt(eraseComponentArrayCount);
        outputBytesToBeErased.writeInt(eraseComponentChronicleArrayElementByteCount + additionalErasedBytes);
        outputBytesToBeErased.writeByte(eraseComponentEntityFormatVersion);
        outputBytesToBeErased.writeByte(eraseComponentEntityTypeToken);
        outputBytesToBeErased.writeInt(mergeIntoNid);
        writeUuidsAndRemaining(mergedUuids, outputBytesToBeErased, readBufToBeErased);

        /*
        Need to manage possible time duplicates on merge. This may require creating a new stamp and nudging the time
        by a second to make the result unique. Since we need a new stamp, that is managed at the entity, rather than
        the primitive level.
         */

        return PrimitiveDataService.merge(outputBytesToMergeInto.asArray(), outputBytesToBeErased.asArray());
    }

    /**
     *
     */
    void writeUuidsAndRemaining(ImmutableList<UUID> mergedUuids, ByteBuf outputBytes, ByteBuf readBuf) {
        /*
         * Merging the UUIDs outside of a versioned object creates challenges wrt data integrity.
         * Consider how we could version uuids within a public id. Maybe additional UUIDs are added
         * to the public ID and has its own time stamp?
         */
        long[] additionalUuidLongs = UuidUtil.asArray(mergedUuids);
        outputBytes.writeLong(additionalUuidLongs[0]); // The initial UUID is always present
        outputBytes.writeLong(additionalUuidLongs[1]);
        outputBytes.writeByte((byte) (additionalUuidLongs.length - 2));
        for (int i = 2; i < additionalUuidLongs.length; i++) {
            outputBytes.writeLong(additionalUuidLongs[i]);
        }
        int readBufToMergeIntoRemaining = readBuf.readRemaining();
        for (int i = 0; i < readBufToMergeIntoRemaining; i++) {
            outputBytes.writeByte(readBuf.get());
        }
    }

    ImmutableList<UUID> getUuidsFromBytes(ByteBuf readBuf) {
        MutableList<UUID> uuids = Lists.mutable.withInitialCapacity(2);
        long msb = readBuf.readLong();
        long lsb = readBuf.readLong();
        uuids.add(new UUID(msb, lsb));
        int additionalUuidLongSize = readBuf.readByte();
        if (additionalUuidLongSize > 0) {
            long[] additionalUuidLongs = new long[additionalUuidLongSize];
            for (int i = 0; i < additionalUuidLongSize; i++) {
                additionalUuidLongs[i] = readBuf.readLong();
            }
            ImmutableList<UUID> additionalUuids = UuidUtil.toList(additionalUuidLongs);
            uuids.addAll(additionalUuids.castToList());
        }
        return uuids.toImmutableList();
    }

    @Override
    public void put(long nid, byte[] bytesToOverwrite) {
        this.entityToBytesMap.put(Nid.narrowChecked(nid), bytesToOverwrite);
    }


    /**
     * Controller for SpinedArrayProvider lifecycle management.
     * <p>     * Handles heavyweight initialization including data loading, indexing, and UUID mapping.
     */
    public abstract static class Controller extends ProviderController<SpinedArrayProvider>
            implements DataServiceController<PrimitiveDataService> {

        @Override
        public void setDataUriOption(DataUriOption option) {
            super.setDataUriOption(option);
            if (option != null) {
                ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, option.toFile());
            }
        }

        @Override
        public Optional<String> openConflict(DataUriOption option) {
            return option == null ? Optional.empty()
                    : DataStoreLockProbe.openConflict(option.toFile());
        }

        @Override
        protected SpinedArrayProvider createProvider() throws Exception {
            return new SpinedArrayProvider();
        }

        @Override
        protected void startProvider(SpinedArrayProvider provider) {
            // SpinedArrayProvider starts itself in constructor
            // Just wait for it to be ready
            lifecycle.set(Lifecycle.RUNNING);
        }

        @Override
        protected void stopProvider(SpinedArrayProvider provider) {
            provider.close();
        }

        @Override
        protected void cleanupProvider(SpinedArrayProvider provider) throws Exception {
            // Additional cleanup if needed
            provider.save();
        }

        @Override
        protected String getProviderName() {
            return "SpinedArrayProvider";
        }

        @Override
        public ServiceLifecyclePhase getLifecyclePhase() {
            return ServiceLifecyclePhase.DATA_STORAGE;
        }

        @Override
        public Optional<ServiceExclusionGroup> getMutualExclusionGroup() {
            return Optional.of(ServiceExclusionGroup.DATA_PROVIDER);
        }

        // ========== DataServiceController Implementation ==========

        @Override
        public ImmutableList<Class<?>> serviceClasses() {
            // SpinedArrayProvider (the generic type parameter P) implements PrimitiveDataService
            // This establishes the contract: ProviderController<SpinedArrayProvider> provides PrimitiveDataService
            return Lists.immutable.of(PrimitiveDataService.class);
        }

        @Override
        public boolean running() {
            return getProvider() != null && lifecycle.get() == Lifecycle.RUNNING;
        }

        @Override
        public void start() {
            startup();
        }

        @Override
        public void stop() {
            shutdown();
        }

        @Override
        public void save() {
            SpinedArrayProvider provider = getProvider();
            if (provider != null) {
                provider.save();
            }
        }

        @Override
        public void reload() {
            throw new UnsupportedOperationException("Reload not yet supported");
        }

        // Note: provider() method is inherited from ProviderController base class
    }

    public static class OpenController extends Controller {
        public static final String CONTROLLER_NAME = "Open SpinedArrayStore";

        @Override
        public void setDataUriOption(DataUriOption option) {
            super.setDataUriOption(option);
            if (option != null) {
                ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, option.toFile());
                ServiceProperties.set(ServiceKeys.DATA_STORE_EXPECT_EMPTY, Boolean.FALSE);
            }
        }

        @Override
        public boolean isValidDataLocation(String name) {
            File rootFolder = new File(System.getProperty("user.home"), "Solor");
            File checkDir = new File(rootFolder, name);
            if (!checkDir.exists() || !checkDir.isDirectory()) {
                return false;
            }
            // The two directories every spined-array store has. A store written before 2026-10-07
            // also holds a nidToPatternNidMap directory, a map nothing read, which is ignored.
            File nidToBytesDir = new File(checkDir, "nidToByteArrayMap");
            File nidToCitingDir = new File(checkDir, "nidToCitingComponentNidMap");
            return nidToBytesDir.isDirectory()
                    && nidToCitingDir.isDirectory();
        }

        @Override
        public List<DataUriOption> providerOptions() {
            List<DataUriOption> dataUriOptions = new ArrayList<>();
            File rootFolder = new File(System.getProperty("user.home"), "Solor");
            if (!rootFolder.exists()) {
                rootFolder.mkdirs();
            }
            File[] files = rootFolder.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory() && isValidDataLocation(f.getName())) {
                        dataUriOptions.add(new DataUriOption(f.getName(), f.toURI()));
                    }
                }
            }
            return dataUriOptions;
        }

        @Override
        public String controllerName() {
            return CONTROLLER_NAME;
        }

        @Override
        public int getSubPriority() {
            return 30;
        }
    }

    public static class NewController extends Controller {
        public static final String CONTROLLER_NAME = "New SpinedArrayStore";
        private static final DataServiceProperty NEW_FOLDER_PROPERTY =
                new DataServiceProperty("New folder name", false, true);

        private final MutableMap<DataServiceProperty, String> providerProperties = Maps.mutable.empty();
        private String importDataFileString;

        public NewController() {
            providerProperties.put(NEW_FOLDER_PROPERTY, null);
        }

        @Override
        public void setDataUriOption(DataUriOption option) {
            super.setDataUriOption(option);
            ServiceProperties.set(ServiceKeys.DATA_STORE_EXPECT_EMPTY, Boolean.TRUE);
            if (option != null) {
                // toFile() decodes the URI; URL.getFile() would keep %20 for a space (ike-issues#1156).
                importDataFileString = option.toFile().getAbsolutePath();
            }
        }

        @Override
        public ImmutableMap<DataServiceProperty, String> providerProperties() {
            return providerProperties.toImmutable();
        }

        @Override
        public void setDataServiceProperty(DataServiceProperty key, String value) {
            providerProperties.put(key, value);
        }

        @Override
        public ValidationRecord[] validate(DataServiceProperty dataServiceProperty, Object value, Object target) {
            if (NEW_FOLDER_PROPERTY.equals(dataServiceProperty)) {
                File rootFolder = new File(System.getProperty("user.home"), "Solor");
                if (value instanceof String fileName) {
                    if (fileName.isBlank()) {
                        return new ValidationRecord[]{new ValidationRecord(ValidationSeverity.ERROR,
                                "Directory name cannot be blank", target)};
                    } else {
                        File possibleFile = new File(rootFolder, fileName);
                        if (possibleFile.exists() && !isEmptyDirectory(possibleFile)) {
                            return new ValidationRecord[]{new ValidationRecord(ValidationSeverity.ERROR,
                                    "Directory exists and is not empty", target)};
                        }
                    }
                }
            }
            return new ValidationRecord[]{};
        }

        @Override
        public List<DataUriOption> providerOptions() {
            List<DataUriOption> dataUriOptions = new ArrayList<>();
            File rootFolder = new File(System.getProperty("user.home"), "Solor");
            if (!rootFolder.exists()) {
                rootFolder.mkdirs();
            }
            File[] files = rootFolder.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (isValidDataLocation(f.getName())) {
                        dataUriOptions.add(new DataUriOption(f.getName(), f.toURI()));
                    }
                }
            }
            return dataUriOptions;
        }

        @Override
        protected SpinedArrayProvider createProvider() throws Exception {
            ServiceProperties.set(ServiceKeys.DATA_STORE_EXPECT_EMPTY, Boolean.TRUE);
            File rootFolder = new File(System.getProperty("user.home"), "Solor");
            String folderName = providerProperties.get(NEW_FOLDER_PROPERTY);
            if (folderName == null || folderName.isBlank()) {
                throw new IllegalStateException("New folder name not set for New SpinedArrayStore");
            }
            File dataDirectory = new File(rootFolder, folderName);
            assertNewDataDirectory(dataDirectory);
            ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, dataDirectory);
            return new SpinedArrayProvider();
        }

        @Override
        protected void initializeProvider(SpinedArrayProvider provider) {
            File rootFolder = new File(System.getProperty("user.home"), "Solor");
            File dataDirectory = new File(rootFolder, providerProperties.get(NEW_FOLDER_PROPERTY));
            ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, dataDirectory);

            if (importDataFileString != null) {
                File importFile = new File(importDataFileString);
                LOG.info("Queueing starter data for deferred import: {}", importFile.getName());
                dev.ikm.tinkar.entity.load.DataLoadProvider dataLoadService =
                        dev.ikm.tinkar.entity.load.DataLoadProvider.get();
                dataLoadService.addFile(importFile);
            } else {
                LOG.warn("No import file specified - creating empty database");
            }
        }

        @Override
        public boolean isValidDataLocation(String name) {
            return name.toLowerCase().endsWith("pb.zip") ||
                    (name.toLowerCase().endsWith(".zip") && name.toLowerCase().contains("tink"));
        }

        @Override
        public String controllerName() {
            return CONTROLLER_NAME;
        }

        @Override
        public int getSubPriority() {
            return 31;
        }

        private static void assertNewDataDirectory(File dir) {
            if (dir.exists()) {
                if (!dir.isDirectory()) {
                    throw new IllegalStateException("New SpinedArrayStore path is not a directory: " + dir.getAbsolutePath());
                }
                if (!isEmptyDirectory(dir)) {
                    throw new IllegalStateException("New SpinedArrayStore directory is not empty: " + dir.getAbsolutePath());
                }
            }
        }

        private static boolean isEmptyDirectory(File dir) {
            String[] entries = dir.list();
            return entries == null || entries.length == 0;
        }
    }
}
