package org.hl7.tinkar.provider.spinedarray;

import org.eclipse.collections.api.block.procedure.primitive.IntProcedure;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.collection.*;
import org.hl7.tinkar.common.service.*;
import org.hl7.tinkar.common.util.ints2long.IntsInLong;
import org.hl7.tinkar.common.util.time.Stopwatch;
import org.hl7.tinkar.provider.search.Indexer;
import org.hl7.tinkar.provider.search.Searcher;
import org.hl7.tinkar.provider.spinedarray.internal.Get;
import org.hl7.tinkar.provider.spinedarray.internal.Put;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.ObjIntConsumer;

/**
 * Maybe a hybrid of SpinedArrayProvider and MVStoreProvider is worth considering.
 * <p>
 * SpinedArrayProvider is performing horribly becuase of dependency on ConcurrentUuidIntHashMap serilization.
 * <p>
 * MVStore performs worse when iterating over entities.
 */
public class SpinedArrayProvider implements PrimitiveDataService, NidGenerator {
    protected static final File defaultDataDirectory = new File("target/spinedarrays/");
    private static final Logger LOG = LoggerFactory.getLogger(SpinedArrayProvider.class);
    protected static SpinedArrayProvider singleton;
    protected static LongAdder writeSequence = new LongAdder();
    protected final CountDownLatch uuidsLoadedLatch = new CountDownLatch(1);
    final AtomicInteger nextNid = new AtomicInteger(Integer.MIN_VALUE + 1);

    final ConcurrentHashMap<UUID, Integer> uuidToNidMap = new ConcurrentHashMap<>();
    final SpinedByteArrayMap entityToBytesMap;
    final SpinedIntIntMap nidToPatternNidMap;
    /**
     * Using "citing" instead of "referencing" to make the field names more distinct.
     */
    final SpinedIntLongArrayMap nidToCitingComponentsNidMap;
    final SpinedIntIntArrayMap patternToElementNidsMap;

    final File nidToPatternNidMapDirectory;
    final File nidToByteArrayMapDirectory;
    final File nidToCitingComponentNidMapDirectory;
    final File patternToElementNidsMapDirectory;
    final File nextNidKeyFile;
    final Indexer indexer;
    final Searcher searcher;

    public SpinedArrayProvider() throws IOException {
        Stopwatch stopwatch = new Stopwatch();
        LOG.info("Opening SpinedArrayProvider");
        File configuredRoot = ServiceProperties.get(ServiceKeys.DATA_STORE_ROOT, defaultDataDirectory);
        configuredRoot.mkdirs();
        SpinedArrayProvider.singleton = this;
        Get.singleton = this;
        Put.singleton = this;

        this.nidToPatternNidMapDirectory = new File(configuredRoot, "nidToPatternNidMap");
        this.nidToPatternNidMapDirectory.mkdirs();
        this.nidToByteArrayMapDirectory = new File(configuredRoot, "nidToByteArrayMap");
        this.nidToByteArrayMapDirectory.mkdirs();
        this.nidToCitingComponentNidMapDirectory = new File(configuredRoot, "nidToCitingComponentNidMap");
        this.nidToCitingComponentNidMapDirectory.mkdirs();
        this.patternToElementNidsMapDirectory = new File(configuredRoot, "patternToElementNidsMap");
        this.patternToElementNidsMapDirectory.mkdirs();
        this.nextNidKeyFile = new File(configuredRoot, "nextNidKeyFile");

        this.entityToBytesMap = new SpinedByteArrayMap(new ByteArrayFileStore(nidToByteArrayMapDirectory));
        this.nidToPatternNidMap = new SpinedIntIntMap(KeyType.NID_KEY);
        this.nidToPatternNidMap.read(this.nidToPatternNidMapDirectory);
        this.nidToCitingComponentsNidMap = new SpinedIntLongArrayMap(new IntLongArrayFileStore(nidToCitingComponentNidMapDirectory));
        this.patternToElementNidsMap = new SpinedIntIntArrayMap(new IntIntArrayFileStore(patternToElementNidsMapDirectory));

        if (nextNidKeyFile.exists()) {
            String nextNidString = Files.readString(this.nextNidKeyFile.toPath());
            nextNid.set(Integer.valueOf(nextNidString));
        }
        Executor.threadPool().execute(() -> {
            Stopwatch uuidNidMapFromEntitiesStopwatch = new Stopwatch();
            LOG.info("Starting UUID strategy 2");
            UuidNidCollector uuidNidCollector = new UuidNidCollector(uuidToNidMap);
            try {
                this.entityToBytesMap.forEachParallel(uuidNidCollector);
                this.uuidsLoadedLatch.countDown();
            } catch (ExecutionException | InterruptedException e) {
                LOG.error(e.getLocalizedMessage(), e);
            } finally {
                uuidNidMapFromEntitiesStopwatch.stop();
                LOG.info("Finished UUID strategy 2 in: " + uuidNidMapFromEntitiesStopwatch.durationString());
                LOG.info(uuidNidCollector.report());
            }
        });

        stopwatch.stop();
        LOG.info("Opened SpinedArrayProvider in: " + stopwatch.durationString());

        File indexDir = new File(configuredRoot, "lucene");
        this.indexer = new Indexer(indexDir.toPath());
        this.searcher = new Searcher();
    }

