package org.hl7.tinkar.provider.spinedarray;

import org.hl7.tinkar.collection.SpineFileUtil;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static org.hl7.tinkar.collection.SpineFileUtil.SPINE_PREFIX;

public class SpinedArrayFileStore {
    protected static final Logger LOG = Logger.getLogger(SpinedArrayFileStore.class.getName());
    protected final Semaphore diskSemaphore;

    protected final File directory;

    protected int spineSize;

    public SpinedArrayFileStore(File directory) {
        this(directory, new Semaphore(1));
    }

    public SpinedArrayFileStore(File directory, Semaphore diskSemaphore) {
        this.directory = directory;
        this.spineSize = SpineFileUtil.readSpineCount(directory);
        this.diskSemaphore = diskSemaphore;
    }

    public final void writeSpineCount(int spineCount) {
        try {
            this.spineSize = spineCount;
            SpineFileUtil.writeSpineCount(directory, spineCount);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public final int getSpineCount() {
        return SpineFileUtil.readSpineCount(directory);
    }

    public final int sizeOnDisk() {
        if (directory == null) {
            return 0;
        }
        File[] files = directory.listFiles((pathname) -> {
            return pathname.getName().startsWith(SPINE_PREFIX);
        });
        int size = 0;
        for (File spineFile : files) {
            size = (int) (size + spineFile.length());
        }
        return size;
    }
}
