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
package dev.ikm.tinkar.provider.ephemeral;

import dev.ikm.tinkar.collection.KeyType;
import dev.ikm.tinkar.collection.SpinedIntIntMapAtomic;
import dev.ikm.tinkar.common.alert.AlertStreams;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.*;
import dev.ikm.tinkar.common.sets.ConcurrentHashSet;
import dev.ikm.tinkar.common.util.ints2long.IntsInLong;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.provider.search.SearchService;
import org.eclipse.collections.api.block.procedure.Procedure2;
import org.eclipse.collections.api.block.procedure.primitive.IntProcedure;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.ObjIntConsumer;


public class ProviderEphemeral implements PrimitiveDataService, NidGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(ProviderEphemeral.class);
    protected static AtomicReference<ProviderEphemeral> providerReference = new AtomicReference<>();
    protected static ProviderEphemeral singleton;
    protected static LongAdder writeSequence = new LongAdder();
    // TODO I don't think the spines need to be atomic for this use case of nids -> elementIndices.
    //  There is no update after initial value set...
    final SpinedIntIntMapAtomic nidToPatternNidMap = new SpinedIntIntMapAtomic(KeyType.NID_KEY);
    /**
     * Using "citing" instead of "referencing" to make the field names more distinct.
     */
    final ConcurrentHashMap<Integer, long[]> nidToCitingComponentsNidMap = ConcurrentHashMap.newMap();
    final ConcurrentHashMap<Integer, ConcurrentSkipListSet<Integer>> patternToElementNidsMap = ConcurrentHashMap.newMap();
    final ConcurrentHashSet<Integer> patternNids = new ConcurrentHashSet();
    final ConcurrentHashSet<Integer> conceptNids = new ConcurrentHashSet();
    final ConcurrentHashSet<Integer> semanticNids = new ConcurrentHashSet();
    final ConcurrentHashSet<Integer> stampNids = new ConcurrentHashSet();
    private final ConcurrentHashMap<Integer, byte[]> nidComponentMap = ConcurrentHashMap.newMap();
    private final ConcurrentHashMap<UUID, Integer> uuidNidMap = new ConcurrentHashMap<>();
    private final AtomicInteger nextNid = new AtomicInteger(PrimitiveDataService.FIRST_NID);
    final StableValue<SearchService> searchService = StableValue.of();

    private ProviderEphemeral() {
        LOG.info("Constructing ProviderEphemeral");
    }

    public static PrimitiveDataService provider() {
        if (singleton == null) {
            singleton = providerReference.updateAndGet(providerEphemeral -> {
                if (providerEphemeral == null) {
                    return new ProviderEphemeral();
                }
                return providerEphemeral;
            });
        }
        return singleton;
    }

    @Override
    public long writeSequence() {
        return writeSequence.sum();
    }

    @Override
    public void close() {
        this.providerReference.set(null);
        this.singleton = null;
    }

    @Override
    public int nidForUuids(UUID... uuids) {
        return PrimitiveDataService.nidForUuids(uuidNidMap, this, uuids);
    }

    @Override
    public boolean hasUuid(UUID uuid) {
        return uuidNidMap.containsKey(uuid);
    }

    @Override
    public int nidForUuids(ImmutableList<UUID> uuidList) {
        return PrimitiveDataService.nidForUuids(uuidNidMap, this, uuidList);
    }

    @Override
    public boolean hasPublicId(PublicId publicId) {
        return publicId.asUuidList().stream().anyMatch(uuidNidMap::containsKey);
    }

    @Override
    public void forEach(ObjIntConsumer<byte[]> action) {
        nidComponentMap.forEach((integer, bytes) -> action.accept(bytes, integer));
    }

    @Override
    public void forEachParallel(ObjIntConsumer<byte[]> action) {
        int threadCount = TinkExecutor.threadPool().getMaximumPoolSize();
        List<Procedure2<Integer, byte[]>> blocks = new ArrayList<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            blocks.add((Procedure2<Integer, byte[]>) (integer, bytes) -> action.accept(bytes, integer));
        }
        nidComponentMap.parallelForEachKeyValue(blocks, TinkExecutor.threadPool());
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
        return nidComponentMap.get(nid);
    }

    @Override
    public byte[] merge(int nid, int patternNid, int referencedComponentNid, byte[] value, Object sourceObject, DataActivity activity) {
        if (!nidToPatternNidMap.containsKey(nid)) {
            this.nidToPatternNidMap.put(nid, patternNid);
            if (patternNid != Integer.MAX_VALUE) {

                this.nidToPatternNidMap.put(nid, patternNid);
                if (patternNid != Integer.MAX_VALUE) {
                    long citationLong = IntsInLong.ints2Long(nid, patternNid);
                    this.nidToCitingComponentsNidMap.merge(referencedComponentNid, new long[]{citationLong},
                            PrimitiveDataService::mergeCitations);
                    this.patternToElementNidsMap.getIfAbsentPut(nid, () -> new ConcurrentSkipListSet<>()).add(nid);
                }
            }
        }
        if (sourceObject instanceof ConceptEntity concept) {
            this.conceptNids.add(concept.nid());
        } else if (sourceObject instanceof SemanticEntity semanticEntity) {
            this.semanticNids.add(semanticEntity.nid());
        } else if (sourceObject instanceof PatternEntity patternEntity) {
            this.patternNids.add(patternEntity.nid());
        } else if (sourceObject instanceof StampEntity stampEntity) {
            this.stampNids.add(stampEntity.nid());
        }
        byte[] mergedBytes = nidComponentMap.merge(nid, value, PrimitiveDataService::merge);
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
        nidToPatternNidMap.forEach((nid, setNid) -> {
            if (patternNid == setNid) {
                procedure.accept(nid);
            }
        });
    }

    @Override
    public void forEachPatternNid(IntProcedure procedure) {
        this.patternNids.forEach(procedure::accept);
    }

    @Override
    public void forEachConceptNid(IntProcedure procedure) {
        this.conceptNids.forEach(procedure::accept);
    }

    @Override
    public void forEachStampNid(IntProcedure procedure) {
        this.stampNids.forEach(procedure::accept);
    }

    @Override
    public void forEachSemanticNid(IntProcedure procedure) {
        this.semanticNids.forEach(procedure::accept);
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
        return "Ephemeral data";
    }

    @Override
    public int newNid() {
        return nextNid.getAndIncrement();
    }

    /**
     * Controller for ProviderEphemeral lifecycle management (creating new ephemeral database with data import).
     * <p>
     * Ephemeral provider is in-memory only and typically used for creating new databases.
     * </p>
     */
    public static class NewController extends ProviderController<ProviderEphemeral>
            implements DataServiceController<PrimitiveDataService> {

        public static final String CONTROLLER_NAME = "New Ephemeral";
        private DataUriOption dataUriOption;
        private String importDataFileString;
        private final AtomicBoolean loading = new AtomicBoolean(false);

        @Override
        protected ProviderEphemeral createProvider() throws Exception {
            return ProviderEphemeral.provider() instanceof ProviderEphemeral p ? p : null;
        }

        @Override
        protected void startProvider(ProviderEphemeral provider) {
            // Provider starts itself
        }

        @Override
        protected void stopProvider(ProviderEphemeral provider) {
            provider.close();
        }

        @Override
        protected String getProviderName() {
            return "ProviderEphemeral";
        }

        @Override
        protected void initializeProvider(ProviderEphemeral provider) throws Exception {
            // Load data from file if specified
            if (importDataFileString != null) {
                try {
                    loading.set(true);
                    ServiceLoader<LoadDataFromFileController> controllerFinder =
                            PluggableService.load(LoadDataFromFileController.class);
                    LoadDataFromFileController loader = controllerFinder.findFirst()
                            .orElseThrow(() -> new IllegalStateException("No LoadDataFromFileController found"));
                    Future<EntityCountSummary> loadFuture =
                            (Future<EntityCountSummary>) loader.load(new File(importDataFileString));
                    EntityCountSummary entityCountSummary = loadFuture.get();
                    LOG.info("Loaded ephemeral data: " + entityCountSummary);
                } finally {
                    loading.set(false);
                }
            }
        }

        @Override
        public ServiceLifecyclePhase getLifecyclePhase() {
            return ServiceLifecyclePhase.DATA_STORAGE;
        }

        @Override
        public int getSubPriority() {
            return 40; // After persistent providers
        }

        @Override
        public Optional<ServiceExclusionGroup> getMutualExclusionGroup() {
            return Optional.of(ServiceExclusionGroup.DATA_PROVIDER);
        }

        // ========== DataServiceController Implementation ==========

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
        public boolean isValidDataLocation(String name) {
            return name.toLowerCase().endsWith("pb.zip") ||
                    (name.toLowerCase().endsWith(".zip") && name.toLowerCase().contains("tink"));
        }

        @Override
        public void setDataUriOption(DataUriOption dataUriOption) {
            this.dataUriOption = dataUriOption;
            if (dataUriOption != null) {
                try {
                    importDataFileString = dataUriOption.uri().toURL().getFile();
                } catch (MalformedURLException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }

        @Override
        public String controllerName() {
            return CONTROLLER_NAME;
        }

        @Override
        public ImmutableList<Class<?>> serviceClasses() {
            // ProviderEphemeral (the generic type parameter P) implements PrimitiveDataService
            // This establishes the contract: ProviderController<ProviderEphemeral> provides PrimitiveDataService
            return Lists.immutable.of(PrimitiveDataService.class);
        }

        @Override
        public boolean running() {
            return ProviderEphemeral.singleton != null;
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
            // Nothing to save for ephemeral provider
        }

        @Override
        public void reload() {
            throw new UnsupportedOperationException("Can't reload ephemeral provider");
        }

        // Note: provider() method is inherited from ProviderController base class

        @Override
        public boolean loading() {
            return loading.get();
        }
    }
}
