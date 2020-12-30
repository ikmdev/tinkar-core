package org.hl7.tinkar.provider.chronology.mapdb;

import com.google.auto.service.AutoService;
import org.hl7.tinkar.component.ChronologyService;
import org.hl7.tinkar.service.PrimitiveDataService;
import org.hl7.tinkar.util.UuidUtil;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

@AutoService(PrimitiveDataService.class)
public class MapDbProvider implements PrimitiveDataService {

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
    HTreeMap<Integer, long[]> nidUuidMap;
    HTreeMap<UUID, Integer> stampUuidNidMap;

    public MapDbProvider() {
        System.out.println("Starting MapDbProvider");
        this.db = DBMaker.fileDB(mapdbFile).fileMmapEnable().make();
        nidUuidMap = db.hashMap("nidUuidMap", Serializer.INTEGER, Serializer.LONG_ARRAY).createOrOpen();
        uuidNidMap = db.hashMap("uuidNidMap", Serializer.UUID, Serializer.INTEGER).createOrOpen();
        stampUuidNidMap = db.hashMap("stampUuidNidMap", Serializer.UUID, Serializer.INTEGER).createOrOpen();
        nidComponentMap = db.hashMap("nidComponentMap", Serializer.INTEGER, Serializer.BYTE_ARRAY).createOrOpen();
    }

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
    public ConcurrentMap<Integer, long[]> nidUuidMap() {
        return nidUuidMap;
    }

    @Override
    public AtomicInteger nextNid() {
        return nextNid;
    }
}
