package org.hl7.tinkar.integration.provider.ephemeral;

import org.hl7.tinkar.common.service.DataUriOption;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.service.ServiceProperties;
import org.hl7.tinkar.entity.load.LoadEntitiesFromDtoFile;
import org.hl7.tinkar.entity.load.LoadEntitiesFromProtocolBuffersFile;
import org.hl7.tinkar.entity.util.EntityCounter;
import org.hl7.tinkar.entity.util.EntityProcessor;
import org.hl7.tinkar.entity.util.EntityRealizer;
import org.hl7.tinkar.integration.TestConstants;
import org.hl7.tinkar.protobuf.PBTinkarMsg;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ProtocolBuffersTest {

    private static Logger LOG = Logger.getLogger(ProtocolBuffersTest.class.getName());

//    @AfterSuite()
//    public void teardownSuite() {
//        LOG.info("teardownSuite");
//        PrimitiveData.stop();
//    }

    @Test(testName = "Load Protocol Buffer Binary File", enabled = false)
    public void loadPBFile() throws IOException {
        LOG.info(ServiceProperties.jvmUuid());
        PrimitiveData.selectControllerByName(TestConstants.EPHEMERAL_STORE_NAME);
        PrimitiveData.getController().setDataUriOption(
                new DataUriOption(TestConstants.PB_TEST_FILE.getName(), TestConstants.PB_TEST_FILE.toURI()));
        PrimitiveData.start();
    }

    @Test(dependsOnMethods = {"loadPBFile"}, enabled = false)
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
