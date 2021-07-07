package org.hl7.tinkar.provider.ephemeral;

import org.eclipse.collections.api.block.procedure.Procedure2;
import org.eclipse.collections.api.block.procedure.primitive.IntProcedure;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.hl7.tinkar.collection.KeyType;
import org.hl7.tinkar.collection.SpinedIntIntMapAtomic;
import org.hl7.tinkar.common.service.Executor;
import org.hl7.tinkar.common.service.NidGenerator;
import org.hl7.tinkar.common.service.PrimitiveDataService;
import org.hl7.tinkar.common.util.ints2long.IntsInLong;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.ObjIntConsumer;


public class ProviderEphemeral implements PrimitiveDataService, NidGenerator {

    protected static AtomicReference<ProviderEphemeral> providerReference = new AtomicReference<>();
    protected static ProviderEphemeral singleton;
    protected static LongAdder writeSequence = new LongAdder();

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

    private final ConcurrentHashMap<Integer, byte[]> nidComponentMap = ConcurrentHashMap.newMap();
    private final ConcurrentHashMap<UUID, Integer> uuidNidMap = new ConcurrentHashMap<>();
    private final AtomicInteger nextNid = new AtomicInteger(Integer.MIN_VALUE + 1);

    // TODO I don't think the spines need to be atomic for this use case of nids -> elementIndices.
    //  There is no update after initial value set...
    final SpinedIntIntMapAtomic nidToPatternNidMap = new SpinedIntIntMapAtomic(KeyType.NID_KEY);
    /**
     * Using "citing" instead of "referencing" to make the field names more distinct.
     */
    final ConcurrentHashMap<Integer, long[]> nidToCitingComponentsNidMap = ConcurrentHashMap.newMap();
    final ConcurrentHashMap<Integer, int[]> patternToElementNidsMap = ConcurrentHashMap.newMap();

    private ProviderEphemeral() {
        System.out.println("Constructing ProviderEphemeral");
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
                     long citationLong = IntsInLong.ints2Long(nid, patternNid);
                     this.nidToCitingComponentsNidMap.merge(referencedComponentNid, new long[]{citationLong},
                            PrimitiveDataService::mergeCitations);
                    this.patternToElementNidsMap.merge(patternNid, new int[]{nid},
                            PrimitiveDataService::mergePatternElements);
                }
            }
        }
        byte[] mergedBytes = nidComponentMap.merge(nid, value, PrimitiveDataService::merge);
        writeSequence.increment();
        return mergedBytes;
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
    public byte[] getBytes(int nid) {
        return nidComponentMap.get(nid);
    }

    @Override
    public void forEach(ObjIntConsumer<byte[]> action) {
        nidComponentMap.forEach((integer, bytes) -> action.accept(bytes, integer));
    }

    @Override
    public void forEachParallel(ObjIntConsumer<byte[]> action) {
        int threadCount = Executor.threadPool().getMaximumPoolSize();
        List<Procedure2<Integer, byte[]>> blocks = new ArrayList<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            blocks.add((Procedure2<Integer, byte[]>) (integer, bytes) -> action.accept(bytes, integer));
        }
        nidComponentMap.parallelForEachKeyValue(blocks, Executor.threadPool());
    }

    @Override
    public int nidForUuids(UUID... uuids) {
        return PrimitiveDataService.nidForUuids(uuidNidMap, this, uuids);
    }

    @Override
    public int nidForUuids(ImmutableList<UUID> uuidList) {
        return PrimitiveDataService.nidForUuids(uuidNidMap, this, uuidList);
    }

    @Override
    public int newNid() {
        return nextNid.getAndIncrement();
    }
}
