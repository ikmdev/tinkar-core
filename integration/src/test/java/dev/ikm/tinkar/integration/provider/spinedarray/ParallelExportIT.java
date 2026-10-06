package dev.ikm.tinkar.integration.provider.spinedarray;

import dev.ikm.tinkar.entity.changeset.ChangeSetFormat;
import dev.ikm.tinkar.common.service.EntityCountSummary;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.entity.aggregator.DefaultEntityAggregator;
import dev.ikm.tinkar.entity.aggregator.EntityAggregator;
import dev.ikm.tinkar.entity.export.ExportEntitiesToProtobufFile;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.schema.TinkarMsg;
import org.eclipse.collections.api.factory.primitive.IntLists;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.IntConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exporting with concurrently delivered entities must still write a well-formed
 * stream (IKE-Network/ike-issues#1142).
 *
 * <p>{@code RocksProvider.forEachSemanticNid} delivers semantics from parallel
 * tasks; the exporter used to write each record to the shared zip stream without
 * synchronization, so records interleaved and the export could not be parsed back.
 * This test reproduces the concurrent delivery on the SpinedArray store with an
 * aggregator that hands every entity to the exporter from a parallel stream.
 */
class ParallelExportIT {

    private static final File DATASTORE_ROOT =
            TestConstants.createFilePathInTargetFromClassName.apply(ParallelExportIT.class);
    private static final File EXPORT_FILE = new File(DATASTORE_ROOT.getParentFile(), "parallel-export-pb.zip");

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

    /** Delivers the default aggregation's nids from a parallel stream. */
    private static final class ParallelDeliveryAggregator extends EntityAggregator {
        private final DefaultEntityAggregator delegate = new DefaultEntityAggregator();

        @Override
        public EntityCountSummary aggregate(IntConsumer nidConsumer) {
            MutableIntList nids = IntLists.mutable.empty();
            EntityCountSummary summary = delegate.aggregate(nids::add);
            Arrays.stream(nids.toArray()).parallel().forEach(nidConsumer);
            return summary;
        }
    }

    @Test
    void concurrentlyDeliveredExport_parsesBackCompletely() throws IOException {
        EXPORT_FILE.delete();
        EntityCountSummary exported =
                new ExportEntitiesToProtobufFile(EXPORT_FILE, new ParallelDeliveryAggregator()).compute();
        assertTrue(exported.getTotalCount() > 1_000, "the starter data exports thousands of entities");

        long parsed = 0;
        String manifest = null;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(EXPORT_FILE))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("META-INF/MANIFEST.MF")) {
                    manifest = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    continue;
                }
                if (ChangeSetFormat.isMetadata(entry.getName())) {
                    continue; // the identity index, or other metadata: not records
                }
                // Throws InvalidProtocolBufferException on an interleaved stream.
                while (TinkarMsg.parseDelimitedFrom(zis) != null) {
                    parsed++;
                }
            }
        }

        assertEquals(exported.getTotalCount(), parsed, "every exported record parses back");
        assertTrue(manifest != null && manifest.contains("Total-Count: " + parsed),
                "the manifest count matches the records in the stream");
    }
}
