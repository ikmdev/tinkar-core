package dev.ikm.tinkar.entity.export;

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.transfom.EntityTransformer;
import dev.ikm.tinkar.schema.PBTinkarMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

public class ExportEntitiesToProtobufFile extends TrackingCallable<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(ExportEntitiesToProtobufFile.class);
    private FileOutputStream fileOutputStream;
    private final EntityTransformer entityTransformer = new EntityTransformer();
    final AtomicInteger exportConceptCount = new AtomicInteger();
    final AtomicInteger exportSemanticCount = new AtomicInteger();
    final AtomicInteger exportPatternCount = new AtomicInteger();

    final AtomicInteger nullEntities = new AtomicInteger(0);

    static final int VERBOSE_ERROR_COUNT = 10;


    public ExportEntitiesToProtobufFile(File file) {
        super(false, true);
        try {
            fileOutputStream = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        LOG.info("Exporting entities to: " + file);
    }

    /**
     * The compute function is responsible for getting the primitive data and calling the entityTransform class
     * @return a count of the entities transformed
     * @throws IOException
     */
    @Override
    public Integer compute() throws IOException {

        // Setting about AtomicInteger counts to keep track of entity count.
        AtomicInteger verboseErrors = new AtomicInteger(0);
        AtomicInteger conceptCount = new AtomicInteger(0);
        PrimitiveData.get().forEachConceptNid(nid -> conceptCount.incrementAndGet());
        LOG.info("Found " + conceptCount.get() + " concept nids.");

        AtomicInteger semanticCount = new AtomicInteger(0);
        PrimitiveData.get().forEachSemanticNid(nid -> semanticCount.incrementAndGet());
        LOG.info("Found " + semanticCount.get() + " semantic nids.");

        AtomicInteger patternCount = new AtomicInteger(0);
        PrimitiveData.get().forEachPatternNid(nid -> patternCount.incrementAndGet());
        LOG.info("Found " + patternCount.get() + " pattern nids.");

        // Looping through each concept and calling the transformer
        PrimitiveData.get().forEachConceptNid(conceptNid -> {
            try {
                Entity<? extends EntityVersion> conceptEntity = EntityService.get().getEntityFast(conceptNid);
                if(conceptEntity != null){
                    PBTinkarMsg pbTinkarMsg = entityTransformer.transform(conceptEntity);
                    writePBTinkarMsg(pbTinkarMsg);
                    exportConceptCount.incrementAndGet();
                } else {
                    nullEntities.incrementAndGet();
                    if (verboseErrors.get() < VERBOSE_ERROR_COUNT) {
                        LOG.warn("No concept entity for: " + conceptNid);
                        verboseErrors.incrementAndGet();
                    }
                }
            } catch (UnsupportedOperationException | IllegalStateException | NullPointerException exception){
                LOG.error("Processing conceptNid: " + conceptNid,exception);
                exception.printStackTrace();
            }});

        // Looping through each semantic and calling the transformer
        PrimitiveData.get().forEachSemanticNid(semanticNid -> {
            try {
                Entity<? extends EntityVersion> semanticEntity = EntityService.get().getEntityFast(semanticNid);
                if(semanticEntity != null){
                    PBTinkarMsg pbTinkarMsg = entityTransformer.transform(semanticEntity);
                    writePBTinkarMsg(pbTinkarMsg);
                    exportSemanticCount.incrementAndGet();
                } else {
                    nullEntities.incrementAndGet();
                    if (verboseErrors.get() < VERBOSE_ERROR_COUNT) {
                        LOG.warn("No semantic entity for: " + semanticNid);
                        verboseErrors.incrementAndGet();
                    }
                }
            }catch (UnsupportedOperationException | IllegalStateException exception){
                LOG.error("Processing semanticNid: " + semanticNid,exception);
                exception.printStackTrace();
            }
        });

        // Looping through each pattern and calling the transformer
        PrimitiveData.get().forEachPatternNid(patternNid -> {
            try {
                Entity<? extends EntityVersion> patternEntity = EntityService.get().getEntityFast(patternNid);
                if(patternEntity != null){
                    PBTinkarMsg pbTinkarMsg = entityTransformer.transform(patternEntity);
                    writePBTinkarMsg(pbTinkarMsg);
                    exportPatternCount.incrementAndGet();
                } else {
                    nullEntities.incrementAndGet();
                    if (verboseErrors.get() < VERBOSE_ERROR_COUNT) {
                        LOG.warn("No pattern entity for: " + patternEntity);
                        verboseErrors.incrementAndGet();
                    }
                }
            }catch (UnsupportedOperationException | IllegalStateException exception){
                LOG.error("Processing patternNid: " + patternNid,exception);
                exception.printStackTrace();
            }
        });

        return conceptCount.get() + patternCount.get() + semanticCount.get();
    }

    /**
     *  This method is used to create a string with all counts for entities. Primarily used in testing to ensure
     *  all entities loaded in are being exported with the same amounts of each entity object.
     * @return a String with a message of the counts
     */
    public String reportCount(){
        StringBuilder sb = new StringBuilder();
        sb.append("Exported: " + exportConceptCount + " Concepts AFTER the export.\n");
        sb.append("Exported: " + exportSemanticCount + " Semantics AFTER the export.\n");
        sb.append("Exported: " + exportPatternCount + " Patterns AFTER the export.\n");
        sb.append("Found: " + nullEntities.get() + " references to null items.");
        sb.append("\n");
        return sb.toString();
    }

    private synchronized void writePBTinkarMsg(PBTinkarMsg pbTinkarMsg) {
        byte[] pbMessageBytes = pbTinkarMsg.toByteArray();
        try {
            this.fileOutputStream.write(ByteBuffer.allocate(pbMessageBytes.length + 1)
                    .put((byte) pbMessageBytes.length)
                    .put(pbMessageBytes)
                    .array());
        } catch(IOException exception){
            exception.printStackTrace();
            throw new RuntimeException();
        }
    }
}
