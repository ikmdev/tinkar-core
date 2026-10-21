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
package dev.ikm.tinkar.provider.spinedarray64;

import dev.ikm.tinkar.common.alert.AlertStreams;
import dev.ikm.tinkar.common.id.EntityKey;
import dev.ikm.tinkar.common.id.Nid;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.id.impl.NidLayout;
import dev.ikm.tinkar.common.service.DataActivity;
import dev.ikm.tinkar.common.service.DataServiceController;
import dev.ikm.tinkar.common.service.DataServiceProperty;
import dev.ikm.tinkar.common.service.DataUriOption;
import dev.ikm.tinkar.common.service.DataUse;
import dev.ikm.tinkar.common.service.DiagnosticText;
import dev.ikm.tinkar.common.service.EntityRecordFormat2;
import dev.ikm.tinkar.common.service.NidGenerator;
import dev.ikm.tinkar.common.service.PluggableService;
import dev.ikm.tinkar.common.service.PrimitiveDataSearchResult;
import dev.ikm.tinkar.common.service.PrimitiveDataService;
import dev.ikm.tinkar.common.service.ProviderController;
import dev.ikm.tinkar.common.service.ScopeErasure;
import dev.ikm.tinkar.common.service.SearchService;
import dev.ikm.tinkar.common.service.ServiceExclusionGroup;
import dev.ikm.tinkar.common.service.ServiceKeys;
import dev.ikm.tinkar.common.service.ServiceLifecycleManager;
import dev.ikm.tinkar.common.service.ServiceLifecyclePhase;
import dev.ikm.tinkar.common.service.ServiceProperties;
import dev.ikm.tinkar.common.service.TinkExecutor;
import dev.ikm.tinkar.common.service.internal.EntityStore;
import dev.ikm.tinkar.common.util.SetOnce;
import dev.ikm.tinkar.common.util.time.Stopwatch;
import dev.ikm.tinkar.common.validation.ValidationRecord;
import dev.ikm.tinkar.common.validation.ValidationSeverity;
import dev.ikm.tinkar.entity.ChangeSetWriterService;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityRecordFactory;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.scope.DataUseIndex;
import dev.ikm.tinkar.provider.search.DataStoreLockProbe;
import org.eclipse.collections.api.block.procedure.primitive.LongProcedure;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.primitive.LongSets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BinaryOperator;
import java.util.function.ObjLongConsumer;

/**
 * The 64-bit spined-array store: entity bytes held in memory in spined arrays, as the sequential
 * {@code SpinedArrayProvider} holds them, but indexed per pattern, pattern sequence then element
 * sequence, under 64-bit nids ({@link Nid#compose64}, {@link NidLayout#SIXTY_FOUR_BIT}), with
 * records in entity format 2 (design-2026-10-20-instance-data, "The 64-bit spined-array engine";
 * IKE-Network/ike-issues#1313). Each pattern's elements are one range, a {@link PatternTable}, so a
 * component's pattern is read from its nid, a pattern's elements are enumerated by walking its
 * table, and a scope of instance data is erased by dropping its patterns' tables.
 *
 * <p>It shares no storage code with the sequential engine, which is frozen beside it. Persistent,
 * it is written to disk by changed spine ({@link StoreFiles}); memory-only, it keeps nothing on
 * disk and ends with its JVM.
 */
public class SpinedArray64Provider implements PrimitiveDataService, EntityStore, NidGenerator, ScopeErasure {
    private static final Logger LOG = LoggerFactory.getLogger(SpinedArray64Provider.class);

    /** The data root when none is configured. */
    static final File DEFAULT_DATA_DIRECTORY = new File("target/spinedarrays64/");
    /** The {@link #name()} of a memory-only store. */
    static final String MEMORY_ONLY_NAME = "Memory-only data (64-bit)";
    /** What the root of another engine's store holds: the sequential spined array's entity map, a Rocks store's database. */
    private static final String[] OTHER_STORES = {"nidToByteArrayMap", "rocks"};

    private final String name;
    /** The store's files; null for a memory-only store. */
    private final StoreFiles files;
    private final SpineGeometry geometry;
    private final Object growth = new Object();
    private volatile PatternTable[] tables = new PatternTable[64];
    private final Identities identities;
    private final LongAdder writeSequence = new LongAdder();
    private final ImmutableList<ChangeSetWriterService> changeSetWriterServices;
    /** What uses the store's instance and ephemeral data, for scope deletes (IKE-Network/ike-issues#1313). */
    private final DataUseIndex dataUseIndex;
    private final SetOnce<SearchService> searchService = new SetOnce<>();
    private final AtomicBoolean open = new AtomicBoolean(true);
    private volatile boolean loadPhase = false;

