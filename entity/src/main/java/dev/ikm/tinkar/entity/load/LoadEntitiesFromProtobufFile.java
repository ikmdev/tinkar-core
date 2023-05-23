package dev.ikm.tinkar.entity.load;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.entity.transfom.TinkarSchemaToEntityTransformer;
import dev.ikm.tinkar.schema.PBTinkarMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The purpose of this class is to successfully load all Protobuf messages from a protobuf file and transform them into entities.
 */
public class LoadEntitiesFromProtobufFile extends TrackingCallable<Integer> {

    protected static final Logger LOG = LoggerFactory.getLogger(LoadEntitiesFromProtobufFile.class.getName());
    private static final int MAX_TASK_COUNT = Runtime.getRuntime().availableProcessors() * 2;
    final File importFile;
    static final AtomicInteger importCount = new AtomicInteger();
    final Semaphore taskSemaphore = new Semaphore(MAX_TASK_COUNT, false);
    final AtomicInteger exceptionCount = new AtomicInteger();
    final AtomicInteger importConceptCount = new AtomicInteger();
    final AtomicInteger importSemanticCount = new AtomicInteger();
    final AtomicInteger importPatternCount = new AtomicInteger();
    final AtomicInteger importStampCount = new AtomicInteger();

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

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(importFile))) {
            Consumer<Entity<? extends  EntityVersion>> entityConsumer = entity -> {
                try {
                    EntityService.get().putEntity(entity);
                    updateCounts(entity);
                } catch (Throwable e) {
                    // output entities with issues
                    StringBuilder sb = new StringBuilder();
                    sb.append("Error persisting entity types: " + entity.entityDataType() + " ");
                    entity.publicId().asUuidList().forEach(c -> sb.append(" " + c.toString() + " "));
                    LOG.error(sb.toString());
                    e.printStackTrace();
                }
            };

            Consumer<StampEntity<StampEntityVersion>> stampEntityConsumer = stampEntity -> {
                EntityService.get().putEntity(stampEntity);
                updateSTAMPCount();
            };

            ZipEntry zipEntry = zis.getNextEntry();
            LOG.info("The zip entry name: " + zipEntry.getName());
            LOG.info("The zip entry size: " + zipEntry.getSize());
            TinkarSchemaToEntityTransformer transformer = TinkarSchemaToEntityTransformer.getInstance();
            while (zipEntry != null) {
                while (zis.available() > 0) {
                    PBTinkarMsg pbTinkarMsg = PBTinkarMsg.parseDelimitedFrom(zis);

                    if(pbTinkarMsg == null)
                    {
                        LOG.warn("Possible byte before end of file.");
                        continue;
                    }
                    transformer.transform(pbTinkarMsg, entityConsumer, stampEntityConsumer);
                }
                // advance next zip entry
                zipEntry = zis.getNextEntry();
            }
        } catch (IllegalStateException | InvalidProtocolBufferException e) {
            e.printStackTrace();
            exceptionCount.incrementAndGet();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        updateProgress(sizeForAll, sizeForAll);
        updateTitle("POST-Loaded from " + importFile.getName());
        LOG.info("The imported count of messages = " + importCount.get());
        return importCount.get();
    }

    private void updateCounts(Entity entity){
        if(entity instanceof ConceptEntity<?>){
            importConceptCount.incrementAndGet();
        }else if(entity instanceof SemanticEntity<?>){
            importSemanticCount.incrementAndGet();
        }else if(entity instanceof PatternEntity<?>){
            importPatternCount.incrementAndGet();
        }
        importCount.incrementAndGet();
    }

    private void updateSTAMPCount(){
        importStampCount.incrementAndGet();
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
