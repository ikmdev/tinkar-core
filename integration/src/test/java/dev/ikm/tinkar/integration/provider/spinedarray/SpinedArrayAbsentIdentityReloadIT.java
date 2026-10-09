package dev.ikm.tinkar.integration.provider.spinedarray;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A SpinedArray store that opens with an {@code absentIdentities.txt} — written by an earlier
 * session's save — knows the components it names again: their UUIDs resolve to the same nids, so
 * references to them still hold, and asking which component a nid is still has an answer.
 */
class SpinedArrayAbsentIdentityReloadIT {

    private static final File DATASTORE_ROOT =
            TestConstants.createFilePathInTargetFromClassName.apply(SpinedArrayAbsentIdentityReloadIT.class);
    private static final PublicId ABSENT = PublicIds.of(UUID.randomUUID());
    /** A nid well below where a fresh store starts minting, as an earlier session would have used. */
    private static final int NID = Integer.MIN_VALUE + 7;

    @BeforeAll
    static void openAStoreWithASavedAbsentIdentity() throws Exception {
        FileUtil.recursiveDelete(DATASTORE_ROOT);
        Files.createDirectories(DATASTORE_ROOT.toPath());
        Files.writeString(DATASTORE_ROOT.toPath().resolve("absentIdentities.txt"),
                NID + " " + ABSENT.asUuidArray()[0] + "\n");
        TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, DATASTORE_ROOT);
    }

    @AfterAll
    static void stopStore() {
        TestHelper.stopDatabase();
    }

    @Test
    void theSavedIdentityIsKnownAgain() {
        assertEquals(ABSENT, PrimitiveData.publicId(NID));
        assertEquals(NID, PrimitiveData.nid(ABSENT));
    }
}
