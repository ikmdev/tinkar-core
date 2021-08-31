package org.hl7.tinkar.provider.mvstore;

import org.eclipse.collections.api.block.procedure.primitive.IntProcedure;
import org.eclipse.collections.api.list.ImmutableList;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.OffHeapStore;
import org.hl7.tinkar.common.service.*;
import org.hl7.tinkar.common.util.ints2long.IntsInLong;
import org.hl7.tinkar.common.util.time.Stopwatch;
import org.hl7.tinkar.provider.mvstore.internal.Get;
import org.hl7.tinkar.provider.mvstore.internal.Put;
import org.hl7.tinkar.provider.search.Indexer;
import org.hl7.tinkar.provider.search.Searcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
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
    final Indexer indexer;
    final Searcher searcher;
    protected LongAdder writeSequence = new LongAdder();

    public MVStoreProvider() throws IOException {
        Stopwatch stopwatch = new Stopwatch();
        LOG.info("Opening MVStoreProvider");
        this.offHeap = new OffHeapStore();
        File configuredRoot = ServiceProperties.get(ServiceKeys.DATA_STORE_ROOT, defaultDataDirectory);
        configuredRoot.mkdirs();
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

        if (this.uuidToNidMap.containsKey(nextNidKey)) {
            this.nextNid = new AtomicInteger(this.uuidToNidMap.get(nextNidKey));
        } else {
            this.nextNid = new AtomicInteger(Integer.MIN_VALUE + 1);
        }

        Get.singleton = this;
        Put.singleton = this;

        MVStoreProvider.singleton = this;
        stopwatch.stop();
        LOG.info("Opened MVStoreProvider in: " + stopwatch.durationString());

        File indexDir = new File(configuredRoot, "lucene");
        this.indexer = new Indexer(indexDir.toPath());
        this.searcher = new Searcher();
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
            this.indexer.close();
            this.store.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            stopwatch.stop();
            LOG.info("Closed MVStoreProvider in: " + stopwatch.durationString());
        }
    }

    public void save() {
        Stopwatch stopwatch = new Stopwatch();
        LOG.info("Saving MVStoreProvider");
        try {
            this.uuidToNidMap.put(nextNidKey, nextNid.get());
            this.store.commit();
            this.offHeap.sync();
            this.indexer.commit();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            stopwatch.stop();
            LOG.info("Saved MVStoreProvider in: " + stopwatch.durationString());
        }
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
    public void forEach(ObjIntConsumer<byte[]> action) {
        nidToComponentMap.entrySet().forEach(entry -> action.accept(entry.getValue(), entry.getKey()));
    }

    @Override
    public void forEachParallel(ObjIntConsumer<byte[]> action) {
        nidToComponentMap.entrySet().stream().parallel().forEach(entry -> action.accept(entry.getValue(), entry.getKey()));
    }

    @Override
    public byte[] getBytes(int nid) {
        return this.nidToComponentMap.get(nid);
    }

    @Override
    public byte[] merge(int nid, int patternNid, int referencedComponentNid, byte[] value, Object sourceObject) {
        if (!nidToPatternNidMap.containsKey(nid)) {
            this.nidToPatternNidMap.put(nid, patternNid);
            if (patternNid != Integer.MAX_VALUE) {

                this.nidToPatternNidMap.put(nid, patternNid);
                if (patternNid != Integer.MAX_VALUE) {
                    long citationLong = IntsInLong.ints2Long(nid, patternNid);
                    this.nidToCitingComponentsNidMap.merge(referencedComponentNid, new long[]{citationLong},
                            PrimitiveDataService::mergeCitations);
                    this.patternToElementNidsMap.merge(patternNid, new int[]{nid},
                            PrimitiveDataService::mergePatternElements);
                }
            }
        }
        byte[] mergedBytes = nidToComponentMap.merge(nid, value, PrimitiveDataService::merge);
        writeSequence.increment();
        this.indexer.index(sourceObject);
        return mergedBytes;
    }

    @Override
    public PrimitiveDataSearchResult[] search(String query, int maxResultSize) throws Exception {
        return this.searcher.search(query, maxResultSize);
    }

    @Override
    public void forEachSemanticNidOfPattern(int patternNid, IntProcedure procedure) {
        this.nidToPatternNidMap.forEach((nid, defForNid) -> {
            if (defForNid == patternNid) {
                procedure.accept(nid);
            }
        });
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
}
