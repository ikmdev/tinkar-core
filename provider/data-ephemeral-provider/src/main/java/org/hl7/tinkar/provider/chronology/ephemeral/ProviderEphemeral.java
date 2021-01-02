package org.hl7.tinkar.provider.chronology.ephemeral;

import com.google.auto.service.AutoService;

import org.eclipse.collections.api.block.procedure.Procedure2;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.hl7.tinkar.service.PrimitiveDataService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

@AutoService(PrimitiveDataService.class)
public class ProviderEphemeral implements PrimitiveDataService {

    private static final int EXECUTOR_THREADS = Runtime.getRuntime().availableProcessors() * 2;

    private static ExecutorService executorService = Executors.newFixedThreadPool(EXECUTOR_THREADS);

    protected static ProviderEphemeral singleton;

    private final ConcurrentHashMap<Integer, byte[]> nidComponentMap = ConcurrentHashMap.newMap();
    private final ConcurrentHashMap<UUID, Integer> uuidNidMap = new ConcurrentHashMap<>();
    private final AtomicInteger nextNid = new AtomicInteger(Integer.MIN_VALUE + 1);

    public ProviderEphemeral() {
        singleton = this;
    }

    @Override
    public byte[] merge(int nid, byte[] value, BiFunction<byte[], byte[], byte[]> remappingFunction) {
        return nidComponentMap.merge(nid, value, remappingFunction);
    }

    @Override
    public void close() {
        executorService.shutdown();
    }

    @Override
    public byte[] getBytes(int nid) {
        return nidComponentMap.get(nid);
    }

    @Override
    public ConcurrentMap<UUID, Integer> uuidNidMap() {
        return uuidNidMap;
    }

    @Override
    public AtomicInteger nextNid() {
        return nextNid;
    }

    @Override
    public void forEach(BiConsumer<Integer, byte[]> action) {
        nidComponentMap.forEach(action);
    }

    @Override
    public void forEachParallel(Procedure2<Integer, byte[]> action) {
        List<Procedure2<Integer, byte[]>> blocks = new ArrayList<>(EXECUTOR_THREADS);
        for (int i = 0; i < EXECUTOR_THREADS; i++) {
            blocks.add(action);
        }
        nidComponentMap.parallelForEachKeyValue(blocks, executorService);
    }
}
