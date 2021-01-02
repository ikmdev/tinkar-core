package org.hl7.tinkar.provider.chronology.mapdb;

import com.google.auto.service.AutoService;
import org.eclipse.collections.api.block.procedure.Procedure2;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.hl7.tinkar.service.PrimitiveDataService;
import org.hl7.tinkar.util.UuidUtil;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import java.io.File;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

@AutoService(PrimitiveDataService.class)
public class MapDbProvider implements PrimitiveDataService {


    private static final int EXECUTOR_THREADS = Runtime.getRuntime().availableProcessors() * 2;

    private static ExecutorService executorService = Executors.newFixedThreadPool(EXECUTOR_THREADS);

    protected static MapDbProvider singleton;

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
    public AtomicInteger nextNid() {
        return nextNid;
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
