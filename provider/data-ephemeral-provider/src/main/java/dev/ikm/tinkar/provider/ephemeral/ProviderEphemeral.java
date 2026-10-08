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

import org.eclipse.collections.api.block.procedure.primitive.LongProcedure;
import java.util.function.ObjLongConsumer;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;

import dev.ikm.tinkar.common.id.Nid;
import dev.ikm.tinkar.common.service.SequentialNids;
import dev.ikm.tinkar.common.service.internal.EntityStore;
import dev.ikm.tinkar.common.util.SetOnce;
import dev.ikm.tinkar.collection.KeyType;
import dev.ikm.tinkar.collection.SpinedIntIntMapAtomic;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.impl.NidLayout;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.*;
import dev.ikm.tinkar.common.sets.ConcurrentHashSet;
import dev.ikm.tinkar.common.util.ints2long.IntsInLong;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.common.service.SearchService;
import org.eclipse.collections.api.block.procedure.Procedure2;
import org.eclipse.collections.api.block.procedure.primitive.IntProcedure;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.ObjIntConsumer;


public class ProviderEphemeral implements PrimitiveDataService, EntityStore, NidGenerator {
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
    private final AtomicInteger nextNid = new AtomicInteger(SequentialNids.FIRST_NID);
    final SetOnce<SearchService> searchService = new SetOnce<>();
    private volatile boolean loadPhase = false;

    private ProviderEphemeral() {
        LOG.info("Constructing ProviderEphemeral");
        NidLayout.activate(NidLayout.SEQUENTIAL);
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
    public PublicId publicIdForNid(long nid) {
        // Reverse lookup over the identity map: correctness over speed — used by
        // export paths for referenced components that are not present as entities.
        List<UUID> uuids = new ArrayList<>();
        uuidNidMap.forEach((uuid, mappedNid) -> {
            if (mappedNid == nid) {
                uuids.add(uuid);
            }
        });
        if (uuids.isEmpty()) {
            throw new IllegalStateException("No public id minted for nid " + nid + " in this store");
        }
        return PublicIds.of(uuids);
    }

    @Override
    public long nidForUuids(UUID... uuids) {
        return SequentialNids.nidForUuids(uuidNidMap, () -> Nid.narrowChecked(newNid()), uuids);
    }

    @Override
    public boolean hasUuid(UUID uuid) {
        return uuidNidMap.containsKey(uuid);
    }

    @Override
    public long nidForUuids(ImmutableList<UUID> uuidList) {
        return SequentialNids.nidForUuids(uuidNidMap, () -> Nid.narrowChecked(newNid()), uuidList);
    }

    @Override
    public boolean hasPublicId(PublicId publicId) {
        return publicId.asUuidList().stream().anyMatch(uuidNidMap::containsKey);
    }

    @Override
    public void forEach(ObjLongConsumer<byte[]> action) {
        nidComponentMap.forEach((integer, bytes) -> action.accept(bytes, integer));
    }

    @Override
    public void forEachParallel(ObjLongConsumer<byte[]> action) {
        int threadCount = TinkExecutor.threadPool().getMaximumPoolSize();
        List<Procedure2<Integer, byte[]>> blocks = new ArrayList<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            blocks.add((Procedure2<Integer, byte[]>) (integer, bytes) -> action.accept(bytes, integer));
        }
        nidComponentMap.parallelForEachKeyValue(blocks, TinkExecutor.threadPool());
    }

    @Override
    public void forEachParallel(ImmutableLongList nids, ObjLongConsumer<byte[]> action) {
        nids.primitiveParallelStream().forEach(nid -> {
            byte[] bytes = nidComponentMap.get(Nid.narrowChecked(nid));
            if (bytes != null) {
                action.accept(bytes, nid);
            }
        });
    }

    @Override
    public void forEach(ImmutableLongList nids, ObjLongConsumer<byte[]> action) {
        nids.forEach(nid -> {
            byte[] bytes = nidComponentMap.get(Nid.narrowChecked(nid));
            if (bytes != null) {
                action.accept(bytes, nid);
            }
        });
    }

