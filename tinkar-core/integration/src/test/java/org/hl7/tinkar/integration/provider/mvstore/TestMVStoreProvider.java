package org.hl7.tinkar.integration.provider.mvstore;


import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.service.ServiceKeys;
import org.hl7.tinkar.common.service.ServiceProperties;
import org.hl7.tinkar.common.util.time.Stopwatch;
import org.hl7.tinkar.entity.load.LoadEntitiesFromDtoFile;
import org.hl7.tinkar.entity.util.EntityCounter;
import org.hl7.tinkar.entity.util.EntityProcessor;
import org.hl7.tinkar.entity.util.EntityRealizer;
import org.hl7.tinkar.integration.TestConstants;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Concerned that MVStore may not be "bullet proof" based on this exception. Will watch, and save for posterity.
 * <p>
 * Mar 28, 2021 9:41:28 AM org.hl7.tinkar.integration.provider.mvstore.MVStoreProviderTest teardownSuite
 * INFO: teardownSuite
 * <p>
 * java.lang.IllegalStateException: Chunk 77 not found [1.4.200/9]
 * <p>
 * at org.h2.mvstore.DataUtils.newIllegalStateException(DataUtils.java:950)
 * at org.h2.mvstore.MVStore.getChunk(MVStore.java:1230)
 * at org.h2.mvstore.MVStore.readBufferForPage(MVStore.java:1214)
 * at org.h2.mvstore.MVStore.readPage(MVStore.java:2209)
 * at org.h2.mvstore.MVMap.readPage(MVMap.java:672)
 * at org.h2.mvstore.Page$NonLeaf.getChildPage(Page.java:1043)
 * at org.h2.mvstore.Cursor.hasNext(Cursor.java:53)
 * at org.h2.mvstore.MVMap$2$1.hasNext(MVMap.java:802)
 * at java.base/java.util.concurrent.ConcurrentMap.forEach(ConcurrentMap.java:112)
 * at org.hl7.tinkar.provider.mvstore.MVStoreProvider.forEachSemanticNidForComponentOfType(MVStoreProvider.java:98)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestMVStoreProvider {

    private static final Logger LOG = LoggerFactory.getLogger(TestMVStoreProvider.class);

    @BeforeAll
    static void setupSuite() {
        LOG.info("Clear caches");
        CachingService.clearAll();
        LOG.info("Setup suite: " + LOG.getName());
        LOG.info(ServiceProperties.jvmUuid());
        ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, TestConstants.MVSTORE_ROOT);
        PrimitiveData.selectControllerByName(TestConstants.MV_STORE_OPEN_NAME);
        PrimitiveData.start();
    }


    @AfterAll
    static void teardownSuite() {
        LOG.info("Teardown suite: " + LOG.getName());
        PrimitiveData.stop();
    }

    @Test
    @Order(1)
    public void loadChronologies() throws IOException {
        LoadEntitiesFromDtoFile loadTink = new LoadEntitiesFromDtoFile(TestConstants.TINK_TEST_FILE);
        int count = loadTink.compute();
        LOG.info("Loaded. " + loadTink.report() + "\n\n");
    }

    @Test
    public void count() {
        if (!PrimitiveData.running()) {
            LOG.info("Reloading MVStoreProvider");
            Stopwatch reloadStopwatch = new Stopwatch();
            PrimitiveData.start();
            LOG.info("Reloading in: " + reloadStopwatch.durationString() + "\n\n");
        }
        EntityProcessor processor = new EntityCounter();
        PrimitiveData.get().forEach(processor);
        LOG.info("MVS Sequential count: \n" + processor.report() + "\n\n");
        processor = new EntityCounter();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("MVS Parallel count: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        LOG.info("MVS Sequential realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("MVS Parallel realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        LOG.info("MVS Sequential realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("MVS Parallel realization: \n" + processor.report() + "\n\n");
    }

    @Test
    public void openAndClose() {
        if (PrimitiveData.running()) {
            Stopwatch closingStopwatch = new Stopwatch();
            PrimitiveData.stop();
            closingStopwatch.end();
            LOG.info("MVS Closed in: " + closingStopwatch.durationString() + "\n\n");
        }
        LOG.info("Reloading MVStoreProvider");
        Stopwatch reloadStopwatch = new Stopwatch();
        PrimitiveData.start();
        LOG.info("MVS Reloading in: " + reloadStopwatch.durationString() + "\n\n");
        EntityProcessor processor = new EntityCounter();
        PrimitiveData.get().forEach(processor);
        LOG.info("MVS Sequential count: \n" + processor.report() + "\n\n");
        processor = new EntityCounter();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("MVS Parallel count: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        LOG.info("MVS Sequential realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("MVS Parallel realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        LOG.info("MVS Sequential realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("MVS Parallel realization: \n" + processor.report() + "\n\n");
    }
}
