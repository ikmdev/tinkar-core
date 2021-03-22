package org.hl7.tinkar.integration.provider.mvstore;


import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.service.ServiceKeys;
import org.hl7.tinkar.common.service.ServiceProperties;
import org.hl7.tinkar.common.util.time.Stopwatch;
import org.hl7.tinkar.entity.LoadEntitiesFromDTO;
import org.hl7.tinkar.entity.util.EntityCounter;
import org.hl7.tinkar.entity.util.EntityProcessor;
import org.hl7.tinkar.entity.util.EntityRealizer;
import org.hl7.tinkar.integration.TestConstants;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.logging.Logger;

public class MVStoreProviderTest {

    private static Logger LOG = Logger.getLogger(MVStoreProviderTest.class.getName());

    @BeforeSuite
    public void setupSuite() {
        LOG.info("setupSuite: " + this.getClass().getSimpleName());
        LOG.info(ServiceProperties.jvmUuid());
        ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, TestConstants.MVSTORE_ROOT);
        PrimitiveData.selectControllerByName(TestConstants.MV_STORE_NAME);
        PrimitiveData.start();
    }


    @AfterSuite
    public void teardownSuite() {
        LOG.info("teardownSuite");
        PrimitiveData.stop();
    }

    @Test
    public void loadChronologies() throws IOException {
        LoadEntitiesFromDTO loadTink = new LoadEntitiesFromDTO(TestConstants.TINK_TEST_FILE);
        int count = loadTink.call();
        LOG.info("Loaded. " + loadTink.report() + "\n\n");
    }

    @Test(dependsOnMethods = { "loadChronologies" })
    public void count() {
        if (!PrimitiveData.running()) {
            LOG.info("Reloading MVStoreProvider");
            Stopwatch reloadStopwatch = new Stopwatch();
            PrimitiveData.start();
            LOG.info("Reloading in: " + reloadStopwatch.elapsedTime()+ "\n\n");
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
            LOG.info("MVS Closed in: " + closingStopwatch.elapsedTime()+ "\n\n");
        }
        LOG.info("Reloading MVStoreProvider");
        Stopwatch reloadStopwatch = new Stopwatch();
        PrimitiveData.start();
        LOG.info("MVS Reloading in: " + reloadStopwatch.elapsedTime()+ "\n\n");
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
