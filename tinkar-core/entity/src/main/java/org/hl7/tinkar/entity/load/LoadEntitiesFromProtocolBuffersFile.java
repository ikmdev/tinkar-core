package org.hl7.tinkar.entity.load;

import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.service.Executor;
import org.hl7.tinkar.common.service.TrackingCallable;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.EntityService;
import org.hl7.tinkar.entity.transfom.EntityTransform;
import org.hl7.tinkar.entity.transfom.EntityTransformFactory;
import org.hl7.tinkar.entity.transfom.TransformDataType;
import org.hl7.tinkar.protobuf.PBTinkarMsg;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class LoadEntitiesFromProtocolBuffersFile extends TrackingCallable<Integer> {
    protected static final Logger LOG = Logger.getLogger(LoadEntitiesFromProtocolBuffersFile.class.getName());
    private static final int MAX_TASK_COUNT = Runtime.getRuntime().availableProcessors() * 2;
    final File importFile;
    final AtomicInteger importCount = new AtomicInteger();
    final Semaphore taskSemaphore = new Semaphore(MAX_TASK_COUNT, false);
    final AtomicInteger exceptionCount = new AtomicInteger();
    ConcurrentSkipListSet<PublicId> exceptionRecords = new ConcurrentSkipListSet<>();

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
            LOG.info(this.getClass().getSimpleName() + ": begin processing " + pbMessageCountStream.readLong() + " protocol buffers messages");

            ByteBuffer byteBuffer;
            int pbMessageLength;
            int bytesReadCount;

            while(pbStream.available() > 0){
                bytesReadCount = 0;
                pbMessageLength = pbStream.readInt();

                if(pbMessageLength == -1){
                    break; //EOF
                }
                taskSemaphore.acquireUninterruptibly();

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
                EntityTransform<PBTinkarMsg, Entity> entityTransform =
                        EntityTransformFactory.getTransform(TransformDataType.PROTOCOL_BUFFERS, TransformDataType.ENTITY);
                final Entity entity = entityTransform.transform(PBTinkarMsg.parseFrom(byteBuffer));

                Executor.threadPool().execute(() -> {
                    try {
                        EntityService.get().putEntity(entity);
                    } catch (Throwable e) {
                        e.printStackTrace();
                        exceptionRecords.add(entity.publicId());
                    } finally {
                        taskSemaphore.release();
                    }
                });

                importCount.incrementAndGet();
            }
        } catch (EOFException exception) {
            exception.printStackTrace();
        }

        taskSemaphore.acquireUninterruptibly(MAX_TASK_COUNT);
        StringBuilder logOutput = new StringBuilder()
                .append("Imported: ")
                .append(importCount)
                .append(" entities in: ")
                .append(durationString())
                .append(" with ")
                .append(exceptionCount.get())
                .append(" exceptions.")
                .append("\n");

        LOG.info(logOutput.toString());
        updateProgress(sizeForAll, sizeForAll);
        updateMessage(logOutput.toString());
        updateTitle("Loaded from " + importFile.getName());

        return importCount.get();
    }
}
