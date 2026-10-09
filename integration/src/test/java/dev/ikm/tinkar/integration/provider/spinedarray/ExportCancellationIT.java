package dev.ikm.tinkar.integration.provider.spinedarray;

import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.entity.export.ExportEntitiesToProtobufFile;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * An export stops when the caller that started it cancels, wherever it has got to, and says it
 * was cancelled rather than that it failed.
 *
 * <p>The service runs exports as jobs a user can cancel, and tracks the cancel with a handle of
 * its own, so it asks through {@link ExportEntitiesToProtobufFile#cancelWhen}.
 */
class ExportCancellationIT {

    private static final File DATASTORE_ROOT =
            TestConstants.createFilePathInTargetFromClassName.apply(ExportCancellationIT.class);
    private static final File EXPORT_FILE = new File(DATASTORE_ROOT.getParentFile(), "export-cancellation.zip");

    @BeforeAll
    static void beforeAll() {
        FileUtil.recursiveDelete(DATASTORE_ROOT);
        TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, DATASTORE_ROOT);
        new LoadEntitiesFromProtobufFile(TestConstants.PB_STARTER_DATA_REASONED).compute();
    }

    @AfterAll
    static void afterAll() {
        TestHelper.stopDatabase();
    }

    @Test
    void anExportCancelledBeforeItStartsStopsAtTheFirstCheck() {
        ExportEntitiesToProtobufFile export = new ExportEntitiesToProtobufFile(EXPORT_FILE);
        export.cancelWhen(() -> true);
        assertTimeoutPreemptively(Duration.ofSeconds(60),
                () -> assertThrows(CancellationException.class, export::compute));
    }

    @Test
    void anExportCancelledWhileWritingRecordsStops() {
        // Asked once per record as the chunks are written, and a few times between phases:
        // cancelled partway through the records.
        AtomicInteger asked = new AtomicInteger();
        ExportEntitiesToProtobufFile export = new ExportEntitiesToProtobufFile(EXPORT_FILE);
        export.cancelWhen(() -> asked.incrementAndGet() > 500);
        assertTimeoutPreemptively(Duration.ofSeconds(60),
                () -> assertThrows(CancellationException.class, export::compute));
    }
}
