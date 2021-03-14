package org.hl7.tinkar.provider.spinedarray;

import org.hl7.tinkar.collection.store.ByteArrayArrayStore;
import org.hl7.tinkar.collection.store.ByteArrayStore;

import java.io.*;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.hl7.tinkar.collection.SpineFileUtil.SPINE_PREFIX;

public class ByteArrayFileStore extends SpinedArrayFileStore implements ByteArrayStore {
    protected static final Logger LOG = Logger.getLogger(ByteArrayArrayFileStore.class.getName());

    public ByteArrayFileStore(File directory) {
        super(directory);
    }

    public ByteArrayFileStore(File directory, Semaphore diskSemaphore) {
        super(directory, diskSemaphore);
    }

    @Override
    public Optional<AtomicReferenceArray<byte[]>> get(int spineIndex) {
        String spineKey = SPINE_PREFIX + spineIndex;
        File spineFile = new File(directory, spineKey);
        if (spineFile.exists()) {
            diskSemaphore.acquireUninterruptibly();
            try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(spineFile)))) {
                int arraySize = dis.readInt();
                byte[][] spineArray = new byte[arraySize][];
                for (int i = 0; i < arraySize; i++) {
                    int valueSize = dis.readInt();
                    if (valueSize != 0) {
                        byte[] value = new byte[valueSize];
                        dis.readFully(value);
                        spineArray[i] = value;
                    }
                }
                AtomicReferenceArray<byte[]> spine = new AtomicReferenceArray<>(spineArray);
                return Optional.of(spine);

            } catch (IOException ex) {
                LOG.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
            } finally {
                diskSemaphore.release();
            }
        }
        return Optional.empty();
    }

    @Override
    public void put(int spineIndex, AtomicReferenceArray<byte[]> spine) {
        String spineKey = SPINE_PREFIX + spineIndex;
        File spineFile = new File(directory, spineKey);
        diskSemaphore.acquireUninterruptibly();
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(spineFile)))) {
            dos.writeInt(spine.length());
            for (int i = 0; i < spine.length(); i++) {
                byte[] value = spine.get(i);
                if (value == null) {
                    dos.writeInt(0);
                } else {
                    dos.writeInt(value.length);
                    dos.write(value);
                }
            }
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, ex.getLocalizedMessage(), ex);
        } finally {
            diskSemaphore.release();
        }
    }

}
