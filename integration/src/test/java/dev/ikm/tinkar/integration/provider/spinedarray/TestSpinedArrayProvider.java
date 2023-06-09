package dev.ikm.tinkar.integration.provider.spinedarray;

import dev.ikm.tinkar.integration.TestConstants;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.common.util.time.Stopwatch;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.PrimitiveDataSearchResult;
import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.ServiceProperties;
import dev.ikm.tinkar.common.service.ServiceKeys;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromDtoFile;
import dev.ikm.tinkar.entity.util.EntityCounter;
import dev.ikm.tinkar.entity.util.EntityProcessor;
import dev.ikm.tinkar.entity.util.EntityRealizer;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestSpinedArrayProvider {
    private static final Logger LOG = LoggerFactory.getLogger(TestSpinedArrayProvider.class);

    @BeforeAll
    static void setupSuite() {
        LOG.info("Clear caches");
        CachingService.clearAll();
        LOG.info("Setup Suite: " + LOG.getName());
        LOG.info(ServiceProperties.jvmUuid());
        ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, TestConstants.SAP_ROOT);
        FileUtil.recursiveDelete(TestConstants.SAP_ROOT);
        PrimitiveData.selectControllerByName(TestConstants.SA_STORE_OPEN_NAME);
        PrimitiveData.start();
    }

    @AfterAll
    static void teardownSuite() {
        LOG.info("Teardown Suite: " + LOG.getName());
        PrimitiveData.stop();
    }

    @Test
    @Order(1)
    public void loadChronologies() throws IOException {
        File file = TestConstants.TINK_TEST_FILE;
        LoadEntitiesFromDtoFile loadTink = new LoadEntitiesFromDtoFile(file);
        int count = loadTink.compute();
        LOG.info("Loaded. " + loadTink.report());
    }

    @Test
    @Order(2)
    public void count() {
        LOG.info("Starting count. ");
        if (!PrimitiveData.running()) {
            LOG.info("Reloading Spined Array Provider");
            Stopwatch reloadStopwatch = new Stopwatch();
            PrimitiveData.start();
            LOG.info("Reloading in: " + reloadStopwatch.durationString() + "\n\n");
        }
        try {
            PrimitiveDataSearchResult[] results = PrimitiveData.get().search("occupation", 50);
            LOG.info("Search results: \n" + Arrays.toString(results) + "\n\n");
        } catch (Exception e) {
            e.printStackTrace();
        }

        EntityProcessor processor = new EntityCounter();
        PrimitiveData.get().forEach(processor);
        LOG.info("SAP Sequential count: \n" + processor.report() + "\n\n");
        processor = new EntityCounter();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("SAP Parallel count: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        LOG.info("SAP Sequential realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("SAP Parallel realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        LOG.info("SAP Sequential realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("SAP Parallel realization: \n" + processor.report() + "\n\n");
    }

    @Test
    @Order(3)
    public void openAndClose() {
        LOG.info("Starting open and close. ");
        if (PrimitiveData.running()) {
            Stopwatch closingStopwatch = new Stopwatch();
            PrimitiveData.stop();
            closingStopwatch.end();
            LOG.info("SAP Closed in: " + closingStopwatch.durationString() + "\n\n");
        }
        LOG.info("Reloading Spined Array Provider");
        Stopwatch reloadStopwatch = new Stopwatch();
        PrimitiveData.start();
        LOG.info("SAP Reloading in: " + reloadStopwatch.durationString() + "\n\n");
        EntityProcessor processor = new EntityCounter();
        PrimitiveData.get().forEach(processor);
        LOG.info("SAP Sequential count: \n" + processor.report() + "\n\n");
        processor = new EntityCounter();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("SAP Parallel count: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        LOG.info("SAP Sequential realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("SAP Parallel realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        LOG.info("SAP Sequential realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("SAP Parallel realization: \n" + processor.report() + "\n\n");
        PrimitiveData.get().close();
    }

}
