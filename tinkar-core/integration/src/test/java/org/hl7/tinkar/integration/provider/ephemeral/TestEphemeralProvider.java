package org.hl7.tinkar.integration.provider.ephemeral;

import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.service.ServiceProperties;
import org.hl7.tinkar.entity.load.LoadEntitiesFromDtoFile;
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
class TestEphemeralProvider {
    private static final Logger LOG = LoggerFactory.getLogger(TestEphemeralProvider.class);

    @BeforeAll
    static void setupSuite() {
        LOG.info("Clear caches");
        CachingService.clearAll();
        LOG.info("Setup Ephemeral Suite: " + LOG.getName());
        LOG.info(ServiceProperties.jvmUuid());
        PrimitiveData.selectControllerByName(TestConstants.EPHEMERAL_STORE_NAME);
        /*
         Loaded during loadChronologies() test part... Add back in if you want automatic load during setup.

         PrimitiveData.getController().setDataUriOption(
                new DataUriOption(TestConstants.TINK_TEST_FILE.getName(), TestConstants.TINK_TEST_FILE.toURI()));
         */
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
        LOG.info(count + " entitles loaded from file: " + loadTink.report() + "\n\n");
    }

    @Test
    @Order(2)
    public void count() {
        EntityProcessor processor = new EntityCounter();
        PrimitiveData.get().forEach(processor);
        LOG.info("EPH Sequential count: \n" + processor.report() + "\n\n");
        processor = new EntityCounter();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("EPH Parallel count: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        LOG.info("EPH Sequential realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("EPH Parallel realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        LOG.info("EPH Sequential realization: \n" + processor.report() + "\n\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        LOG.info("EPH Parallel realization: \n" + processor.report() + "\n\n");
    }
}
