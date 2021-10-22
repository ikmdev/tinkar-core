package org.hl7.tinkar.entity.export;

import org.hl7.tinkar.common.service.*;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

public class ExportEntitiesToProtocolBuffers extends TrackingCallable<Void> {

    private final Logger LOG = Logger.getLogger(ExportEntitiesToProtocolBuffers.class.getName());

    private Path exportPath;
    private static final int MAX_THREAD_COUNT = Runtime.getRuntime().availableProcessors() * 2;
    private final Semaphore threadSemaphore = new Semaphore(MAX_THREAD_COUNT);

    public ExportEntitiesToProtocolBuffers(Path exportPath) {
        super(false, true);
        this.exportPath = exportPath;
        LOG.info("Exporting entities to: " + exportPath.toString());
    }

    @Override
    public Void compute() throws IOException {
        final ByteArrayOutputStream pbConceptChronologyStream = new ByteArrayOutputStream();
        final ByteArrayOutputStream pbSemanticChronologyStream = new ByteArrayOutputStream();
        final ByteArrayOutputStream pbPatternChronologyStream = new ByteArrayOutputStream();
        final FileOutputStream fileOutputStream = new FileOutputStream(exportPath.toFile());

        threadSemaphore.acquireUninterruptibly();
        Executor.threadPool().execute(() -> {
            PrimitiveData.get().forEachConceptNid(conceptNid -> {
            });
            threadSemaphore.release();
        });

        threadSemaphore.acquireUninterruptibly();
        Executor.threadPool().execute(() -> {
            PrimitiveData.get().forEachSemanticNid(semanticNid -> {

            });
            threadSemaphore.release();
        });

        threadSemaphore.acquireUninterruptibly();
        Executor.threadPool().execute(() -> {
            PrimitiveData.get().forEachPatternNid(patternNid -> {

            });
            threadSemaphore.release();
        });

        threadSemaphore.acquireUninterruptibly(MAX_THREAD_COUNT);
        fileOutputStream.write(pbConceptChronologyStream.toByteArray());
        fileOutputStream.write(pbSemanticChronologyStream.toByteArray());
        fileOutputStream.write(pbPatternChronologyStream.toByteArray());

        return null;
    }
}
