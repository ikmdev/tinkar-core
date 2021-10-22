package org.hl7.tinkar.integration.provider.protocolbuffers;

import org.hl7.tinkar.common.service.DataUriOption;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.service.ServiceProperties;
import org.hl7.tinkar.entity.export.ExportEntitiesToProtocolBuffers;
import org.hl7.tinkar.integration.TestConstants;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

public class ProtocolBuffersExportTest {

    private static Logger LOG = Logger.getLogger(ProtocolBuffersExportTest.class.getName());

    @BeforeTest
    private void beforeTest(){
        LOG.info("Before Test: " + this.getClass().getSimpleName());
    }

    @AfterTest
    private void afterTest(){
        LOG.info("After Test: " + this.getClass().getSimpleName());
        PrimitiveData.start();
    }

    @Test
    private void loadEntities(){
        LOG.info(ServiceProperties.jvmUuid());
        PrimitiveData.selectControllerByName(TestConstants.EPHEMERAL_STORE_NAME);
        PrimitiveData.getController().setDataUriOption(
                new DataUriOption(TestConstants.PB_TEST_FILE.getName(), TestConstants.PB_TEST_FILE.toURI()));
        PrimitiveData.start();
    }

    @Test(dependsOnMethods = {"loadEntities"})
    private void exportEntities() throws IOException {
        ExportEntitiesToProtocolBuffers exportEntities =
                new ExportEntitiesToProtocolBuffers(Path.of(TestConstants.PB_EXPORT_TEST_FILE.toURI()));
        exportEntities.compute();
    }
}
