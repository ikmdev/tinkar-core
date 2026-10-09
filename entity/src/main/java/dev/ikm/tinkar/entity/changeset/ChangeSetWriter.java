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

import dev.ikm.tinkar.common.service.EntityCountSummary;
import dev.ikm.tinkar.schema.TinkarMsg;
import dev.ikm.tinkar.terms.KernelTerm;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.Manifest;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * What every writer of a format-3 change set shares: a record entry written as a gzip spool
 * with the CRC and SHA-256 of its stored bytes, stored in the zip as it is; and the manifest
 * that lists the entries. The exporter writes from a store, the compaction from another change set.
 */
public final class ChangeSetWriter {
    private ChangeSetWriter() {
    }

    /**
     * The deflate level of a record entry's gzip stream: 3, where a measured 256 MB of SNOMED CT
     * records compressed at 149 MB/s to 44.4 percent, against 79 MB/s to 42.3 percent at the
     * default 6 and 169 MB/s to 45.7 percent at 1 (2026-10-08). Set {@code ike.export.deflateLevel}
     * to trade size for time.
     */
    public static final int DEFLATE_LEVEL = Integer.getInteger("ike.export.deflateLevel", 3);

    /** A gzip stream at {@link #DEFLATE_LEVEL}, which every record entry of a format-3 change set is written with. */
    public static GZIPOutputStream gzip(OutputStream out) throws IOException {
        return new GZIPOutputStream(out, 1 << 16) {
            {
                def.setLevel(DEFLATE_LEVEL);
            }
        };
    }

    /** A record entry as written: its name, its spool, and what the manifest says about it. */
    public record Entry(String name, Path spool, long size, long crc, String sha256, long count) {
    }

    /**
     * A gzip spool of records for one entry, checksumming and digesting the gzip bytes as they
     * are written, so the entry is stored by the zip without a second pass. The manifest's
     * SHA-256 is of the stored bytes, the gzip stream as the zip holds it.
     */
    public static final class Spool implements Closeable {
        private final String name;
        private final Path path;
        private final CRC32 crc = new CRC32();
        private final MessageDigest sha256;
        private final OutputStream out;
        private long count;

        public Spool(Path directory, int ordinal, String label) throws IOException {
            this.name = ChangeSetFormat.recordEntryName(ordinal, label);
            this.path = directory.resolve(String.format("%04d.pb.gz", ordinal));
            try {
                this.sha256 = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
            OutputStream file = new BufferedOutputStream(Files.newOutputStream(path), 1 << 20);
            CheckedOutputStream checked = new CheckedOutputStream(file, crc);
            DigestOutputStream digested = new DigestOutputStream(checked, sha256);
            this.out = gzip(digested);
        }

        public void write(TinkarMsg record) throws IOException {
            record.writeDelimitedTo(out);
            count++;
        }

        public long count() {
            return count;
        }

        /** Closes the spool and describes it; the entry is then stored with {@link #store}. */
        public Entry finish() throws IOException {
            out.close();
            return new Entry(name, path, Files.size(path), crc.getValue(), HexFormat.of().formatHex(sha256.digest()), count);
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }

    /** Stores a finished spool in the zip as it is, uncompressed by the zip, and deletes the spool. */
    public static void store(ZipOutputStream zos, Entry entry) throws IOException {
        ZipEntry zipEntry = new ZipEntry(entry.name());
        zipEntry.setMethod(ZipEntry.STORED);
        zipEntry.setSize(entry.size());
        zipEntry.setCompressedSize(entry.size());
        zipEntry.setCrc(entry.crc());
        zos.putNextEntry(zipEntry);
        Files.copy(entry.spool(), zos);
        zos.closeEntry();
        Files.delete(entry.spool());
    }

    /**
     * The format-3 manifest: the version, the counts, the component count, the record entries
     * with their counts and hashes, then {@code sections}: the module and author entries, as
     * the exporter renders them from the store or the compaction copies them from its source.
     */
    public static String manifestContent(EntityCountSummary counts, List<Entry> entries, long componentCount, String sections) {
        StringBuilder manifest = new StringBuilder()
                .append(ChangeSetFormat.VERSION_ATTRIBUTE).append(": ").append(ChangeSetFormat.CURRENT_VERSION).append("\n")
                .append("Packager-Name: ").append(KernelTerm.KOMET_USER.description()).append("\n")
                .append("Package-Date: ").append(LocalDateTime.now(Clock.systemUTC())).append("\n")
                .append("Total-Count: ").append(counts.getTotalCount()).append("\n")
                .append("Concept-Count: ").append(counts.conceptCount()).append("\n")
                .append("Semantic-Count: ").append(counts.semanticCount()).append("\n")
                .append("Pattern-Count: ").append(counts.patternCount()).append("\n")
                .append("Stamp-Count: ").append(counts.stampCount()).append("\n")
                .append(ChangeSetFormat.COMPONENT_COUNT_ATTRIBUTE).append(": ").append(componentCount).append("\n")
                .append(ChangeSetFormat.RECORD_ENTRIES_ATTRIBUTE).append(": ").append(entries.size()).append("\n");
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            manifest.append(ChangeSetFormat.entryAttribute(i + 1)).append(": ")
                    .append(entry.name()).append(' ').append(entry.count()).append(' ').append(entry.sha256()).append("\n");
        }
        manifest.append(sections).append("\n"); // the final newline the Manifest spec requires
        return manifest.toString();
    }

