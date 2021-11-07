package org.hl7.tinkar.integration.provider.ephemeral;

import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.service.ServiceProperties;
import org.hl7.tinkar.entity.load.LoadEntitiesFromProtocolBuffersFile;
import org.hl7.tinkar.entity.util.EntityCounter;
import org.hl7.tinkar.entity.util.EntityProcessor;
import org.hl7.tinkar.entity.util.EntityRealizer;
import org.hl7.tinkar.integration.TestConstants;
import org.hl7.tinkar.protobuf.PBTinkarMsg;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestProtocolBuffers {

    private static final Logger LOG = LoggerFactory.getLogger(TestProtocolBuffers.class);
    private final File pbBinaryFile = TestConstants.PB_TEST_FILE;

    @BeforeAll
    static void setupSuite() {
        LOG.info("SetupPB Suite: " + LOG.getName());
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
        LOG.info("Teardown PB Suite");
        PrimitiveData.stop();
    }

    @Test
    @Order(1)
    public void readPBFile() throws IOException {
        try (ZipFile zipFile = new ZipFile(pbBinaryFile, StandardCharsets.UTF_8)) {
            ZipEntry exportPBEntry = zipFile.getEntry("export.pb");
            ZipEntry exportPBCountEntry = zipFile.getEntry("count");
            DataInputStream pbStream = new DataInputStream(zipFile.getInputStream(exportPBEntry));
            DataInputStream countStream = new DataInputStream(zipFile.getInputStream(exportPBCountEntry));
            long pbCount = countStream.readLong();
            Map<PBTinkarMsg.ValueCase, AtomicInteger> pbMessageBreakdown = new HashMap<>();

            ByteBuffer byteBuffer;
            LocalDateTime startTime = LocalDateTime.now();
            int pbMessageLength;
            int bytesReadCount;
            int pbMessageCount = 0;

            LOG.info("Started PB read at " + startTime);
            while (pbStream.available() > 0) {
                bytesReadCount = 0;
                pbMessageLength = pbStream.readInt();

                if (pbMessageLength == -1) {
                    break;
                }
                byteBuffer = ByteBuffer.allocate(pbMessageLength);

                while (bytesReadCount < pbMessageLength) {
                    int sourceIndex = bytesReadCount;
                    byte[] bytesRead;

                    if (bytesReadCount == 0) {
                        bytesRead = new byte[pbMessageLength];
                        bytesReadCount = pbStream.read(bytesRead, 0, pbMessageLength);
                    } else {
                        int lengthLeftToRead = pbMessageLength - bytesReadCount;
                        bytesRead = new byte[lengthLeftToRead];
                        bytesReadCount += pbStream.read(bytesRead, 0, lengthLeftToRead);
                    }

                    byteBuffer.put(sourceIndex, bytesRead);
                }

                PBTinkarMsg pbTinkarMsg = PBTinkarMsg.parseFrom(byteBuffer.array());
                if (pbMessageBreakdown.containsKey(pbTinkarMsg.getValueCase())) {
                    pbMessageBreakdown.get(pbTinkarMsg.getValueCase()).incrementAndGet();
                } else {
                    pbMessageBreakdown.put(pbTinkarMsg.getValueCase(), new AtomicInteger(0));
                }
                pbMessageCount++;
            }
            LocalDateTime finishTime = LocalDateTime.now();

            LOG.info("Finished PB read at " + finishTime + "\n"
                    + "Read " + pbMessageCount + " PBTinkMsg objects" + "\n"
                    + "Missing PBTinkarMsg: " + (pbCount - pbMessageCount) + "\n"
                    + "Total PB read time (sec) " + Duration.between(startTime, finishTime).toSeconds());

        } catch (EOFException exception) {
        }
    }

    @Test
    @Order(2)
    public void loadPBFile() throws IOException {
        LoadEntitiesFromProtocolBuffersFile loadPB = new LoadEntitiesFromProtocolBuffersFile(pbBinaryFile);
        int count = loadPB.compute();
//        LOG.info("File Loaded. " + loadPB.report() + "\n\n");
    }

    @Test
    @Order(2)
    public void count() {
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

    @Test
    @Disabled
    public void exportPBFile() throws IOException {

    }

}
