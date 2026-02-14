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

import dev.ikm.tinkar.common.alert.AlertStreams;
import dev.ikm.tinkar.common.id.EntityKey;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.id.impl.NidCodec6;
import dev.ikm.tinkar.common.service.DataActivity;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceLifecycleManager;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.common.util.io.CountingInputStream;
import dev.ikm.tinkar.coordinate.Coordinates;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinate;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.common.service.EntityCountSummary;
import dev.ikm.tinkar.entity.transform.TinkarSchemaToEntityTransformer;
import dev.ikm.tinkar.schema.PatternChronology;
import dev.ikm.tinkar.schema.SemanticChronology;
import dev.ikm.tinkar.schema.TinkarMsg;
import dev.ikm.tinkar.terms.EntityBinding;
import dev.ikm.tinkar.terms.EntityProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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

/**
 * The purpose of this class is to successfully load all Protobuf messages from a protobuf file and transform them into entities.
 */
public class LoadEntitiesFromProtobufFile extends TrackingCallable<EntityCountSummary> {

    protected static final Logger LOG = LoggerFactory.getLogger(LoadEntitiesFromProtobufFile.class.getName());
    public static final int InputStreamBufferSize = 1024 * 1024;

    private final TinkarSchemaToEntityTransformer entityTransformer =
            TinkarSchemaToEntityTransformer.getInstance();
    private static final String MANIFEST_RELPATH = "META-INF/MANIFEST.MF";
    private final File importFile;
    private final AtomicLong importCount = new AtomicLong();
    private final AtomicLong importConceptCount = new AtomicLong();
    private final AtomicLong importSemanticCount = new AtomicLong();
    private final AtomicLong importPatternCount = new AtomicLong();
    private final AtomicLong importStampCount = new AtomicLong();
    
    private final AtomicLong identifierCount = new AtomicLong();
    private final boolean useMultiPassImport;
    private final Set<UUID> watchList = new CopyOnWriteArraySet<>();

    public static final ScopedValue<TinkarMsg> SCOPED_TINKAR_MSG = ScopedValue.newInstance();
    public static final ScopedValue<Set<UUID>> SCOPED_WATCH_LIST = ScopedValue.newInstance();

