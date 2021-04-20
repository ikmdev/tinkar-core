package org.hl7.tinkar.integration.provider.mvstore;


import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.service.ServiceKeys;
import org.hl7.tinkar.common.service.ServiceProperties;
import org.hl7.tinkar.common.util.time.Stopwatch;
import org.hl7.tinkar.entity.load.LoadEntitiesFromDtoFile;
import org.hl7.tinkar.entity.util.EntityCounter;
import org.hl7.tinkar.entity.util.EntityProcessor;
import org.hl7.tinkar.entity.util.EntityRealizer;
import org.hl7.tinkar.integration.TestConstants;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.logging.Logger;

/**
 Concerned that MVStore may not be "bullet proof" based on this exception. Will watch, and save for posterity.
 *
 Mar 28, 2021 9:41:28 AM org.hl7.tinkar.integration.provider.mvstore.MVStoreProviderTest teardownSuite
 INFO: teardownSuite

 java.lang.IllegalStateException: Chunk 77 not found [1.4.200/9]

 at org.h2.mvstore.DataUtils.newIllegalStateException(DataUtils.java:950)
 at org.h2.mvstore.MVStore.getChunk(MVStore.java:1230)
 at org.h2.mvstore.MVStore.readBufferForPage(MVStore.java:1214)
 at org.h2.mvstore.MVStore.readPage(MVStore.java:2209)
 at org.h2.mvstore.MVMap.readPage(MVMap.java:672)
 at org.h2.mvstore.Page$NonLeaf.getChildPage(Page.java:1043)
 at org.h2.mvstore.Cursor.hasNext(Cursor.java:53)
 at org.h2.mvstore.MVMap$2$1.hasNext(MVMap.java:802)
 at java.base/java.util.concurrent.ConcurrentMap.forEach(ConcurrentMap.java:112)
 at org.hl7.tinkar.provider.mvstore.MVStoreProvider.forEachSemanticNidForComponentOfType(MVStoreProvider.java:98)
 */
public class MVStoreProviderTest {

    private static Logger LOG = Logger.getLogger(MVStoreProviderTest.class.getName());

    @BeforeSuite
    public void setupSuite() {
        LOG.info("setupSuite: " + this.getClass().getSimpleName());
        LOG.info(ServiceProperties.jvmUuid());
        ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, TestConstants.MVSTORE_ROOT);
        PrimitiveData.selectControllerByName(TestConstants.MV_STORE_OPEN_NAME);
        PrimitiveData.start();
    }


    @AfterSuite
    public void teardownSuite() {
        LOG.info("teardownSuite");
        PrimitiveData.stop();
    }

    @Test
    public void loadChronologies() throws IOException {
        LoadEntitiesFromDtoFile loadTink = new LoadEntitiesFromDtoFile(TestConstants.TINK_TEST_FILE);
        int count = loadTink.compute();
        LOG.info("Loaded. " + loadTink.report() + "\n\n");
    }

    @Test(dependsOnMethods = { "loadChronologies" })
    public void count() {
        if (!PrimitiveData.running()) {
            LOG.info("Reloading MVStoreProvider");
            Stopwatch reloadStopwatch = new Stopwatch();
            PrimitiveData.start();
            LOG.info("Reloading in: " + reloadStopwatch.durationString()+ "\n\n");
        }
        EntityProcessor processor = new EntityCounter();
        PrimitiveData.get().forEach(processor);
        LOG.info("MVS Sequential count: \n" + processor.report() + "\n\n");
        processor = new EntityCounter();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("MVS Parallel count: \n" + processor.report()+ "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        LOG.info("MVS Sequential realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("MVS Parallel realization: \n" + processor.report()+ "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        LOG.info("MVS Sequential realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("MVS Parallel realization: \n" + processor.report()+ "\n\n");
    }

    @Test(dependsOnMethods = { "loadChronologies" })
    public void openAndClose() {
        if (PrimitiveData.running()) {
            Stopwatch closingStopwatch = new Stopwatch();
            PrimitiveData.stop();
            closingStopwatch.end();
            LOG.info("MVS Closed in: " + closingStopwatch.durationString()+ "\n\n");
        }
        LOG.info("Reloading MVStoreProvider");
        Stopwatch reloadStopwatch = new Stopwatch();
        PrimitiveData.start();
        LOG.info("MVS Reloading in: " + reloadStopwatch.durationString()+ "\n\n");
        EntityProcessor processor = new EntityCounter();
        PrimitiveData.get().forEach(processor);
        LOG.info("MVS Sequential count: \n" + processor.report() + "\n\n");
        processor = new EntityCounter();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("MVS Parallel count: \n" + processor.report()+ "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        LOG.info("MVS Sequential realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("MVS Parallel realization: \n" + processor.report()+ "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        LOG.info("MVS Sequential realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("MVS Parallel realization: \n" + processor.report()+ "\n\n");
     }
}
