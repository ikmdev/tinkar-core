package dev.ikm.tinkar.provider.changeset;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.DataActivity;
import dev.ikm.tinkar.common.service.SaveState;
import dev.ikm.tinkar.common.service.ServiceKeys;
import dev.ikm.tinkar.common.service.ServiceProperties;
import dev.ikm.tinkar.common.util.time.DateTimeUtil;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.entity.export.ExportEntitiesToProtobufFile;
import dev.ikm.tinkar.entity.transform.EntityToTinkarSchemaTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ChangeSetWriterProvider implements ChangeSetWriterService, SaveState {
    private static final Logger LOG = LoggerFactory.getLogger(ChangeSetWriterProvider.class);

    private enum STATE {
        INITIALIZING,
        RUNNING,
        ROTATING,
        STOPPED,
        FAILED
    }
    AtomicReference<STATE> state = new AtomicReference<>(STATE.INITIALIZING);
    AtomicReference<Thread> serviceThread = new AtomicReference<>();
    /**
     * Initialization-on-demand holder idiom:
     * This is the preferred approach in most cases as it provides lazy initialization with thread safety.
     */
    private static class ChangeSetWriterHolder {
        public static final ChangeSetWriterProvider INSTANCE = new ChangeSetWriterProvider();
    }

    public static ChangeSetWriterProvider provider() {
        return ChangeSetWriterHolder.INSTANCE;
    }

    private final File changeSetFolder;
    private final AtomicReference<File> zipAtomicReference = new AtomicReference<>();
    private final LinkedBlockingQueue<Entity> entitiesToWrite = new LinkedBlockingQueue<>();

    private ChangeSetWriterProvider() {
        Optional<File> optionalDataStoreRoot = ServiceProperties.get(ServiceKeys.DATA_STORE_ROOT);
        if (optionalDataStoreRoot.isPresent()) {
            this.changeSetFolder = new File(optionalDataStoreRoot.get(), "changeSets");
            if (!changeSetFolder.exists()) {
                changeSetFolder.mkdirs();
            }
            newZipFile();
            startService();
        } else {
            throw new IllegalStateException("ServiceKeys.DATA_STORE_ROOT not provided.");
        }
    }

    @Override
    public void writeToChangeSet(Entity entity, DataActivity activity) {
        try {
            switch (activity) {
                case SYNCHRONIZABLE_EDIT -> this.entitiesToWrite.put(entity);
                case LOADING_CHANGE_SET, INITIALIZE, LOCAL_EDIT, DATA_REPAIR -> {}
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void newZipFile() {
        this.zipAtomicReference.accumulateAndGet(null, (prev, x) -> {
            if (prev == null) {
                File newFile = new File(changeSetFolder,
                        DateTimeUtil.nowWithZoneCompact().replace(':', '\uA789') + " ChangeSet.zip");
                try {
                    newFile.createNewFile();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return newFile;
            }
            return prev;
        });
    }

    private void startService() {
        AtomicBoolean deleteZipFile = new AtomicBoolean(false);
         Thread.ofVirtual().start(() -> {
            Thread.currentThread().setName("ChangeSetWriterProvider-ServiceThread");
            serviceThread.set(Thread.currentThread());
            state.set(STATE.RUNNING);
            File protobufFile = zipAtomicReference.get();
            LongAdder entityCount = new LongAdder();
            LongAdder conceptsCount = new LongAdder();
            LongAdder semanticsCount = new LongAdder();
            LongAdder patternsCount = new LongAdder();
            LongAdder stampsCount = new LongAdder();

            final Set<PublicId> moduleList = new HashSet<>();
            final Set<PublicId> authorList = new HashSet<>();
            final EntityToTinkarSchemaTransformer entityTransformer =
                    EntityToTinkarSchemaTransformer.getInstance();

            try (FileOutputStream fos = new FileOutputStream(protobufFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos);
                 ZipOutputStream zos = new ZipOutputStream(bos)) {
                // Create a single entry for all changes in this zip file
                ZipEntry zipEntry = new ZipEntry("Entities");
                zos.putNextEntry(zipEntry);
                try {
                    while (state.get() == STATE.RUNNING) {
                        Entity entityToWrite = this.entitiesToWrite.take();
                        if (!entityToWrite.equals(StampRecord.nonExistentStamp())) {
                            // TODO: Need to also not write if the database is loading change sets.
                            entityCount.increment();
                            switch (entityToWrite) {
                                case ConceptEntity ignored -> conceptsCount.increment();
                                case SemanticEntity ignored -> semanticsCount.increment();
                                case PatternEntity ignored -> patternsCount.increment();
                                case StampEntity stampEntity -> {
                                    stampsCount.increment();
                                    // Store Module & Author Dependencies for Manifest
                                    moduleList.add(stampEntity.module().publicId());
                                    authorList.add(stampEntity.author().publicId());
                                }
                                default -> {
                                    throw new IllegalStateException("Unexpected value: " + entityToWrite);
                                }
                            }
                            // Transform and write data
                            entityTransformer.transform(entityToWrite).writeDelimitedTo(zos);
                        }
                    }
                } catch (InterruptedException e) {
                    // Expected and supported.
                }
                if (entityCount.sum() > 0) {
                    LOG.info("Data zipEntry size: " + zipEntry.getSize());
                    LOG.info("Data zipEntry compressed size: " + zipEntry.getCompressedSize());

                    // Write Manifest File
                    ZipEntry manifestEntry = new ZipEntry("META-INF/MANIFEST.MF");
                    zos.putNextEntry(manifestEntry);
                    zos.write(generateManifestContent(entityCount,
                            conceptsCount,
                            semanticsCount,
                            patternsCount,
                            stampsCount,
                            moduleList,
                            authorList).getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                    zos.flush();
                } else {
                    deleteZipFile.set(true);
                }
                // Cleanup
                zos.finish();

            } catch (IOException e) {
                state.set(STATE.FAILED);
                throw new RuntimeException(e);
            }
            if (deleteZipFile.get()) {
                protobufFile.delete();
            }
        });
    }

    private String generateManifestContent(LongAdder entityCount,
                                           LongAdder conceptsCount,
                                           LongAdder semanticsCount,
                                           LongAdder patternsCount,
                                           LongAdder stampsCount,
                                           Set<PublicId> moduleList,
                                           Set<PublicId> authorList){
        return ExportEntitiesToProtobufFile.generateManifestContent(entityCount.sum(),
                conceptsCount.sum(),
                semanticsCount.sum(),
                patternsCount.sum(),
                stampsCount.sum(),
                moduleList,
                authorList);
    }

    @Override
    public void save() {
        checkpoint(true);
     }

    @Override
    public void shutdown() {
        checkpoint(false);
    }

    private void checkpoint(boolean restart) {
        switch (serviceThread.get()) {
            case Thread thread -> thread.interrupt();
            case null -> {}
        }
        if (restart) {
            // start a new thread with a new file
            startService();
        }
    }
}
