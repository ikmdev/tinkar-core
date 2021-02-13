package org.hl7.tinkar.common.provider.chronology.openhft;

import com.google.auto.service.AutoService;
import net.openhft.chronicle.core.values.IntValue;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ChronicleMapBuilder;
import net.openhft.chronicle.values.Values;
import org.eclipse.collections.api.block.procedure.Procedure2;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.provider.chronology.openhft.internal.Put;
import org.hl7.tinkar.common.service.PrimitiveDataService;
import org.hl7.tinkar.common.util.UuidUtil;
import org.hl7.tinkar.common.provider.chronology.openhft.internal.Get;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

@AutoService(PrimitiveDataService.class)
public class OpenHFTProvider implements PrimitiveDataService {

    // http://blog.siteos.de/java/javaee/chroniclemap/2016/05/16/How-To-Use-ChronicleMap-With-JavaEE.html
    //

    private static final File dataDirectory = new File("target/chronicle-map/");
    static {
        dataDirectory.mkdirs();
    }
    private static final File nidEntityFile = new File(dataDirectory, "entities.dat");
    private static final File uuidNidFile = new File(dataDirectory, "uuidNidMap.dat");
    private static final File stampUuidFile = new File(dataDirectory, "stampUuidMap.dat");


    private static final byte[] nextNidKey = UuidUtil.getRawBytes(new UUID(Long.MAX_VALUE, Long.MIN_VALUE));
    private static final AtomicInteger nextNid = new AtomicInteger(Integer.MIN_VALUE + 1);

    ChronicleMap<IntValue, byte[]> nidComponentMap;
    ChronicleMap<long[], IntValue> uuidNidMap;

    ChronicleMap<long[], IntValue> stampUuidNidMap;

    public OpenHFTProvider() {
        try {
            this.uuidNidMap = ChronicleMapBuilder
                    .of(long[].class, IntValue.class)
                    .name("uuidNidMap")
                    .entries(10_000_000)
                    .checksumEntries(false)
                    .constantKeySizeBySample(new long[2])
                    .createOrRecoverPersistedTo(uuidNidFile, true);

            this.stampUuidNidMap = ChronicleMapBuilder
                    .of(long[].class, IntValue.class)
                    .name("stampUuidNidMap")
                    .entries(1_000)
                    .checksumEntries(false)
                    .constantKeySizeBySample(new long[2])
                    .createOrRecoverPersistedTo(stampUuidFile, true);

            IntValue defaultFirstNidValue = Values.newHeapInstance(IntValue.class);
            defaultFirstNidValue.setValue(Integer.MIN_VALUE + 1);
            this.nextNid.set(this.uuidNidMap.getOrDefault(nextNidKey, defaultFirstNidValue).getValue());

            this.nidComponentMap = ChronicleMapBuilder
                    .of(IntValue.class, byte[].class)
                    .name("entities")
                    .entries(10_000_000)
                    .checksumEntries(false)
                    .averageValueSize(100)
                    .createOrRecoverPersistedTo(nidEntityFile, true);

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Get.singleton = this;
        Put.singleton = this;
    }

    public void close() {
        this.uuidNidMap.close();
        this.stampUuidNidMap.close();
        this.nidComponentMap.close();
    }

    @Override
    public byte[] merge(int nid, byte[] value) {
        return new byte[0];
    }

    @Override
    public byte[] getBytes(int nid) {
        return this.nidComponentMap.get(nid);
    }

    @Override
    public int nidForUuids(UUID... uuids) {
        throw new UnsupportedOperationException();
        //return PrimitiveDataService.nidForUuids(uuidNidMap, nextNid, uuids);
    }

    @Override
    public int nidForUuids(ImmutableList<UUID> uuidList) {
        throw new UnsupportedOperationException();
        //return PrimitiveDataService.nidForUuids(uuidNidMap, nextNid, uuidList);
    }

    @Override
    public void forEach(BiConsumer<Integer, byte[]> action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEachParallel(Procedure2<Integer, byte[]> action) {
        throw new UnsupportedOperationException();
    }
}
