/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.entity.load;

import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.entity.transform.TinkarSchemaToEntityTransformer;
import dev.ikm.tinkar.schema.TinkarMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The purpose of this class is to successfully load all Protobuf messages from a protobuf file and transform them into entities.
 */
public class LoadEntitiesFromProtobufFile extends TrackingCallable<Integer> {

    protected static final Logger LOG = LoggerFactory.getLogger(LoadEntitiesFromProtobufFile.class.getName());
    final File importFile;
    static final AtomicInteger importCount = new AtomicInteger();
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
     * @return Integer count of all entities/protobuf messages loaded/exported
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
                    sb.append("Error persisting entity types: ")
                            .append(entity.entityDataType())
                            .append(" ");
                    entity.publicId().asUuidList().forEach(c -> sb.append(" ")
                            .append(c.toString())
                            .append(" "));
                    LOG.error(sb.toString());
                    e.printStackTrace();
                }
            };

            Consumer<StampEntity<StampEntityVersion>> stampEntityConsumer = stampEntity -> {
                EntityService.get().putEntity(stampEntity);
                updateSTAMPCount();
            };

            ZipEntry zipEntry = Objects.requireNonNull(zis.getNextEntry());
            LOG.info("The zip entry name: " + zipEntry.getName());
            LOG.info("The zip entry size: " + zipEntry.getSize());
            TinkarSchemaToEntityTransformer transformer = TinkarSchemaToEntityTransformer.getInstance();
            // TODO: Refactor to better handle importing with manifest
            while (zipEntry != null) {
                if (zipEntry.getName().equals("META-INF/MANIFEST.MF")) {
                    break;
                }
                while (zis.available() > 0) {
                    // Last byte is -1 to tell protobuf parser that it is at EOF
                    // Since there is a final byte, zis.available will still return 1
                    // And pbTinkarMsg should be null for the last entry
                    TinkarMsg pbTinkarMsg = TinkarMsg.parseDelimitedFrom(zis);

                    if (zis.available() == 0) {
                        break;
                    }
                    if(pbTinkarMsg == null) {
                        LOG.warn("Possible byte before end of file.");
                        continue;
                    }
                    transformer.transform(pbTinkarMsg, entityConsumer, stampEntityConsumer);
                }
                // advance next zip entry
                zipEntry = zis.getNextEntry();
            }
        } catch (IllegalStateException e) {
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
