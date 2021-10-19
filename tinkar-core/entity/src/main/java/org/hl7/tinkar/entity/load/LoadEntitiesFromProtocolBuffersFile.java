package org.hl7.tinkar.entity.load;

import org.hl7.tinkar.common.service.Executor;
import org.hl7.tinkar.common.service.TrackingCallable;
import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.entity.EntityService;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class LoadEntitiesFromProtocolBuffersFile extends TrackingCallable<Integer> {
    protected static final Logger LOG = Logger.getLogger(LoadEntitiesFromProtocolBuffersFile.class.getName());
    private static final int MAX_TASK_COUNT = 100;
    final File importFile;
    final AtomicInteger importCount = new AtomicInteger();
    final Semaphore runningTasks = new Semaphore(MAX_TASK_COUNT, false);
    final AtomicInteger exceptionCount = new AtomicInteger();

    public LoadEntitiesFromProtocolBuffersFile(File importFile) {
        super(false, true);
        this.importFile = importFile;
        LOG.info("Loading entities from: " + importFile.getAbsolutePath());
    }

    public Integer compute() throws IOException {
        updateTitle("Loading " + importFile.getName());
        LOG.info(getTitle());

        double sizeForAll = 0;
        try (ZipFile zipFile = new ZipFile(importFile, StandardCharsets.UTF_8)) {
            ZipEntry exportPBEntry = zipFile.getEntry("export.pb");
            ZipEntry pbMessageCountEntry = zipFile.getEntry("count");
            double totalSize = exportPBEntry.getSize();
            sizeForAll += totalSize;
            DataInputStream pbStream = new DataInputStream(zipFile.getInputStream(exportPBEntry));
            DataInputStream pbMessageCountStream = new DataInputStream(zipFile.getInputStream(pbMessageCountEntry));
            LOG.info("LoadEntitiesFromPBFile: begin processing " + pbMessageCountStream.readLong() + " protocol buffer messages");

            ByteBuffer byteBuffer;
            int pbMessageLength;
            int bytesReadCount;

            while(pbStream.available() > 0){
                runningTasks.acquireUninterruptibly();

                bytesReadCount = 0;
                pbMessageLength = pbStream.readInt();

                if(pbMessageLength == -1){
                    break;
                }
                byteBuffer = ByteBuffer.allocate(pbMessageLength);

                while(bytesReadCount < pbMessageLength){
                    int sourceIndex = bytesReadCount;
                    byte[] bytesRead;

                    if(bytesReadCount == 0){
                        bytesRead = new byte[pbMessageLength];
                        bytesReadCount = pbStream.read(bytesRead, 0, pbMessageLength);
                    }else {
                        int lengthLeftToRead = pbMessageLength - bytesReadCount;
                        bytesRead = new byte[lengthLeftToRead];
                        bytesReadCount += pbStream.read(bytesRead, 0, lengthLeftToRead);
                    }
                    byteBuffer.put(sourceIndex, bytesRead);
                }
                Executor.threadPool().execute(new PutChronology(ProtocolBuffersEntityFactory.make(byteBuffer)));
                importCount.incrementAndGet();
            }
        } catch (EOFException exception) {
            exception.printStackTrace();
        }

        runningTasks.acquireUninterruptibly(MAX_TASK_COUNT);
        LOG.info("Imported: " + importCount + " items in: " + durationString() + " with "
                + exceptionCount.get()
                + " exceptions."
                + "\n");
        updateProgress(sizeForAll, sizeForAll);
        updateMessage(String.format("Imported %,d items in " + durationString(), importCount.get()));
        updateTitle("Loaded from " + importFile.getName());

        return importCount.get();
    }

    private class PutChronology implements Runnable {
        final Chronology chronology;

        public PutChronology(Chronology chronology) {
            this.chronology = chronology;
        }

        @Override
        public void run() {
            try {
                EntityService.get().putChronology(chronology);
            } catch (Throwable e) {
                e.printStackTrace();
            } finally {
                runningTasks.release();
            }
        }
    }
}
