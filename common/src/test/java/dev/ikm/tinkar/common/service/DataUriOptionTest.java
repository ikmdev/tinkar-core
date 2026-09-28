package dev.ikm.tinkar.common.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DataUriOption#toFile()} must decode the URI, so an import file whose
 * name or folder contains a space or other escaped character is found
 * (IKE-Network/ike-issues#1156).
 */
class DataUriOptionTest {

    @TempDir
    Path temp;

    @ParameterizedTest
    @ValueSource(strings = {
            "starter-pb.zip",
            "My KB-migration-pb.zip",
            "Keith's KB-migration-pb.zip",
            "two  spaces-pb.zip",
            "percent%20literal-pb.zip",
            "accénted ✓-pb.zip"})
    void toFileFindsTheFileWhateverItsName(String name) throws IOException {
        Path folder = Files.createDirectories(temp.resolve("Solor folder"));
        Path file = Files.createFile(folder.resolve(name));
        DataUriOption option = new DataUriOption(name, file.toUri());

        File resolved = option.toFile();

        assertEquals(file.toFile().getAbsoluteFile(), resolved.getAbsoluteFile());
        assertTrue(resolved.exists(), () -> "not found: " + resolved);
    }

    @Test
    void urlGetFileKeepsTheEncodingWhichIsWhyProvidersMustNotUseIt() throws IOException {
        Path file = Files.createFile(temp.resolve("My KB-migration-pb.zip"));
        URI uri = file.toUri();

        String urlFile = uri.toURL().getFile();

        assertTrue(urlFile.endsWith("My%20KB-migration-pb.zip"), urlFile);
        assertTrue(new DataUriOption("x", uri).toFile().exists());
    }

    @Test
    void persistedUriStringRoundTrips() throws IOException {
        Path file = Files.createFile(temp.resolve("My KB-migration-pb.zip"));
        // SelectDataSourceController persists option.uri().toString() and reads it back.
        URI restored = URI.create(file.toUri().toString());

        assertTrue(new DataUriOption("x", restored).toFile().exists());
    }
}
