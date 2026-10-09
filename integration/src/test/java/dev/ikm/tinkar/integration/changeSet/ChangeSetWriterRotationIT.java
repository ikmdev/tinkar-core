package dev.ikm.tinkar.integration.changeSet;

import dev.ikm.tinkar.common.service.DataActivity;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.changeset.ChangeSetFormat;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.provider.changeset.ChangeSetWriterProvider;
import dev.ikm.tinkar.schema.TinkarMsg;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Manifest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Saves that overlap rotate the changeset writer one at a time, leaving one service thread.
 *
 * <p>Two rotations that overlapped once each started a service thread; the one the writer did
 * not keep ran on with a file no save would close, took entities from the queue into it, and,
 * idle, asked for a rotation every poll — each one a chance for another such thread, until the
 * tasks they submitted filled the heap.
 */
class ChangeSetWriterRotationIT {

    private static final File DATASTORE_ROOT =
            TestConstants.createFilePathInTargetFromClassName.apply(ChangeSetWriterRotationIT.class);
    private static final int OVERLAPPING_SAVES = 8;
    private static final int WRITES = 50;

    @BeforeAll
    static void start() {
        FileUtil.recursiveDelete(DATASTORE_ROOT);
        TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, DATASTORE_ROOT);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
    }

    @AfterAll
    static void stop() {
        TestHelper.stopDatabase();
    }

    @Test
    void everythingWrittenAfterOverlappingSavesIsInAClosedFile() throws Exception {
        ChangeSetWriterProvider writer = ChangeSetWriterProvider.provider();
        CompletableFuture.allOf(IntStream.range(0, OVERLAPPING_SAVES)
                        .mapToObj(_ -> CompletableFuture.supplyAsync(writer::save).thenCompose(saved -> saved))
                        .toArray(CompletableFuture[]::new))
                .get(1, TimeUnit.MINUTES);

        // A service thread left over would take some of these into a file no save closes.
        // Distinct concepts: a component written twice is carried once at close.
        List<Entity<?>> concepts = new ArrayList<>();
        EntityService.get().forEachConceptEntity(concept -> {
            if (concepts.size() < WRITES) {
                concepts.add(concept);
            }
        });
        assertEquals(WRITES, concepts.size(), "distinct concepts to write");
        for (Entity<?> concept : concepts) {
            writer.writeToChangeSet(concept, DataActivity.SYNCHRONIZABLE_EDIT);
        }
        writer.save().get(1, TimeUnit.MINUTES);

        Path folder = DATASTORE_ROOT.toPath().resolve(Path.of("changeSets", "src", "main", "resources"));
        int recordsInClosedFiles = 0;
        try (Stream<Path> files = Files.list(folder)) {
            for (Path changeSet : files.filter(path -> path.getFileName().toString().endsWith("ike-cs.zip")).toList()) {
                recordsInClosedFiles += recordsIfFinished(changeSet.toFile());
            }
        }
        assertEquals(WRITES, recordsInClosedFiles, "Records in closed changesets in " + folder);
    }

    /** The records in a finished change set; none in one still open. */
    private static int recordsIfFinished(File file) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            Manifest manifest = ChangeSetFormat.manifest(zip).orElse(null);
            if (manifest == null) {
                return 0;
            }
            int records = 0;
            for (ChangeSetFormat.RecordEntry entry : ChangeSetFormat.recordEntries(zip, manifest)) {
                try (InputStream in = ChangeSetFormat.openRecords(zip, entry.entry())) {
                    while (TinkarMsg.parseDelimitedFrom(in) != null) {
                        records++;
                    }
                }
            }
            return records;
        }
    }
}
