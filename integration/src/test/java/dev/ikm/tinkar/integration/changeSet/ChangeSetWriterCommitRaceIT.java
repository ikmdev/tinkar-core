package dev.ikm.tinkar.integration.changeSet;

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.DataActivity;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.transaction.StampedWriter;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.ChangeSetManifest;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.provider.changeset.ChangeSetWriterProvider;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import dev.ikm.tinkar.terms.State;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a transaction writes is in the change set, however soon it commits after the write.
 *
 * <p>A version is uncommitted while its transaction is open, so the answer changes when another thread
 * commits it. The writer asked twice: whether an entity was uncommitted, and then under which stamps. An
 * entity whose transaction committed between the two was uncommitted under no stamp, so it was neither held
 * back nor written, and the change set went without it, with nothing in the log. It was met on 2026-11-23,
 * by a record written alone in its transaction (IKE-Network/ike-issues#1399).
 */
class ChangeSetWriterCommitRaceIT {

    private static final File DATASTORE_ROOT =
            TestConstants.createFilePathInTargetFromClassName.apply(ChangeSetWriterCommitRaceIT.class);
    /** Of four times as many, the writer lost 232 and 237 before it was fixed. */
    private static final int WRITES = 500;

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
    void everySemanticCommittedRightAfterItsWriteIsInTheChangeSet() throws Exception {
        ChangeSetWriterProvider changeSets = ChangeSetWriterProvider.provider();
        for (int i = 0; i < WRITES; i++) {
            try (StampedWriter writer = StampedWriter.open("comment " + i, State.ACTIVE, System.currentTimeMillis(),
                    KernelConcept.USER, KernelConcept.PRIMORDIAL_MODULE, KernelConcept.DEVELOPMENT_PATH)) {
                long nid = writer.semantic(PublicIds.of(UUID.randomUUID()), KernelPattern.COMMENT_PATTERN.publicId(),
                        KernelConcept.USER.publicId(), "Comment " + i);
                // As a store on disk does inside the put; a store of a test hands nothing to the writer.
                changeSets.writeToChangeSet(EntityHandle.get(nid).expectSemantic(), DataActivity.SYNCHRONIZABLE_EDIT);
                writer.commit();
            }
            if (i % 5 == 0) {
                Thread.sleep(1);      // the writer's thread catches up, and takes the next entity as it is put
            }
        }
        changeSets.save().get(1, TimeUnit.MINUTES);

        Path folder = DATASTORE_ROOT.toPath().resolve(Path.of("changeSets", "src", "main", "resources"));
        long comments = 0;
        try (Stream<Path> files = Files.list(folder)) {
            for (Path changeSet : files.filter(path -> path.getFileName().toString().endsWith("ike-cs.zip")).toList()) {
                comments += ChangeSetManifest.of(changeSet.toFile()).semanticsOf(KernelPattern.COMMENT_PATTERN);
            }
        }
        assertEquals(WRITES, comments, "comments in the closed change sets in " + folder);
    }
}
