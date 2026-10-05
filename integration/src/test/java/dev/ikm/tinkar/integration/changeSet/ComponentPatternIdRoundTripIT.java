package dev.ikm.tinkar.integration.changeSet;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.EntityCountSummary;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.entity.export.ExportEntitiesToProtobufFile;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.schema.TinkarMsg;
import dev.ikm.tinkar.terms.EntityBinding;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every exported record names the pattern its component is an element of (tinkar-schema#43), and
 * changesets with and without those names both import.
 *
 * <p>Semantics always carried their pattern. Concepts, patterns and stamps now do too, so a
 * pattern-encoding store can assign each one its nid from the record alone. The starter data
 * predates the fields, so loading it covers the backward-compatible path; re-importing the export
 * with the multi-pass loader covers the new one.
 */
class ComponentPatternIdRoundTripIT {

    private static final File DATASTORE_ROOT =
            TestConstants.createFilePathInTargetFromClassName.apply(ComponentPatternIdRoundTripIT.class);
    private static final File EXPORT = new File(DATASTORE_ROOT, "pattern-id-export.zip");

    @BeforeAll
    static void beforeAll() {
        FileUtil.recursiveDelete(DATASTORE_ROOT);
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE, DATASTORE_ROOT);
        // Written before tinkar-schema#43: no record carries a concept, pattern or stamp pattern.
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
    }

    @AfterAll
    static void afterAll() {
        TestHelper.stopDatabase();
    }

    @Test
    void everyExportedRecordNamesItsPattern() throws IOException {
        new ExportEntitiesToProtobufFile(EXPORT).compute();

        List<TinkarMsg> records = readRecords(EXPORT);
        assertFalse(records.isEmpty(), "export wrote no records");
        int concepts = 0, patterns = 0, stamps = 0;
        for (TinkarMsg record : records) {
            switch (record.getValueCase()) {
                case CONCEPT_CHRONOLOGY -> {
                    concepts++;
                    assertTrue(record.getConceptChronology().hasPatternForConceptPublicId());
                    assertPattern(EntityBinding.Concept.pattern().publicId(),
                            record.getConceptChronology().getPatternForConceptPublicId());
                }
                case PATTERN_CHRONOLOGY -> {
                    patterns++;
                    assertTrue(record.getPatternChronology().hasPatternForPatternPublicId());
                    assertPattern(EntityBinding.Pattern.pattern().publicId(),
                            record.getPatternChronology().getPatternForPatternPublicId());
                }
                case STAMP_CHRONOLOGY -> {
                    stamps++;
                    assertTrue(record.getStampChronology().hasPatternForStampPublicId());
                    assertPattern(EntityBinding.Stamp.pattern().publicId(),
                            record.getStampChronology().getPatternForStampPublicId());
                }
                default -> { }
            }
        }
        assertTrue(concepts > 0 && patterns > 0 && stamps > 0,
                "expected concepts, patterns and stamps; got " + concepts + "/" + patterns + "/" + stamps);
    }

    @Test
    void anExportCarryingPatternIdsReimportsWithTheMultiPassLoader() {
        File export = new File(DATASTORE_ROOT, "pattern-id-reimport.zip");
        EntityCountSummary exported = new ExportEntitiesToProtobufFile(export).compute();

        EntityCountSummary reimported = new LoadEntitiesFromProtobufFile(export, true).compute();

        // Against the export, not the starter load: the starter file repeats some records (388
        // concept records for 379 concepts), which the store merges. Into the same store, every
        // component already exists, so this exercises nid assignment from the new fields alone.
        assertEquals(exported.conceptCount(), reimported.conceptCount());
        assertEquals(exported.semanticCount(), reimported.semanticCount());
        assertEquals(exported.patternCount(), reimported.patternCount());
        assertEquals(exported.stampCount(), reimported.stampCount());
    }

    private static void assertPattern(PublicId expected, dev.ikm.tinkar.schema.PublicId actual) {
        List<UUID> uuids = actual.getUuidsList().stream().map(UUID::fromString).toList();
        assertEquals(expected.asUuidList().castToList(), uuids);
    }

    private static List<TinkarMsg> readRecords(File zip) throws IOException {
        List<TinkarMsg> records = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.getName().startsWith("META-INF")) {
                    continue;
                }
                TinkarMsg record;
                while ((record = TinkarMsg.parseDelimitedFrom(in)) != null) {
                    records.add(record);
                }
            }
        }
        return records;
    }
}
