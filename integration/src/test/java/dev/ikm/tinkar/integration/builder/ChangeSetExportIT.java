package dev.ikm.tinkar.integration.builder;

import dev.ikm.tinkar.common.service.EntityCountSummary;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.KnowledgeSet;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.entity.aggregator.TemporalEntityAggregator;
import dev.ikm.tinkar.entity.export.ExportEntitiesToProtobufFile;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChangeSetExportIT {

    @BeforeAll
    static void beforeAll() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
    }

    @AfterAll
    static void afterAll() {
        TestHelper.stopDatabase();
    }

    @Test
    void exportSelfContainedSet() throws Exception {
        KnowledgeSet set = KnowledgeSet.of("77777777-7777-5777-9777-777777777777");
        ActiveStamp stamp = Stamp.active("2026-07-03T00:00:00Z",
                set.conceptRef("Probe author (Probe)"),
                set.conceptRef("Probe module (Probe)"),
                TinkarTerm.DEVELOPMENT_PATH);
        set.concept("Probe author (Probe)").at(stamp).synonym("Probe author");
        set.concept("Probe module (Probe)").at(stamp).synonym("Probe module");
        set.concept("Probe thing (Probe)").at(stamp).synonym("Probe thing");
        set.write();

        Path out = Path.of(System.getProperty("user.dir")).resolve("target").resolve("export-probe.zip");
        Files.deleteIfExists(out);
        // Real-time aggregation excludes the pre-inception NONEXISTENT_STAMP sentinel the
        // store mints at startup, whose module/author are unresolvable in a store seeded
        // only by this set (a whole-store export NPEs on it in the manifest pass).
        EntityCountSummary summary = new ExportEntitiesToProtobufFile(out.toFile(),
                new TemporalEntityAggregator(0L, Long.MAX_VALUE)).compute();
        assertEquals(3, summary.conceptCount());
        assertEquals(1, summary.stampCount());
        assertTrue(summary.semanticCount() >= 6, "FQN + synonym + dialects per concept");
        assertTrue(Files.size(out) > 0);
    }
}
