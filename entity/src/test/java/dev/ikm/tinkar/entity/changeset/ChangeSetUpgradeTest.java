/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.entity.changeset;

import dev.ikm.tinkar.schema.ConceptChronology;
import dev.ikm.tinkar.schema.ConceptVersion;
import dev.ikm.tinkar.schema.PublicId;
import dev.ikm.tinkar.schema.TinkarMsg;
import dev.ikm.tinkar.terms.EntityBinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static dev.ikm.tinkar.entity.changeset.ChangeSetToolsTest.CONCEPT_A;
import static dev.ikm.tinkar.entity.changeset.ChangeSetToolsTest.NEVER_WRITTEN;
import static dev.ikm.tinkar.entity.changeset.ChangeSetToolsTest.STAMP;
import static dev.ikm.tinkar.entity.changeset.ChangeSetToolsTest.journal;
import static dev.ikm.tinkar.entity.changeset.ChangeSetToolsTest.patternOf;
import static dev.ikm.tinkar.entity.changeset.ChangeSetToolsTest.recordsByComponent;
import static dev.ikm.tinkar.entity.changeset.ChangeSetToolsTest.sequenceOf;
import static dev.ikm.tinkar.entity.changeset.ChangeSetToolsTest.stamp;
import static dev.ikm.tinkar.entity.changeset.ChangeSetToolsTest.writeFormat2;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Change sets upgraded in place against a knowledge base, over change sets written by hand in
 * every format; the knowledge base is a resolver that knows the one component the journal refers
 * to without carrying it.
 */
class ChangeSetUpgradeTest {

    @TempDir
    Path dir;

    static final UUID[] CONCEPT_PATTERN = EntityBinding.Concept.pattern().publicId().asUuidArray();
    static final Function<UUID[], Optional<UUID[]>> KNOWS_IT =
            uuids -> uuids[0].equals(NEVER_WRITTEN) ? Optional.of(CONCEPT_PATTERN) : Optional.empty();
    static final Function<UUID[], Optional<UUID[]>> KNOWS_NOTHING = uuids -> Optional.empty();

    /** A store's change-set folder with one file of every kind; the names sort in this order. */
    record Folder(Path path, Path format1, Path format2, Path format3WithoutPatterns, Path current, Path empty,
                  Path notAZip) {
    }

    Folder folder() throws Exception {
        Path folder = Files.createDirectories(dir.resolve("store/changeSets/src/main/resources"));
        TinkarMsg textNamed = TinkarMsg.newBuilder().setConceptChronology(ConceptChronology.newBuilder()
                .setPublicId(PublicId.newBuilder().addUuids(CONCEPT_A.toString()))
                .addConceptVersions(ConceptVersion.newBuilder()
                        .setStampChronologyPublicId(PublicId.newBuilder().addUuids(STAMP.toString())))).build();
        Path format1 = writeFormat2(folder.resolve("a format 1 ike-cs.zip"), List.of(stamp(STAMP), textNamed), false);
        // A writer's name carries ꞉ (U+A789) for each colon of its time.
        Path format2 = writeFormat2(folder.resolve("b User 20261012T17꞉03꞉04PDT 8hP ike-cs.zip"), journal(), true);
        Path withoutPatterns = folder.resolve("c format 3 ike-cs.zip");
        new ChangeSetCompaction(format2.toFile(), withoutPatterns.toFile()).call();
        Path current = folder.resolve("d current ike-cs.zip");
        new ChangeSetCompaction(format2.toFile(), current.toFile(), KNOWS_IT).call();
        Path empty = Files.createFile(folder.resolve("e empty ike-cs.zip"));
        Path notAZip = Files.writeString(folder.resolve("f notes.zip"), "not a zip");
        Files.writeString(folder.resolve("journal.zip.spool"), "a session's journal, never chosen");
        return new Folder(folder, format1, format2, withoutPatterns, current, empty, notAZip);
    }

    @Test
    void eachFileIsAssessedByWhatItNeeds() throws Exception {
        Folder folder = folder();
        assertEquals(ChangeSetUpgrade.Need.FORMAT_1, ChangeSetUpgrade.assess(folder.format1()).need());
        assertEquals(ChangeSetUpgrade.Need.FORMAT_2, ChangeSetUpgrade.assess(folder.format2()).need());
        assertEquals(ChangeSetUpgrade.Need.FORMAT_3_WITHOUT_PATTERNS,
                ChangeSetUpgrade.assess(folder.format3WithoutPatterns()).need());
        assertEquals(ChangeSetUpgrade.Need.CURRENT, ChangeSetUpgrade.assess(folder.current()).need());
        assertEquals(ChangeSetUpgrade.Need.EMPTY, ChangeSetUpgrade.assess(folder.empty()).need());
        assertEquals(ChangeSetUpgrade.Need.NOT_A_CHANGE_SET, ChangeSetUpgrade.assess(folder.notAZip()).need());
    }

