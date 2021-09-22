package org.hl7.tinkar.integration.provider.ephemeral;

import org.hl7.tinkar.integration.TestConstants;
import org.hl7.tinkar.protobuf.PBTinkarMsg;
import org.testng.annotations.Test;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PBTest {

    private static Logger LOG = Logger.getLogger(PBTest.class.getName());
    private final File pbBinaryFile = TestConstants.PB_TEST_FILE;

    @Test(testName = "Read Protocol Buffer Binary File", enabled = true)
    public void readPBFile() throws IOException {
        try (ZipFile zipFile = new ZipFile(pbBinaryFile, StandardCharsets.UTF_8)) {
            ZipEntry exportPBEntry = zipFile.getEntry("export.pb");
            ZipEntry exportPBCountEntry = zipFile.getEntry("count");
            DataInputStream pbStream = new DataInputStream(zipFile.getInputStream(exportPBEntry));
            DataInputStream countStream = new DataInputStream(zipFile.getInputStream(exportPBCountEntry));

            long pbCount = countStream.readLong();
            LOG.info("Protobuf PBTinkarMsg Count: " + pbCount);

            StringBuilder stringBuilder = new StringBuilder();
            for(int i = 1; i <= pbCount; i++){
                stringBuilder.setLength(0);
                int pbMessageLength = pbStream.readInt();
                if(pbMessageLength == -1){
                    LOG.info("reached EOF");
                    break;
                }
                byte[] bytes = new byte[pbMessageLength];
                pbStream.read(bytes, 0, pbMessageLength);
                stringBuilder.append("PBTinkarMsg # " + i + "\n");
                stringBuilder.append(PBTinkarMsg.parseFrom(bytes).toString());
                LOG.info(stringBuilder.toString());
            }

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
