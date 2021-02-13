package org.hl7.tinkar.common.provider.chronology.mapdb;

import com.google.auto.service.AutoService;
import org.eclipse.collections.api.block.procedure.Procedure2;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.service.PrimitiveDataService;
import org.hl7.tinkar.common.util.UuidUtil;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

@AutoService(PrimitiveDataService.class)
public class MapDbProvider implements PrimitiveDataService {


    protected static AtomicReference<MapDbProvider> providerReference = new AtomicReference<>();
    protected static MapDbProvider singleton;

    public static MapDbProvider provider() {
        if (singleton == null) {
            singleton = providerReference.updateAndGet(providerEphemeral -> {
                if (providerEphemeral == null) {
                    return new MapDbProvider();
                }
                return providerEphemeral;
            });
        }
        return singleton;
    }
    private static final int EXECUTOR_THREADS = Runtime.getRuntime().availableProcessors() * 2;

    private static ExecutorService executorService = Executors.newFixedThreadPool(EXECUTOR_THREADS);

    private static final File dataDirectory = new File("target/mapdb/");
    static {
        dataDirectory.mkdirs();
    }
    private static final File mapdbFile = new File(dataDirectory, "tinkar.mapdb");
    private final DB db;

    private static final byte[] nextNidKey = UuidUtil.getRawBytes(new UUID(Long.MAX_VALUE, Long.MIN_VALUE));
    private static final AtomicInteger nextNid = new AtomicInteger(Integer.MIN_VALUE + 1);


    HTreeMap<Integer, byte[]> nidComponentMap;
    HTreeMap<UUID, Integer> uuidNidMap;
    HTreeMap<UUID, Integer> stampUuidNidMap;

    public MapDbProvider() {
        System.out.println("Starting MapDbProvider");
        this.db = DBMaker.fileDB(mapdbFile).fileMmapEnable().make();
         uuidNidMap = db.hashMap("uuidNidMap", Serializer.UUID, Serializer.INTEGER).createOrOpen();
        stampUuidNidMap = db.hashMap("stampUuidNidMap", Serializer.UUID, Serializer.INTEGER).createOrOpen();
        nidComponentMap = db.hashMap("nidComponentMap", Serializer.INTEGER, Serializer.BYTE_ARRAY).createOrOpen();
        MapDbProvider.singleton = this;
    }

    @Override
    public void close() {
        db.close();
    }

    @Override
    public byte[] merge(int nid, byte[] value) {
        return nidComponentMap.merge(nid, value, PrimitiveDataService::merge);
    }

    @Override
    public byte[] getBytes(int nid) {
        return nidComponentMap.get(nid);
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
        nidComponentMap.getEntries().stream().parallel().forEach(entry -> action.accept(entry.getKey(), entry.getValue()));
     }

}
