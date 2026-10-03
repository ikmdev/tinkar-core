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
package dev.ikm.tinkar.integration.provider.spinedarray;

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.entity.export.ExportEntitiesToProtobufFile;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.fixtures.ForkedJvm;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.fixtures.TestTags;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starter data through a store's whole life and back: imported and closed; opened again
 * and exported to protobuf; the export imported into a fresh store. Each store lifetime is
 * a stage in its own JVM ({@link ForkedJvm}), because a JVM starts the store services
 * once. The times each stage records are the import and export baseline for this data.
 */
@Tag(TestTags.STAGED)
class ProtobufRoundTripIT {

    private static final Logger LOG = LoggerFactory.getLogger(ProtobufRoundTripIT.class);

    private static final File WORK = TestConstants.createFilePathInTargetFromClassName.apply(ProtobufRoundTripIT.class);

    private static final String SOURCE_STORE = "source.store";
    private static final String RESTORED_STORE = "restored.store";
    private static final String STARTER_DATA = "starter.data";
    private static final String EXPORT_FILE = "export.file";

    @Test
    void starterDataSurvivesCloseReopenAndAProtobufRoundTrip() {
        FileUtil.recursiveDelete(WORK);
        assertTrue(WORK.mkdirs(), "Could not create " + WORK);
        Properties in = new Properties();
        in.setProperty(SOURCE_STORE, new File(WORK, "source").getPath());
        in.setProperty(RESTORED_STORE, new File(WORK, "restored").getPath());
        in.setProperty(STARTER_DATA, TestConstants.PB_STARTER_DATA_REASONED.getPath());
        in.setProperty(EXPORT_FILE, new File(WORK, "export.pb.zip").getPath());

        Properties imported = ForkedJvm.run(ImportStarterData.class, in);
        Properties exported = ForkedJvm.run(ReopenAndExport.class, imported);
        Properties restored = ForkedJvm.run(ImportTheExport.class, exported);

        long loaded = count(restored, "loaded.count");
        long inSourceStore = count(restored, "source.store.count");
        long afterReopen = count(restored, "reopened.store.count");
        long exportedCount = count(restored, "exported.count");
        long reimported = count(restored, "reimported.count");
        long inRestoredStore = count(restored, "restored.store.count");

        assertTrue(inSourceStore > 0, "The source store is empty after the import");
        // The starter-data file holds some components more than once; the store holds each once.
        assertTrue(inSourceStore <= loaded, "The store holds " + inSourceStore + " entities from " + loaded + " loaded");
        assertEquals(inSourceStore, afterReopen, "Entities in the source store after it was closed and opened again");
        assertEquals(afterReopen, exportedCount, "Entities exported from the reopened store");
        assertEquals(exportedCount, reimported, "Entities read from the export file");
        assertEquals(exportedCount, inRestoredStore, "Entities in the fresh store after importing the export");

        LOG.info("Starter data baseline: {} entities; load {} ms, export {} ms ({} bytes), import of the export {} ms",
                exportedCount, restored.getProperty("load.millis"), restored.getProperty("export.millis"),
                restored.getProperty("export.bytes"), restored.getProperty("reimport.millis"));
    }

    private static long count(Properties properties, String key) {
        return Long.parseLong(properties.getProperty(key));
    }

    private static long entitiesInStore() {
        AtomicLong count = new AtomicLong();
        PrimitiveData.get().forEach((bytes, nid) -> count.incrementAndGet());
        return count.get();
    }

    /** Stage 1: a new store, the starter data loaded into it, and the store closed. */
    static class ImportStarterData implements ForkedJvm.Stage {
        @Override
        public void run(Properties in, Properties out) {
            out.putAll(in);
            TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, new File(in.getProperty(SOURCE_STORE)));
            try {
                long start = System.currentTimeMillis();
                long loaded = new LoadEntitiesFromProtobufFile(new File(in.getProperty(STARTER_DATA))).compute().getTotalCount();
                out.setProperty("load.millis", Long.toString(System.currentTimeMillis() - start));
                out.setProperty("loaded.count", Long.toString(loaded));
                out.setProperty("source.store.count", Long.toString(entitiesInStore()));
            } finally {
                TestHelper.stopDatabase();
            }
        }
    }

    /** Stage 2: the store stage 1 closed, opened again and exported. */
    static class ReopenAndExport implements ForkedJvm.Stage {
        @Override
        public void run(Properties in, Properties out) throws Exception {
            out.putAll(in);
            TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, new File(in.getProperty(SOURCE_STORE)));
            try {
                out.setProperty("reopened.store.count", Long.toString(entitiesInStore()));
                File exportFile = new File(in.getProperty(EXPORT_FILE));
                long start = System.currentTimeMillis();
                long exported = new ExportEntitiesToProtobufFile(exportFile).compute().getTotalCount();
                out.setProperty("export.millis", Long.toString(System.currentTimeMillis() - start));
                out.setProperty("exported.count", Long.toString(exported));
                out.setProperty("export.bytes", Long.toString(exportFile.length()));
            } finally {
                TestHelper.stopDatabase();
            }
        }
    }

    /** Stage 3: a fresh store, and the export from stage 2 imported into it. */
    static class ImportTheExport implements ForkedJvm.Stage {
        @Override
        public void run(Properties in, Properties out) {
            out.putAll(in);
            TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, new File(in.getProperty(RESTORED_STORE)));
            try {
                long start = System.currentTimeMillis();
                long reimported = new LoadEntitiesFromProtobufFile(new File(in.getProperty(EXPORT_FILE))).compute().getTotalCount();
                out.setProperty("reimport.millis", Long.toString(System.currentTimeMillis() - start));
                out.setProperty("reimported.count", Long.toString(reimported));
                out.setProperty("restored.store.count", Long.toString(entitiesInStore()));
            } finally {
                TestHelper.stopDatabase();
            }
        }
    }
}
