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
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ikm.tinkar.importer.ImportException;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A GSRS origin: the dump GSRS publishes ({@code dump-public-2026-08-06.gsrs}), kept byte for
 * byte, beside the {@code META-INF/MANIFEST.MF} that describes it ({@link OriginManifest}).
 * {@link GsrsOriginBuilder} writes one.
 *
 * <p>The dump is a gzip of one substance record per line, each line two tab-separated empty
 * fields and then the record's JSON. The records are read from it as published, one line at a
 * time, so the origin is never unpacked.
 *
 * <p>The origin is a zip, or a directory holding the same two files. The manifest is at
 * {@code META-INF/MANIFEST.MF}; the dump is found by its {@code .gsrs} suffix at any depth, and
 * must be the one file the manifest names.
 */
public final class GsrsOrigin implements AutoCloseable {

    /** Where the manifest is. */
    public static final String MANIFEST = "META-INF/MANIFEST.MF";
    /** The suffix of the dump GSRS publishes. */
    public static final String DUMP_SUFFIX = ".gsrs";
    /** What precedes each record's JSON on its line: two empty tab-separated fields. */
    public static final String LINE_PREFIX = "\t\t";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A file of the origin that can be opened, and where it was found. */
    private interface OriginFile {
        InputStream open() throws IOException;

        String where();

        String fileName();
    }

    private record OnDisk(Path file) implements OriginFile {
        @Override
        public InputStream open() throws IOException {
            return Files.newInputStream(file);
        }

        @Override
        public String where() {
            return file.toString();
        }

        @Override
        public String fileName() {
            return file.getFileName().toString();
        }
    }

    private record InArchive(ZipFile zip, ZipEntry entry) implements OriginFile {
        @Override
        public InputStream open() throws IOException {
            return zip.getInputStream(entry);
        }

        @Override
        public String where() {
            return zip.getName() + "!/" + entry.getName();
        }

        @Override
        public String fileName() {
            String name = entry.getName();
            return name.substring(name.lastIndexOf('/') + 1);
        }
    }

    /**
     * What one pass over a dump measured.
     *
     * @param bytes   the dump's size
     * @param sha256  its SHA-256, lower-case hex
     * @param crc32   its CRC-32, as a zip entry stored without compression records it
     * @param records how many records it holds
     */
    record Measure(long bytes, String sha256, long crc32, long records) {
    }

    private final Path path;
    private OriginFile manifestFile;
    private final List<OriginFile> dumps = new ArrayList<>();
    private ZipFile archive;
    private OriginManifest manifest;

    private GsrsOrigin(Path path) {
        this.path = path;
    }

    /**
     * Finds an origin's files and reads its manifest.
     *
     * @param path the origin: a zip or a directory
     * @return the origin, to be closed when read
     * @throws ImportException when the origin cannot be read, its manifest is missing or of
     *                         another format, or its dump is missing, found twice, or not the
     *                         file the manifest names
     */
    public static GsrsOrigin open(Path path) {
        GsrsOrigin origin = new GsrsOrigin(path);
        try {
            if (Files.isDirectory(path)) {
                origin.scanDirectory(path);
            } else {
                origin.scanArchive(path);
            }
            if (origin.manifestFile == null) {
                throw new ImportException("The GSRS origin " + path + " has no " + MANIFEST);
            }
            if (origin.dumps.size() != 1) {
                throw new ImportException("Expected one " + DUMP_SUFFIX + " dump in the GSRS origin " + path
                        + ", found " + origin.dumps.stream().map(OriginFile::where).toList());
            }
            String named = origin.manifest().fileName();
            if (!origin.dump().fileName().equals(named)) {
                throw new ImportException("The GSRS origin " + path + " holds " + origin.dump().where()
                        + ", but its manifest names " + named);
            }
            return origin;
        } catch (IOException e) {
            origin.close();
            throw new ImportException("Cannot read the GSRS origin " + path, e);
        } catch (RuntimeException e) {
            origin.close();
            throw e;
        }
    }

    /**
     * The origin's name, for the report: the zip's or directory's own name.
     *
     * @return the name
     */
    public String name() {
        return path.getFileName().toString();
    }

    /**
     * The manifest, read once.
     *
     * @return the manifest
     * @throws ImportException when it cannot be read, or is not of this format
     */
    public OriginManifest manifest() {
        if (manifest == null) {
            try (InputStream in = manifestFile.open()) {
                manifest = OriginManifest.parse(new Manifest(in));
            } catch (IOException e) {
                throw new ImportException("Cannot read " + manifestFile.where(), e);
            }
        }
        return manifest;
    }

    /**
     * Checks the dump is the one the manifest describes: its size, SHA-256 and record count.
     * Reads the whole dump.
     *
     * @throws ImportException when the dump cannot be read, or differs from its manifest
     */
    public void verify() {
        Measure measured;
        try (InputStream in = dump().open()) {
            measured = measure(in, dump().where());
        } catch (IOException e) {
            throw new ImportException("Cannot read " + dump().where(), e);
        }
        OriginManifest expected = manifest();
        List<String> differences = new ArrayList<>();
        if (measured.bytes() != expected.bytes()) {
            differences.add(measured.bytes() + " bytes, not " + expected.bytes());
        }
        if (!measured.sha256().equals(expected.sha256())) {
            differences.add("SHA-256 " + measured.sha256() + ", not " + expected.sha256());
        }
        if (measured.records() != expected.records()) {
            differences.add(measured.records() + " records, not " + expected.records());
        }
        if (!differences.isEmpty()) {
            throw new ImportException(dump().where() + " is not the dump its manifest describes: "
                    + String.join("; ", differences));
        }
    }