    @Test
    void aZipWithNoManifestIsNotAChangeSet() throws Exception {
        Path zip = dir.resolve("other.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("readme.txt"));
            out.write("hello".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        ChangeSetUpgrade.Assessment assessment = ChangeSetUpgrade.assess(zip);
        assertEquals(ChangeSetUpgrade.Need.NOT_A_CHANGE_SET, assessment.need());
        assertTrue(assessment.detail().contains(ChangeSetFormat.MANIFEST), assessment.detail());
    }

    @Test
    void aFolderContributesItsOwnZipFilesInNameOrder() throws Exception {
        Folder folder = folder();
        Files.createDirectories(folder.path().resolve("nested"));
        Files.copy(folder.format2(), folder.path().resolve("nested/z ike-cs.zip"));
        List<Path> files = ChangeSetUpgrade.changeSets(List.of(folder.path(), folder.format1()));
        assertEquals(List.of(folder.format1(), folder.format2(), folder.format3WithoutPatterns(), folder.current(),
                        folder.empty(), folder.notAZip()).stream().map(path -> path.toAbsolutePath().normalize()).toList(),
                files, "each once, no spool, no subfolder");
    }

    @Test
    void planningConvertsIntoTheWorkFolderAndChangesNothingChosen() throws Exception {
        Folder folder = folder();
        byte[] before = Files.readAllBytes(folder.format2());
        ChangeSetUpgrade.Plan plan = plan(folder, KNOWS_IT);
        assertArrayEquals(before, Files.readAllBytes(folder.format2()));
        assertEquals(3, plan.replaceable().size(), plan.markdown());
        assertTrue(plan.clean());
        assertEquals(1, plan.count(ChangeSetUpgrade.Need.EMPTY));
        assertEquals(1, plan.count(ChangeSetUpgrade.Need.NOT_A_CHANGE_SET));
        assertEquals(1, plan.count(ChangeSetUpgrade.Need.CURRENT));
        for (ChangeSetUpgrade.Converted upgrade : plan.replaceable()) {
            assertTrue(upgrade.upgraded().startsWith(dir.resolve("work")));
            assertEquals(ChangeSetUpgrade.Need.CURRENT, ChangeSetUpgrade.assess(upgrade.upgraded()).need());
        }
        String report = plan.markdown();
        assertTrue(report.contains("3 change set(s) upgraded to format 3 (1 from format 1, 1 from format 2, 1 from format 3 without patterns)"), report);
        assertTrue(report.contains("1 empty file(s) skipped"), report);
        assertTrue(report.contains("`f notes.zip`"), report);
    }

    @Test
    void archivingThenReplacingUpgradesEveryOriginalInPlace() throws Exception {
        Folder folder = folder();
        FileTime modified = FileTime.fromMillis(1_700_000_000_000L);
        Files.setLastModifiedTime(folder.format2(), modified);
        byte[] original = Files.readAllBytes(folder.format2());
        ChangeSetUpgrade.Plan plan = plan(folder, KNOWS_IT);

        ChangeSetUpgrade.Archive archive = plan.archive(dir.resolve("ike-archive"), LocalDate.of(2026, 10, 24));
        assertEquals("2026-10-24-change-set-originals.tar.gz", archive.file().getFileName().toString());
        Path extracted = extract(archive.file());
        Path top = extracted.resolve("2026-10-24-change-set-originals");
        assertTrue(Files.readString(top.resolve("README.adoc")).startsWith("= Change-set originals, 2026-10-24"));
        String manifest = Files.readString(top.resolve("manifest-sha256.txt"));
        assertEquals(3, manifest.lines().count(), manifest);
        Path absolute = folder.format2().toAbsolutePath().normalize();
        assertTrue(manifest.contains(ChangeSetUpgrade.sha256(folder.format2()) + "  " + absolute), manifest);
        Path archived = top.resolve("root").resolve(ChangeSetUpgrade.rootRelative(absolute));
        assertArrayEquals(original, Files.readAllBytes(archived), "the original, by its absolute path and its ꞉ name");

        ChangeSetUpgrade.Replacement replacement = plan.replace(archive);
        assertEquals(3, replacement.replaced().size(), replacement.markdown());
        assertTrue(replacement.skipped().isEmpty());
        for (Path changeSet : List.of(folder.format1(), folder.format2(), folder.format3WithoutPatterns())) {
            assertEquals(ChangeSetUpgrade.Need.CURRENT, ChangeSetUpgrade.assess(changeSet).need(), changeSet.toString());
            assertTrue(new ChangeSetVerification(changeSet.toFile()).call().ok());
        }
        assertEquals(sequenceOf(folder.format2(), CONCEPT_PATTERN[0]), patternOf(folder.format2(), NEVER_WRITTEN));
        assertEquals(recordsByComponent(folder.current()).keySet(), recordsByComponent(folder.format2()).keySet());
        assertEquals(modified, Files.getLastModifiedTime(folder.format2()), "the original's time is kept");
        assertEquals(0, Files.size(folder.empty()), "an empty file is left alone");
        assertFalse(Files.exists(dir.resolve("work")), "the work folder goes once every upgrade is placed");
    }

    @Test
    void aChangeSetNamingComponentsTheBaseDoesNotHoldIsLeftAsItWas() throws Exception {
        Folder folder = folder();
        byte[] before = Files.readAllBytes(folder.format2());
        ChangeSetUpgrade.Plan plan = plan(folder, KNOWS_NOTHING);
        assertFalse(plan.clean());
        assertEquals(1, plan.replaceable().size(), "only the text-named format 1 refers to nothing unknown");
        assertEquals(2, plan.incomplete().size());
        assertTrue(plan.markdown().contains("`" + NEVER_WRITTEN + "`"), "the component is named, never guessed");

        ChangeSetUpgrade.Replacement replacement = plan.replace(plan.archive(dir.resolve("ike-archive"), LocalDate.of(2026, 10, 24)));
        assertEquals(List.of(folder.format1().toAbsolutePath().normalize()), replacement.replaced());
        assertArrayEquals(before, Files.readAllBytes(folder.format2()));
    }

    @Test
    void anOriginalChangedAfterArchivingIsNotReplaced() throws Exception {
        Folder folder = folder();
        ChangeSetUpgrade.Plan plan = plan(folder, KNOWS_IT);
        ChangeSetUpgrade.Archive archive = plan.archive(dir.resolve("ike-archive"), LocalDate.of(2026, 10, 24));
        writeFormat2(folder.format2(), List.of(stamp(STAMP)), true);
        ChangeSetUpgrade.Replacement replacement = plan.replace(archive);
        assertEquals(2, replacement.replaced().size());
        assertEquals(1, replacement.skipped().size());
        assertEquals("changed since it was archived", replacement.skipped().getFirst().reason());
        assertEquals(ChangeSetUpgrade.Need.FORMAT_2, ChangeSetUpgrade.assess(folder.format2()).need());
    }

    @Test
    void aSecondArchiveTheSameDayTakesTheNextName() throws Exception {
        Folder folder = folder();
        Path archives = dir.resolve("ike-archive");
        LocalDate date = LocalDate.of(2026, 10, 24);
        plan(folder, KNOWS_IT).archive(archives, date);
        ChangeSetUpgrade.Archive second = plan(folder, KNOWS_IT).archive(archives, date);
        assertEquals("2026-10-24-change-set-originals-2.tar.gz", second.file().getFileName().toString());
        assertTrue(Files.isDirectory(extract(second.file()).resolve("2026-10-24-change-set-originals-2/root")));
    }

    @Test
    void aPaxRecordCountsItsOwnLength() {
        String record = TarGzWriter.paxRecord("path", "x".repeat(93));
        assertEquals(record.getBytes(StandardCharsets.UTF_8).length, Integer.parseInt(record.substring(0, record.indexOf(' '))));
        String unicode = TarGzWriter.paxRecord("path", "User 20261012T17꞉03");
        assertEquals(unicode.getBytes(StandardCharsets.UTF_8).length, Integer.parseInt(unicode.substring(0, unicode.indexOf(' '))));
    }

    private ChangeSetUpgrade.Plan plan(Folder folder, Function<UUID[], Optional<UUID[]>> patternOf) throws IOException {
        Path work = dir.resolve("work");
        return ChangeSetUpgrade.plan(ChangeSetUpgrade.changeSets(List.of(folder.path())), patternOf, "the test base",
                work, (index, count, changeSet) -> { });
    }

    /** Extracts with the system's own tar, which is what a person restoring will use. */
    private Path extract(Path archive) throws Exception {
        Path into = Files.createTempDirectory(dir, "extracted");
        Process tar = new ProcessBuilder("tar", "-xzf", archive.toString(), "-C", into.toString())
                .redirectErrorStream(true).start();
        String output = new String(tar.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, tar.waitFor(), output);
        return into;
    }
}
