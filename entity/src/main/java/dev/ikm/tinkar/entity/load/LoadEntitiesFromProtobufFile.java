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
package dev.ikm.tinkar.entity.load;

import dev.ikm.tinkar.entity.changeset.ChangeSetFormat;
import dev.ikm.tinkar.entity.changeset.IdentityIndex;
import dev.ikm.tinkar.entity.changeset.SchemaIds;
import dev.ikm.tinkar.common.service.internal.EntityStore;
import dev.ikm.tinkar.common.util.thread.StructuredScopes;
import dev.ikm.tinkar.common.util.thread.SubtaskFailedException;
import dev.ikm.tinkar.common.alert.AlertStreams;
import dev.ikm.tinkar.common.id.EntityKey;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.id.impl.NidLayout;
import dev.ikm.tinkar.common.service.DataActivity;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.SearchService;
import dev.ikm.tinkar.common.service.ServiceLifecycleManager;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.common.util.io.CountingInputStream;
import dev.ikm.tinkar.coordinate.Coordinates;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinate;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.common.service.EntityCountSummary;
import dev.ikm.tinkar.entity.EntityText;
import dev.ikm.tinkar.entity.transform.TinkarSchemaToEntityTransformer;
import dev.ikm.tinkar.schema.PatternChronology;
import dev.ikm.tinkar.schema.SemanticChronology;
import dev.ikm.tinkar.schema.TinkarMsg;
import dev.ikm.tinkar.terms.EntityBinding;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.entity.changeset.ComponentTable;
import java.util.zip.GZIPInputStream;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipFile;
import java.util.Optional;

/**
 * The purpose of this class is to successfully load all Protobuf messages from a protobuf file and transform them into entities.
 *
 * <h2>Watch list</h2>
 * A watch list traces chosen components through an import, for debugging identity and
 * allocation problems. Name the components' UUIDs, any of each public id's UUIDs, either with
 * the system property {@value #WATCH_PROPERTY} or with {@link #watch(UUID...)}:
 * <pre>{@code
 * -Dtinkar.import.watch=<uuid>[,<uuid>...]
 * }</pre>
 * UUIDs are separated by commas or whitespace. For every watched UUID the import logs, at
 * {@code INFO}:
 * <ul>
 *     <li>the record and its nid when pass 1 reads records for their identities (format
 *     version 1, multi-pass);</li>
 *     <li>each watched entity as it is stored;</li>
 *     <li>after the import, the UUID's nid, entity key, pattern and element sequences, stored
 *     bytes and text.</li>
 * </ul>
 * The watch list is bound to {@link #SCOPED_WATCH_LIST} for the whole import, so a store can
 * trace its own work on watched components: the RocksDB store logs each watched entity-key
 * allocation. Work an import hands to a parallel stream, rather than to a structured task
 * scope, runs outside that binding; the identity index (format version 2) registers its nids
 * that way, so the store traces only the allocations made on the importing thread.
 */
public class LoadEntitiesFromProtobufFile extends TrackingCallable<EntityCountSummary> {

    protected static final Logger LOG = LoggerFactory.getLogger(LoadEntitiesFromProtobufFile.class.getName());
    public static final int InputStreamBufferSize = 1024 * 1024;

    private final TinkarSchemaToEntityTransformer entityTransformer =
            TinkarSchemaToEntityTransformer.getInstance();
    private final File importFile;
    private final AtomicLong importCount = new AtomicLong();
    private final AtomicLong importConceptCount = new AtomicLong();
    private final AtomicLong importSemanticCount = new AtomicLong();
    private final AtomicLong importPatternCount = new AtomicLong();
    private final AtomicLong importStampCount = new AtomicLong();
    
    private final AtomicLong identifierCount = new AtomicLong();
    private final boolean useMultiPassImport;
    /** The format version the changeset's manifest names; known once {@link #compute()} has read it. */
    private volatile int formatVersion;
    /** Whether the import took every component's identity from the changeset's identity index. */
    private volatile boolean usedIdentityIndex;
    private final Set<UUID> watchList = new CopyOnWriteArraySet<>(watchListFromProperty());

    /**
     * The system property naming UUIDs to watch during every import, separated by commas or
     * whitespace; see the class documentation.
     */
    public static final String WATCH_PROPERTY = "tinkar.import.watch";

