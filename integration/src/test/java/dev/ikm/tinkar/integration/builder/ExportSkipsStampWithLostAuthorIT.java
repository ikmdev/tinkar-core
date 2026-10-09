package dev.ikm.tinkar.integration.builder;

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.EntityCountSummary;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.StampVersionRecord;
import dev.ikm.tinkar.entity.aggregator.TemporalEntityAggregator;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.KnowledgeSet;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.entity.export.ExportEntitiesToProtobufFile;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.terms.State;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A stamp whose author the store can no longer name is skipped by an export, which still
 * completes.
 *
 * <p>A store can hold such a stamp: one written in a session whose author never had an entity,
 * by a store that then lost the author's UUID on restart. The export skips the stamp as a
 * dangling reference — and used to fail anyway, at the very end, because it had already listed
 * the stamp's author for the manifest before finding that the stamp could not be written.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExportSkipsStampWithLostAuthorIT {

    @BeforeAll
    static void beforeAll() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
    }

    @AfterAll
    static void afterAll() {
        TestHelper.stopDatabase();
    }

    @Test
    void anExportSkipsTheStampAndCompletes() throws Exception {
        KnowledgeSet set = KnowledgeSet.of("88888888-8888-5888-9888-888888888888");
        ActiveStamp stamp = Stamp.active("2026-07-03T00:00:00Z",
                set.conceptRef("Lost probe author (Probe)"),
                set.conceptRef("Lost probe module (Probe)"),
                KernelTerm.DEVELOPMENT_PATH);
        set.concept("Lost probe author (Probe)").at(stamp).synonym("Lost probe author");
        set.concept("Lost probe module (Probe)").at(stamp).synonym("Lost probe module");
        set.write();

        // A nid well past any this store has minted, so it has no public id.
        long lostAuthorNid = PrimitiveData.nid(PublicIds.of(UUID.randomUUID())) + 100_000;
        assertThrows(IllegalStateException.class, () -> PrimitiveData.publicId(lostAuthorNid));
        EntityService.get().putEntity(stampWithAuthor(lostAuthorNid,
                Instant.parse("2026-07-04T00:00:00Z").toEpochMilli()));

        Path out = Path.of(System.getProperty("user.dir")).resolve("target").resolve("export-lost-author.zip");
        Files.deleteIfExists(out);
        EntityCountSummary summary = new ExportEntitiesToProtobufFile(out.toFile(),
                new TemporalEntityAggregator(0L, Long.MAX_VALUE)).compute();

        assertEquals(2, summary.conceptCount());
        assertEquals(1, summary.stampCount(), "the set's stamp, without the one whose author is lost");
    }

    private static StampRecord stampWithAuthor(long authorNid, long time) {
        UUID stampUuid = UUID.randomUUID();
        RecordListBuilder<StampVersionRecord> versions = RecordListBuilder.make();
        StampRecord stamp = new StampRecord(stampUuid.getMostSignificantBits(),
                stampUuid.getLeastSignificantBits(), null, PrimitiveData.nid(PublicIds.of(stampUuid)), versions);
        versions.add(new StampVersionRecord(stamp, State.ACTIVE.nid(), time, authorNid,
                KernelTerm.PRIMORDIAL_MODULE.nid(), KernelTerm.DEVELOPMENT_PATH.nid()));
        versions.build();
        return stamp;
    }
}
