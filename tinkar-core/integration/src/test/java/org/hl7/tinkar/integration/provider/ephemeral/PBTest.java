package org.hl7.tinkar.integration.provider.ephemeral;

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
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PBTest {

    private static Logger LOG = Logger.getLogger(PBTest.class.getName());
    private final File pbBinaryFile = TestConstants.PB_TEST_FILE;

    @Test(testName = "Read Protocol Buffer Binary File", enabled = false)
    public void readPBFile() throws IOException {
        try (ZipFile zipFile = new ZipFile(pbBinaryFile, StandardCharsets.UTF_8)) {
            ZipEntry exportPBEntry = zipFile.getEntry("export.pb");
            ZipEntry exportPBCountEntry = zipFile.getEntry("count");
            DataInputStream pbStream = new DataInputStream(zipFile.getInputStream(exportPBEntry));
            DataInputStream countStream = new DataInputStream(zipFile.getInputStream(exportPBCountEntry));
            long pbCount = countStream.readLong();

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

                PBTinkarMsg.parseFrom(byteBuffer.array());
                pbMessageCount++;
            }
            LocalDateTime finishTime = LocalDateTime.now();

            LOG.info("Finished PB read at " + finishTime);
            LOG.info("Read " + pbMessageCount + " PBTinkMsg objects");
            LOG.info("Missing PBTinkarMsg: " + (pbCount - pbMessageCount));
            LOG.info("Total PB read time (sec) " + Duration.between(startTime, finishTime).toSeconds());


        } catch (EOFException exception) {
        }

    }

    @Test(testName = "Load Protocol Buffer Binary File", enabled = false)
    public void loadPBFile() throws IOException {

    }

    @Test(testName = "Export to Protocol Buffer Binary File", enabled = false)
    public void exportPBFile() throws IOException {

    }

}
