package org.hl7.tinkar.provider.spinedarray;

import org.eclipse.collections.api.block.procedure.primitive.IntProcedure;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.collection.*;
import org.hl7.tinkar.common.service.*;
import org.hl7.tinkar.common.util.ints2long.IntsInLong;
import org.hl7.tinkar.provider.search.Indexer;
import org.hl7.tinkar.provider.search.Searcher;
import org.hl7.tinkar.provider.spinedarray.internal.Get;
import org.hl7.tinkar.provider.spinedarray.internal.Put;

import java.io.*;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.ObjIntConsumer;

import static java.lang.System.Logger.Level.ERROR;

public class SpinedArrayProvider implements PrimitiveDataService, NidGenerator {
    protected static final System.Logger LOG = System.getLogger(SpinedArrayProvider.class.getName());

    protected static final File defaultDataDirectory = new File("target/spinedarrays/");
    protected static SpinedArrayProvider singleton;
    protected static LongAdder writeSequence = new LongAdder();
    final AtomicInteger nextNid = new AtomicInteger(Integer.MIN_VALUE + 1);

    final ConcurrentUuidIntHashMap uuidToNidMap = new ConcurrentUuidIntHashMap();
    final SpinedByteArrayMap entityToBytesMap;
    final SpinedIntIntMap nidToPatternNidMap;
    /**
     * Using "citing" instead of "referencing" to make the field names more distinct.
     */
    final SpinedIntLongArrayMap nidToCitingComponentsNidMap;
    final SpinedIntIntArrayMap patternToElementNidsMap;

    final File uuidNidMapFile;
    final File nidToPatternNidMapDirectory;
    final File nidToByteArrayMapDirectory;
    final File nidToCitingComponentNidMapDirectory;
    final File patternToElementNidsMapDirectory;
    final Indexer indexer;
    final Searcher searcher;

    public SpinedArrayProvider() throws IOException {
        File configuredRoot = ServiceProperties.get(ServiceKeys.DATA_STORE_ROOT, defaultDataDirectory);
        configuredRoot.mkdirs();
        SpinedArrayProvider.singleton = this;
        Get.singleton = this;
        Put.singleton = this;

        this.uuidNidMapFile = new File(configuredRoot, "uuidNidMap");
        this.nidToPatternNidMapDirectory = new File(configuredRoot, "nidToPatternNidMap");
        this.nidToPatternNidMapDirectory.mkdirs();
        this.nidToByteArrayMapDirectory = new File(configuredRoot, "nidToByteArrayMap");
        this.nidToByteArrayMapDirectory.mkdirs();
        this.nidToCitingComponentNidMapDirectory = new File(configuredRoot, "nidToCitingComponentNidMap");
        this.nidToCitingComponentNidMapDirectory.mkdirs();
        this.patternToElementNidsMapDirectory = new File(configuredRoot, "patternToElementNidsMap");
        this.patternToElementNidsMapDirectory.mkdirs();

        this.entityToBytesMap = new SpinedByteArrayMap(new ByteArrayFileStore(nidToByteArrayMapDirectory));
        this.nidToPatternNidMap = new SpinedIntIntMap(KeyType.NID_KEY);
        this.nidToPatternNidMap.read(this.nidToPatternNidMapDirectory);
        this.nidToCitingComponentsNidMap = new SpinedIntLongArrayMap(new IntLongArrayFileStore(nidToCitingComponentNidMapDirectory));
        this.patternToElementNidsMap = new SpinedIntIntArrayMap(new IntIntArrayFileStore(patternToElementNidsMapDirectory));

        if (uuidNidMapFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(uuidNidMapFile))) {
                uuidToNidMap.readExternal(ois);
                UUID nextNidKey = new UUID(0, 0);
                nextNid.set(uuidToNidMap.get(nextNidKey));
            } catch (IOException | ClassNotFoundException e) {
                LOG.log(ERROR, e.getLocalizedMessage(), e);
                throw new RuntimeException(e);
            }
        }
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
        try {
            save();
            entityToBytesMap.close();
            SpinedArrayProvider.singleton = null;
            this.indexer.commit();
            this.indexer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void save() {
        UUID nextNidKey = new UUID(0, 0);
        uuidToNidMap.put(nextNidKey, nextNid.get());
        try (ObjectOutputStream objectOutputStream =
                     new ObjectOutputStream(new FileOutputStream(uuidNidMapFile))) {
            uuidToNidMap.writeExternal(objectOutputStream);
        } catch (IOException e) {
            LOG.log(ERROR, e.getLocalizedMessage(), e);
        }
        nidToPatternNidMap.write(this.nidToPatternNidMapDirectory);
        this.entityToBytesMap.write();
        this.nidToCitingComponentsNidMap.write();
        this.patternToElementNidsMap.write();
    }

    @Override
    public int nidForUuids(UUID... uuids) {
        if (uuids.length == 1) {
            return uuidToNidMap.getIfAbsentPut(uuids[0], () -> newNid());
        }
        int nid = Integer.MAX_VALUE;
        for (UUID uuid : uuids) {
            if (nid == Integer.MAX_VALUE) {
                nid = uuidToNidMap.getIfAbsentPut(uuids[0], () -> newNid());
            } else {
                uuidToNidMap.put(uuid, nid);
            }
        }
        return nid;
    }

    @Override
    public int newNid() {
        return nextNid.getAndIncrement();
    }

    @Override
    public int nidForUuids(ImmutableList<UUID> uuidList) {
        if (uuidList.size() == 1) {
            return uuidToNidMap.getIfAbsentPut(uuidList.get(0), () -> newNid());
        }
        int nid = Integer.MAX_VALUE;
        for (UUID uuid : uuidList) {
            if (nid == Integer.MAX_VALUE) {
                nid = uuidToNidMap.getIfAbsentPut(uuid, () -> newNid());
            } else {
                uuidToNidMap.put(uuid, nid);
            }
        }
        return nid;
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
    public SearchResult[] search(String query, int maxResultSize) throws Exception {
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