    public static final ScopedValue<TinkarMsg> SCOPED_TINKAR_MSG = ScopedValue.newInstance();
    /**
     * The watched UUIDs, bound for the whole of an import; stores and other code an import calls
     * read it to trace their work on watched components.
     */
    public static final ScopedValue<Set<UUID>> SCOPED_WATCH_LIST = ScopedValue.newInstance();

    /**
     * Create a loader that auto-detects the import mode based on the provider.
     * <p>     * Uses multi-pass import for providers that encode pattern information in NIDs
     * (e.g., RocksDB), and single-pass import for sequential NID providers
     * (the spined array, persistent or ephemeral).
     * 
     * @param importFile the protobuf file to import
     */
    public LoadEntitiesFromProtobufFile(File importFile) {
        this(importFile, PrimitiveData.requiresMultiPassImport());
    }

    /**
     * Create a loader with configurable pass mode.
     * @param importFile the protobuf file to import
     * @param useMultiPassImport if true, use multi-pass import (default); if false, use 1-pass import
     */
    public LoadEntitiesFromProtobufFile(File importFile, boolean useMultiPassImport) {
        super(false, true);
        this.importFile = importFile;
        this.useMultiPassImport = useMultiPassImport;
        LOG.info("Loading entities from: " + importFile.getAbsolutePath() + " using " +
                (useMultiPassImport ? "multi-pass" : "1-pass") + " import mode");
        if (!watchList.isEmpty()) {
            LOG.info("Watching {} from -D{}", watchList, WATCH_PROPERTY);
        }
    }

    /**
     * Adds UUIDs to this import's watch list, alongside any the {@value #WATCH_PROPERTY}
     * property names; see the class documentation.
     *
     * @param uuids any UUIDs of the components to trace
     * @return this loader
     */
    public LoadEntitiesFromProtobufFile watch(UUID... uuids) {
        watchList.addAll(Arrays.asList(uuids));
        return this;
    }

    private static Set<UUID> watchListFromProperty() {
        String property = System.getProperty(WATCH_PROPERTY, "").strip();
        if (property.isEmpty()) {
            return Set.of();
        }
        Set<UUID> uuids = new LinkedHashSet<>();
        for (String token : property.split("[,\\s]+")) {
            try {
                uuids.add(UUID.fromString(token));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("-D" + WATCH_PROPERTY + " names " + token
                        + ", which is not a UUID", e);
            }
        }
        return uuids;
    }

    private boolean isWatched(PublicId publicId) {
        for (UUID uuid : publicId.asUuidArray()) {
            if (watchList.contains(uuid)) {
                return true;
            }
        }
        return false;
    }

    private void traceIfWatched(Entity<? extends EntityVersion> entity) {
        if (!watchList.isEmpty() && isWatched(entity.publicId())) {
            LOG.info("Stored watched entity nid {} (0x{}): {}", entity.nid(), Long.toHexString(entity.nid()), entity);
        }
    }

    /** Logs where each watched UUID landed, once the import has stored everything. */
    private void reportWatchList() {
        if (watchList.isEmpty()) {
            return;
        }
        LOG.info("Checking watchList: {} ", watchList);
        for (UUID uuid : watchList) {
            Optional<dev.ikm.tinkar.common.id.EntityKey> entityKey = PrimitiveData.getEntityKey(uuid);
            if (entityKey.isEmpty()) {
                LOG.info("Watched {} is not in the store", uuid);
                continue;
            }
            StringBuilder sb = new StringBuilder();
            long nid = PrimitiveData.get().nidForUuids(uuid);
            int patternSequence = PrimitiveData.patternSequenceForNid(nid);
            long elementSequence = PrimitiveData.elementSequenceForNid(nid);
            byte[] entityBytes = EntityStore.current().getBytes(nid);
            sb.append("\n\nnid for ").append(uuid).append(" is: ").append(nid);
            sb.append("\npatternSequence for ").append(uuid).append(" is: ").append(patternSequence);
            sb.append("\nelementSequence for ").append(uuid).append(" is: ").append(elementSequence);
            sb.append("\nentityBytes for ").append(uuid).append(" is: ").append(Arrays.toString(entityBytes));
            sb.append("\nentityKey for ").append(uuid).append(" is: ").append(entityKey);
            sb.append("\nText with nid: ").append(PrimitiveData.textWithNid(nid));
            LOG.info(sb.toString());
        }
    }

