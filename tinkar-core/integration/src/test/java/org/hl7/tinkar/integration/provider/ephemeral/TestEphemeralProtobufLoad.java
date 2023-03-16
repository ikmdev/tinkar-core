package org.hl7.tinkar.integration.provider.ephemeral;

import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.service.ServiceProperties;
import org.hl7.tinkar.entity.export.ExportEntitiesToProtobufFile;
import org.hl7.tinkar.entity.load.LoadEntitiesFromDtoFile;
import org.hl7.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import org.hl7.tinkar.entity.util.EntityCounter;
import org.hl7.tinkar.entity.util.EntityProcessor;
import org.hl7.tinkar.entity.util.EntityRealizer;
import org.hl7.tinkar.integration.TestConstants;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

@Disabled
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestEphemeralProtobufLoad {

    private static final Logger LOG = LoggerFactory.getLogger(TestEphemeralProtobufLoad.class);

    @BeforeAll
    public void setupSuite() {
        LOG.info("Clear caches");
        CachingService.clearAll();
        LOG.info("Setup Ephemeral Protobuf Suite: " + LOG.getName());
        LOG.info(ServiceProperties.jvmUuid());
        PrimitiveData.selectControllerByName(TestConstants.EPHEMERAL_STORE_NAME);
        PrimitiveData.start();
    }

    @AfterAll
    public void teardownSuite() {
        LOG.info("Teardown Suite: " + LOG.getName());
        PrimitiveData.stop();
    }

    @Test
    @Order(1)
    @Disabled
    public void loadProtobufFile() throws IOException {
        File file = TestConstants.PB_TEST_FILE;
        LoadEntitiesFromProtobufFile loadProtobuf = new LoadEntitiesFromProtobufFile(file);
        int count = loadProtobuf.compute();
        LOG.info(count + " entitles loaded from file: " + loadProtobuf.report() + "\n\n");
    }

    @Test
    @Order(2)
    @Disabled
    public void count(){
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

    @Test
    @Order(2)
    public void loadTinkFile() throws IOException {
        File file = TestConstants.TINK_TEST_FILE;
        LoadEntitiesFromDtoFile loadDTO = new LoadEntitiesFromDtoFile(file);
        int count = loadDTO.compute();
        LOG.info(count + " entitles loaded from file: " + loadDTO.report() + "\n\n");
    }

    @Test
    @Order(3)
    public void exportEntitiesToProtobuf() throws IOException {
        ExportEntitiesToProtobufFile exportEntitiesToProtobufFile =
                new ExportEntitiesToProtobufFile(TestConstants.PB_EXPORT_TEST_FILE);
        exportEntitiesToProtobufFile.compute();
    }
}
