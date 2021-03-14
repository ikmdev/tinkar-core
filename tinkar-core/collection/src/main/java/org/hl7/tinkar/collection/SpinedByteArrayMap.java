package org.hl7.tinkar.collection;

import org.hl7.tinkar.collection.store.ByteArrayStore;
import org.hl7.tinkar.common.service.PrimitiveDataService;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.logging.Logger;

public class SpinedByteArrayMap extends SpinedIntObjectMap<byte[]> {

    private static final Logger LOG = Logger.getLogger(SpinedByteArrayArrayMap.class.getName());
    private final ByteArrayStore byteArrayStore;

    public SpinedByteArrayMap(ByteArrayStore byteArrayStore) {
        super(byteArrayStore.getSpineCount());
        this.byteArrayStore = byteArrayStore;
    }

    public int sizeOnDisk() {
        return byteArrayStore.sizeOnDisk();
    }

    public int memoryInUse() {
        AtomicInteger sizeInBytes = new AtomicInteger();
        sizeInBytes.addAndGet(((spineSize * 8) * getSpineCount()));
        forEachSpine((AtomicReferenceArray<byte[]> spine, int spineIndex) -> {
            for (int i = 0; i < spine.length(); i++) {
                byte[] value = spine.get(i);
                sizeInBytes.addAndGet(value.length + 4); // 4 bytes = integer length of the array of array length.
            }
        });
        return sizeInBytes.get();
    }


    protected AtomicReferenceArray<byte[]> readSpine(int spineIndex) {
        Optional<AtomicReferenceArray<byte[]>> optionalSpine = this.byteArrayStore.get(spineIndex);
        if (optionalSpine.isPresent()) {
            return optionalSpine.get();
        }
        return new AtomicReferenceArray<>(spineSize);
    }

    public boolean write() {

        try {
            fileSemaphore.acquireUninterruptibly();
            this.byteArrayStore.writeSpineCount(getSpineCount());
            return forEachChangedSpine((AtomicReferenceArray<byte[]> spine, int spineIndex) -> {
                this.byteArrayStore.put(spineIndex, spine);
            });
        } finally {
            fileSemaphore.release();
        }
    }

 }