    /**
     * This purpose of this method is to process all protobuf messages and call the entity transformer.
     * @return EntityCountSummary count of all entities/protobuf messages loaded/exported
     */
    public EntityCountSummary compute() {
        try {
            initCounts();

            updateTitle("Import Protobuf Data from " + importFile.getName());
            updateProgress(-1, 1);
            updateMessage("Analyzing Import File...");

            // Analyze Manifest and update tracking callable
            List<Map.Entry<PublicId, String>> manifestEntryData = new ArrayList<>(); // a public id is never a hash key
            long expectedImports = analyzeManifest(manifestEntryData);
            LOG.info(expectedImports + " Entities to process, format version " + formatVersion + "...");
            if (formatVersion >= 3) {
                return ScopedValue.where(SCOPED_WATCH_LIST, Collections.unmodifiableSet(watchList))
                        .call(() -> computeFormat3(expectedImports, manifestEntryData));
            }

            // With an identity index every nid is assigned from it and the records are read
            // once, in either mode; without one (format version 1), the mode decides.
            boolean indexed = ChangeSetFormat.hasIdentityIndex(importFile);
            if (formatVersion >= 2 && !indexed) {
                throw new IllegalStateException(importFile.getName() + " names " + ChangeSetFormat.VERSION_ATTRIBUTE
                        + ": " + formatVersion + " but carries no identity index (" + ChangeSetFormat.IDENTITY_INDEX + ")");
            }
            Set<UUID> watched = Collections.unmodifiableSet(watchList);
            if (useMultiPassImport || indexed) {
                return ScopedValue.where(SCOPED_WATCH_LIST, watched)
                        .call(() -> computeMultiPass(expectedImports, manifestEntryData, indexed));
            } else {
                return ScopedValue.where(SCOPED_WATCH_LIST, watched)
                        .call(() -> computeOnePass(expectedImports, manifestEntryData));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Import with every nid assigned before any record is transformed, so forward references
     * resolve. Pass 1 assigns the nids: from the changeset's identity index when it has one,
     * else by reading every record for its identity. Pass 2 transforms and stores the records.
     */
    private EntityCountSummary computeMultiPass(long expectedImports, List<Map.Entry<PublicId, String>> manifestEntryData,
                                                boolean indexed) throws Exception {
        updateMessage("Starting multi-pass import...");
        // Progress is in bytes throughout: the first half of the bar is the first pass, the
        // second half the second, so the bar never resets when a pass changes its measure. The
        // identity index's registration reports components, scaled onto the first half.
        final long fileLength = Math.max(1, this.importFile.length());
        final long expected = Math.max(1, expectedImports);
        updateProgress(0, fileLength * 2);

        // Pass 1: generate identifiers for all entities
        EntityService.get().beginLoadPhase();
        CopyOnWriteArrayList<PublicId> patternIds = new CopyOnWriteArrayList<>();

        if (indexed) {
            updateMessage("Registering identifiers from the identity index (step 1 of 2)...");
            identifierCount.set(IdentityIndex.registerNids(importFile, registered -> {
                updateMessage(String.format("Registering identifiers from the identity index (step 1 of 2): %,d of %,d",
                        registered, expected));
                updateProgress(Math.min(fileLength, (long) ((double) registered / expected * fileLength)), fileLength * 2);
            }));
            usedIdentityIndex = true;
        } else try (FileInputStream fileIn = new FileInputStream(importFile);
             BufferedInputStream buffIn = new BufferedInputStream(fileIn, InputStreamBufferSize);
             CountingInputStream countingIn = new CountingInputStream(buffIn);
             ZipInputStream zis = new ZipInputStream(countingIn)) {
            updateMessage("Assigning identifiers by reading every record (step 1 of 2)...");
            ZipEntry zipEntry;
            int messageIndex = 0;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (!ChangeSetFormat.isMetadata(zipEntry.getName())) {
                    Semaphore permits = new Semaphore(Runtime.getRuntime().availableProcessors() * 8);

                    try (StructuredTaskScope<Object, Void, SubtaskFailedException> scope = StructuredScopes.open()) {
                        while (zis.available() > 0) {
                            // zis.available returns 1 until AFTER EOF has been reached
                            TinkarMsg pbTinkarMsg = TinkarMsg.parseDelimitedFrom(zis);
                            permits.acquire();
                            scope.fork(() -> {
                                try {
                                    ScopedValue.where(SCOPED_TINKAR_MSG, pbTinkarMsg).call(() -> {
                                                if (pbTinkarMsg != null) {
                                                    // Batch progress updates to prevent hanging the UI thread
                                                    if (identifierCount.incrementAndGet() % 1000 == 0) {
                                                        updateProgress(countingIn.getBytesRead(), fileLength * 2);
                                                    }
                                                    long nid = switch (pbTinkarMsg.getValueCase()) {
                                                        case CONCEPT_CHRONOLOGY ->
                                                                makeNid(EntityBinding.Concept.pattern(), pbTinkarMsg.getConceptChronology().getPublicId());
                                                        case SEMANTIC_CHRONOLOGY ->
                                                                makeNid(pbTinkarMsg.getSemanticChronology());
                                                        case PATTERN_CHRONOLOGY ->
                                                                makeNid(EntityBinding.Pattern.pattern(), pbTinkarMsg.getPatternChronology().getPublicId());
                                                        case STAMP_CHRONOLOGY ->
                                                                makeNid(EntityBinding.Stamp.pattern(), pbTinkarMsg.getStampChronology().getPublicId());
                                                        case VALUE_NOT_SET ->
                                                                throw new IllegalStateException("Tinkar message value not set");
                                                    };
                                                    if (pbTinkarMsg.getValueCase().getNumber() == TinkarMsg.ValueCase.PATTERN_CHRONOLOGY.getNumber()) {
                                                        PatternChronology patternChronology = pbTinkarMsg.getPatternChronology();
                                                        patternIds.add(getEntityPublicId(patternChronology.getPublicId()));
                                                    }
                                                }
                                                return null;

                                            });
                                } catch (Throwable t) {
                                    LOG.error("Unhandled exception in identifier subtask for msg: {}", pbTinkarMsg, t);
                                    throw t; // preserve failure semantics
                                } finally {
                                    permits.release();
                                }
                            });
                        }
                        scope.join();
                    }
                }
            }
            updateMessage("Imported identifiers...");
            LOG.info("Imported {} identifiers", String.format("%,d",identifierCount.get()));
        }

        // Pass 2: load entities into RocksDB
        updateMessage("Importing records (step 2 of 2)...");
        try (FileInputStream fileIn = new FileInputStream(importFile);
             BufferedInputStream buffIn = new BufferedInputStream(fileIn, InputStreamBufferSize); // Increased buffer size
             CountingInputStream countingIn = new CountingInputStream(buffIn);
             ZipInputStream zis = new ZipInputStream(countingIn)) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (ChangeSetFormat.isMetadata(zipEntry.getName())) {
                    continue;
                }
                importRecords(zis, () -> updateProgress(fileLength + countingIn.getBytesRead(), fileLength * 2));
            }
            StringBuilder stringBuilder = new StringBuilder();

            patternIds.forEach(patternId -> {
                long nid = PrimitiveData.get().nidForUuids(patternId.asUuidArray());
                PatternEntity patternEntity = EntityHandle.get(nid).asPattern().orElse(null);
                StampCoordinate stampCoordinate = Coordinates.Stamp.DevelopmentLatest();
                String entityText = PrimitiveData.textWithNid(nid);
                // Any of the pattern's UUIDs finds its key.
                PrimitiveData.getEntityKey(patternId.leastUuid()).ifPresent(entityKey ->
                        stringBuilder.append("\n\nPattern: ").append(entityText).append(" ").append(patternId.idString())
                                .append(" EntityKey: ").append(entityKey));

                stringBuilder.append("\n nid=").append(nid).append(" (0x").append(String.format("%08X", nid)).append(")").append(" pattern sequence=").append(NidLayout.active().decodePatternSequence(nid)).append(" element sequence=").append(NidLayout.active().decodeElementSequence(nid));
                stringBuilder.append("\nPatternEntity: ").append(patternEntity);
            });

            LOG.info("PatternInfo:\n {}", stringBuilder);

            LOG.info("Imported {} entities", String.format("%,d", importCount.get()));


            verifyManifest(manifestEntryData);

            reportWatchList();
        } catch (IOException e) {
            updateTitle("Import Protobuf Data from " + importFile.getName() + " with error(s)");
            throw new RuntimeException(e);
        } finally {
            try {
                EntityService.get().endLoadPhase();
            } catch (Exception e) {
                LOG.error("Encountered exception {}", e.getMessage());
            }
            commitSearchIndexIfAvailable();
            updateMessage("In " + durationString());
            updateProgress(1, 1);
        }
        if (importCount.get() != expectedImports) {
            LOG.warn("ERROR: Expected " + expectedImports + " imported Entities, but imported " + importCount.get());
        }
        return summarize();
    }

