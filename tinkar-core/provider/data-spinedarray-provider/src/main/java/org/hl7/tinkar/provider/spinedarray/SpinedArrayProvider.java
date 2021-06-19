package org.hl7.tinkar.provider.spinedarray;

import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import org.eclipse.collections.api.block.procedure.primitive.IntProcedure;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.primitive.LongSet;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.eclipse.collections.impl.factory.primitive.LongSets;
import org.hl7.tinkar.collection.*;
import org.hl7.tinkar.common.service.*;
import org.hl7.tinkar.provider.spinedarray.internal.Get;
import org.hl7.tinkar.provider.spinedarray.internal.Put;

import java.io.*;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.ObjIntConsumer;

// TODO finish replacing logging api with System logger?
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

    public SpinedArrayProvider() {
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
    }

    @Override
    public void close() {
        save();
        entityToBytesMap.close();
        SpinedArrayProvider.singleton = null;
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
    public int newNid() {
        return nextNid.getAndIncrement();
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
    public byte[] merge(int nid, int patternNid, int referencedComponentNid, byte[] value) {
            if (!this.entityToBytesMap.containsKey(nid)) {

                this.nidToPatternNidMap.put(nid, patternNid);
                if (patternNid != Integer.MAX_VALUE) {
                    // Citing component, pattern...
                    long citationLong = (((long) nid) << 32) | (patternNid & 0xffffffffL);
                    //int citingComponentNid = (int) (citationLong >> 32);
                    //int patternNid = (int) citationLong;
                    this.nidToCitingComponentsNidMap.accumulateAndGet(referencedComponentNid, new long[]{citationLong},
                            PrimitiveDataService::mergeCitations);
                    this.patternToElementNidsMap.accumulateAndGet(patternNid, new int[]{nid},
                            PrimitiveDataService::mergePatternElements);
                }
            }
            byte[] mergedBytes = this.entityToBytesMap.accumulateAndGet(nid, value, PrimitiveDataService::merge);
            writeSequence.increment();
            return mergedBytes;
     }

    @Override
    public long writeSequence() {
        return writeSequence.sum();
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
