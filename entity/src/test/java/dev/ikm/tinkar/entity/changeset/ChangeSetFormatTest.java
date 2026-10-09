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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A changeset's manifest is read where the exporter leaves it: last, after the records. */
class ChangeSetFormatTest {

    @TempDir
    Path dir;

    @Test
    void readsTheManifestWrittenAfterTheRecords() throws IOException {
        Path changeSet = dir.resolve("records-then-manifest.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(changeSet))) {
            out.putNextEntry(new ZipEntry("records"));
            out.write(new byte[8 * 1024 * 1024]);
            out.closeEntry();
            out.putNextEntry(new ZipEntry(ChangeSetFormat.IDENTITY_INDEX));
            out.write(new byte[1024]);
            out.closeEntry();
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().putValue("Total-Count", "60312654");
            manifest.getMainAttributes().putValue(ChangeSetFormat.VERSION_ATTRIBUTE, "2");
            out.putNextEntry(new ZipEntry(ChangeSetFormat.MANIFEST));
            manifest.write(out);
            out.closeEntry();
        }

        try (ZipFile zip = new ZipFile(changeSet.toFile())) {
            Optional<Manifest> read = ChangeSetFormat.manifest(zip);
            assertTrue(read.isPresent());
            assertEquals("60312654", read.get().getMainAttributes().getValue("Total-Count"));
            assertEquals(2, ChangeSetFormat.version(read.get().getMainAttributes(), changeSet.getFileName().toString()));
        }
        assertTrue(ChangeSetFormat.hasIdentityIndex(changeSet.toFile()));
    }

    @Test
    void format3EntriesAreListedByTheManifestAndReadThroughGzip() throws IOException {
        Path changeSet = dir.resolve("format3.zip");
        byte[] records = "not really records, but bytes the gzip carries".getBytes();
        java.io.ByteArrayOutputStream gzipped = new java.io.ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(gzipped)) {
            gz.write(records);
        }
        String first = ChangeSetFormat.recordEntryName(1, "patterns");
        String second = ChangeSetFormat.recordEntryName(2, "stamps");
        assertEquals("records/0001-patterns.pb.gz", first);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(changeSet))) {
            for (String name : new String[]{second, first}) { // zip order is not the manifest's
                ZipEntry entry = new ZipEntry(name);
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(gzipped.size());
                entry.setCompressedSize(gzipped.size());
                java.util.zip.CRC32 crc = new java.util.zip.CRC32();
                crc.update(gzipped.toByteArray());
                entry.setCrc(crc.getValue());
                out.putNextEntry(entry);
                out.write(gzipped.toByteArray());
                out.closeEntry();
            }
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().putValue(ChangeSetFormat.VERSION_ATTRIBUTE, "3");
            manifest.getMainAttributes().putValue(ChangeSetFormat.RECORD_ENTRIES_ATTRIBUTE, "2");
            manifest.getMainAttributes().putValue(ChangeSetFormat.entryAttribute(1), first + " 57 abc");
            manifest.getMainAttributes().putValue(ChangeSetFormat.entryAttribute(2), second + " 10784 def");
            out.putNextEntry(new ZipEntry(ChangeSetFormat.MANIFEST));
            manifest.write(out);
            out.closeEntry();
        }
        try (ZipFile zip = new ZipFile(changeSet.toFile())) {
            Manifest manifest = ChangeSetFormat.manifest(zip).orElseThrow();
            assertEquals(3, ChangeSetFormat.version(manifest.getMainAttributes(), "format3.zip"));
            var entries = ChangeSetFormat.recordEntries(zip, manifest);
            assertEquals(2, entries.size());
            assertEquals(first, entries.get(0).entry().getName());
            assertEquals(57, entries.get(0).count());
            assertEquals("def", entries.get(1).sha256());
            try (var in = ChangeSetFormat.openRecords(zip, entries.get(0).entry())) {
                assertArrayEquals(records, in.readAllBytes());
            }
        }
    }

    @Test
    void aZipWithoutAManifestHasNone() throws IOException {
        Path changeSet = dir.resolve("no-manifest.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(changeSet))) {
            out.putNextEntry(new ZipEntry("records"));
            out.write(new byte[16]);
            out.closeEntry();
        }
        try (ZipFile zip = new ZipFile(changeSet.toFile())) {
            assertTrue(ChangeSetFormat.manifest(zip).isEmpty());
        }
    }
}
