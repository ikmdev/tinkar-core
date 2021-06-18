package org.hl7.tinkar.provider.spinedarray;

import org.hl7.tinkar.collection.SpineFileUtil;
import org.hl7.tinkar.collection.store.IntIntArrayStore;
import org.hl7.tinkar.collection.store.IntLongArrayStore;

import java.io.*;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IntLongArrayFileStore extends SpinedArrayFileStore implements IntLongArrayStore {
    protected static final Logger LOG = Logger.getLogger(IntIntArrayFileStore.class.getName());

    public IntLongArrayFileStore(File directory) {
        super(directory);
    }

    public IntLongArrayFileStore(File directory, Semaphore diskSemaphore) {
        super(directory, diskSemaphore);
    }

    @Override
    public Optional<AtomicReferenceArray<long[]>> get(int spineIndex) {
        String spineKey = SpineFileUtil.SPINE_PREFIX + spineIndex;
        File spineFile = new File(directory, spineKey);
        if (!spineFile.exists()) {
            return Optional.empty();
        }
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(spineFile)))) {
            int arraySize = dis.readInt();
            AtomicReferenceArray<long[]> data = new AtomicReferenceArray<>(arraySize);
            for (int i = 0; i < arraySize; i++) {
                int valueSize = dis.readInt();
                if (valueSize != 0) {
                    long[] value = new long[valueSize];
                    for (int j = 0; j < valueSize; j++) {
                        value[j] = dis.readLong();
                    }
                    data.set(i, value);
                }
            }
            return Optional.of(data);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void put(int spineIndex, AtomicReferenceArray<long[]> spine) {
        directory.mkdirs();
        String spineKey = SpineFileUtil.SPINE_PREFIX + spineIndex;
        File spineFile = new File(directory, spineKey);
        diskSemaphore.acquireUninterruptibly();
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(spineFile)))) {
            dos.writeInt(spine.length());
            for (int i = 0; i < spine.length(); i++) {
                long[] value = spine.get(i);
                if (value == null) {
                    dos.writeInt(0);
                } else {
                    dos.writeInt(value.length);
                    for (long valueElement : value) {
                        dos.writeLong(valueElement);
                    }
                }
            }
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
            throw new RuntimeException(ex);
        } finally {
            diskSemaphore.release();
        }
    }
}
