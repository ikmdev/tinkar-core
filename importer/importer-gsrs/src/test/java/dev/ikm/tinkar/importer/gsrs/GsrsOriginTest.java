/*
 * Copyright © 2026 IKE Network (support@ike.network)
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
package dev.ikm.tinkar.importer.gsrs;

import com.fasterxml.jackson.databind.JsonNode;
import dev.ikm.tinkar.importer.ImportException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Building a GSRS origin from a dump laid out as GSRS publishes it, and reading it back: the
 * manifest's description of the dump, the dump kept byte for byte, the records without their
 * leading empty fields, and the origins and dumps that are refused.
 */
class GsrsOriginTest {

    private static final String DUMP_NAME = "dump-public-2026-08-06.gsrs";

    private static final List<String> RECORDS = List.of(
            "{\"uuid\":\"5a2a1b2c-0001-4c1e-9d0a-000000000001\",\"approvalID\":\"LYR4M0NH37\","
                    + "\"substanceClass\":\"chemical\",\"structure\":{\"formula\":\"Na\",\"charge\":1}}",
            "{\"uuid\":\"5a2a1b2c-0002-4c1e-9d0a-000000000002\",\"approvalID\":\"X1N4508KF1\","
                    + "\"substanceClass\":\"chemical\",\"structure\":{\"formula\":\"Cr\",\"charge\":3}}",
            "{\"uuid\":\"5a2a1b2c-0003-4c1e-9d0a-000000000003\",\"approvalID\":\"3G0H8C9362\","
                    + "\"substanceClass\":\"chemical\",\"structure\":{\"formula\":\"Co\",\"charge\":0}}");

    @Test
    @DisplayName("Builds an origin: the manifest first, describing the dump, and the dump stored byte for byte")
    void buildsAnOrigin(@TempDir Path dir) throws IOException {
        Path dump = dump(dir.resolve(DUMP_NAME), RECORDS);
        Path zip = dir.resolve("gsrs-origin-20260806.zip");

        OriginManifest built = GsrsOriginBuilder.build(dump, zip, null, null);

        assertEquals(OriginManifest.FORMAT, built.format());
        assertEquals(GsrsOriginBuilder.SOURCE, built.source());
        assertEquals(URI.create("https://gsrs.ncats.nih.gov/"), built.site());
        assertEquals(URI.create("https://gsrs.ncats.nih.gov/assets/downloads/" + DUMP_NAME), built.url());
        assertEquals(LocalDate.of(2026, 8, 6), built.releaseDate(), "the date in the dump's name");
        assertEquals(DUMP_NAME, built.fileName());
        assertEquals(Files.size(dump), built.bytes());
        assertEquals(sha256(Files.readAllBytes(dump)), built.sha256());
        assertEquals(3, built.records());

        try (ZipFile archive = new ZipFile(zip.toFile())) {
            List<String> names = Collections.list(archive.entries()).stream().map(ZipEntry::getName).toList();
            assertEquals(List.of(GsrsOrigin.MANIFEST, DUMP_NAME), names, "the manifest comes first");
            ZipEntry entry = archive.getEntry(DUMP_NAME);
            assertEquals(ZipEntry.STORED, entry.getMethod(), "the dump is already a gzip");
            try (InputStream in = archive.getInputStream(entry)) {
                assertArrayEquals(Files.readAllBytes(dump), in.readAllBytes(), "the dump as published");
            }
            Manifest manifest;
            try (InputStream in = archive.getInputStream(archive.getEntry(GsrsOrigin.MANIFEST))) {
                manifest = new Manifest(in);
            }
            assertEquals("1.0", manifest.getMainAttributes().getValue("Manifest-Version"));
            assertEquals("2026-08-06", manifest.getMainAttributes().getValue(OriginManifest.RELEASE_DATE_ATTRIBUTE));
            assertEquals(built.sha256(), manifest.getMainAttributes().getValue(OriginManifest.SHA256_ATTRIBUTE));
        }

        try (GsrsOrigin origin = GsrsOrigin.open(zip)) {
            assertEquals("gsrs-origin-20260806.zip", origin.name());
            assertEquals(built, origin.manifest());
            origin.verify();
            assertEquals(List.of("LYR4M0NH37", "X1N4508KF1", "3G0H8C9362"), approvalIds(origin));
        }
    }

    @Test
    @DisplayName("Building again from the same dump writes the same zip")
    void buildsTheSameZipAgain(@TempDir Path dir) throws IOException {
        Path dump = dump(dir.resolve(DUMP_NAME), RECORDS);
        GsrsOriginBuilder.build(dump, dir.resolve("one/gsrs-origin-20260806.zip"), null, null);
        GsrsOriginBuilder.build(dump, dir.resolve("two/gsrs-origin-20260806.zip"), null, null);
        assertArrayEquals(Files.readAllBytes(dir.resolve("one/gsrs-origin-20260806.zip")),
                Files.readAllBytes(dir.resolve("two/gsrs-origin-20260806.zip")));
    }

