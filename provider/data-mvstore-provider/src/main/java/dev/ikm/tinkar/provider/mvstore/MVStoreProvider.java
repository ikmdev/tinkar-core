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
package dev.ikm.tinkar.provider.mvstore;

import dev.ikm.tinkar.common.alert.AlertStreams;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.*;
import dev.ikm.tinkar.common.util.ints2long.IntsInLong;
import dev.ikm.tinkar.common.util.time.Stopwatch;
import dev.ikm.tinkar.common.validation.ValidationRecord;
import dev.ikm.tinkar.common.validation.ValidationSeverity;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityCountSummary;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.provider.search.SearchService;
import org.eclipse.collections.api.block.procedure.primitive.IntProcedure;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.OffHeapStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.ObjIntConsumer;

/**
 * TODO: Maybe also consider making use of: https://blogs.oracle.com/javamagazine/creating-a-java-off-heap-in-memory-database?source=:em:nw:mt:::RC_WWMK200429P00043:NSL400123121
 */
public class MVStoreProvider implements PrimitiveDataService, NidGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(MVStoreProvider.class);
    private static final File defaultDataDirectory = new File("target/mvstore/");
    private static final String databaseFileName = "mvstore.dat";
    private static final UUID nextNidKey = new UUID(Long.MAX_VALUE, Long.MIN_VALUE);
    protected static MVStoreProvider singleton;
    protected final AtomicInteger nextNid;
    final OffHeapStore offHeap;
    final MVStore store;
    final MVMap<Integer, byte[]> nidToComponentMap;
    final MVMap<UUID, Integer> uuidToNidMap;
    final MVMap<UUID, Integer> stampUuidToNidMap;
    final MVMap<Integer, Integer> nidToPatternNidMap;
    /**
     * Using "citing" instead of "referencing" to make the field names more distinct.
     */
    final MVMap<Integer, long[]> nidToCitingComponentsNidMap;
    final MVMap<Integer, int[]> patternToElementNidsMap;
    final String name;
    protected LongAdder writeSequence = new LongAdder();
    ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Integer>> patternElementNidsMap = ConcurrentHashMap.newMap();
    final StableValue<SearchService> searchService = StableValue.of();


    public MVStoreProvider() throws IOException {
        Stopwatch stopwatch = new Stopwatch();
        LOG.info("Opening MVStoreProvider");
        this.offHeap = new OffHeapStore();
        File configuredRoot = ServiceProperties.get(ServiceKeys.DATA_STORE_ROOT, defaultDataDirectory);
        configuredRoot.mkdirs();
        this.name = configuredRoot.getName();
        File databaseFile = new File(configuredRoot, databaseFileName);
        LOG.info("Starting MVStoreProvider from: " + databaseFile.getAbsolutePath());
        this.offHeap.open(databaseFile.getAbsolutePath(), false, null);
        this.store = new MVStore.Builder().fileName(databaseFile.getAbsolutePath()).open();

        this.nidToComponentMap = store.openMap("nidToComponentMap");
        this.uuidToNidMap = store.openMap("uuidToNidMap");
        this.stampUuidToNidMap = store.openMap("stampUuidToNidMap");
        this.nidToPatternNidMap = store.openMap("nidToPatternNidMap");
        this.nidToCitingComponentsNidMap = store.openMap("nidToCitingComponentsNidMap");
        this.patternToElementNidsMap = store.openMap("patternToElementNidsMap");
        for (int patternNid : patternToElementNidsMap.keySet()) {
            int[] elementNids = patternToElementNidsMap.get(patternNid);
            for (int elementNid : elementNids) {
                addToElementSet(patternNid, elementNid);
            }
        }

        if (this.uuidToNidMap.containsKey(nextNidKey)) {
            this.nextNid = new AtomicInteger(this.uuidToNidMap.get(nextNidKey));
        } else {
            this.nextNid = new AtomicInteger(PrimitiveDataService.FIRST_NID);
        }

        MVStoreProvider.singleton = this;
        stopwatch.stop();
        LOG.info("Opened MVStoreProvider in: " + stopwatch.durationString());
    }

    public boolean addToElementSet(int patternNid, int elementNid) {
        return null == patternElementNidsMap.getIfAbsentPut(patternNid, integer -> new ConcurrentHashMap<>())
                .put(elementNid, elementNid);
    }

    @Override
    public int newNid() {
        return nextNid.getAndIncrement();
    }

    @Override
    public long writeSequence() {
        return writeSequence.sum();
    }

    public void close() {
        Stopwatch stopwatch = new Stopwatch();
        LOG.info("Closing MVStoreProvider");
        try {
            save();
            this.store.close();
        } catch (Exception e) {
            LOG.error("Error closing MVStoreProvider", e);
        } finally {
            stopwatch.stop();
            LOG.info("Closed MVStoreProvider in: " + stopwatch.durationString());
        }
    }

    public void save() {
        Stopwatch stopwatch = new Stopwatch();
        LOG.info("Saving MVStoreProvider");
        this.uuidToNidMap.put(nextNidKey, nextNid.get());
        for (Pair<Integer, ConcurrentHashMap<Integer, Integer>> keyValue : patternElementNidsMap.keyValuesView()) {
            patternToElementNidsMap.put(keyValue.getOne(), keyValue.getTwo().keySet()
                    .stream().mapToInt(value -> (int) value).toArray());
        }
        this.store.commit();
        this.offHeap.sync();
        stopwatch.stop();
        LOG.info("Saved MVStoreProvider in: " + stopwatch.durationString());
    }

    @Override
    public int nidForUuids(UUID... uuids) {
        return PrimitiveDataService.nidForUuids(uuidToNidMap, this, uuids);
    }

    @Override
    public int nidForUuids(ImmutableList<UUID> uuidList) {
        return PrimitiveDataService.nidForUuids(uuidToNidMap, this, uuidList);
    }

    @Override
    public boolean hasUuid(UUID uuid) {
        return uuidToNidMap.containsKey(uuid);
    }

    @Override
    public boolean hasPublicId(PublicId publicId) {
        return publicId.asUuidList().stream().anyMatch(uuidToNidMap::containsKey);
    }

    @Override
    public void forEach(ObjIntConsumer<byte[]> action) {
        nidToComponentMap.entrySet().forEach(entry -> action.accept(entry.getValue(), entry.getKey()));
    }

    @Override
    public void forEachParallel(ObjIntConsumer<byte[]> action) {
        nidToComponentMap.entrySet().stream().parallel().forEach(entry -> action.accept(entry.getValue(), entry.getKey()));
    }

    @Override
    public void forEachParallel(ImmutableIntList nids, ObjIntConsumer<byte[]> action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEach(ImmutableIntList nids, ObjIntConsumer<byte[]> action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getBytes(int nid) {
        return this.nidToComponentMap.get(nid);
    }

    @Override
    public byte[] merge(int nid, int patternNid, int referencedComponentNid, byte[] value, Object sourceObject, DataActivity dataActivity) {
        if (!nidToPatternNidMap.containsKey(nid)) {
            this.nidToPatternNidMap.put(nid, patternNid);
            if (patternNid != Integer.MAX_VALUE) {

                this.nidToPatternNidMap.put(nid, patternNid);
                if (patternNid != Integer.MAX_VALUE) {
                    long citationLong = IntsInLong.ints2Long(nid, patternNid);
                    this.nidToCitingComponentsNidMap.merge(referencedComponentNid, new long[]{citationLong},
                            PrimitiveDataService::mergeCitations);
                    // TODO this will be slow merge for large sets. Consider alternatives.
                    this.addToElementSet(patternNid, nid);
                }
            }
        }
        byte[] mergedBytes = nidToComponentMap.merge(nid, value, PrimitiveDataService::merge);
        writeSequence.increment();

        // Delegate indexing to SearchProvider
        try {
            getSearchService().index(sourceObject);
        } catch (Exception e) {
            // Search service may not be available yet during startup
            LOG.debug("SearchService not available for indexing", e);
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

    @Override
    public PrimitiveDataSearchResult[] search(String query, int maxResultSize) throws Exception {
        return getSearchService().search(query, maxResultSize);
    }

    @Override
    public CompletableFuture<Void> recreateLuceneIndex() throws Exception {
        return getSearchService().recreateIndex();
    }

    @Override
    public void forEachSemanticNidOfPattern(int patternNid, IntProcedure procedure) {
        Set<Integer> elementNids = getElementNidsForPatternNid(patternNid);
        if (elementNids != null && elementNids.size() > 0) {
            for (int elementNid : elementNids) {
                procedure.accept(elementNid);
            }
        } else {
            Entity entity = Entity.getFast(patternNid);
            if (entity instanceof PatternEntity == false) {
                throw new IllegalStateException("Trying to iterate elements for entity that is not a pattern: " + entity);
            }

        }
    }

    public Set<Integer> getElementNidsForPatternNid(int patternNid) {
        if (patternElementNidsMap.containsKey(patternNid)) {
            return patternElementNidsMap.get(patternNid).keySet();
        }
        return Set.of();
    }

    @Override
    public void forEachPatternNid(IntProcedure procedure) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEachConceptNid(IntProcedure procedure) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEachStampNid(IntProcedure procedure) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEachSemanticNid(IntProcedure procedure) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEachSemanticNidForComponent(int componentNid, IntProcedure procedure) {
        long[] citationLongs = this.nidToCitingComponentsNidMap.get(componentNid);
        if (citationLongs != null) {
            for (long citationLong : citationLongs) {
                int citingComponentNid = (int) (citationLong >> 32);
                procedure.accept(citingComponentNid);
            }
        }
    }

    @Override
    public void forEachSemanticNidForComponentOfPattern(int componentNid, int patternNid, IntProcedure procedure) {
        long[] citationLongs = this.nidToCitingComponentsNidMap.get(componentNid);
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

    /**
     * Base Controller for MVStoreProvider lifecycle management.
     * <p>
     * Handles heavyweight initialization including data loading and indexing.
     * </p>
     */
    public abstract static class Controller extends ProviderController<MVStoreProvider>
            implements DataServiceController<PrimitiveDataService> {

        @Override
        protected MVStoreProvider createProvider() throws Exception {
            return new MVStoreProvider();
        }

        @Override
        protected void startProvider(MVStoreProvider provider) {
            // Provider starts itself in constructor
        }

        @Override
        protected void stopProvider(MVStoreProvider provider) {
            provider.close();
        }

        @Override
        protected void cleanupProvider(MVStoreProvider provider) throws Exception {
            // save() is already called in close(), no need to call it again
        }

        @Override
        protected String getProviderName() {
            return "MVStoreProvider";
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
            // MVStoreProvider (the generic type parameter P) implements PrimitiveDataService
            // This establishes the contract: ProviderController<MVStoreProvider> provides PrimitiveDataService
            return Lists.immutable.of(PrimitiveDataService.class);
        }

        @Override
        public boolean running() {
            return getProvider() != null && MVStoreProvider.singleton != null;
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
            MVStoreProvider provider = getProvider();
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

    /**
     * Controller for opening an existing MVStore database.
     */
    public static class OpenController extends Controller {
        public static final String CONTROLLER_NAME = "Open MV Store";

        @Override
        public void setDataUriOption(DataUriOption option) {
            super.setDataUriOption(option);
            if (option != null) {
                ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, option.toFile());
            }
        }

        @Override
        public boolean isValidDataLocation(String name) {
            return name.equals("mvstore.dat");
        }

        @Override
        public String controllerName() {
            return CONTROLLER_NAME;
        }

        @Override
        public int getSubPriority() {
            return 20; // After SpinedArray
        }
    }

    /**
     * Controller for creating a new MVStore database and importing data.
     */
    public static class NewController extends Controller {
        public static final String CONTROLLER_NAME = "New MV Store";
        private static final DataServiceProperty NEW_FOLDER_PROPERTY =
                new DataServiceProperty("New folder name", false, true);

        private final MutableMap<DataServiceProperty, String> providerProperties = Maps.mutable.empty();
        private String importDataFileString;

        public NewController() {
            providerProperties.put(NEW_FOLDER_PROPERTY, null);
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
                        if (possibleFile.exists()) {
                            return new ValidationRecord[]{new ValidationRecord(ValidationSeverity.ERROR,
                                    "Directory already exists", target)};
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
        public void setDataUriOption(DataUriOption option) {
            super.setDataUriOption(option);
            if (option != null) {
                try {
                    importDataFileString = option.uri().toURL().getFile();
                } catch (MalformedURLException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }

        @Override
        protected void initializeProvider(MVStoreProvider provider) throws Exception {
            // Set up the data directory from properties
            File rootFolder = new File(System.getProperty("user.home"), "Solor");
            File dataDirectory = new File(rootFolder, providerProperties.get(NEW_FOLDER_PROPERTY));
            ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, dataDirectory);

            // Load data from file if specified
            if (importDataFileString != null) {
                ServiceLoader<LoadDataFromFileController> controllerFinder =
                        PluggableService.load(LoadDataFromFileController.class);
                LoadDataFromFileController loader = controllerFinder.findFirst()
                        .orElseThrow(() -> new IllegalStateException("No LoadDataFromFileController found"));
                Future<EntityCountSummary> loadFuture =
                        (Future<EntityCountSummary>) loader.load(new File(importDataFileString));
                EntityCountSummary entityCountSummary = loadFuture.get();
                LOG.info("Loaded data: " + entityCountSummary);
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
            return 21; // After Open
        }

        @Override
        public boolean loading() {
            return importDataFileString != null && !running();
        }
    }
}