    /**
     * Imports the records of one stream: each is transformed on the pool and stored, with
     * {@code tick} run every thousand records for progress. Shared by the format-2 records
     * pass and every record entry of a format-3 changeset.
     */
    private void importRecords(InputStream in, Runnable tick) throws Exception {
        Consumer<Entity<? extends EntityVersion>> entityConsumer = entity -> {
            EntityService.get().putEntityNoCache(entity, DataActivity.LOADING_CHANGE_SET);
            updateCounts(entity);
            traceIfWatched(entity);
        };
        final AtomicInteger errorCount = new AtomicInteger();
        try (StructuredTaskScope<Object, Void, SubtaskFailedException> scope = StructuredScopes.open()) {
            Semaphore permits = new Semaphore(Runtime.getRuntime().availableProcessors() * 8);
            TinkarMsg pbTinkarMsg;
            while ((pbTinkarMsg = TinkarMsg.parseDelimitedFrom(in)) != null) {
                TinkarMsg record = pbTinkarMsg;
                permits.acquire();
                scope.fork(() -> ScopedValue.where(SCOPED_TINKAR_MSG, record).call(() -> {
                    try {
                        entityTransformer.transform(record, entityConsumer, (stampEntity) -> {
                        });
                        // Batch progress updates to prevent hanging the UI thread
                        if (importCount.incrementAndGet() % 1000 == 0) {
                            tick.run();
                        }
                    } catch (RuntimeException e) {
                        if (e instanceof IllegalStateException && e.getMessage() != null
                                && (e.getMessage().contains("No entity key found for UUIDs")
                                || e.getMessage().contains("Entity byte[] not found"))) {
                            LOG.error("{}. Error transforming Protobuf message: {} \n  {}", errorCount.getAndIncrement(), e.getMessage(), record);
                        } else {
                            LOG.error("{}. Error transforming Protobuf message: {}  \n  {}", errorCount.getAndIncrement(), e.getMessage(), record, e);
                        }
                    } finally {
                        permits.release();
                    }
                    return null;
                }));
            }
            scope.join();
        }
    }