    /**
     * Opens the store: memory-only when {@code root} is null, else the persistent store there,
     * created if the root holds none.
     *
     * @throws IllegalStateException if the root holds something that is not a store of this
     *                               engine, or was expected empty and is not
     */
    SpinedArray64Provider(File root) {
        Stopwatch stopwatch = new Stopwatch();
        NidLayout.activate(NidLayout.SIXTY_FOUR_BIT);
        if (root == null) {
            this.name = MEMORY_ONLY_NAME;
            this.files = null;
            this.geometry = SpineGeometry.defaults();
        } else {
            if (!root.isDirectory() && !root.mkdirs()) {
                throw new IllegalStateException("Could not create " + root.getAbsolutePath());
            }
            this.name = root.getName();
            this.files = new StoreFiles(root);
            if (StoreFiles.holdsAStore(root)) {
                this.geometry = files.readFormat();
            } else {
                for (String other : OTHER_STORES) {
                    if (new File(root, other).exists()) {
                        throw new IllegalStateException(root.getAbsolutePath() + " holds " + other
                                + ", another engine's store, not a 64-bit spined-array store");
                    }
                }
                this.geometry = SpineGeometry.defaults();
                files.writeFormat(geometry);
            }
        }
        this.identities = new Identities(sequence -> table(sequence).nextElementSequence());
        // The pattern-of-patterns issues sequences after the three fixed patterns.
        table(Identities.PATTERN_OF_PATTERNS).raiseTo(Identities.FIRST_SEMANTIC_PATTERN);
        table(Identities.CONCEPT_PATTERN);
        table(Identities.STAMP_PATTERN);
        if (files != null) {
            openTables();
        }
        // Nothing is written between the open and here, so the files are what the index's file was written against.
        this.dataUseIndex = files == null
                ? DataUseIndex.memoryOnly(this::forEachParallel)
                : DataUseIndex.open(files.root(), fingerprint(), !holdsRecords(), this::forEachParallel);

        if (files == null) {
            // A change set is a record on disk of what was edited; a memory-only store keeps none.
            this.changeSetWriterServices = Lists.immutable.empty();
        } else {
            ServiceLoader<ChangeSetWriterService> loader = PluggableService.load(ChangeSetWriterService.class);
            MutableList<ChangeSetWriterService> writers = Lists.mutable.empty();
            loader.stream().forEach(provider -> writers.add(provider.get()));
            this.changeSetWriterServices = writers.toImmutable();
            LOG.info("Loaded {} ChangeSetWriterService(s)", writers.size());
        }
        stopwatch.stop();
        LOG.info("Opened the 64-bit spined-array store {} in {}: {} pattern table(s), {} UUIDs, spines {}",
                name, stopwatch.durationString(), patternSequences().length, identities.size(), geometry);
    }

    /**
     * Gives every pattern on disk its table, the counters what was saved raised to one past the
     * last element each table holds, and the public-id map the UUIDs of every record, read once
     * in parallel by spine; then the identities of nids minted and never written.
     */
    private void openTables() {
        for (int sequence : files.patternSequences()) {
            table(sequence);
        }
        files.readCounters().forEach((sequence, next) -> table(sequence).raiseTo(next));
        List<Future<?>> reads = new ArrayList<>();
        for (int sequence : patternSequences()) {
            PatternTable table = table(sequence);
            int spines = table.records.spineCount();
            for (int spine = 0; spine < spines; spine++) {
                int index = spine;
                reads.add(TinkExecutor.threadPool().submit(() -> table.records.forEachOnSpine(index, (slot, record) ->
                        identities.opened(table.nidOf(slot), EntityRecordFormat2.uuids(record).castToList()))));
            }
        }
        await(reads);
        for (int sequence : patternSequences()) {
            PatternTable table = table(sequence);
            table.raiseTo(table.records.end() + 1);
        }
        files.readAbsentIdentities().forEach((nid, uuids) -> {
            if (!Nid.isValid64(nid)) {
                throw new IllegalStateException(StoreFiles.ABSENT_FILE + " in " + name + " names " + uuids[0]
                        + " under a nid that is not a 64-bit one: the file is another engine's");
            }
            identities.restoreUnwritten(nid, uuids);
            table(Nid.patternSequence64(nid)).raiseTo(Nid.elementSequence64(nid) + 1);
        });
    }

    /** Whether any table holds a record beyond the fixed bindings, which hold none until written. */
    private boolean holdsRecords() {
        for (int sequence : patternSequences()) {
            if (table(sequence).records.end() > 0) {
                return true;
            }
        }
        return false;
    }

    /** The files the store is saved in, as a fingerprint for the data-use index. */
    private long fingerprint() {
        File root = files.root();
        return DataUseIndex.fingerprintOf(new File(root, StoreFiles.PATTERNS), new File(root, StoreFiles.COUNTERS_FILE),
                new File(root, StoreFiles.ABSENT_FILE));
    }