    /**
     * The substance records, in dump order, each the record's JSON as GSRS published it. The
     * stream must be closed.
     *
     * @return the records
     * @throws ImportException when the dump, or a line of it, cannot be read
     */
    public Stream<JsonNode> records() {
        OriginFile located = dump();
        try {
            BufferedReader reader = lines(located.open());
            long[] lineNumber = {0};
            return reader.lines().map(line -> {
                lineNumber[0]++;
                return line;
            }).filter(line -> !line.isBlank()).map(line -> {
                try {
                    return MAPPER.readTree(recordJson(line, lineNumber[0], located.where()));
                } catch (IOException e) {
                    throw new ImportException("Cannot read the record on line " + lineNumber[0] + " of "
                            + located.where(), e);
                }
            }).onClose(() -> {
                try {
                    reader.close();
                } catch (IOException e) {
                    throw new ImportException("Cannot close " + located.where(), e);
                }
            });
        } catch (IOException e) {
            throw new ImportException("Cannot read " + located.where(), e);
        }
    }

    @Override
    public void close() {
        if (archive != null) {
            try {
                archive.close();
            } catch (IOException ignored) {
                // a read-only archive: nothing to lose
            }
        }
    }

    /**
     * Measures a dump in one pass: its bytes, SHA-256 and CRC-32 as stored, and its records
     * once ungzipped, checking each line is a record.
     *
     * @param raw   the dump as published, to be closed by the caller
     * @param where where the dump is, for messages
     * @return what was measured
     * @throws IOException     when the dump cannot be read
     * @throws ImportException when a line is not a record
     */
    static Measure measure(InputStream raw, String where) throws IOException {
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform has SHA-256", e);
        }
        CountingInputStream counted = new CountingInputStream(raw);
        CheckedInputStream checked = new CheckedInputStream(new DigestInputStream(counted, sha256), new CRC32());
        long records = 0;
        long lineNumber = 0;
        BufferedReader reader = lines(checked);
        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (!line.isBlank()) {
                recordJson(line, lineNumber, where);
                records++;
            }
        }
        // the gzip trailer may be left unread: read it, so the size and digests cover every byte
        checked.transferTo(OutputStream.nullOutputStream());
        return new Measure(counted.count(), HexFormat.of().formatHex(sha256.digest()), checked.getChecksum().getValue(),
                records);
    }

    /**
     * The JSON of one line of a dump: the line without its two leading empty fields.
     *
     * @throws ImportException when the line is not two empty fields and then a JSON object
     */
    static String recordJson(String line, long lineNumber, String where) {
        if (!line.startsWith(LINE_PREFIX) || line.length() == LINE_PREFIX.length()
                || line.charAt(LINE_PREFIX.length()) != '{') {
            throw new ImportException("Line " + lineNumber + " of " + where
                    + " is not two empty tab-separated fields and then a JSON record: "
                    + line.substring(0, Math.min(line.length(), 40)).replace("\t", "\\t"));
        }
        return line.substring(LINE_PREFIX.length());
    }

    private static BufferedReader lines(InputStream raw) throws IOException {
        return new BufferedReader(new InputStreamReader(new GZIPInputStream(raw, 1 << 16), StandardCharsets.UTF_8),
                1 << 16);
    }

    private OriginFile dump() {
        return dumps.getFirst();
    }

    private void scanDirectory(Path directory) throws IOException {
        Path manifestPath = directory.resolve(MANIFEST);
        if (Files.isRegularFile(manifestPath)) {
            manifestFile = new OnDisk(manifestPath);
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.filter(Files::isRegularFile).filter(file -> !file.toString().contains("__MACOSX"))
                    .filter(file -> isDump(file.getFileName().toString())).forEach(file -> dumps.add(new OnDisk(file)));
        }
    }

    private void scanArchive(Path archivePath) throws IOException {
        archive = new ZipFile(archivePath.toFile(), StandardCharsets.UTF_8);
        ZipEntry manifestEntry = archive.getEntry(MANIFEST);
        if (manifestEntry != null) {
            manifestFile = new InArchive(archive, manifestEntry);
        }
        archive.stream().filter(entry -> !entry.isDirectory() && !entry.getName().contains("__MACOSX"))
                .filter(entry -> isDump(entry.getName())).forEach(entry -> dumps.add(new InArchive(archive, entry)));
    }

    private static boolean isDump(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(DUMP_SUFFIX);
    }

    /** Counts the bytes read through it. */
    private static final class CountingInputStream extends FilterInputStream {
        private long count;

        CountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                count++;
            }
            return b;
        }

        @Override
        public int read(byte[] bytes, int off, int len) throws IOException {
            int read = super.read(bytes, off, len);
            if (read > 0) {
                count += read;
            }
            return read;
        }

        long count() {
            return count;
        }
    }
}
