package dev.ikm.tinkar.integration.changeSet;

import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.fixtures.NewEphemeralKeyValueProvider;
import dev.ikm.tinkar.schema.TinkarMsg;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * A multi-pass import that hits a bad record must fail, not hang.
 *
 * <p>The reader limits in-flight subtasks with a semaphore, each subtask releasing its permit when
 * it finishes. The first failure cancels the scope, and a cancelled scope does not run the subtasks
 * forked after it — so with more records left than permits, those permits never come back and the
 * reader used to block in {@code acquire()} forever. Seen for real migrating a 6-bit dataset to the
 * 8-bit nid layout, where an out-of-range element sequence left the import stuck on
 * "Starting multi-pass import".
 */
@ExtendWith(NewEphemeralKeyValueProvider.class)
class MultiPassImportFailureIT {

    /** Comfortably more records than the reader's permits (8 per core). */
    private static final int RECORD_COUNT = Runtime.getRuntime().availableProcessors() * 8 * 20;

    @TempDir
    Path tempDir;

    @Test
    void aFailingRecordFailsTheImportInsteadOfHanging() throws IOException {
        File changeSet = writeChangeSetOfEmptyRecords(tempDir.resolve("bad-changeset.zip").toFile());
        LoadEntitiesFromProtobufFile loader = new LoadEntitiesFromProtobufFile(changeSet, true);

        // A record with no value set fails in pass 1 ("Tinkar message value not set").
        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> assertThrows(RuntimeException.class, loader::compute));
    }

    private static File writeChangeSetOfEmptyRecords(File file) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))) {
            zos.putNextEntry(new ZipEntry("Entities"));
            for (int i = 0; i < RECORD_COUNT; i++) {
                TinkarMsg.getDefaultInstance().writeDelimitedTo(zos);
            }
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zos.write(("Manifest-Version: 1.0\nTotal-Count: " + RECORD_COUNT + "\n\n")
                    .getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return file;
    }
}
