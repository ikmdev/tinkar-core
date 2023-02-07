package org.hl7.tinkar.entity.export;

import org.hl7.tinkar.common.service.*;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.EntityService;
import org.hl7.tinkar.entity.EntityVersion;
import org.hl7.tinkar.entity.transfom.EntityTransformer;
import org.hl7.tinkar.protobuf.PBTinkarMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

public class ExportEntitiesToProtobufFile extends TrackingCallable<Void> {
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

    @Override
    public Void compute() throws IOException {

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
                        LOG.info("No concept entity for: " + conceptNid);
                        verboseErrors.incrementAndGet();
                    }
                }
            } catch (UnsupportedOperationException | IllegalStateException | NullPointerException exception){
                LOG.error("Processing conceptNid: " + conceptNid,exception);
                exception.printStackTrace();
        }});

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
                        LOG.info("No semantic entity for: " + semanticNid);
                        verboseErrors.incrementAndGet();
                    }
                }
            }catch (UnsupportedOperationException | IllegalStateException exception){
                LOG.error("Processing semanticNid: " + semanticNid,exception);
                exception.printStackTrace();
            }
        });

        PrimitiveData.get().forEachPatternNid(patternNid -> {
            try {
                Entity<? extends EntityVersion> patternEntity = EntityService.get().getEntityFast(patternNid);
                if(patternEntity != null){
                    PBTinkarMsg pbTinkarMsg = entityTransformer.transform(patternEntity);
                    writePBTinkarMsg(pbTinkarMsg);
                    entityTransformer.transform(patternEntity);
                    exportPatternCount.incrementAndGet();
                } else {
                    nullEntities.incrementAndGet();
                    if (verboseErrors.get() < VERBOSE_ERROR_COUNT) {
                        LOG.info("No pattern entity for: " + patternEntity);
                        verboseErrors.incrementAndGet();
                    }
                }
            }catch (UnsupportedOperationException | IllegalStateException exception){
                LOG.error("Processing patternNid: " + patternNid,exception);
                exception.printStackTrace();
            }
        });


        LOG.info(reportCount());

        return null;
    }

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
            }
            catch(IOException exception){
                exception.printStackTrace();
            }
        }
    }
