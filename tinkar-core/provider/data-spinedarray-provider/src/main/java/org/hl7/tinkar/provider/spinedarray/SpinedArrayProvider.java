package org.hl7.tinkar.provider.spinedarray;

import org.eclipse.collections.api.block.procedure.primitive.IntProcedure;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.set.primitive.IntSet;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.hl7.tinkar.collection.KeyType;
import org.hl7.tinkar.collection.SpinedByteArrayMap;
import org.hl7.tinkar.collection.SpinedIntIntMap;
import org.hl7.tinkar.collection.SpinedIntLongArrayMap;
import org.hl7.tinkar.common.service.*;
import org.hl7.tinkar.common.sets.ConcurrentHashSet;
import org.hl7.tinkar.common.util.ints2long.IntsInLong;
import org.hl7.tinkar.common.util.time.Stopwatch;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.entity.transaction.Transaction;
import org.hl7.tinkar.provider.search.Indexer;
import org.hl7.tinkar.provider.search.Searcher;
import org.hl7.tinkar.provider.spinedarray.internal.Get;
import org.hl7.tinkar.provider.spinedarray.internal.Put;
import org.hl7.tinkar.terms.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
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
    final AtomicInteger nextNid = new AtomicInteger(PrimitiveDataService.FIRST_NID);

    final ConcurrentHashMap<UUID, Integer> uuidToNidMap = ConcurrentHashMap.newMap();
    final ConcurrentHashSet<Integer> patternNids = new ConcurrentHashSet();
    final ConcurrentHashSet<Integer> conceptNids = new ConcurrentHashSet();
    final ConcurrentHashSet<Integer> semanticNids = new ConcurrentHashSet();
    final ConcurrentHashSet<Integer> stampNids = new ConcurrentHashSet();
    final ConcurrentHashMap<Integer, ConcurrentHashSet<Integer>> patternElementNidsMap = ConcurrentHashMap.newMap();

    final SpinedByteArrayMap entityToBytesMap;
    final SpinedIntIntMap nidToPatternNidMap;
    /**
     * Using "citing" instead of "referencing" to make the field names more distinct.
     */
    final SpinedIntLongArrayMap nidToCitingComponentsNidMap;

    final File nidToPatternNidMapDirectory;
    final File nidToByteArrayMapDirectory;
    final File nidToCitingComponentNidMapDirectory;
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
        this.nextNidKeyFile = new File(configuredRoot, "nextNidKeyFile");

        this.entityToBytesMap = new SpinedByteArrayMap(new ByteArrayFileStore(nidToByteArrayMapDirectory));
        this.nidToPatternNidMap = new SpinedIntIntMap(KeyType.NID_KEY);
        this.nidToPatternNidMap.read(this.nidToPatternNidMapDirectory);
        this.nidToCitingComponentsNidMap = new SpinedIntLongArrayMap(new IntLongArrayFileStore(nidToCitingComponentNidMapDirectory));

        if (nextNidKeyFile.exists()) {
            String nextNidString = Files.readString(this.nextNidKeyFile.toPath());
            nextNid.set(Integer.valueOf(nextNidString));
        }
        Executor.threadPool().execute(() -> {
            Stopwatch uuidNidMapFromEntitiesStopwatch = new Stopwatch();
            LOG.info("Starting UUID strategy 2");
            UuidNidCollector uuidNidCollector = new UuidNidCollector(uuidToNidMap,
                    patternNids, conceptNids, semanticNids, stampNids, patternElementNidsMap);
            try {
                this.entityToBytesMap.forEachParallel(uuidNidCollector);
                this.uuidsLoadedLatch.countDown();
                LOG.info("Searching for canceled stamps. ");
                for (int stampNid : stampNids) {
                    StampRecord stamp = Entity.getStamp(stampNid);
                    if (stamp.time() == Long.MAX_VALUE && Transaction.forStamp(stamp).isEmpty()) {
                        // Uncommmitted stamp outside of a transaction on restart. Set to canceled.
                        LOG.warn("Canceling uncommitted stamp: " + stamp.publicId().asUuidList());
                        StampVersionRecord lastVersion = stamp.lastVersion();
                        StampVersionRecord canceledVersion = lastVersion.with().time(Long.MIN_VALUE).stateNid(State.CANCELED.nid()).build();
                        stamp.versionRecords().clear();
                        stamp.versionRecords().add(canceledVersion);
                        byte[] stampBytes = stamp.getBytes();
                        this.entityToBytesMap.put(stampNid, stampBytes);
                    }
                    if (stamp.lastVersion().stateNid() == State.CANCELED.nid()) {
                        PrimitiveData.get().addCanceledStampNid(stampNid);
                    }
                }
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
        if (nid == Integer.MIN_VALUE) {
            LOG.error("NID should not be Integer.MIN_VALUE");
            throw new IllegalStateException("NID should not be Integer.MIN_VALUE");
        }
        if (!this.entityToBytesMap.containsKey(nid)) {
            this.nidToPatternNidMap.put(nid, patternNid);
            if (patternNid != Integer.MAX_VALUE) {
                long citationLong = IntsInLong.ints2Long(nid, patternNid);
                this.nidToCitingComponentsNidMap.accumulateAndGet(referencedComponentNid, new long[]{citationLong},
                        PrimitiveDataService::mergeCitations);
                addToPatternElementSet(patternNid, nid);
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
        }
        byte[] mergedBytes = this.entityToBytesMap.accumulateAndGet(nid, value, PrimitiveDataService::merge);
        writeSequence.increment();
        this.indexer.index(sourceObject);
        return mergedBytes;
    }

    public boolean addToPatternElementSet(int patternNid, int elementNid) {

        return patternElementNidsMap.getIfAbsentPut(patternNid, integer -> new ConcurrentHashSet())
                .add(elementNid);
    }

    @Override
    public PrimitiveDataSearchResult[] search(String query, int maxResultSize) throws Exception {
        return this.searcher.search(query, maxResultSize);
    }

    public int[] semanticNidsOfPattern(int patternNid) {
        IntSet elementNids = getElementNidsForPatternNid(patternNid);
        if (elementNids.notEmpty()) {
            MutableIntList elementNidList = IntLists.mutable.withInitialCapacity(elementNids.size());
            elementNids.forEach(integer -> elementNidList.add(integer));
            return elementNidList.toArray();
        }
        return new int[0];
    }

    public IntSet getElementNidsForPatternNid(int patternNid) {
        if (patternElementNidsMap.containsKey(patternNid)) {
            return IntSets.immutable.ofAll(patternElementNidsMap.get(patternNid).stream().mapToInt(value -> (int) value));
        }
        return IntSets.immutable.empty();
    }

    @Override
    public void forEachSemanticNidOfPattern(int patternNid, IntProcedure procedure) {
        IntSet elementNids = getElementNidsForPatternNid(patternNid);
        if (elementNids.notEmpty()) {
            elementNids.forEach(procedure);
        } else {
            Entity entity = Entity.getFast(patternNid);
            if (entity instanceof PatternEntity == false) {
                throw new IllegalStateException("Trying to iterate elements for entity that is not a pattern: " + entity);
            }

        }
    }

    @Override
    public void forEachPatternNid(IntProcedure procedure) {
        try {
            this.uuidsLoadedLatch.await();
            this.patternNids.forEach(patternNid -> procedure.accept(patternNid));
        } catch (InterruptedException e) {
            LOG.error(e.getLocalizedMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void forEachConceptNid(IntProcedure procedure) {
        try {
            this.uuidsLoadedLatch.await();
            this.conceptNids.forEach(conceptNid -> procedure.accept(conceptNid));
        } catch (InterruptedException e) {
            LOG.error(e.getLocalizedMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void forEachStampNid(IntProcedure procedure) {
        try {
            this.uuidsLoadedLatch.await();
            this.stampNids.forEach(stampNid -> procedure.accept(stampNid));
        } catch (InterruptedException e) {
            LOG.error(e.getLocalizedMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void forEachSemanticNid(IntProcedure procedure) {
        try {
            this.uuidsLoadedLatch.await();
            this.semanticNids.forEach(semanticNid -> procedure.accept(semanticNid));
        } catch (InterruptedException e) {
            LOG.error(e.getLocalizedMessage(), e);
            throw new RuntimeException(e);
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
