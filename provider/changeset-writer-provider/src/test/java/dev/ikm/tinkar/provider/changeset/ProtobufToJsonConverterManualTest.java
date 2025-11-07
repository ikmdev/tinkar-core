package dev.ikm.tinkar.provider.changeset;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Manual test for converting actual protobuf files.
 * Remove @Disabled annotation to run with your actual data.
 */
public class ProtobufToJsonConverterManualTest {

    @Test
    @Disabled("Manual test - enable when needed and update path to your data")
    void testConvertActualDirectory() throws Exception {
        ProtobufToJsonConverterTool converter = new ProtobufToJsonConverterTool();

        File directory = new File("~/Solor/datastore/changesets/src/main/resources");
        assertTrue(directory.exists(), "Test directory should exist");

        long totalEntities = converter.convertDirectory(directory);

        System.out.println("Total entities converted: " + totalEntities);
        System.out.println("Check the directory for generated .json files");
    }

    @Test
    @Disabled("Manual test - enable when needed and update path to your data")
    void testConvertSingleFile() throws Exception {
        ProtobufToJsonConverterTool converter = new ProtobufToJsonConverterTool();

        File inputFile = new File("~/Solor/datastore/changesets/src/main/resources/User (SOLOR) 20251106T160419EST dgx ike-cs.zip");
        File outputFile = new File("~/Solor/datastore/changesets/src/main/resources/User (SOLOR) 20251106T160419EST dgx.json");

        assertTrue(inputFile.exists(), "Input file should exist");

        long entities = converter.convert(inputFile, outputFile);

        System.out.println("Converted " + entities + " entities");
        assertTrue(outputFile.exists(), "Output JSON file should be created");
    }

    @Test
    @Disabled("Manual test - demonstrate file filter working")
    void testFileFilterFindsFiles() {
        File dir = new File("~/Solor/datastore/changesets/src/main/resources");
        assertTrue(dir.exists(), "Test directory should exist");

        File[] files = dir.listFiles((d, name) ->
            name.endsWith("pb.zip") || name.endsWith("ike-cs.zip")
        );

        System.out.println("File filter found " + files.length + " files:");
        for (File f : files) {
            System.out.println("  - " + f.getName());
            System.out.println("    Size: " + f.length() + " bytes");
        }

        assertTrue(files.length > 0, "Should find at least one protobuf file");
    }
}