    @Override
    public long writeSequence() {
        return writeSequence.sum();
    }

    @Override
    public void close() {
        Stopwatch stopwatch = new Stopwatch();
        LOG.info("Closing SpinedArrayProvider");
        try {
            save();
            entityToBytesMap.close();
            SpinedArrayProvider.singleton = null;
            this.indexer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            stopwatch.stop();
            LOG.info("Closed SpinedArrayProvider in: " + stopwatch.durationString());
        }

    }

    public void save() {
        Stopwatch stopwatch = new Stopwatch();
        LOG.info("Saving SpinedArrayProvider");
        try {
            Files.writeString(this.nextNidKeyFile.toPath(), Integer.toString(nextNid.get()));
            nidToPatternNidMap.write(this.nidToPatternNidMapDirectory);
            this.entityToBytesMap.write();
            this.nidToCitingComponentsNidMap.write();
            this.patternToElementNidsMap.write();
            this.indexer.commit();
        } catch (IOException e) {
            LOG.error(e.getLocalizedMessage(), e);
        } finally {
            stopwatch.stop();
            LOG.info("Save SpinedArrayProvider in: " + stopwatch.durationString());
        }
    }

    @Override
    public int nidForUuids(UUID... uuids) {
        try {
            this.uuidsLoadedLatch.await();
            if (uuids.length == 1) {
                return uuidToNidMap.computeIfAbsent(uuids[0], uuidKey -> newNid());
            }
            int nid = Integer.MAX_VALUE;
            for (UUID uuid : uuids) {
                if (nid == Integer.MAX_VALUE) {
                    nid = uuidToNidMap.computeIfAbsent(uuids[0], uuidKey -> newNid());
                } else {
                    uuidToNidMap.put(uuid, nid);
                }
            }
            return nid;
        } catch (InterruptedException e) {
            LOG.error(e.getLocalizedMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public int newNid() {
        return nextNid.getAndIncrement();
    }

    @Override
    public int nidForUuids(ImmutableList<UUID> uuidList) {
        try {
            this.uuidsLoadedLatch.await();
            if (uuidList.size() == 1) {
                return uuidToNidMap.computeIfAbsent(uuidList.get(0), uuidKey -> newNid());
            }
            int nid = Integer.MAX_VALUE;
            for (UUID uuid : uuidList) {
                if (nid == Integer.MAX_VALUE) {
                    nid = uuidToNidMap.computeIfAbsent(uuid, uuidKey -> newNid());
                } else {
                    uuidToNidMap.put(uuid, nid);
                }
            }
            return nid;
        } catch (InterruptedException e) {
            LOG.error(e.getLocalizedMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void forEach(ObjIntConsumer<byte[]> action) {
        this.entityToBytesMap.forEach(action);
    }

    @Override
    public void forEachParallel(ObjIntConsumer<byte[]> action) {
        try {
            this.entityToBytesMap.forEachParallel(action);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] getBytes(int nid) {
        return this.entityToBytesMap.get(nid);
    }

    @Override
    public byte[] merge(int nid, int patternNid, int referencedComponentNid, byte[] value, Object sourceObject) {
        if (!this.entityToBytesMap.containsKey(nid)) {

            this.nidToPatternNidMap.put(nid, patternNid);
            if (patternNid != Integer.MAX_VALUE) {
                long citationLong = IntsInLong.ints2Long(nid, patternNid);
                this.nidToCitingComponentsNidMap.accumulateAndGet(referencedComponentNid, new long[]{citationLong},
                        PrimitiveDataService::mergeCitations);
                this.patternToElementNidsMap.accumulateAndGet(patternNid, new int[]{nid},
                        PrimitiveDataService::mergePatternElements);
            }
        }
        byte[] mergedBytes = this.entityToBytesMap.accumulateAndGet(nid, value, PrimitiveDataService::merge);
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
        int[] elementNids = this.patternToElementNidsMap.get(patternNid);
        if (elementNids != null && elementNids.length > 0 && elementNids[0] != Integer.MAX_VALUE) {
            for (int elementNid : elementNids) {
                procedure.accept(elementNid);
            }
        } else {
            nidToPatternNidMap.forEach((nid, defNidForNid) -> {
                if (defNidForNid == patternNid) {
                    procedure.accept(nid);
                }
            });
        }
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
