package dev.ikm.tinkar.integration.changeSet;

import dev.ikm.tinkar.common.service.DataActivity;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.changeset.ChangeSetFormat;
import dev.ikm.tinkar.entity.changeset.SchemaIds;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.provider.changeset.ChangeSetWriterProvider;
import dev.ikm.tinkar.schema.TinkarMsg;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A change set that cannot be written: the save says so, and what it was to hold is written by the next save that
 * can.
 *
 * <p>The writer's thread once ended with the record in hand, and the save completed as if it were written: a sync
 * said it had succeeded, and the record was in no change set from then on.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChangeSetWriterUnwrittenIT {

    private static final File DATASTORE_ROOT =
            TestConstants.createFilePathInTargetFromClassName.apply(ChangeSetWriterUnwrittenIT.class);
    private static final Path FOLDER = DATASTORE_ROOT.toPath().resolve(Path.of("changeSets", "src", "main", "resources"));
    private static final Path ASIDE = DATASTORE_ROOT.toPath().resolve("resources-moved-aside");

    private static Iterator<Entity<?>> concepts;
    private static Thread.UncaughtExceptionHandler before;

    private ChangeSetWriterProvider writer;

    @BeforeAll
    static void start() {
        // A write that fails while no save is waiting for it is an uncaught exception, which Komet shows.
        before = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((_, _) -> { });
        FileUtil.recursiveDelete(DATASTORE_ROOT);
        TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, DATASTORE_ROOT);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
        List<Entity<?>> found = new ArrayList<>();
        EntityService.get().forEachConceptEntity(concept -> {
            if (found.size() < 20) {
                found.add(concept);
            }
        });
        concepts = found.iterator();
    }

    @AfterAll
    static void stop() {
        TestHelper.stopDatabase();
        Thread.setDefaultUncaughtExceptionHandler(before);
    }

    @BeforeEach
    void saveWhatIsOpen() throws Exception {
        writer = ChangeSetWriterProvider.provider();
        writer.save().get(1, TimeUnit.MINUTES);
    }

    @AfterEach
    void unblock() throws IOException {
        if (Files.exists(ASIDE)) {
            unblockFolder();
        }
    }

    @Test
    @Order(1)
    void aSaveThatCannotWriteFailsAndTheNextOneWrites() throws Exception {
        Entity<?> concept = concepts.next();
        blockFolder();
        write(concept);
        assertSaveFails();
        assertSaveFails();

        Entity<?> another = concepts.next();
        write(another);
        unblockFolder();
        writer.save().get(1, TimeUnit.MINUTES);
        assertEquals(1, recordsOf(concept), "the record that could not be written");
        assertEquals(1, recordsOf(another), "and the one written since");
    }

    @Test
    @Order(2)
    void aWriteThatFailedBeforeTheSaveIsWrittenByIt() throws Exception {
        Entity<?> concept = concepts.next();
        blockFolder();
        write(concept);
        Thread.sleep(1_000);                                 // the writer's thread has taken it, and ended
        unblockFolder();
        writer.save().get(1, TimeUnit.MINUTES);
        assertEquals(1, recordsOf(concept));

        Entity<?> after = concepts.next();
        write(after);
        writer.save().get(1, TimeUnit.MINUTES);
        assertEquals(1, recordsOf(after), "the writer goes on");
    }

    @Test
    @Order(3)
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "a folder with an open file is not moved there")
    void recordsOfAFileThatCannotBeFinishedAreWrittenToTheNext() throws Exception {
        List<Entity<?>> written = List.of(concepts.next(), concepts.next(), concepts.next());
        for (Entity<?> concept : written) {
            write(concept);
        }
        awaitSpool();
        blockFolder();                                       // its journal is begun, and cannot be finished
        assertSaveFails();

        unblockFolder();
        writer.save().get(1, TimeUnit.MINUTES);
        for (Entity<?> concept : written) {
            assertEquals(1, recordsOf(concept));
        }
    }

    @Test
    @Order(4)
    void shuttingDownWithAChangeSetThatCannotBeWrittenDoesNotThrow() throws Exception {
        // The store is saved after its writer has shut down, so what the change sets lack it must still get to save.
        write(concepts.next());
        blockFolder();
        assertDoesNotThrow(writer::shutdown);
        unblockFolder();
    }

    private void write(Entity<?> concept) {
        writer.writeToChangeSet(concept, DataActivity.SYNCHRONIZABLE_EDIT);
    }

    private void assertSaveFails() {
        ExecutionException failed = assertThrows(ExecutionException.class, () -> writer.save().get(1, TimeUnit.MINUTES));
        assertInstanceOf(UncheckedIOException.class, failed.getCause());
    }

    /** A file in the place of the folder: nothing can be written there, whoever runs the test. */
    private static void blockFolder() throws IOException {
        Files.move(FOLDER, ASIDE);
        Files.writeString(FOLDER, "not a folder");
    }

    private static void unblockFolder() throws IOException {
        Files.delete(FOLDER);
        Files.move(ASIDE, FOLDER);
    }

    private static void awaitSpool() throws Exception {
        for (int i = 0; i < 100; i++) {
            try (Stream<Path> files = Files.list(FOLDER)) {
                if (files.anyMatch(file -> file.getFileName().toString().endsWith(".spool"))) {
                    Thread.sleep(500);                       // and the records after the first
                    return;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("no journal was begun");
    }

    /** In how many finished change sets a component has a record. */
    private static int recordsOf(Entity<?> concept) throws IOException {
        UUID id = concept.publicId().leastUuid();
        int records = 0;
        try (Stream<Path> files = Files.list(FOLDER)) {
            for (Path changeSet : files.filter(path -> path.getFileName().toString().endsWith("ike-cs.zip")).toList()) {
                try (ZipFile zip = new ZipFile(changeSet.toFile())) {
                    Manifest manifest = ChangeSetFormat.manifest(zip).orElse(null);
                    assertTrue(manifest != null, "a finished change set: " + changeSet.getFileName());
                    for (ChangeSetFormat.RecordEntry entry : ChangeSetFormat.recordEntries(zip, manifest)) {
                        try (InputStream in = ChangeSetFormat.openRecords(zip, entry.entry())) {
                            for (TinkarMsg record = TinkarMsg.parseDelimitedFrom(in); record != null;
                                 record = TinkarMsg.parseDelimitedFrom(in)) {
                                if (record.hasConceptChronology()
                                        && List.of(SchemaIds.uuids(ChangeSetFormat.componentOf(record))).contains(id)) {
                                    records++;
                                }
                            }
                        }
                    }
                }
            }
        }
        return records;
    }
}
