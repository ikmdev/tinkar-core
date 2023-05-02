package dev.ikm.tinkar.entity.load;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.ikm.tinkar.common.service.TinkExecutor;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.transfom.ProtobufTransformer;
import dev.ikm.tinkar.schema.PBTinkarMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The purpose of this class is to successfully load all Protobuf messages from a protobuf file and transform them into entities.
 */
public class LoadEntitiesFromProtobufFile extends TrackingCallable<Integer> {

    protected static final Logger LOG = LoggerFactory.getLogger(LoadEntitiesFromProtobufFile.class.getName());
    private static final int MAX_TASK_COUNT = Runtime.getRuntime().availableProcessors() * 2;
    final File importFile;
    final AtomicInteger importCount = new AtomicInteger();
    final Semaphore taskSemaphore = new Semaphore(MAX_TASK_COUNT, false);
    final AtomicInteger exceptionCount = new AtomicInteger();

    public LoadEntitiesFromProtobufFile(File importFile) {
        super(false, true);
        this.importFile = importFile;
        LOG.info("Loading entities from: " + importFile.getAbsolutePath());
    }

    /**
     * This purpose of this method is to process all protobuf messages and call the entity transformer.
     * @return a Integer count of all entities/protobuf messages loaded/exported
     * @throws IOException
     */
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
            LOG.info(this.getClass().getSimpleName() + ": begin processing "
                    + pbMessageCountStream.readLong() + " protocol buffers messages");

            ByteBuffer byteBuffer;
            int pbMessageLength;
            int bytesReadCount;

            while (pbStream.available() > 0) {
                bytesReadCount = 0;
                pbMessageLength = pbStream.readInt();

                if (pbMessageLength == -1) {
                    break; //EOF
                }

                byteBuffer = ByteBuffer.allocate(pbMessageLength);

                while (bytesReadCount < pbMessageLength) {
                    int sourceIndex = bytesReadCount;
                    byte[] bytesRead;

                    if (bytesReadCount == 0) {
                        bytesRead = new byte[pbMessageLength];
                        bytesReadCount = pbStream.read(bytesRead, 0, pbMessageLength);
                    } else {
                        int lengthLeftToRead = pbMessageLength - bytesReadCount;
                        bytesRead = new byte[lengthLeftToRead];
                        bytesReadCount += pbStream.read(bytesRead, 0, lengthLeftToRead);
                    }
                    byteBuffer.put(sourceIndex, bytesRead);
                }

                final byte[] pbBytes = byteBuffer.array();

                taskSemaphore.acquireUninterruptibly();
                TinkExecutor.threadPool().execute(() -> {
                    try {
                        ProtobufTransformer transformer = ProtobufTransformer.getInstance();
                        Entity<? extends EntityVersion> entity = transformer.transform(PBTinkarMsg.parseFrom(pbBytes));
                        EntityService.get().putEntity(entity);
                        transformer.getStampEntities()
                                .forEach(stampEntity -> EntityService.get().putEntity(stampEntity));
                    } catch (IllegalStateException | InvalidProtocolBufferException e) {
                        e.printStackTrace();
                        exceptionCount.incrementAndGet();
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

        updateProgress(sizeForAll, sizeForAll);
        updateTitle("Loaded from " + importFile.getName());

        return importCount.get();
    }

    public String report(){
        return "Imported: " +
                importCount +
                " entities in: " +
                durationString() +
                " with " +
                exceptionCount.get() +
                " exceptions." +
                "\n";
    }

}
