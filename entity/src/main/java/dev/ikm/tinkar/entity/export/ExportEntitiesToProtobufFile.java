package dev.ikm.tinkar.entity.export;

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.transfom.EntityToTinkarSchemaTransformer;
import dev.ikm.tinkar.schema.TinkarMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ExportEntitiesToProtobufFile extends TrackingCallable<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(ExportEntitiesToProtobufFile.class);

    File protobufFile;
    private final EntityToTinkarSchemaTransformer entityTransformer = EntityToTinkarSchemaTransformer.getInstance();
    final AtomicInteger exportConceptCount = new AtomicInteger();
    final AtomicInteger exportSemanticCount = new AtomicInteger();
    final AtomicInteger exportPatternCount = new AtomicInteger();

    final AtomicInteger nullEntities = new AtomicInteger(0);

    static final int VERBOSE_ERROR_COUNT = 10;

    public ExportEntitiesToProtobufFile(File file) {
        super(false, true);
        this.protobufFile = file;
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

        try(FileOutputStream fileOutputStream = new FileOutputStream(protobufFile);
            BufferedOutputStream bos = new BufferedOutputStream(fileOutputStream);
            ZipOutputStream zos = new ZipOutputStream(bos)) {

            // Create a single entry
            ZipEntry zipEntry = new ZipEntry(protobufFile.getName().replace(".zip", ""));
            zos.putNextEntry(zipEntry);

            // Looping through each concept and calling the transformer
            PrimitiveData.get().forEachConceptNid(conceptNid -> {
                try {
                    Entity<? extends EntityVersion> conceptEntity = EntityService.get().getEntityFast(conceptNid);
                    if(conceptEntity != null){
                        TinkarMsg pbTinkarMsg = entityTransformer.transform(conceptEntity);
                        pbTinkarMsg.writeDelimitedTo(zos);
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
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            // Looping through each semantic and calling the transformer
            PrimitiveData.get().forEachSemanticNid(semanticNid -> {
                try {
                    Entity<? extends EntityVersion> semanticEntity = EntityService.get().getEntityFast(semanticNid);
                    if(semanticEntity != null){
                        TinkarMsg pbTinkarMsg = entityTransformer.transform(semanticEntity);
                        pbTinkarMsg.writeDelimitedTo(zos);
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
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            // Looping through each pattern and calling the transformer
            PrimitiveData.get().forEachPatternNid(patternNid -> {
                try {
                    Entity<? extends EntityVersion> patternEntity = EntityService.get().getEntityFast(patternNid);
                    if(patternEntity != null){
                        TinkarMsg pbTinkarMsg = entityTransformer.transform(patternEntity);
                        pbTinkarMsg.writeDelimitedTo(zos);
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
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            LOG.info("Zip entry size: " + zipEntry.getSize());
            // finalize zip file
            zos.closeEntry();
            zos.flush();
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return conceptCount.get() + patternCount.get() + semanticCount.get();
    }
}
