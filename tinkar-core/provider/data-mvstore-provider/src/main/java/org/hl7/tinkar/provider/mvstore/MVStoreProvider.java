package org.hl7.tinkar.provider.mvstore;

import org.eclipse.collections.api.block.procedure.primitive.IntProcedure;
import org.eclipse.collections.api.list.ImmutableList;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.OffHeapStore;
import org.hl7.tinkar.collection.SpinedIntIntArrayMap;
import org.hl7.tinkar.collection.SpinedIntLongArrayMap;
import org.hl7.tinkar.common.service.NidGenerator;
import org.hl7.tinkar.common.service.ServiceKeys;
import org.hl7.tinkar.common.service.ServiceProperties;
import org.hl7.tinkar.provider.mvstore.internal.Get;
import org.hl7.tinkar.provider.mvstore.internal.Put;
import org.hl7.tinkar.common.service.PrimitiveDataService;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.ObjIntConsumer;

/**
 *
 * TODO: Maybe also consider making use of: https://blogs.oracle.com/javamagazine/creating-a-java-off-heap-in-memory-database?source=:em:nw:mt:::RC_WWMK200429P00043:NSL400123121
 */
public class MVStoreProvider implements PrimitiveDataService, NidGenerator {

    protected static MVStoreProvider singleton;
    protected LongAdder writeSequence = new LongAdder();
    protected final AtomicInteger nextNid;
    private static final File defaultDataDirectory = new File("target/mvstore/");
    private static final String databaseFileName = "mvstore.dat";

    private static final UUID nextNidKey = new UUID(Long.MAX_VALUE, Long.MIN_VALUE);

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

   public MVStoreProvider() {
        this.offHeap = new OffHeapStore();
        File configuredRoot = ServiceProperties.get(ServiceKeys.DATA_STORE_ROOT, defaultDataDirectory);
        configuredRoot.mkdirs();
        File databaseFile = new File(configuredRoot, databaseFileName);
        System.out.println("Starting MVStoreProvider from: " + databaseFile.getAbsolutePath());
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
    }

    public void close() {
        save();
        this.store.close();
    }

    public void save() {
        this.uuidToNidMap.put(nextNidKey, nextNid.get());
        this.store.commit();
        this.offHeap.sync();
    }

    @Override
    public int newNid() {
        return nextNid.getAndIncrement();
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

    @Override
    public byte[] merge(int nid, int patternNid, int referencedComponentNid, byte[] value) {
       if (!nidToPatternNidMap.containsKey(nid)) {
           this.nidToPatternNidMap.put(nid, patternNid);
           if (patternNid != Integer.MAX_VALUE) {

               this.nidToPatternNidMap.put(nid, patternNid);
               if (patternNid != Integer.MAX_VALUE) {
                   // Citing component, pattern...
                   long citationLong = (((long) nid) << 32) | (patternNid & 0xffffffffL);
                   //int citingComponentNid = (int) (citationLong >> 32);
                   //int patternNid = (int) citationLong;
                   this.nidToCitingComponentsNidMap.merge(referencedComponentNid, new long[]{citationLong},
                           PrimitiveDataService::mergeCitations);
                   this.patternToElementNidsMap.merge(patternNid, new int[]{nid},
                           PrimitiveDataService::mergePatternElements);
               }
           }
       }
       byte[] mergedBytes = nidToComponentMap.merge(nid, value, PrimitiveDataService::merge);
       writeSequence.increment();
       return mergedBytes;
    }

    @Override
    public long writeSequence() {
        return writeSequence.sum();
    }

    @Override
    public byte[] getBytes(int nid) {
        return this.nidToComponentMap.get(nid);
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
}