    /**
     * Format 3 (IKE-Network/ike-issues#1275): registers the component table in table order, so
     * every nid is minted before a record is read and a fresh store's nids ascend with the
     * file; then imports the record entries in the order the manifest lists them, resolving
     * references by sequence through the registered table.
     */
    private EntityCountSummary computeFormat3(long expectedImports, List<Map.Entry<PublicId, String>> manifestEntryData) throws Exception {
        updateMessage("Starting format 3 import...");
        EntityService.get().beginLoadPhase();
        try (ZipFile zip = new ZipFile(importFile)) {
            Manifest manifest = ChangeSetFormat.manifest(zip)
                    .orElseThrow(() -> new IllegalStateException(importFile.getName() + " has no manifest"));
            if (!ChangeSetFormat.hasComponentTable(zip)) {
                throw new IllegalStateException(importFile.getName() + " names " + ChangeSetFormat.VERSION_ATTRIBUTE
                        + ": " + formatVersion + " but carries no component table (" + ChangeSetFormat.COMPONENT_TABLE + ")");
            }
            List<ChangeSetFormat.RecordEntry> entries = ChangeSetFormat.recordEntries(zip, manifest);
            String componentCountValue = manifest.getMainAttributes().getValue(ChangeSetFormat.COMPONENT_COUNT_ATTRIBUTE);
            final long componentCount = Math.max(1, componentCountValue == null ? expectedImports : Long.parseLong(componentCountValue.strip()));
            final long tableBytes = Math.max(1, compressedSize(zip, ChangeSetFormat.COMPONENT_TABLE) + compressedSize(zip, ChangeSetFormat.REFERENCE_TABLE));
            long recordBytes = 0;
            for (ChangeSetFormat.RecordEntry entry : entries) {
                recordBytes += Math.max(0, entry.entry().getCompressedSize());
            }
            final long total = tableBytes + Math.max(1, recordBytes);
            updateProgress(0, total);
            updateMessage("Registering components from the table (step 1 of 2)...");
            long[] nids = ComponentTable.registerNids(zip, componentCount, registered -> {
                updateMessage(String.format("Registering components from the table (step 1 of 2): %,d of %,d", registered, componentCount));
                updateProgress(Math.min(tableBytes, (long) ((double) registered / componentCount * tableBytes)), total);
            });
            identifierCount.set(nids.length - 1);
            usedIdentityIndex = true;
            updateMessage("Importing records (step 2 of 2)...");
            final long[] done = {tableBytes};
            for (ChangeSetFormat.RecordEntry entry : entries) {
                CountingInputStream counting = new CountingInputStream(zip.getInputStream(entry.entry()));
                try (InputStream in = entry.entry().getName().endsWith(ChangeSetFormat.GZIP_SUFFIX)
                        ? new BufferedInputStream(new GZIPInputStream(counting, 1 << 16), InputStreamBufferSize)
                        : new BufferedInputStream(counting, InputStreamBufferSize)) {
                    ScopedValue.where(TinkarSchemaToEntityTransformer.SCOPED_SEQUENCE_NIDS, nids).call(() -> {
                        importRecords(in, () -> updateProgress(done[0] + counting.getBytesRead(), total));
                        return null;
                    });
                }
                done[0] += Math.max(0, entry.entry().getCompressedSize());
            }
            LOG.info("Imported {} entities", String.format("%,d", importCount.get()));
            verifyManifest(manifestEntryData);
            reportWatchList();
        } catch (IOException e) {
            updateTitle("Import Protobuf Data from " + importFile.getName() + " with error(s)");
            throw new RuntimeException(e);
        } finally {
            try {
                EntityService.get().endLoadPhase();
            } catch (Exception e) {
                LOG.error("Encountered exception {}", e.getMessage());
            }
            commitSearchIndexIfAvailable();
            updateMessage("In " + durationString());
            updateProgress(1, 1);
        }
        if (importCount.get() != expectedImports) {
            IllegalStateException e = new IllegalStateException("Import Failed: Expected " + expectedImports + " Entities, but imported " + importCount.get());
            AlertStreams.dispatchToRoot(e);
            throw e;
        }
        return summarize();
    }

