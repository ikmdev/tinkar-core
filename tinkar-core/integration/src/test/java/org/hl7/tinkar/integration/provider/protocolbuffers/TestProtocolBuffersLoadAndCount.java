package org.hl7.tinkar.integration.provider.protocolbuffers;

import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.service.ServiceProperties;
import org.hl7.tinkar.entity.load.LoadEntitiesFromProtocolBuffersFile;
import org.hl7.tinkar.entity.util.EntityCounter;
import org.hl7.tinkar.entity.util.EntityProcessor;
import org.hl7.tinkar.entity.util.EntityRealizer;
import org.hl7.tinkar.integration.TestConstants;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Disabled
class TestProtocolBuffersLoadAndCount {

    private static final Logger LOG = LoggerFactory.getLogger(TestProtocolBuffersLoadAndCount.class);

    @BeforeAll
    static void setupSuite() {
        LOG.info("Clear caches");
        CachingService.clearAll();
        LOG.info("Setup: " + LOG.getName());
        LOG.info(ServiceProperties.jvmUuid());
        PrimitiveData.selectControllerByName(TestConstants.EPHEMERAL_STORE_NAME);
        /*
         Loaded during loadProtocolBuffersFile() test part... Add back in if you want automatic load during setup.

         PrimitiveData.getController().setDataUriOption(
                new DataUriOption(TestConstants.PB_TEST_FILE.getName(), TestConstants.PB_TEST_FILE.toURI()));
         */
        PrimitiveData.start();
    }

    @AfterAll
    static void teardownSuite() {
        LOG.info("Teardown: " + LOG.getName());
        PrimitiveData.stop();
    }

    @BeforeEach
    public void beforeTest() {
        LOG.info("Before Test: " + this.getClass().getSimpleName());
    }

    @AfterEach
    public void afterTest() {
        LOG.info("After Test: " + this.getClass().getSimpleName());
    }

    @Test
    @Order(1)
    public void loadProtocolBuffersFile() throws IOException {
        LOG.info(ServiceProperties.jvmUuid());
        File file = TestConstants.PB_TEST_FILE;
        LoadEntitiesFromProtocolBuffersFile loadTink = new LoadEntitiesFromProtocolBuffersFile(file);
        int count = loadTink.compute();
        LOG.info(count + " entitles loaded from file \n\n");
    }

    @Test
    public void countProtocolBufferEntities() {
        EntityProcessor processor = new EntityCounter();
        PrimitiveData.get().forEach(processor);
        LOG.info("EPH PB Sequential count: \n" + processor.report() + "\n\n");
        processor = new EntityCounter();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("EPH PB Parallel count: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        LOG.info("EPH PB Sequential realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("EPH PB Parallel realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        LOG.info("EPH PB Sequential realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("EPH PB Parallel realization: \n" + processor.report() + "\n\n");
    }
}