    /**
     * The format-2 manifest an expansion writes: version {@value ChangeSetFormat#IDENTITY_INDEX_VERSION},
     * the counts, then {@code sections}.
     */
    public static String identityIndexManifestContent(EntityCountSummary counts, String sections) {
        return new StringBuilder()
                .append(ChangeSetFormat.VERSION_ATTRIBUTE).append(": ").append(ChangeSetFormat.IDENTITY_INDEX_VERSION).append("\n")
                .append("Packager-Name: ").append(KernelTerm.KOMET_USER.description()).append("\n")
                .append("Package-Date: ").append(LocalDateTime.now(Clock.systemUTC())).append("\n")
                .append("Total-Count: ").append(counts.getTotalCount()).append("\n")
                .append("Concept-Count: ").append(counts.conceptCount()).append("\n")
                .append("Semantic-Count: ").append(counts.semanticCount()).append("\n")
                .append("Pattern-Count: ").append(counts.patternCount()).append("\n")
                .append("Stamp-Count: ").append(counts.stampCount()).append("\n")
                .append(sections).append("\n")
                .toString();
    }

    /**
     * The named sections of a manifest rendered again: the module and author entries an
     * importer reads for its dependency check, carried from a source change set to the one
     * written from it.
     */
    public static String sections(Manifest manifest) {
        StringBuilder text = new StringBuilder();
        manifest.getEntries().forEach((name, attributes) -> {
            text.append("\nName: ").append(name).append("\n");
            attributes.forEach((key, value) -> text.append(key).append(": ").append(value).append("\n"));
        });
        return text.toString();
    }

    /** The count summary of records tallied by kind: concepts, semantics, patterns, stamps. */
    public static EntityCountSummary counts(long[] byKind) {
        return new EntityCountSummary(byKind[0], byKind[1], byKind[2], byKind[3]);
    }

    /** The tally index of a record's kind, for {@link #counts}. */
    public static int kindIndex(TinkarMsg record) {
        return switch (record.getValueCase()) {
            case CONCEPT_CHRONOLOGY -> 0;
            case SEMANTIC_CHRONOLOGY -> 1;
            case PATTERN_CHRONOLOGY -> 2;
            case STAMP_CHRONOLOGY -> 3;
            case VALUE_NOT_SET -> throw new IllegalStateException("Tinkar message value not set");
        };
    }
}