    private static long compressedSize(ZipFile zip, String name) {
        ZipEntry entry = zip.getEntry(name);
        return entry == null ? 0 : Math.max(0, entry.getCompressedSize());
    }

    private static void verifyManifest(List<Map.Entry<PublicId, String>> manifestEntryData) {
        manifestEntryData.forEach(entry -> {
            if (!PrimitiveData.get().hasPublicId(entry.getKey())) {
                LOG.warn("Dependent Module or Author is not Present -" +
                        " PublicId: " + entry.getKey().idString() +
                        " Description: " + entry.getValue());
            }
        });
    }

    /**
     * One-pass import: Load entities directly without pre-registering NIDs.
     * This may fail if the changeset contains forward references.
     */
    private EntityCountSummary computeOnePass(long expectedImports, List<Map.Entry<PublicId, String>> manifestEntryData) {
        updateMessage("Importing Protobuf Data (1-pass mode)...");
        LOG.debug("Expected imports: " + expectedImports);

        EntityService.get().beginLoadPhase();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(importFile))) {
            // Consumer to be run for each transformed Entity
            Consumer<Entity<? extends EntityVersion>> entityConsumer = entity -> {
                EntityService.get().putEntityNoCache(entity, DataActivity.LOADING_CHANGE_SET);
                updateCounts(entity);
                traceIfWatched(entity);
            };

            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (ChangeSetFormat.isMetadata(zipEntry.getName())) {
                    continue;
                }

                while (zis.available() > 0) {
                    TinkarMsg pbTinkarMsg = TinkarMsg.parseDelimitedFrom(zis);
                    if (pbTinkarMsg == null) {
                        continue;
                    }

                    // Transform without pre-registered NIDs - may fail on forward references
                    try {
                        entityTransformer.transform(pbTinkarMsg, entityConsumer, (stampEntity) -> {});
                    } catch (Exception e) {
                        LOG.error("1-pass - Error transforming message: {}", e.getMessage(), e);
                        throw e; // Re-throw to fail the test
                    }

                    // Batch progress updates
                    if (importCount.incrementAndGet() % 1000 == 0) {
                        updateProgress(importCount.get(), expectedImports);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("1-pass - Entity loading failed", e);
            updateTitle("Failed: Import Protobuf data from " + importFile.getName());
            AlertStreams.dispatchToRoot(e);
            throw new RuntimeException("1-pass failed", e);
        } finally {
            try {
                EntityService.get().endLoadPhase();
            } catch (Exception e) {
                LOG.error("Encountered exception {}", e.getMessage());
            }
            commitSearchIndexIfAvailable();
            updateMessage("In " + durationString());
            updateProgress(1, 1);
        }
        verifyManifest(manifestEntryData);
        reportWatchList();

        if (importCount.get() != expectedImports) {
            IllegalStateException e = new IllegalStateException("Import Failed: Expected " + expectedImports + " Entities, but imported " + importCount.get());
            AlertStreams.dispatchToRoot(e);
            throw e;
        }
        return summarize();
    }

    /**
     * Generate NID for a protobuf message to register the entity in the datastore.
     * This allows Pass 2 to resolve references.
     */
    private long makeNidForMessage(TinkarMsg pbTinkarMsg) {
        return switch (pbTinkarMsg.getValueCase()) {
            case CONCEPT_CHRONOLOGY -> 
                Entity.nidForConcept(getEntityPublicId(pbTinkarMsg.getConceptChronology().getPublicId()));
            case SEMANTIC_CHRONOLOGY -> {
                var semanticChronology = pbTinkarMsg.getSemanticChronology();
                PublicId patternPublicId = getEntityPublicId(semanticChronology.getPatternForSemanticPublicId());
                PublicId semanticPublicId = getEntityPublicId(semanticChronology.getPublicId());
                yield Entity.nidForSemantic(patternPublicId, semanticPublicId);
            }
            case PATTERN_CHRONOLOGY ->
                Entity.nidForPattern(getEntityPublicId(pbTinkarMsg.getPatternChronology().getPublicId()));
            case STAMP_CHRONOLOGY ->
                Entity.nidForStamp(getEntityPublicId(pbTinkarMsg.getStampChronology().getPublicId()));
            case VALUE_NOT_SET ->
                throw new IllegalStateException("Tinkar message value not set");
        };
    }

    /**
     * Extract PublicId from protobuf PublicId message.
     */
    private static PublicId getEntityPublicId(dev.ikm.tinkar.schema.PublicId pbPublicId) {
        return SchemaIds.toPublicId(pbPublicId);
    }

    /** The format version the changeset's manifest names (1 when it names none); known once {@link #compute()} has run. */
    public int formatVersion() {
        return formatVersion;
    }

    /**
     * Whether the import took every component's identity from the changeset's identity index,
     * reading the records once, rather than reading them for their identities first.
     */
    public boolean usedIdentityIndex() {
        return usedIdentityIndex;
    }

    protected void initCounts() {
        identifierCount.set(0);
        importCount.set(0);
        importConceptCount.set(0);
        importSemanticCount.set(0);
        importPatternCount.set(0);
        importStampCount.set(0);
    }

    private void updateCounts(Entity entity){
        switch (entity) {
            case ConceptEntity ignored -> importConceptCount.incrementAndGet();
            case SemanticEntity ignored -> importSemanticCount.incrementAndGet();
            case PatternEntity ignored -> importPatternCount.incrementAndGet();
            case StampEntity ignored -> importStampCount.incrementAndGet();
            default -> throw new IllegalStateException("Unexpected value: " + EntityText.diagnostic(entity));
        }
    }

    public EntityCountSummary summarize() {
        LOG.info("Imported: " + importCount.get() + " entities in: " + durationString());
        return new EntityCountSummary(
                importConceptCount.get(),
                importSemanticCount.get(),
                importPatternCount.get(),
                importStampCount.get()
        );
    }

    private static void commitSearchIndexIfAvailable() {
        // TODO(temp): two-mode logic (no-op vs. full recreate) is a stand-in
        // for the proper touched-nid catch-up design. See LoadPhaseSearchPolicy
        // for the longer rationale; replace this with the catch-up walk when
        // that design lands.
        dev.ikm.tinkar.entity.LoadPhaseSearchPolicy policy =
                dev.ikm.tinkar.entity.EntityService.get().loadPhaseSearchPolicy();
        if (!policy.recreateRequired()) {
            // The change-set fit under the live-index threshold; per-merge
            // calls during loadPhase wrote its documents, and endLoadPhase
            // made them visible to searches. No post-load search work needed.
            LOG.info("Change-set indexed live during load phase ({} entities, threshold {}) — no recreate needed",
                    policy.liveIndexedCount(),
                    dev.ikm.tinkar.entity.LoadPhaseSearchPolicy.threshold());
            return;
        }
        // Change-set exceeded the live-index threshold — fall back to a full
        // recreate so the entities merged after live indexing was abandoned
        // get into the index.
        LOG.info("Change-set exceeded live-index threshold ({} > {}); scheduling full recreate",
                policy.liveIndexedCount(),
                dev.ikm.tinkar.entity.LoadPhaseSearchPolicy.threshold());
        Optional<SearchService> searchService = ServiceLifecycleManager.get()
                .getRunningService(SearchService.class);
        if (searchService.isEmpty()) {
            policy.markRecreated(); // no index to bring current
            return;
        }
        try {
            Object future = searchService.get().recreateIndex();
            if (future instanceof java.util.concurrent.CompletableFuture<?> cf) {
                cf.get();
            }
            policy.markRecreated();
        } catch (Exception e) {
            LOG.warn("Failed to recreate Lucene index after import", e);
        }
    }

    private long analyzeManifest(List<Map.Entry<PublicId, String>> manifestEntryData) {
        // Through the central directory: the manifest is the last entry, after the records and
        // the identity index, and streaming to it inflates both (IKE-Network/ike-issues#1269).
        try (ZipFile zip = new ZipFile(importFile)) {
            zip.stream().forEach(entry -> LOG.info("{} zip entry size: {}", entry.getName(), entry.getSize()));
            Optional<Manifest> read = ChangeSetFormat.manifest(zip);
            if (read.isEmpty()) {
                return -1;
            }
            Manifest manifest = read.get();
            formatVersion = ChangeSetFormat.version(manifest.getMainAttributes(), importFile.getName());
            long expectedImports = Long.parseLong(manifest.getMainAttributes().getValue("Total-Count"));
            // Get Dependent Module / Author PublicIds and Descriptions
            manifest.getEntries().keySet().forEach((publicIdKey) -> {
                PublicId publicId = PublicIds.of(publicIdKey.split(","));
                String description = manifest.getEntries().get(publicIdKey).getValue("Description");
                manifestEntryData.add(Map.entry(publicId, description));
            });
            return expectedImports;
        } catch (ChangeSetFormat.UnsupportedFormatException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long makeNid(SemanticChronology semanticChronology) {
        return makeNid(EntityProxy.Pattern.make(getEntityPublicId(semanticChronology.getPatternForSemanticPublicId())),
                semanticChronology.getPublicId());
    }


    private long makeNid(EntityProxy.Pattern pattern, dev.ikm.tinkar.schema.PublicId pbPublicId) {
        PublicId publicId = getEntityPublicId(pbPublicId);
        long nid = makeNid(pattern, getEntityPublicId(pbPublicId));
        for (UUID uuid : publicId.asUuidArray()) {
            if (watchList.contains(uuid)) {
                LOG.info("Found watch: {} for: \n\n{}", uuid, SCOPED_TINKAR_MSG.get());
                LOG.info("nid for {} is: {}", uuid, nid);
                LOG.info("nid for {} in hex is: {}", uuid, Long.toHexString(nid));
            }
        }
        return nid;
    }
    private long makeNid(EntityProxy.Pattern pattern, PublicId entityId) {
        dev.ikm.tinkar.common.id.EntityKey entityKey = PrimitiveData.getEntityKey(pattern.publicId(), entityId);
        return entityKey.nid();
    }

}