    /**
     * Create a loader that auto-detects the import mode based on the provider.
     * <p>
     * Uses multi-pass import for providers that encode pattern information in NIDs
     * (e.g., RocksDB), and single-pass import for sequential NID providers
     * (e.g., SpinedArray, MVStore, Ephemeral).
     * </p>
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
            Map<PublicId, String> manifestEntryData =new HashMap();
            long expectedImports = analyzeManifest(manifestEntryData);
            LOG.info(expectedImports + " Entities to process...");

            if (useMultiPassImport) {
                return computeMultiPass(expectedImports, manifestEntryData);
            } else {
                return computeOnePass(expectedImports, manifestEntryData);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Multi-pass import: Imports entities in dependency order to handle forward references.
     * Pass 1: Import non-semantics (Concepts, Patterns, Stamps) - these have no referenced components
     * Pass 2+: Import semantics whose referenced components now exist in the database
     * Repeats until all semantics are successfully imported or no progress is made.
     */
    private EntityCountSummary computeMultiPass(long expectedImports, Map<PublicId, String> manifestEntryData) throws Exception {
        updateMessage("Starting multi-pass import...");
        updateProgress(0, expectedImports * 2);

        // Pass 1: generate identifiers for all entities
        EntityService.get().beginLoadPhase();
        CopyOnWriteArrayList<UUID> patternUuids = new CopyOnWriteArrayList<>();

        try (FileInputStream fileIn = new FileInputStream(importFile);
             BufferedInputStream buffIn = new BufferedInputStream(fileIn, InputStreamBufferSize);
             CountingInputStream countingIn = new CountingInputStream(buffIn);
             ZipInputStream zis = new ZipInputStream(countingIn)) {
            ZipEntry zipEntry;
            int messageIndex = 0;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (!zipEntry.getName().equals(MANIFEST_RELPATH)) {
                    Semaphore permits = new Semaphore(Runtime.getRuntime().availableProcessors() * 8);

                    try (StructuredTaskScope scope = StructuredTaskScope.open()) {
                        while (zis.available() > 0) {
                            // zis.available returns 1 until AFTER EOF has been reached
                            TinkarMsg pbTinkarMsg = TinkarMsg.parseDelimitedFrom(zis);
                            permits.acquire();
                            scope.fork(() -> {
                                try {
                                    ScopedValue.where(SCOPED_TINKAR_MSG, pbTinkarMsg)
                                            .where(SCOPED_WATCH_LIST, watchList).call(() -> {
                                                if (pbTinkarMsg != null) {
                                                    // Batch progress updates to prevent hanging the UI thread
                                                    if (identifierCount.incrementAndGet() % 1000 == 0) {
                                                        updateProgress(countingIn.getBytesRead(), this.importFile.length() * 2);
                                                    }
                                                    int nid = switch (pbTinkarMsg.getValueCase()) {
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
                                                        String uuidStr = patternChronology.getPublicId().getUuidsList().get(0);
                                                        UUID uuid = UUID.fromString(uuidStr);
                                                        patternUuids.add(uuid);
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
        try (FileInputStream fileIn = new FileInputStream(importFile);
             BufferedInputStream buffIn = new BufferedInputStream(fileIn, InputStreamBufferSize); // Increased buffer size
             CountingInputStream countingIn = new CountingInputStream(buffIn);
             ZipInputStream zis = new ZipInputStream(countingIn)) {
            // Consumer to be run for each transformed Entity
            Consumer<Entity<? extends EntityVersion>> entityConsumer = entity -> {
                EntityService.get().putEntityNoCache(entity, DataActivity.LOADING_CHANGE_SET);
                updateCounts(entity);
            };

            ZipEntry zipEntry;
            final AtomicInteger errorCount = new AtomicInteger();
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (zipEntry.getName().equals(MANIFEST_RELPATH)) {
                    continue;
                }
                try (StructuredTaskScope scope = StructuredTaskScope.open()) {
                    Semaphore permits = new Semaphore(Runtime.getRuntime().availableProcessors() * 8);
                    while (zis.available() > 0) {
                        // zis.available returns 1 until AFTER EOF has been reached
                        // Protobuf parser reads the data UP TO EOF, so zis.available
                        // will return 1 when the only thing left is EOF
                        // pbTinkarMsg should be null for this last entry
                        TinkarMsg pbTinkarMsg = TinkarMsg.parseDelimitedFrom(zis);
                        if (pbTinkarMsg == null) {
                            continue;
                        }
                        permits.acquire();
                        scope.fork(() -> ScopedValue.where(SCOPED_TINKAR_MSG, pbTinkarMsg).call(() -> {
                            // TODO: Remove need for Stamp Consumer since Stamps are now consumed by Entity Consumer
                            try {
                                entityTransformer.transform(pbTinkarMsg, entityConsumer, (stampEntity) -> {
                                });
                                // Batch progress updates to prevent hanging the UI thread
                                if (importCount.incrementAndGet() % 1000 == 0) {
                                    updateProgress(this.importFile.length() + countingIn.getBytesRead(), this.importFile.length() * 2);
                                }
                            } catch (RuntimeException e) {
                                if (e instanceof IllegalStateException && e.getMessage().contains("No entity key found for UUIDs")) {
                                    LOG.error("{}. Error transforming Protobuf message: {} \n  {}", errorCount.getAndIncrement(), e.getMessage(), pbTinkarMsg);
                                }
                                if (e instanceof IllegalStateException && e.getMessage().contains("Entity byte[] not found")) {
                                    LOG.error("{}. Error transforming Protobuf message: {} \n  {}", errorCount.getAndIncrement(), e.getMessage(), pbTinkarMsg);
                                } else {
                                    LOG.error("{}. Error transforming Protobuf message: {}  \n  {}", errorCount.getAndIncrement(), e.getMessage(), pbTinkarMsg, e);
                                }
                            } finally {
                                permits.release();
                            }
                            return null;
                        }));
                    }
                    LOG.info("Starting scope.join");
                    scope.join();
                    LOG.info("Finished scope.join");

                }
            }
            StringBuilder stringBuilder = new StringBuilder();

            patternUuids.forEach(patternUuid -> {
                int nid = PrimitiveData.get().nidForUuids(patternUuid);
                PatternEntity patternEntity = EntityService.get().getEntityFast(nid);
                StampCoordinate stampCoordinate = Coordinates.Stamp.DevelopmentLatest();
                String entityText = PrimitiveData.textWithNid(nid);
                PrimitiveData.getEntityKey(patternUuid).ifPresent(entityKey ->
                        stringBuilder.append("\n\nPattern: ").append(entityText).append(" EntityKey: ").append(entityKey));

                stringBuilder.append("\n nid=").append(nid).append(" (0x").append(String.format("%08X", nid)).append(")").append(" pattern sequence=").append(NidCodec6.decodePatternSequence(nid)).append(" element sequence=").append(NidCodec6.decodeElementSequence(nid));
                stringBuilder.append("\nPatternEntity: ").append(patternEntity);
            });

            LOG.info("PatternInfo:\n {}", stringBuilder);

            LOG.info("Imported {} entities", String.format("%,d", importCount.get()));


            verifyManifest(manifestEntryData);

            LOG.info("Checking watchList: {} ", watchList);
            for (UUID uuid : watchList) {
                StringBuilder sb = new StringBuilder();
                Optional<dev.ikm.tinkar.common.id.EntityKey> entityKey = PrimitiveData.getEntityKey(uuid);
                int nid = PrimitiveData.get().nidForUuids(uuid);
                int patternSequence = PrimitiveData.patternSequenceForNid(nid);
                long elementSequence = PrimitiveData.elementSequenceForNid(nid);
                byte[] entityBytes = PrimitiveData.get().getBytes(nid);
                sb.append("\n\nnid for ").append(uuid).append(" is: ").append(nid);
                sb.append("\npatternSequence for ").append(uuid).append(" is: ").append(patternSequence);
                sb.append("\nelementSequence for ").append(uuid).append(" is: ").append(elementSequence);
                sb.append("\nentityBytes for ").append(uuid).append(" is: ").append(Arrays.toString(entityBytes));
                sb.append("\nentityKey for ").append(uuid).append(" is: ").append(entityKey);
                sb.append("\nText with nid: ").append(PrimitiveData.textWithNid(nid));
                LOG.info(sb.toString());
            }
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

    private static void verifyManifest(Map<PublicId, String> manifestEntryData) {
        manifestEntryData.keySet().forEach((publicId) -> {
            if (!PrimitiveData.get().hasPublicId(publicId)) {
                LOG.warn("Dependent Module or Author is not Present -" +
                        " PublicId: " + publicId.idString() +
                        " Description: " + manifestEntryData.get(publicId));
            }
        });
    }

    /**
     * One-pass import: Load entities directly without pre-registering NIDs.
     * This may fail if the changeset contains forward references.
     */
    private EntityCountSummary computeOnePass(long expectedImports, Map<PublicId, String> manifestEntryData) {
        updateMessage("Importing Protobuf Data (1-pass mode)...");
        LOG.debug("Expected imports: " + expectedImports);

        EntityService.get().beginLoadPhase();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(importFile))) {
            // Consumer to be run for each transformed Entity
            Consumer<Entity<? extends EntityVersion>> entityConsumer = entity -> {
                EntityService.get().putEntityNoCache(entity, DataActivity.LOADING_CHANGE_SET);
                updateCounts(entity);
            };

            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (zipEntry.getName().equals(MANIFEST_RELPATH)) {
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
    private int makeNidForMessage(TinkarMsg pbTinkarMsg) {
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
        return PublicIds.of(pbPublicId.getUuidsList().stream()
                .map(UUID::fromString)
                .toList());
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
            default -> throw new IllegalStateException("Unexpected value: " + entity);
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
        try {
            Class<?> searchServiceClass = Class.forName("dev.ikm.tinkar.provider.search.SearchService");
            @SuppressWarnings("unchecked")
            Optional<Object> searchService = (Optional<Object>) ServiceLifecycleManager.get()
                    .getRunningService((Class) searchServiceClass);
            searchService.ifPresent(service -> {
                try {
                    // Recreate the index in batch after import (indexing is skipped during load phase).
                    Object future = service.getClass().getMethod("recreateIndex").invoke(service);
                    if (future instanceof java.util.concurrent.CompletableFuture<?> cf) {
                        cf.get();
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to recreate Lucene index after import", e);
                }
            });
        } catch (ClassNotFoundException e) {
            LOG.debug("SearchService not available on classpath; skipping index rebuild");
        }
    }

    private long analyzeManifest(Map<PublicId, String> manifestEntryData) {
        long expectedImports = -1;

        // Read Manifest from Zip
        try (FileInputStream fileIn = new FileInputStream(importFile);
             BufferedInputStream buffIn = new BufferedInputStream(fileIn, InputStreamBufferSize); // Increased buffer size
             CountingInputStream countingIn = new CountingInputStream(buffIn);
             ZipInputStream zis = new ZipInputStream(countingIn)) {
            ZipEntry zipEntry;
            boolean foundManifest = false;
            while (!foundManifest && (zipEntry = zis.getNextEntry()) != null) {
                if (zipEntry.getName().equals(MANIFEST_RELPATH)) {
                    Manifest manifest = new Manifest(zis);
                    expectedImports = Long.parseLong(manifest.getMainAttributes().getValue("Total-Count"));
                    // Get Dependent Module / Author PublicIds and Descriptions
                    manifest.getEntries().keySet().forEach((publicIdKey) -> {
                        PublicId publicId = PublicIds.of(publicIdKey.split(","));
                        String description = manifest.getEntries().get(publicIdKey).getValue("Description");
                        manifestEntryData.put(publicId, description);
                    });
                    foundManifest = true;
                }
                zis.closeEntry();
                LOG.info(zipEntry.getName() + " zip entry size: " + zipEntry.getSize());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return expectedImports;
    }

    private int makeNid(SemanticChronology semanticChronology) {
        return makeNid(EntityProxy.Pattern.make(getEntityPublicId(semanticChronology.getPatternForSemanticPublicId())),
                semanticChronology.getPublicId());
    }


    private int makeNid(EntityProxy.Pattern pattern, dev.ikm.tinkar.schema.PublicId pbPublicId) {
        PublicId publicId = getEntityPublicId(pbPublicId);
        int nid = makeNid(pattern, getEntityPublicId(pbPublicId));
        for (UUID uuid : publicId.asUuidArray()) {
            if (watchList.contains(uuid)) {
                LOG.info("Found watch: {} for: \n\n{}", uuid, SCOPED_TINKAR_MSG.get());
                LOG.info("nid for {} is: {}", uuid, nid);
                LOG.info("nid for {} in hex is: {}", uuid, Integer.toHexString(nid));
            }
        }
        return nid;
    }
    private int makeNid(EntityProxy.Pattern pattern, PublicId entityId) {
        // Create the UUID -> Nid map here...
        if (SCOPED_WATCH_LIST.isBound()) {

        } else {
            LOG.info("Watch list not bound");
        }
        dev.ikm.tinkar.common.id.EntityKey entityKey = PrimitiveData.getEntityKey(pattern.publicId(), entityId);
        return entityKey.nid();
    }

}
