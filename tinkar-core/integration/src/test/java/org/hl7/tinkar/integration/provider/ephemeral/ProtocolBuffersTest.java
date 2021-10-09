package org.hl7.tinkar.integration.provider.ephemeral;

import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.entity.load.LoadEntitiesFromProtocolBuffersFile;
import org.hl7.tinkar.entity.util.EntityCounter;
import org.hl7.tinkar.entity.util.EntityProcessor;
import org.hl7.tinkar.entity.util.EntityRealizer;
import org.hl7.tinkar.integration.TestConstants;
import org.hl7.tinkar.protobuf.PBTinkarMsg;
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
    private final File pbBinaryFile = TestConstants.PB_TEST_FILE;

//    @BeforeSuite
//    public void setupSuite(){
//        LOG.info("setupSuite: " + this.getClass().getSimpleName());
//        LOG.info(ServiceProperties.jvmUuid());
//        PrimitiveData.selectControllerByName(TestConstants.EPHEMERAL_STORE_NAME);
//        PrimitiveData.getController().setDataUriOption(
//                new DataUriOption(TestConstants.PB_TEST_FILE.getName(), TestConstants.PB_TEST_FILE.toURI()));
//        PrimitiveData.start();
//    }
//
//    @AfterSuite
//    public void teardownSuite() {
//        LOG.info("teardownSuite");
//        PrimitiveData.stop();
//    }


    @Test(testName = "Read Protocol Buffer Binary File", enabled = false)
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
                if(pbMessageBreakdown.containsKey(pbTinkarMsg.getValueCase())){
                    pbMessageBreakdown.get(pbTinkarMsg.getValueCase()).incrementAndGet();
                } else{
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

    @Test(testName = "Load Protocol Buffer Binary File", enabled = false)
    public void loadPBFile() throws IOException {
        LoadEntitiesFromProtocolBuffersFile loadPB = new LoadEntitiesFromProtocolBuffersFile(pbBinaryFile);
        int count = loadPB.compute();
//        LOG.info("File Loaded. " + loadPB.report() + "\n\n");
    }

    @Test(testName = "Export to Protocol Buffer Binary File", enabled = false)
    public void exportPBFile() throws IOException {

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
