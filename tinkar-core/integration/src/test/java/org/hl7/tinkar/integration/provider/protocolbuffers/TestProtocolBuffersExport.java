package org.hl7.tinkar.integration.provider.protocolbuffers;

import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.service.ServiceProperties;
import org.hl7.tinkar.entity.export.ExportEntitiesToProtocolBuffers;
import org.hl7.tinkar.entity.load.LoadEntitiesFromDtoFile;
import org.hl7.tinkar.integration.TestConstants;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestProtocolBuffersExport {

    private static final Logger LOG = LoggerFactory.getLogger(TestProtocolBuffersExport.class);

    @BeforeAll
    static void setupSuite() {
        LOG.info("Setup: " + LOG.getName());
        LOG.info(ServiceProperties.jvmUuid());
        PrimitiveData.selectControllerByName(TestConstants.EPHEMERAL_STORE_NAME);
        /*
         Loaded during loadEntities() test part... Add back in if you want automatic load during setup.

         PrimitiveData.getController().setDataUriOption(
                new DataUriOption(TestConstants.TINK_TEST_FILE.getName(), TestConstants.TINK_TEST_FILE.toURI()));
         */
        PrimitiveData.start();
    }

    @AfterAll
    static void teardownSuite() {
        LOG.info("Teardown: " + LOG.getName());
        PrimitiveData.stop();
    }

    @BeforeEach
    private void beforeTest() {
        LOG.info("Before Test: " + this.getClass().getSimpleName());
    }

    @AfterEach
    private void afterTest() {
        LOG.info("After Test: " + this.getClass().getSimpleName());
    }

    @Test
    @Order(1)
    private void loadEntities() throws IOException {
        File file = TestConstants.PB_TEST_FILE;
        LoadEntitiesFromDtoFile loadTink = new LoadEntitiesFromDtoFile(file);
        int count = loadTink.compute();
        LOG.info(count + "entities loaded from file. " + loadTink.report() + "\n\n");
    }

    @Test
    private void exportEntities() throws IOException {
        ExportEntitiesToProtocolBuffers exportEntities =
                new ExportEntitiesToProtocolBuffers(Path.of(TestConstants.PB_EXPORT_TEST_FILE.toURI()));
        exportEntities.compute();
    }
}
