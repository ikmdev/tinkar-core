package dev.ikm.tinkar.integration.provider.ephemeral;

import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceProperties;
import dev.ikm.tinkar.entity.export.ExportEntitiesToProtobufFile;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromDtoFile;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.integration.TestConstants;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestProtobufRoundTrip {

    private static final Logger LOG = LoggerFactory.getLogger(TestProtobufRoundTrip.class);

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

        PrimitiveData.stop();
    }

    @Test
    @Disabled
    public void roundTripTest() throws IOException {
        // Given initial DTO data
        File fileDTO = TestConstants.TINK_TEST_FILE;
        LoadEntitiesFromDtoFile loadDTO = new LoadEntitiesFromDtoFile(fileDTO);

        // When we import DTO into entities
        int expectedEntityCount = loadDTO.compute();

        // When we export DTO data to protobuf
        File fileProtobuf = TestConstants.PB_TEST_FILE;
        ExportEntitiesToProtobufFile exportEntitiesToProtobufFile = new ExportEntitiesToProtobufFile(fileProtobuf);
        int actualProtobufExportCount = exportEntitiesToProtobufFile.compute();

        // When we import protobuf data into entities
        LoadEntitiesFromProtobufFile loadEntitiesFromProtobufFile = new LoadEntitiesFromProtobufFile(fileProtobuf);
        int actualProtobufImportCount = loadEntitiesFromProtobufFile.compute();

        // Then all imported and exported entities counts should match
        boolean boolEntityCount = expectedEntityCount == actualProtobufExportCount && expectedEntityCount == actualProtobufImportCount;
        assertTrue(expectedEntityCount > 0, "Imported DTO count should be greater than zero.");
        assertTrue(actualProtobufImportCount > 0, "Exported Protobuf count should be greater than zero.");
        assertTrue(actualProtobufExportCount > 0, "Imported Protobuf count should be greater than zero.");
        assertTrue(boolEntityCount, "Counts in round-trip do not match.");
    }
}
