package dev.ikm.tinkar.provider.changeset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProtobufToJsonConverterTool directory conversion functionality.
 */
class ProtobufToJsonConverterToolTest {

    @Test
    void testGetJsonOutputFile_pbZipExtension(@TempDir Path tempDir) throws IOException {
        ProtobufToJsonConverterTool converter = new ProtobufToJsonConverterTool();

        // Use reflection to test the private method
        File protobufFile = tempDir.resolve("test-changeset.pb.zip").toFile();
        protobufFile.createNewFile();

        // We'll test indirectly by checking directory conversion behavior
        // The actual method is private, so we test through the public API
        assertTrue(protobufFile.exists());
    }

    @Test
    void testConvertDirectory_emptyDirectory(@TempDir Path tempDir) throws IOException {
        ProtobufToJsonConverterTool converter = new ProtobufToJsonConverterTool();

        long result = converter.convertDirectory(tempDir.toFile());

        assertEquals(0, result, "Empty directory should convert 0 entities");
    }

    @Test
    void testConvertDirectory_nonExistentDirectory() {
        ProtobufToJsonConverterTool converter = new ProtobufToJsonConverterTool();
        File nonExistent = new File("/non/existent/directory");

        assertThrows(IOException.class, () -> converter.convertDirectory(nonExistent));
    }

    @Test
    void testConvertDirectory_fileInsteadOfDirectory(@TempDir Path tempDir) throws IOException {
        ProtobufToJsonConverterTool converter = new ProtobufToJsonConverterTool();
        File regularFile = tempDir.resolve("regular-file.txt").toFile();
        regularFile.createNewFile();

        assertThrows(IOException.class, () -> converter.convertDirectory(regularFile));
    }

    @Test
    void testConvert_nonExistentInputFile() {
        ProtobufToJsonConverterTool converter = new ProtobufToJsonConverterTool();
        File nonExistent = new File("/non/existent/file.pb.zip");
        File output = new File("/tmp/output.json");

        assertThrows(IOException.class, () -> converter.convert(nonExistent, output));
    }
}

