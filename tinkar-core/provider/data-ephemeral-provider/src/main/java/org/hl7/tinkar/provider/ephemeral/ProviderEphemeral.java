package org.hl7.tinkar.provider.ephemeral;

import org.eclipse.collections.api.block.procedure.Procedure2;
import org.eclipse.collections.api.block.procedure.primitive.IntProcedure;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.hl7.tinkar.collection.KeyType;
import org.hl7.tinkar.collection.SpinedIntIntMapAtomic;
import org.hl7.tinkar.common.service.Executor;
import org.hl7.tinkar.common.service.PrimitiveDataService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ObjIntConsumer;


public class ProviderEphemeral implements PrimitiveDataService {

    protected static AtomicReference<ProviderEphemeral> providerReference = new AtomicReference<>();
    protected static ProviderEphemeral singleton;

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
    final SpinedIntIntMapAtomic nidToReferencedComponentNidMap = new SpinedIntIntMapAtomic(KeyType.NID_KEY);

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
        nidToReferencedComponentNidMap.forEach((nid, referencedComponentNid) -> {
            if (componentNid == referencedComponentNid) {
                procedure.accept(nid);
            }
        });
    }

    @Override
    public void forEachSemanticNidForComponentOfPattern(int componentNid, int patternNid, IntProcedure procedure) {
        nidToReferencedComponentNidMap.forEach((nid, referencedComponentNid) -> {
            if (componentNid == referencedComponentNid) {
                if (nidToPatternNidMap.get(nid) == patternNid) {
                    procedure.accept(nid);
                }
            }
        });
    }

    @Override
    public byte[] merge(int nid, int patternNid, int referencedComponentNid, byte[] value) {
        nidToPatternNidMap.put(nid, patternNid);
        nidToReferencedComponentNidMap.put(nid, referencedComponentNid);
        return nidComponentMap.merge(nid, value, PrimitiveDataService::merge);
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
        return PrimitiveDataService.nidForUuids(uuidNidMap, nextNid, uuids);
    }

    @Override
    public int nidForUuids(ImmutableList<UUID> uuidList) {
        return PrimitiveDataService.nidForUuids(uuidNidMap, nextNid, uuidList);
    }
}
