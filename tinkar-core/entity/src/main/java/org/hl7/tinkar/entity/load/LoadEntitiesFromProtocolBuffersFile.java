package org.hl7.tinkar.entity.load;

import org.hl7.tinkar.common.service.Executor;
import org.hl7.tinkar.common.service.TrackingCallable;
import org.hl7.tinkar.entity.EntityService;
import org.hl7.tinkar.protobuf.PBTinkarMsg;

import java.io.*;
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
            double totalSize = exportPBEntry.getSize();
            sizeForAll += totalSize;
            DataInputStream pbStream = new DataInputStream(zipFile.getInputStream(exportPBEntry));
            LOG.info("LoadEntitiesFromPBFile: begin processing");

            ByteBuffer byteBuffer;
            int pbMessageLength;
            int bytesReadCount;

            while(pbStream.available() > 0){
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

                final byte[] pbBytes = byteBuffer.array();
                Executor.threadPool().execute(() -> {
                    try {
//                        EntityService.get().putEntity(ProtocolBuffersEntityFactory.make(PBTinkarMsg.parseFrom(pbBytes)));
                    } catch (Throwable e) {
                        e.printStackTrace();
                        exceptionCount.incrementAndGet();
                        System.exit(0);
                    } finally {
                        runningTasks.release();
                    }
                });
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
}
