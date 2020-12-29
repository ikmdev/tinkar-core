package org.hl7.tinkar.provider.chronology.ephemeral;

import com.google.auto.service.AutoService;

import org.hl7.tinkar.service.PrimitiveDataService;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

@AutoService(PrimitiveDataService.class)
public class EntityProviderEphemeral implements PrimitiveDataService {
    private final ConcurrentHashMap<Integer, byte[]> nidComponentMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> uuidNidMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, long[]> nidUuidMap = new ConcurrentHashMap<>();
    private final AtomicInteger nextNid = new AtomicInteger(Integer.MIN_VALUE + 1);

    public EntityProviderEphemeral() {}


    @Override
    public byte[] merge(int nid, byte[] value, BiFunction<byte[], byte[], byte[]> remappingFunction) {
        return nidComponentMap.merge(nid, value, remappingFunction);
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
    public ConcurrentMap<Integer, long[]> nidUuidMap() {
        return nidUuidMap;
    }

    @Override
    public AtomicInteger nextNid() {
        return nextNid;
    }
}