    @Override
    public byte[] getBytes(long nid) {
        return nidComponentMap.get(Nid.narrowChecked(nid));
    }

    @Override
    public byte[] merge(long nid, long patternNid, long referencedComponentNid, byte[] value, Object sourceObject, DataActivity activity) {
        if (!nidToPatternNidMap.containsKey(Nid.narrowChecked(nid))) {
            // A concept, pattern or stamp comes with the not-applicable sentinel,
            // Integer.MAX_VALUE (Nid.NOT_APPLICABLE), as its pattern; only a semantic is indexed.
            this.nidToPatternNidMap.put(Nid.narrowChecked(nid), Nid.narrowChecked(patternNid));
            if (!Nid.isNotApplicable(patternNid)) {

                this.nidToPatternNidMap.put(Nid.narrowChecked(nid), Nid.narrowChecked(patternNid));
                if (!Nid.isNotApplicable(patternNid)) {
                    long citationLong = IntsInLong.ints2Long(Nid.narrowChecked(nid), Nid.narrowChecked(patternNid));
                    this.nidToCitingComponentsNidMap.merge(Nid.narrowChecked(referencedComponentNid), new long[]{citationLong},
                            PrimitiveDataService::mergeCitations);
                    this.patternToElementNidsMap.getIfAbsentPut(Nid.narrowChecked(nid), () -> new ConcurrentSkipListSet<>()).add(Nid.narrowChecked(nid));
                }
            }
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
        byte[] mergedBytes = nidComponentMap.merge(Nid.narrowChecked(nid), value, PrimitiveDataService::merge);
        writeSequence.increment();

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
    public CompletableFuture<Void> recreateLuceneIndex() throws Exception {
        return getSearchService().recreateIndex();
    }

    @Override
    public void forEachSemanticNidOfPattern(long patternNid, LongProcedure procedure) {
        nidToPatternNidMap.forEach((nid, setNid) -> {
            if (patternNid == setNid) {
                procedure.accept(nid);
            }
        });
    }

    @Override
    public void forEachPatternNid(LongProcedure procedure) {
        this.patternNids.forEach(procedure::accept);
    }

    @Override
    public void forEachConceptNid(LongProcedure procedure) {
        this.conceptNids.forEach(procedure::accept);
    }

    @Override
    public void forEachStampNid(LongProcedure procedure) {
        this.stampNids.forEach(procedure::accept);
    }

    @Override
    public void forEachSemanticNid(LongProcedure procedure) {
        this.semanticNids.forEach(procedure::accept);
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
        return "Ephemeral data";
    }

    @Override
    public long newNid() {
        return nextNid.getAndIncrement();
    }

    /**
     * Controller for ProviderEphemeral lifecycle management (creating new ephemeral database with data import).
     * <p>     * Ephemeral provider is in-memory only and typically used for creating new databases.
     */
    public static class NewController extends ProviderController<ProviderEphemeral>
            implements DataServiceController<PrimitiveDataService> {

        public static final String CONTROLLER_NAME = "Load Ephemeral Store";
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
            // Queue data file for loading in DATA_LOAD phase (don't load it now)
            if (importDataFileString != null) {
                try {
                    loading.set(true);
                    File importFile = new File(importDataFileString);
                    LOG.info("Queueing starter data for deferred import: {}", importFile.getName());
                    dev.ikm.tinkar.entity.load.DataLoadProvider dataLoadService =
                            dev.ikm.tinkar.entity.load.DataLoadProvider.get();
                    dataLoadService.addFile(importFile);
                } finally {
                    loading.set(false);
                }
            } else {
                LOG.warn("No import file specified - creating empty database");
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
                // toFile() decodes the URI; URL.getFile() would keep %20 for a space (ike-issues#1156).
                importDataFileString = dataUriOption.toFile().getAbsolutePath();
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
