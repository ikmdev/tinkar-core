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

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityCountSummary;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.transform.TinkarSchemaToEntityTransformer;
import dev.ikm.tinkar.schema.TinkarMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The purpose of this class is to successfully load all Protobuf messages from a protobuf file and transform them into entities.
 */
public class LoadEntitiesFromProtobufFile extends TrackingCallable<EntityCountSummary> {

    protected static final Logger LOG = LoggerFactory.getLogger(LoadEntitiesFromProtobufFile.class.getName());

    private final TinkarSchemaToEntityTransformer entityTransformer =
            TinkarSchemaToEntityTransformer.getInstance();
    private static final String MANIFEST_RELPATH = "META-INF/MANIFEST.MF";
    private final File importFile;
    private final AtomicLong importCount = new AtomicLong();
    private final AtomicLong importConceptCount = new AtomicLong();
    private final AtomicLong importSemanticCount = new AtomicLong();
    private final AtomicLong importPatternCount = new AtomicLong();
    private final AtomicLong importStampCount = new AtomicLong();

    public LoadEntitiesFromProtobufFile(File importFile) {
        super(false, true);
        this.importFile = importFile;
        LOG.info("Loading entities from: " + importFile.getAbsolutePath());
    }

    /**
     * This purpose of this method is to process all protobuf messages and call the entity transformer.
     * @return EntityCountSummary count of all entities/protobuf messages loaded/exported
     */
    public EntityCountSummary compute() {
        initCounts();

        updateTitle("Import Protobuf Data from " + importFile.getName());
        updateProgress(-1, 1);
        updateMessage("Analyzing Import File...");

        // Analyze Manifest and update tracking callable
        long expectedImports = analyzeManifest();
        LOG.info(expectedImports + " Entities to process...");
        updateProgress(0, expectedImports);
        updateMessage("Importing Protobuf Data...");

        // Process Protobuf Entry
        EntityService.get().beginLoadPhase();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(importFile))) {
            // Consumer to be run for each transformed Entity
            Consumer<Entity<? extends  EntityVersion>> entityConsumer = entity -> {
                    EntityService.get().putEntityQuietly(entity);
                    updateCounts(entity);
            };

            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (zipEntry.getName().equals(MANIFEST_RELPATH)) {
                    continue;
                }
                while (zis.available() > 0) {
                    // zis.available returns 1 until AFTER EOF has been reached
                    // Protobuf parser reads the data UP TO EOF, so zis.available
                    // will return 1 when the only thing left is EOF
                    // pbTinkarMsg should be null for this last entry
                    TinkarMsg pbTinkarMsg = TinkarMsg.parseDelimitedFrom(zis);
                    if (pbTinkarMsg == null) {
                        continue;
                    }

                    // TODO: Remove need for Stamp Consumer since Stamps are now consumed by Entity Consumer
                    entityTransformer.transform(pbTinkarMsg, entityConsumer, (stampEntity) -> {});

                    // Batch progress updates to prevent hanging the UI thread
                    if (importCount.incrementAndGet() % 1000 == 0) {
                        updateProgress(importCount.get(), expectedImports);
                    }
                }
            }
        } catch (IOException e) {
            updateTitle("Import Protobuf Data from " + importFile.getName() + " with error(s)");
            throw new RuntimeException(e);
        } finally {
            try {
                EntityService.get().endLoadPhase();
            } catch (Exception e) {
                LOG.error("Encountered exception {}", e.getMessage());
            }
            updateMessage("In " + durationString());
            updateProgress(1,1);
        }

        if (importCount.get() != expectedImports) {
            throw new IllegalStateException("ERROR: Expected " + expectedImports + " imported Entities, but imported " + importCount.get());
        }
        return summarize();
    }

    private void updateCounts(Entity entity){
        switch (entity) {
            case ConceptEntity ignored -> importConceptCount.incrementAndGet();
            case SemanticEntity ignored -> importSemanticCount.incrementAndGet();
            case PatternEntity ignored -> importPatternCount.incrementAndGet();
            case StampEntity ignored -> importStampCount.incrementAndGet();
            default -> throw new IllegalStateException("Unexpected value: " + entity);
        }
    }

    public EntityCountSummary summarize() {
        LOG.info("Imported: " + importCount.get() + " entities in: " + durationString());
        return new EntityCountSummary(
                importConceptCount.get(),
                importSemanticCount.get(),
                importPatternCount.get(),
                importStampCount.get()
        );
    }

    protected void initCounts() {
        importCount.set(0);
        importConceptCount.set(0);
        importSemanticCount.set(0);
        importPatternCount.set(0);
        importStampCount.set(0);
    }

    private long analyzeManifest() {
        long expectedImports = -1;
        Map<PublicId, String> manifestEntryData = new HashMap<>();

        // Read Manifest from Zip
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(importFile))) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (zipEntry.getName().equals(MANIFEST_RELPATH)) {
                    Manifest manifest = new Manifest(zis);
                    expectedImports = Long.parseLong(manifest.getMainAttributes().getValue("Total-Count"));
                    // Get Dependent Module / Author PublicIds and Descriptions
                    manifest.getEntries().keySet().forEach((publicIdKey) -> {
                        PublicId publicId = PublicIds.of(publicIdKey.split(","));
                        String description = manifest.getEntries().get(publicIdKey).getValue("Description");
                        manifestEntryData.put(publicId, description);
                    });
                }
                zis.closeEntry();
                LOG.info(zipEntry.getName() + " zip entry size: " + zipEntry.getSize());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        manifestEntryData.keySet().forEach((publicId) -> {
            if (!PrimitiveData.get().hasPublicId(publicId)) {
                LOG.warn("Dependent Module or Author is not Present -" +
                        " PublicId: " + publicId.idString() +
                        " Description: " + manifestEntryData.get(publicId));
            }
        });

        return expectedImports;
    }
}
