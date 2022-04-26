package org.hl7.tinkar.entity.export;

import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.service.TinkExecutor;
import org.hl7.tinkar.common.service.TrackingCallable;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.EntityService;
import org.hl7.tinkar.entity.EntityVersion;
import org.hl7.tinkar.entity.transfom.EntityTransformer;
import org.hl7.tinkar.protobuf.PBTinkarMsg;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class ExportEntitiesToProtobufFile extends TrackingCallable<Void> {

    private final Logger LOG = Logger.getLogger(ExportEntitiesToProtobufFile.class.getName());

    private static final int MAX_THREAD_COUNT = Runtime.getRuntime().availableProcessors() * 2;
    private final Semaphore threadSemaphore = new Semaphore(MAX_THREAD_COUNT);
    private FileOutputStream fileOutputStream;
    private final EntityTransformer entityTransformer = new EntityTransformer();


    public ExportEntitiesToProtobufFile(File file) {
        super(false, true);
        try {
            fileOutputStream = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        LOG.info("Exporting entities to: " + file);
    }

    @Override
    public Void compute() throws IOException {

        threadSemaphore.acquireUninterruptibly();
        TinkExecutor.threadPool().execute(() -> {
            PrimitiveData.get().forEachConceptNid(conceptNid -> {
                try {
                    Entity<? extends EntityVersion> conceptEntity = EntityService.get().getEntityFast(conceptNid);
                    writePBTinkarMsg(entityTransformer.transform(conceptEntity));
                }catch (IllegalStateException | IOException exception){
                    exception.printStackTrace();
                }
            });
            threadSemaphore.release();
        });

        threadSemaphore.acquireUninterruptibly();
        TinkExecutor.threadPool().execute(() -> {
            PrimitiveData.get().forEachSemanticNid(semanticNid -> {
                Entity<? extends EntityVersion> semanticEntity = EntityService.get().getEntityFast(semanticNid);
                try {
                    Entity<? extends EntityVersion> conceptEntity = EntityService.get().getEntityFast(semanticNid);
                    writePBTinkarMsg(entityTransformer.transform(semanticEntity));
                }catch (IllegalStateException | IOException exception){
                    exception.printStackTrace();
                }
            });
            threadSemaphore.release();
        });

        threadSemaphore.acquireUninterruptibly();
        TinkExecutor.threadPool().execute(() -> {
            PrimitiveData.get().forEachPatternNid(patternNid -> {
                Entity<? extends EntityVersion> patternEntity = EntityService.get().getEntityFast(patternNid);
                try {
                    Entity<? extends EntityVersion> conceptEntity = EntityService.get().getEntityFast(patternNid);
                    writePBTinkarMsg(entityTransformer.transform(patternEntity));
                }catch (IllegalStateException | IOException exception){
                    exception.printStackTrace();
                }
            });
            threadSemaphore.release();
        });

        threadSemaphore.acquireUninterruptibly(MAX_THREAD_COUNT);

        return null;
    }

    private synchronized void writePBTinkarMsg(PBTinkarMsg pbTinkarMsg) throws IOException {
        byte[] pbMessageBytes = pbTinkarMsg.toByteArray();
        this.fileOutputStream.write(ByteBuffer.allocate(pbMessageBytes.length + 1)
                .put((byte) pbMessageBytes.length)
                .put(pbMessageBytes)
                .array());
    }

}
