package org.hl7.tinkar.provider.chronology.mvstore;

import com.google.auto.service.AutoService;
import org.eclipse.collections.api.block.procedure.Procedure2;
import org.eclipse.collections.api.list.ImmutableList;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.OffHeapStore;
import org.hl7.tinkar.provider.chronology.mvstore.internal.Get;
import org.hl7.tinkar.provider.chronology.mvstore.internal.Put;
import org.hl7.tinkar.service.PrimitiveDataService;
import org.hl7.tinkar.util.UuidUtil;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

@AutoService(PrimitiveDataService.class)
public class MVStoreProvider implements PrimitiveDataService {

    protected static MVStoreProvider singleton;
    private static final File dataDirectory = new File("target/mvstore/");
    static {
        dataDirectory.mkdirs();
    }

    private static final File mvstoreFile = new File(dataDirectory, "mvstore.dat");

    private static final byte[] nextNidKey = UuidUtil.getRawBytes(new UUID(Long.MAX_VALUE, Long.MIN_VALUE));
    private static final AtomicInteger nextNid = new AtomicInteger(Integer.MIN_VALUE + 1);

    final OffHeapStore offHeap;
    final MVStore store;
    final MVMap<Integer, byte[]> nidComponentMap;
    final MVMap<UUID, Integer> uuidNidMap;

    final MVMap<UUID, Integer> stampUuidNidMap;

    public MVStoreProvider() {
        this.offHeap = new OffHeapStore();
        this.offHeap.open(mvstoreFile.getAbsolutePath(), false, null);
        this.store = new MVStore.Builder().fileName(mvstoreFile.getAbsolutePath()).open();
        this.nidComponentMap = store.openMap("nidComponentMap");
        this.uuidNidMap = store.openMap("uuidNidMap");
        this.stampUuidNidMap = store.openMap("stampUuidNidMap");
        Get.singleton = this;
        Put.singleton = this;
        MVStoreProvider.singleton = this;
    }

    public void close() {
        this.store.commit();
        this.offHeap.sync();
        this.store.close();
    }

    @Override
    public byte[] merge(int nid, byte[] value) {
        return nidComponentMap.merge(nid, value, PrimitiveDataService::merge);
    }

    @Override
    public byte[] getBytes(int nid) {
        return this.nidComponentMap.get(nid);
    }

    @Override
    public int nidForUuids(UUID... uuids) {
        return PrimitiveDataService.nidForUuids(uuidNidMap, nextNid, uuids);
    }

    @Override
    public int nidForUuids(ImmutableList<UUID> uuidList) {
        return PrimitiveDataService.nidForUuids(uuidNidMap, nextNid, uuidList);
    }

    @Override
    public void forEach(BiConsumer<Integer, byte[]> action) {
        nidComponentMap.forEach(action);
    }

    @Override
    public void forEachParallel(Procedure2<Integer, byte[]> action) {
        nidComponentMap.entrySet().stream().parallel().forEach(entry -> action.accept(entry.getKey(), entry.getValue()));
    }
}