    private static void await(List<Future<?>> futures) {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (ExecutionException e) {
                throw new IllegalStateException(e.getCause());
            }
        }
    }

    // ---------- tables ----------

    /** The table of a pattern sequence, made if the store has none. */
    PatternTable table(int sequence) {
        if (sequence < 1 || sequence > Nid.MAX_SEQUENCE_64) {
            throw new IllegalArgumentException("pattern sequence " + sequence + " is out of range");
        }
        PatternTable[] array = tables;
        if (sequence < array.length && array[sequence] != null) {
            return array[sequence];
        }
        synchronized (growth) {
            array = tables;
            if (sequence < array.length && array[sequence] != null) {
                return array[sequence];
            }
            PatternTable made = makeTable(sequence);
            if (sequence >= array.length) {
                array = Arrays.copyOf(array, Math.max(sequence + 1, array.length * 2));
            }
            array[sequence] = made;
            tables = array;
            return made;
        }
    }

    /** The table of a pattern sequence, or null. */
    PatternTable tableIfPresent(int sequence) {
        PatternTable[] array = tables;
        return sequence > 0 && sequence < array.length ? array[sequence] : null;
    }

    private PatternTable makeTable(int sequence) {
        if (files == null) {
            return new PatternTable(sequence, new SpinedSlots<>(geometry, null, null),
                    new SpinedSlots<>(geometry, null, null), PatternTable.FIRST_ELEMENT);
        }
        return new PatternTable(sequence,
                new SpinedSlots<>(geometry, spine -> files.readRecords(sequence, spine),
                        files.spinesOnDisk(sequence, StoreFiles.RECORDS)),
                new SpinedSlots<>(geometry, spine -> files.readCitations(sequence, spine),
                        files.spinesOnDisk(sequence, StoreFiles.CITATIONS)),
                PatternTable.FIRST_ELEMENT);
    }

    /** The pattern sequences that have a table, ascending. */
    int[] patternSequences() {
        PatternTable[] array = tables;
        return java.util.stream.IntStream.range(1, array.length).filter(sequence -> array[sequence] != null).toArray();
    }

    /** The record of a nid, or null; null for a nid that is not a 64-bit one. */
    private byte[] record(long nid) {
        if (!Nid.isValid64(nid)) {
            return null;
        }
        PatternTable table = tableIfPresent(Nid.patternSequence64(nid));
        return table == null ? null : table.records.get(PatternTable.slotOf(nid));
    }

    private long[] citations(long nid) {
        if (!Nid.isValid64(nid)) {
            return null;
        }
        PatternTable table = tableIfPresent(Nid.patternSequence64(nid));
        return table == null ? null : table.citations.get(PatternTable.slotOf(nid));
    }

    // ---------- lifecycle ----------

    /** Whether the store is open. */
    boolean running() {
        return open.get();
    }

    @Override
    public long writeSequence() {
        return writeSequence.sum();
    }

    @Override
    public void close() {
        if (!open.compareAndSet(true, false)) {
            LOG.info("The 64-bit spined-array store {} is already closed", name);
            return;
        }
        Stopwatch stopwatch = new Stopwatch();
        try {
            changeSetWriterServices.forEach(ChangeSetWriterService::shutdown);
            write();
            if (files != null) {
                dataUseIndex.save(fingerprint());
            }
        } catch (RuntimeException e) {
            LOG.error("Error closing the 64-bit spined-array store {}", name, e);
        } finally {
            stopwatch.stop();
            LOG.info("Closed the 64-bit spined-array store {} in {}", name, stopwatch.durationString());
        }
    }

    /** Writes the changed spines, the dropped ranges' deletions, the counters and the unwritten identities. */
    public void save() {
        if (files == null || !open.get()) {
            return;
        }
        write();
    }

    private void write() {
        if (files == null) {
            return;
        }
        Stopwatch stopwatch = new Stopwatch();
        int[] written = {0};
        Map<Integer, Integer> counters = new TreeMap<>();
        for (int sequence : patternSequences()) {
            PatternTable table = table(sequence);
            if (table.purge) {
                table.purge = false;
                files.deletePattern(sequence);
            }
            table.records.takeChanged((slots, spine) -> {
                files.writeRecords(sequence, spine, slots);
                written[0]++;
            });
            table.citations.takeChanged((slots, spine) -> {
                files.writeCitations(sequence, spine, slots);
                written[0]++;
            });
            counters.put(sequence, table.next());
        }
        files.writeCounters(counters);
        files.writeAbsentIdentities(identities.unwritten());
        stopwatch.stop();
        LOG.info("Saved the 64-bit spined-array store {}: {} spine(s) in {}", name, written[0], stopwatch.durationString());
    }

    @Override
    public String name() {
        return name;
    }

    // ---------- identity ----------

    /** A 64-bit store mints a nid under the entity's pattern: see {@link #getEntityKey(PublicId, PublicId)}. */
    @Override
    public long newNid() {
        throw new UnsupportedOperationException("A 64-bit store mints a nid under the entity's pattern: "
                + "ask for the key by pattern and public id");
    }

    @Override
    public long nidForUuids(UUID... uuids) {
        return identities.nidForUuids(uuids);
    }

    @Override
    public long nidForUuids(ImmutableList<UUID> uuidList) {
        return identities.nidForUuids(uuidList.toArray(new UUID[0]));
    }

    @Override
    public boolean hasUuid(UUID uuid) {
        return identities.knows(uuid);
    }

    @Override
    public boolean hasPublicId(PublicId publicId) {
        for (UUID uuid : publicId.asUuidArray()) {
            if (identities.knows(uuid)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public EntityKey getEntityKey(PublicId patternId, PublicId entityId) {
        return EntityKey.ofNid(identities.nidFor(patternId, entityId));
    }

    @Override
    public Optional<EntityKey> getEntityKey(UUID uuid) {
        return identities.nid(uuid).map(EntityKey::ofNid);
    }

    /** Patterns are discovered before elements are keyed under them, so an import takes two passes. */
    @Override
    public boolean requiresMultiPassImport() {
        return true;
    }

    /** The UUIDs of the entity stored under a nid, from its record; for a nid minted and never written, from its mint. */
    @Override
    public PublicId publicIdForNid(long nid) {
        byte[] record = record(nid);
        if (record != null) {
            return PublicIds.of(EntityRecordFormat2.uuids(record).toArray(new UUID[0]));
        }
        return identities.publicIdOf(nid).orElseThrow(() ->
                new IllegalStateException("No entity is stored for nid " + nid + " in " + name + ", and no public id was minted for it"));
    }

    // ---------- records ----------

    @Override
    public byte[] getBytes(long nid) {
        return record(nid);
    }

    @Override
    public byte[] merge(long nid, long patternNid, long referencedComponentNid, byte[] value, Object sourceObject,
                        DataActivity activity) {
        return merge(nid, patternNid, referencedComponentNid, value, sourceObject, activity,
                (stored, incoming) -> EntityRecordFormat2.merge(stored, incoming, this::isCanceledStampNid));
    }

    @Override
    public byte[] mergeReplacingVersion(long nid, long patternNid, long referencedComponentNid, long withdrawnStampNid,
                                        byte[] value, Object sourceObject, DataActivity activity) {
        // The version leaves the stored record inside the same atomic accumulation that merges
        // the value in, so no reader sees the record with neither.
        return merge(nid, patternNid, referencedComponentNid, value, sourceObject, activity,
                (stored, incoming) -> EntityRecordFormat2.merge(EntityRecordFactory.withoutVersion(stored, withdrawnStampNid),
                        incoming, this::isCanceledStampNid));
    }

    private byte[] merge(long nid, long patternNid, long referencedComponentNid, byte[] value, Object sourceObject,
                         DataActivity activity, BinaryOperator<byte[]> accumulator) {
        Nid.validate64(nid);
        if (!EntityRecordFormat2.isFormat2(value)) {
            throw new IllegalArgumentException("A 64-bit store holds format 2 records; " + DiagnosticText.component(nid) + " was given "
                    + (value == null || value.length == 0 ? "no record" : "a format " + value[0] + " record"));
        }
        PatternTable table = table(Nid.patternSequence64(nid));
        byte[] merged = table.records.accumulateAndGet(PatternTable.slotOf(nid), value, accumulator);
        identities.written(nid);
        if (sourceObject instanceof Entity<?> entity && entity.additionalUuidLongs() != null
                && entity.additionalUuidLongs().notEmpty()) {
            identities.mapIfAbsent(nid, entity.publicId().asUuidArray());
        }
        dataUseIndex.merged(sourceObject);
        // The citation after the record: a reader following citations from the referenced
        // component finds a record at every nid it is given.
        long referenced = sourceObject instanceof SemanticEntity<?> semantic ? semantic.referencedComponentNid()
                : Nid.isNotApplicable(patternNid) ? 0 : referencedComponentNid;
        if (referenced != 0 && Nid.isValid64(referenced)) {
            cite(referenced, nid);
        }
        writeSequence.increment();
        changeSetWriterServices.forEach(writer -> writer.writeToChangeSet((Entity) sourceObject, activity));

        // Live indexing, within the load phase policy, as the other stores do.
        if (!loadPhase || EntityService.get().loadPhaseSearchPolicy().shouldIndexLive()) {
            try {
                getSearchService().index(sourceObject);
            } catch (Exception e) {
                LOG.debug("SearchService not available for indexing", e);
            }
        }
        return merged;
    }

    /** Adds a semantic to the citations of the component it references: a sorted array of nids, each once. */
    private void cite(long referenced, long citing) {
        PatternTable table = table(Nid.patternSequence64(referenced));
        table.citations.updateAndGet(PatternTable.slotOf(referenced), present -> {
            if (present == null) {
                return new long[]{citing};
            }
            int at = Arrays.binarySearch(present, citing);
            if (at >= 0) {
                return present;
            }
            int insert = -at - 1;
            long[] grown = new long[present.length + 1];
            System.arraycopy(present, 0, grown, 0, insert);
            grown[insert] = citing;
            System.arraycopy(present, insert, grown, insert + 1, present.length - insert);
            return grown;
        });
    }

    /** Takes a semantic out of the citations of the component it referenced. */
    private void uncite(long referenced, long citing) {
        if (!Nid.isValid64(referenced)) {
            return;
        }
        PatternTable table = tableIfPresent(Nid.patternSequence64(referenced));
        if (table == null) {
            return;
        }
        table.citations.updateAndGet(PatternTable.slotOf(referenced), present -> {
            if (present == null) {
                return null;
            }
            int at = Arrays.binarySearch(present, citing);
            if (at < 0) {
                return present;
            }
            if (present.length == 1) {
                return null;
            }
            long[] shrunk = new long[present.length - 1];
            System.arraycopy(present, 0, shrunk, 0, at);
            System.arraycopy(present, at + 1, shrunk, at, present.length - at - 1);
            return shrunk;
        });
    }

    // ---------- scans ----------

    /** Visits every record, from the calling thread, pattern by pattern in nid order. */
    @Override
    public void forEach(ObjLongConsumer<byte[]> action) {
        for (int sequence : patternSequences()) {
            PatternTable table = table(sequence);
            table.records.forEach((slot, record) -> action.accept(record, table.nidOf(slot)));
        }
    }

    /** Visits every record, a spine per task on the shared pool, so the action must be safe to call concurrently. */
    @Override
    public void forEachParallel(ObjLongConsumer<byte[]> action) {
        List<Future<?>> tasks = new ArrayList<>();
        for (int sequence : patternSequences()) {
            PatternTable table = table(sequence);
            int spines = table.records.spineCount();
            for (int spine = 0; spine < spines; spine++) {
                int index = spine;
                tasks.add(TinkExecutor.threadPool().submit(() ->
                        table.records.forEachOnSpine(index, (slot, record) -> action.accept(record, table.nidOf(slot)))));
            }
        }
        await(tasks);
    }

    @Override
    public void forEach(ImmutableLongList nids, ObjLongConsumer<byte[]> action) {
        nids.forEach(nid -> {
            byte[] record = record(nid);
            if (record != null) {
                action.accept(record, nid);
            }
        });
    }

    @Override
    public void forEachParallel(ImmutableLongList nids, ObjLongConsumer<byte[]> action) {
        long[] all = nids.toArray();
        int chunk = 4_096;
        List<Future<?>> tasks = new ArrayList<>();
        for (int from = 0; from < all.length; from += chunk) {
            int start = from;
            int end = Math.min(all.length, from + chunk);
            tasks.add(TinkExecutor.threadPool().submit(() -> {
                for (int i = start; i < end; i++) {
                    byte[] record = record(all[i]);
                    if (record != null) {
                        action.accept(record, all[i]);
                    }
                }
            }));
        }
        try {
            await(tasks);
        } catch (IllegalStateException e) {
            AlertStreams.dispatchToRoot(e);
        }
    }

    // ---------- enumerations ----------

    /**
     * Visits the semantics of a pattern: the records of its table. The three fixed patterns key
     * concepts, stamps and patterns, not semantics, so they have none here, as in every provider.
     *
     * @throws IllegalStateException if the nid is not a pattern's
     */
    @Override
    public void forEachSemanticNidOfPattern(long patternNid, LongProcedure procedure) {
        int sequence = patternSequenceOf(patternNid);
        if (sequence < Identities.FIRST_SEMANTIC_PATTERN) {
            return;
        }
        forEachElementOf(sequence, procedure);
    }

    @Override
    public int countSemanticsOfPattern(long patternNid) {
        int sequence = patternSequenceOf(patternNid);
        if (sequence < Identities.FIRST_SEMANTIC_PATTERN) {
            return 0;
        }
        int[] count = {0};
        forEachElementOf(sequence, nid -> count[0]++);
        return count[0];
    }

    private static int patternSequenceOf(long patternNid) {
        if (!Nid.isValid64(patternNid) || Nid.patternSequence64(patternNid) != Identities.PATTERN_OF_PATTERNS) {
            throw new IllegalStateException("Trying to iterate elements for entity that is not a pattern: "
                    + DiagnosticText.component(patternNid));
        }
        return Nid.elementSequence64(patternNid);
    }

    private void forEachElementOf(int sequence, LongProcedure procedure) {
        PatternTable table = tableIfPresent(sequence);
        if (table != null) {
            table.records.forEach((slot, record) -> procedure.accept(table.nidOf(slot)));
        }
    }

    @Override
    public void forEachPatternNid(LongProcedure procedure) {
        forEachElementOf(Identities.PATTERN_OF_PATTERNS, procedure);
    }

    @Override
    public void forEachConceptNid(LongProcedure procedure) {
        forEachElementOf(Identities.CONCEPT_PATTERN, procedure);
    }

    @Override
    public void forEachStampNid(LongProcedure procedure) {
        forEachElementOf(Identities.STAMP_PATTERN, procedure);
    }

    /** Visits every semantic nid, pattern by pattern, from the calling thread. */
    @Override
    public void forEachSemanticNid(LongProcedure procedure) {
        for (int sequence : patternSequences()) {
            if (sequence >= Identities.FIRST_SEMANTIC_PATTERN) {
                forEachElementOf(sequence, procedure);
            }
        }
    }

    @Override
    public void forEachSemanticNidForComponent(long componentNid, LongProcedure procedure) {
        long[] citing = citations(componentNid);
        if (citing != null) {
            for (long nid : citing) {
                procedure.accept(nid);
            }
        }
    }

    /** The semantics of one pattern that reference a component: a citing nid names its pattern in its upper half. */
    @Override
    public void forEachSemanticNidForComponentOfPattern(long componentNid, long patternNid, LongProcedure procedure) {
        int sequence = patternSequenceOf(patternNid);
        long[] citing = citations(componentNid);
        if (citing != null) {
            for (long nid : citing) {
                if (Nid.patternSequence64(nid) == sequence) {
                    procedure.accept(nid);
                }
            }
        }
    }

    // ---------- erasure ----------

    /**
     * Erases a closed set of components (IKE-Network/ike-issues#1313). Each component's bytes say
     * what it made: a semantic's citation leaves its referenced component's slot, unless that
     * component is erased too, and its text leaves the search index; every component's UUIDs are
     * forgotten. Where the set holds a pattern and every element its table holds, the table is
     * dropped as a whole, its spines released at once and its directory deleted at the next save;
     * elsewhere the record and citation slots are cleared. Counters stay, so no nid is reused.
     */
    @Override
    public void eraseComponents(long[] nids) {
        MutableLongSet erasing = LongSets.mutable.of(nids);
        // The patterns whose whole range is in the set: dropped, not cleared slot by slot.
        MutableLongSet wholeRanges = LongSets.mutable.empty();
        for (long nid : nids) {
            if (Nid.isValid64(nid) && Nid.patternSequence64(nid) == Identities.PATTERN_OF_PATTERNS
                    && Nid.elementSequence64(nid) >= Identities.FIRST_SEMANTIC_PATTERN) {
                PatternTable table = tableIfPresent(Nid.elementSequence64(nid));
                if (table != null && rangeWithin(table, erasing)) {
                    wholeRanges.add(Nid.elementSequence64(nid));
                }
            }
        }
        for (long nid : nids) {
            byte[] record = record(nid);
            if (record == null) {
                continue;
            }
            Entity<?> entity = EntityRecordFactory.make(record);
            dataUseIndex.erased(entity);
            if (entity instanceof SemanticEntity<?> semantic) {
                long referenced = semantic.referencedComponentNid();
                if (!erasing.contains(referenced)) {
                    uncite(referenced, nid);
                }
                try {
                    getSearchService().remove(nid);
                } catch (IllegalStateException noSearch) {
                    LOG.debug("No search service to remove {} from", nid);
                }
            }
            identities.forget(nid, entity.publicId().asUuidArray());
            int sequence = Nid.patternSequence64(nid);
            if (!wholeRanges.contains(sequence)) {
                PatternTable table = table(sequence);
                table.records.getAndSet(PatternTable.slotOf(nid), null);
                table.citations.getAndSet(PatternTable.slotOf(nid), null);
            }
            if (entity instanceof PatternEntity<?> && !wholeRanges.contains(Nid.elementSequence64(nid))) {
                LOG.debug("Pattern {} is erased with elements outside the set; its range is cleared slot by slot", nid);
            }
            writeSequence.increment();
        }
        wholeRanges.forEach(sequence -> {
            table((int) sequence).drop();
            LOG.info("Dropped the range of pattern {} in {}", sequence, name);
        });
    }

    /** Whether every element a table holds is in the set. */
    private static boolean rangeWithin(PatternTable table, MutableLongSet erasing) {
        boolean[] within = {true};
        table.records.forEach((slot, record) -> {
            if (within[0] && !erasing.contains(table.nidOf(slot))) {
                within[0] = false;
            }
        });
        return within[0];
    }

    @Override
    public Optional<DataUse> dataUse() {
        return Optional.of(dataUseIndex);
    }

    /** Removes the components' records from what each of this store's change-set writers keeps. */
    @Override
    public ChangeSetRemoval removeFromChangeSets(java.util.Set<UUID> components) {
        ChangeSetRemoval removal = ChangeSetRemoval.NONE;
        for (ChangeSetWriterService writer : changeSetWriterServices) {
            removal = removal.plus(writer.removeComponents(components));
        }
        return removal;
    }

    // ---------- search ----------

    private SearchService getSearchService() {
        return searchService.orElseSet(() -> ServiceLifecycleManager.get()
                .getRunningService(SearchService.class)
                .orElseThrow(() -> new IllegalStateException("SearchService not available - ensure services are started")));
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
        dataUseIndex.loadPhase(loadPhase);
    }

    @Override
    public CompletableFuture<Void> recreateLuceneIndex() {
        return getSearchService().recreateIndex();
    }

    // ---------- controllers ----------

    /** The lifecycle of a 64-bit spined-array store, persistent or memory-only. */
    public abstract static class Controller extends ProviderController<SpinedArray64Provider>
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
            return option == null ? Optional.empty() : DataStoreLockProbe.openConflict(option.toFile());
        }

        /** Opens the persistent store at the configured data root, checking it is empty when it is expected to be. */
        @Override
        protected SpinedArray64Provider createProvider() {
            File root = ServiceProperties.get(ServiceKeys.DATA_STORE_ROOT, DEFAULT_DATA_DIRECTORY);
            boolean expectEmpty = ServiceProperties.get(ServiceKeys.DATA_STORE_EXPECT_EMPTY, Boolean.FALSE);
            if (expectEmpty) {
                String[] entries = root.list();
                if (entries != null && entries.length > 0) {
                    throw new IllegalStateException("Expected empty DATA_STORE_ROOT but found contents: " + root.getAbsolutePath());
                }
                ServiceProperties.set(ServiceKeys.DATA_STORE_EXPECT_EMPTY, Boolean.FALSE);
            }
            return new SpinedArray64Provider(root);
        }

        @Override
        protected void startProvider(SpinedArray64Provider provider) {
            // Opened in its constructor.
        }

        @Override
        protected void stopProvider(SpinedArray64Provider provider) {
            provider.close();
        }

        @Override
        protected String getProviderName() {
            return "SpinedArray64Provider";
        }

        @Override
        public ServiceLifecyclePhase getLifecyclePhase() {
            return ServiceLifecyclePhase.DATA_STORAGE;
        }

        @Override
        public Optional<ServiceExclusionGroup> getMutualExclusionGroup() {
            return Optional.of(ServiceExclusionGroup.DATA_PROVIDER);
        }

        @Override
        public ImmutableList<Class<?>> serviceClasses() {
            return Lists.immutable.of(PrimitiveDataService.class);
        }

        @Override
        public boolean running() {
            SpinedArray64Provider provider = getProvider();
            return provider != null && provider.running();
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
            SpinedArray64Provider provider = getProvider();
            if (provider != null) {
                provider.save();
            }
        }

        @Override
        public void reload() {
            throw new UnsupportedOperationException("Reload not yet supported");
        }

        /** The protobuf exports under {@code ~/Solor} a new or memory-only store can load. */
        static List<DataUriOption> loadableFiles(Controller controller) {
            List<DataUriOption> options = new ArrayList<>();
            File rootFolder = new File(System.getProperty("user.home"), "Solor");
            if (!rootFolder.exists()) {
                rootFolder.mkdirs();
            }
            File[] found = rootFolder.listFiles();
            if (found != null) {
                for (File f : found) {
                    if (controller.isValidDataLocation(f.getName())) {
                        options.add(new DataUriOption(f.getName(), f.toURI()));
                    }
                }
            }
            return options;
        }

        static boolean isLoadableFile(String name) {
            String lower = name.toLowerCase();
            return lower.endsWith("pb.zip") || (lower.endsWith(".zip") && lower.contains("tink"));
        }
    }

    /** Opens a 64-bit spined-array store under {@code ~/Solor}. */
    public static class OpenController extends Controller {
        public static final String CONTROLLER_NAME = "Open 64-bit SpinedArrayStore";

        @Override
        public void setDataUriOption(DataUriOption option) {
            super.setDataUriOption(option);
            if (option != null) {
                ServiceProperties.set(ServiceKeys.DATA_STORE_EXPECT_EMPTY, Boolean.FALSE);
            }
        }

        @Override
        public boolean isValidDataLocation(String name) {
            File directory = new File(new File(System.getProperty("user.home"), "Solor"), name);
            return directory.isDirectory() && StoreFiles.holdsAStore(directory);
        }

        @Override
        public List<DataUriOption> providerOptions() {
            List<DataUriOption> options = new ArrayList<>();
            File rootFolder = new File(System.getProperty("user.home"), "Solor");
            if (!rootFolder.exists()) {
                rootFolder.mkdirs();
            }
            File[] found = rootFolder.listFiles();
            if (found != null) {
                for (File f : found) {
                    if (f.isDirectory() && isValidDataLocation(f.getName())) {
                        options.add(new DataUriOption(f.getName(), f.toURI()));
                    }
                }
            }
            return options;
        }

        @Override
        public String controllerName() {
            return CONTROLLER_NAME;
        }

        @Override
        public int getSubPriority() {
            return 32;
        }
    }

    /** Creates a 64-bit spined-array store in a new folder under {@code ~/Solor}, loading a protobuf export into it. */
    public static class NewController extends Controller {
        public static final String CONTROLLER_NAME = "New 64-bit SpinedArrayStore";
        private static final DataServiceProperty NEW_FOLDER_PROPERTY = new DataServiceProperty("New folder name", false, true);

        private final MutableMap<DataServiceProperty, String> providerProperties = Maps.mutable.empty();
        private String importDataFileString;

        public NewController() {
            providerProperties.put(NEW_FOLDER_PROPERTY, null);
        }

        @Override
        public void setDataUriOption(DataUriOption option) {
            // Not the base class's: the option is the file to load, not a data-store root.
            dataUriOptionRef.set(option);
            ServiceProperties.set(ServiceKeys.DATA_STORE_EXPECT_EMPTY, Boolean.TRUE);
            if (option != null) {
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
            if (NEW_FOLDER_PROPERTY.equals(dataServiceProperty) && value instanceof String fileName) {
                if (fileName.isBlank()) {
                    return new ValidationRecord[]{new ValidationRecord(ValidationSeverity.ERROR, "Directory name cannot be blank", target)};
                }
                File possible = new File(new File(System.getProperty("user.home"), "Solor"), fileName);
                String[] entries = possible.list();
                if (possible.exists() && (entries == null || entries.length > 0)) {
                    return new ValidationRecord[]{new ValidationRecord(ValidationSeverity.ERROR, "Directory exists and is not empty", target)};
                }
            }
            return new ValidationRecord[]{};
        }

        @Override
        public List<DataUriOption> providerOptions() {
            return loadableFiles(this);
        }

        @Override
        public boolean isValidDataLocation(String name) {
            return isLoadableFile(name);
        }

        @Override
        protected SpinedArray64Provider createProvider() {
            String folderName = providerProperties.get(NEW_FOLDER_PROPERTY);
            if (folderName == null || folderName.isBlank()) {
                throw new IllegalStateException("New folder name not set for " + CONTROLLER_NAME);
            }
            File dataDirectory = new File(new File(System.getProperty("user.home"), "Solor"), folderName);
            ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, dataDirectory);
            ServiceProperties.set(ServiceKeys.DATA_STORE_EXPECT_EMPTY, Boolean.TRUE);
            return super.createProvider();
        }

        @Override
        protected void initializeProvider(SpinedArray64Provider provider) {
            if (importDataFileString != null) {
                File importFile = new File(importDataFileString);
                LOG.info("Queueing starter data for deferred import: {}", importFile.getName());
                dev.ikm.tinkar.entity.load.DataLoadProvider.get().addFile(importFile);
            } else {
                LOG.warn("No import file specified - creating empty database");
            }
        }

        @Override
        public String controllerName() {
            return CONTROLLER_NAME;
        }

        @Override
        public int getSubPriority() {
            return 33;
        }
    }

    /**
     * Loads a knowledge base into a 64-bit store held in memory only: nothing is read from or
     * written to disk, no change set is written, and the store ends with its JVM. The data URI
     * option names the protobuf export to load at start; with none, the store starts empty.
     */
    public static class LoadController extends Controller {
        public static final String CONTROLLER_NAME = "Load Memory-Only 64-bit Store";
        private String importDataFileString;
        private final AtomicBoolean loading = new AtomicBoolean(false);

        @Override
        public void setDataUriOption(DataUriOption option) {
            // Not the base class's: the option is the file to load, not a data-store root.
            dataUriOptionRef.set(option);
            if (option != null) {
                importDataFileString = option.toFile().getAbsolutePath();
            }
        }

        @Override
        public Optional<String> openConflict(DataUriOption option) {
            // Nothing on disk, so nothing another process could hold open.
            return Optional.empty();
        }

        @Override
        protected SpinedArray64Provider createProvider() {
            return new SpinedArray64Provider(null);
        }

        @Override
        protected void initializeProvider(SpinedArray64Provider provider) {
            if (importDataFileString != null) {
                try {
                    loading.set(true);
                    File importFile = new File(importDataFileString);
                    LOG.info("Queueing starter data for deferred import: {}", importFile.getName());
                    dev.ikm.tinkar.entity.load.DataLoadProvider.get().addFile(importFile);
                } finally {
                    loading.set(false);
                }
            } else {
                LOG.info("No import file specified: the memory-only store starts empty");
            }
        }

        @Override
        public List<DataUriOption> providerOptions() {
            return loadableFiles(this);
        }

        @Override
        public boolean isValidDataLocation(String name) {
            return isLoadableFile(name);
        }

        @Override
        public String controllerName() {
            return CONTROLLER_NAME;
        }

        @Override
        public int getSubPriority() {
            return 41;
        }

        @Override
        public boolean loading() {
            return loading.get();
        }

        @Override
        public void reload() {
            throw new UnsupportedOperationException("A memory-only store cannot be reloaded");
        }
    }
}
