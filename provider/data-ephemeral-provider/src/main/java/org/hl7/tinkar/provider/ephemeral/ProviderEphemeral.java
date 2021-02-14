package org.hl7.tinkar.provider.ephemeral;

import org.eclipse.collections.api.block.procedure.Procedure2;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.hl7.tinkar.common.service.PrimitiveDataService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

public class ProviderEphemeral implements PrimitiveDataService {

    protected static AtomicReference<ProviderEphemeral> providerReference = new AtomicReference<>();
    protected static ProviderEphemeral singleton;

    protected static ProviderEphemeral provider() {
        System.out.println("Providing ProviderEphemeral...");
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

    private static final int EXECUTOR_THREADS = Runtime.getRuntime().availableProcessors() * 2;

    private static ExecutorService executorService = Executors.newFixedThreadPool(EXECUTOR_THREADS);

    private final ConcurrentHashMap<Integer, byte[]> nidComponentMap = ConcurrentHashMap.newMap();
    private final ConcurrentHashMap<UUID, Integer> uuidNidMap = new ConcurrentHashMap<>();
    private final AtomicInteger nextNid = new AtomicInteger(Integer.MIN_VALUE + 1);

    private ProviderEphemeral() {
        System.out.println("Constructing ProviderEphemeral");
    }

    @Override
    public byte[] merge(int nid, byte[] value) {
        return nidComponentMap.merge(nid, value, PrimitiveDataService::merge);
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

    @Override
    public int nidForUuids(UUID... uuids) {
        return PrimitiveDataService.nidForUuids(uuidNidMap, nextNid, uuids);
    }

    @Override
    public int nidForUuids(ImmutableList<UUID> uuidList) {
        return PrimitiveDataService.nidForUuids(uuidNidMap, nextNid, uuidList);
    }
}
