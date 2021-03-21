package org.hl7.tinkar.provider.mvstore;

import com.google.auto.service.AutoService;
import org.eclipse.collections.api.block.procedure.primitive.IntProcedure;
import org.eclipse.collections.api.list.ImmutableList;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.OffHeapStore;
import org.hl7.tinkar.common.service.ServiceKeys;
import org.hl7.tinkar.common.service.ServiceProperties;
import org.hl7.tinkar.provider.mvstore.internal.Get;
import org.hl7.tinkar.provider.mvstore.internal.Put;
import org.hl7.tinkar.common.service.PrimitiveDataService;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ObjIntConsumer;

/**
 *
 * TODO: Maybe also consider making use of: https://blogs.oracle.com/javamagazine/creating-a-java-off-heap-in-memory-database?source=:em:nw:mt:::RC_WWMK200429P00043:NSL400123121
 */
public class MVStoreProvider implements PrimitiveDataService {

    protected static MVStoreProvider singleton;
    private static final File defaultDataDirectory = new File("target/mvstore/");
    private static final String databaseFileName = "mvstore.dat";

    private static final UUID nextNidKey = new UUID(Long.MAX_VALUE, Long.MIN_VALUE);
    private static final AtomicInteger nextNid = new AtomicInteger(Integer.MIN_VALUE + 1);

    final OffHeapStore offHeap;
    final MVStore store;
    final MVMap<Integer, byte[]> nidToComponentMap;
    final MVMap<UUID, Integer> uuidToNidMap;
    final MVMap<UUID, Integer> stampUuidToNidMap;
    final MVMap<Integer, Integer> nidToTypeDefNidMap;
    final MVMap<Integer, Integer> nidToReferencedComponentNidMap;

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
       this.nidToTypeDefNidMap = store.openMap("nidToTypeDefNidMap");
       this.nidToReferencedComponentNidMap = store.openMap("nidToReferencedComponentNidMap");

        if (this.uuidToNidMap.containsKey(nextNidKey)) {
            this.nextNid.set(this.uuidToNidMap.get(nextNidKey));
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
    public void forEachEntityOfType(int typeDefinitionNid, IntProcedure procedure) {
        this.nidToTypeDefNidMap.forEach((nid, defForNid) -> {
            if (defForNid == typeDefinitionNid) {
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
    public void forEachSemanticNidForComponentOfType(int componentNid, int typeDefinitionNid, IntProcedure procedure) {
        nidToReferencedComponentNidMap.forEach((nid, referencedComponentNid) -> {
            if (componentNid == referencedComponentNid) {
                if (nidToTypeDefNidMap.get(nid) == typeDefinitionNid) {
                    procedure.accept(nid);
                }
            }
        });
    }

    @Override
    public byte[] merge(int nid, int definitionNid, int referencedComponentNid, byte[] value) {
       if (!nidToTypeDefNidMap.containsKey(nid)) {
           nidToTypeDefNidMap.putIfAbsent(nid, definitionNid);
           nidToReferencedComponentNidMap.put(nid, referencedComponentNid);
       }

        return nidToComponentMap.merge(nid, value, PrimitiveDataService::merge);
    }

    @Override
    public byte[] getBytes(int nid) {
        return this.nidToComponentMap.get(nid);
    }

    @Override
    public int nidForUuids(UUID... uuids) {
        return PrimitiveDataService.nidForUuids(uuidToNidMap, nextNid, uuids);
    }

    @Override
    public int nidForUuids(ImmutableList<UUID> uuidList) {
        return PrimitiveDataService.nidForUuids(uuidToNidMap, nextNid, uuidList);
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