    @Test
    @DisplayName("Takes a given URL and date over the defaults, and needs a date when the name has none")
    void takesAGivenUrlAndDate(@TempDir Path dir) throws IOException {
        Path dump = dump(dir.resolve("substances.gsrs"), RECORDS);
        URI mirror = URI.create("https://example.org/mirror/substances.gsrs");

        assertThrows(ImportException.class, () -> GsrsOriginBuilder.build(dump, dir.resolve("none.zip"), null, null));
        OriginManifest built = GsrsOriginBuilder.build(dump, dir.resolve("dated.zip"), mirror, LocalDate.of(2026, 9, 1));

        assertEquals(mirror, built.url());
        assertEquals(LocalDate.of(2026, 9, 1), built.releaseDate());
        assertEquals(Optional.of(LocalDate.of(2026, 8, 6)), GsrsOriginBuilder.dateInName(DUMP_NAME));
        assertEquals(Optional.empty(), GsrsOriginBuilder.dateInName("dump-public-2026-13-45.gsrs"));
    }

    @Test
    @DisplayName("Refuses a dump with a line that is not a record, and leaves no zip behind")
    void refusesALineThatIsNotARecord(@TempDir Path dir) throws IOException {
        Path dump = dir.resolve(DUMP_NAME);
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(dump))) {
            out.write(("\t\t" + RECORDS.get(0) + "\n" + RECORDS.get(1) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        Path zip = dir.resolve("gsrs-origin-20260806.zip");

        ImportException refused = assertThrows(ImportException.class, () -> GsrsOriginBuilder.build(dump, zip, null, null));

        assertTrue(refused.getMessage().contains("Line 2"), refused.getMessage());
        assertFalse(Files.exists(zip));
        assertFalse(Files.exists(dir.resolve("gsrs-origin-20260806.zip.part")));
    }

    @Test
    @DisplayName("Reads a directory holding the manifest and, at any depth, the dump")
    void readsADirectory(@TempDir Path dir) throws IOException {
        Path dump = dump(dir.resolve(DUMP_NAME), RECORDS);
        OriginManifest built = GsrsOriginBuilder.build(dump, dir.resolve("origin.zip"), null, null);
        Path origin = directoryOrigin(dir.resolve("gsrs-origin-20260806"), built, dump);

        try (GsrsOrigin opened = GsrsOrigin.open(origin)) {
            assertEquals(built, opened.manifest());
            opened.verify();
            assertEquals(List.of("LYR4M0NH37", "X1N4508KF1", "3G0H8C9362"), approvalIds(opened));
        }
    }

    @Test
    @DisplayName("Refuses a dump that is not the one its manifest describes")
    void refusesAChangedDump(@TempDir Path dir) throws IOException {
        Path dump = dump(dir.resolve(DUMP_NAME), RECORDS);
        OriginManifest built = GsrsOriginBuilder.build(dump, dir.resolve("origin.zip"), null, null);
        Path changed = dump(dir.resolve("changed").resolve(DUMP_NAME), RECORDS.subList(0, 2));
        Path origin = directoryOrigin(dir.resolve("gsrs-origin-20260806"), built, changed);

        try (GsrsOrigin opened = GsrsOrigin.open(origin)) {
            ImportException refused = assertThrows(ImportException.class, opened::verify);
            assertTrue(refused.getMessage().contains("2 records, not 3"), refused.getMessage());
        }
    }

    @Test
    @DisplayName("Refuses an origin with no manifest, a manifest of another format, another dump, or two dumps")
    void refusesMalformedOrigins(@TempDir Path dir) throws IOException {
        Path dump = dump(dir.resolve(DUMP_NAME), RECORDS);
        OriginManifest built = GsrsOriginBuilder.build(dump, dir.resolve("origin.zip"), null, null);

        Path noManifest = Files.createDirectories(dir.resolve("no-manifest"));
        Files.copy(dump, noManifest.resolve(DUMP_NAME));
        assertThrows(ImportException.class, () -> GsrsOrigin.open(noManifest));

        OriginManifest older = new OriginManifest("gsrs-origin/0", built.source(), built.site(), built.url(),
                built.releaseDate(), built.fileName(), built.bytes(), built.sha256(), built.records());
        Path otherFormat = directoryOrigin(dir.resolve("other-format"), older, dump);
        assertThrows(ImportException.class, () -> GsrsOrigin.open(otherFormat));

        Path renamed = dump(dir.resolve("renamed").resolve("dump-public-2026-09-01.gsrs"), RECORDS);
        Path otherDump = directoryOrigin(dir.resolve("other-dump"), built, renamed);
        assertThrows(ImportException.class, () -> GsrsOrigin.open(otherDump));

        Path twoDumps = directoryOrigin(dir.resolve("two-dumps"), built, dump);
        Files.copy(renamed, twoDumps.resolve("dump-public-2026-09-01.gsrs"));
        assertThrows(ImportException.class, () -> GsrsOrigin.open(twoDumps));
    }

    /** Writes a dump as GSRS publishes one: gzipped, each record after two empty tab-separated fields. */
    private static Path dump(Path file, List<String> records) throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            for (String record : records) {
                out.write(("\t\t" + record + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        return file;
    }

    /** An origin as a directory: the manifest, and the dump one directory down. */
    private static Path directoryOrigin(Path directory, OriginManifest manifest, Path dump) throws IOException {
        Path metaInf = Files.createDirectories(directory.resolve("META-INF"));
        try (OutputStream out = Files.newOutputStream(metaInf.resolve("MANIFEST.MF"))) {
            manifest.toManifest().write(out);
        }
        Path nested = Files.createDirectories(directory.resolve("nested"));
        Files.copy(dump, nested.resolve(dump.getFileName()));
        return directory;
    }

    private static List<String> approvalIds(GsrsOrigin origin) {
        try (Stream<JsonNode> records = origin.records()) {
            return records.map(record -> record.path("approvalID").asText()).toList();
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
