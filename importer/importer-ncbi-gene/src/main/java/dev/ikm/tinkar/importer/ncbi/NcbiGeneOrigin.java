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
package dev.ikm.tinkar.importer.ncbi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ikm.tinkar.importer.ImportException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * An NCBI Gene origin: the gene reports NCBI Datasets returned for the genes of some organisms,
 * kept exactly as returned, one per line of {@code genes.jsonl}, beside the {@code manifest.json}
 * that says how they were retrieved ({@link OriginManifest}). The origin is a directory or a zip,
 * and its two files are found by name at any depth, each exactly once, so the builder's layout
 * ({@code ncbi-gene-origin-20261020/manifest.json}) and the files alone both read.
 * {@link NcbiGeneOriginBuilder} writes one.
 */
public final class NcbiGeneOrigin implements AutoCloseable {

    /** The manifest's file name. */
    public static final String MANIFEST_FILE = "manifest.json";
    /** The reports' file name: JSON Lines, one Datasets gene report per line. */
    public static final String REPORTS_FILE = "genes.jsonl";

    private static final Pattern DATE_IN_NAME = Pattern.compile("(?<!\\d)(\\d{8})(?!\\d)");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A located file that can be opened, and where it was found. */
    private interface Located {
        InputStream open() throws IOException;

        String where();
    }

    private record OnDisk(Path file) implements Located {
        @Override
        public InputStream open() throws IOException {
            return Files.newInputStream(file);
        }

        @Override
        public String where() {
            return file.toString();
        }
    }

    private record InArchive(ZipFile zip, ZipEntry entry) implements Located {
        @Override
        public InputStream open() throws IOException {
            return zip.getInputStream(entry);
        }

        @Override
        public String where() {
            return zip.getName() + "!/" + entry.getName();
        }
    }

    private final Path path;
    private final List<Located> manifests = new ArrayList<>();
    private final List<Located> reports = new ArrayList<>();
    private ZipFile archive;
    private OriginManifest manifest;

    private NcbiGeneOrigin(Path path) {
        this.path = path;
    }

    /**
     * Finds an origin's files.
     *
     * @param path the origin: a directory or a zip
     * @return the origin, to be closed when read
     * @throws ImportException when the origin cannot be read, or a file is missing or found twice
     */
    public static NcbiGeneOrigin open(Path path) {
        NcbiGeneOrigin origin = new NcbiGeneOrigin(path);
        try {
            if (Files.isDirectory(path)) {
                origin.scanDirectory(path);
            } else {
                origin.scanArchive(path);
            }
            origin.requireOne(MANIFEST_FILE, origin.manifests);
            origin.requireOne(REPORTS_FILE, origin.reports);
            return origin;
        } catch (IOException e) {
            origin.close();
            throw new ImportException("Cannot read the NCBI Gene origin " + path, e);
        } catch (RuntimeException e) {
            origin.close();
            throw e;
        }
    }

    /**
     * The origin's name, for the report: the directory's or zip's own name.
     *
     * @return the name
     */
    public String name() {
        return path.getFileName().toString();
    }

    /**
     * The date the origin's name carries, as the builder names one
     * ({@code ncbi-gene-origin-20261020}).
     *
     * @return the date, if the name carries one
     */
    public Optional<LocalDate> dateInName() {
        Matcher matcher = DATE_IN_NAME.matcher(name());
        while (matcher.find()) {
            try {
                return Optional.of(LocalDate.parse(matcher.group(1), DateTimeFormatter.BASIC_ISO_DATE));
            } catch (DateTimeParseException notADate) {
                // eight digits that are not a date: keep looking
            }
        }
        return Optional.empty();
    }

    /**
     * The manifest, read once.
     *
     * @return the manifest
     * @throws ImportException when it cannot be read
     */
    public OriginManifest manifest() {
        if (manifest == null) {
            Located located = manifests.getFirst();
            try (InputStream in = located.open()) {
                manifest = OriginManifest.parse(MAPPER.readTree(in));
            } catch (IOException e) {
                throw new ImportException("Cannot read " + located.where(), e);
            }
        }
        return manifest;
    }

    /**
     * The reports as NCBI returned them, in file order. The stream must be closed.
     *
     * @return the reports' JSON
     * @throws ImportException when the file cannot be read
     */
    public Stream<JsonNode> rawReports() {
        Located located = reports.getFirst();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(located.open(), StandardCharsets.UTF_8), 1 << 16);
            return reader.lines().filter(line -> !line.isBlank()).map(line -> {
                try {
                    return MAPPER.readTree(line);
                } catch (IOException e) {
                    throw new ImportException("Cannot read a gene report line in " + located.where(), e);
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

    /**
     * The reports, parsed, in file order.
     *
     * @return the genes
     * @throws ImportException when the file, or a report in it, cannot be read
     */
    public List<GeneReport> reports() {
        try (Stream<JsonNode> raw = rawReports()) {
            return raw.map(GeneReport::parse).toList();
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

    private void requireOne(String fileName, List<Located> found) {
        if (found.size() != 1) {
            throw new ImportException("Expected one " + fileName + " in the NCBI Gene origin " + path + ", found "
                    + found.stream().map(Located::where).toList());
        }
    }

    private void scanDirectory(Path directory) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(directory)) {
            files = walk.filter(Files::isRegularFile).filter(file -> !file.toString().contains("__MACOSX")).toList();
        }
        for (Path file : files) {
            classify(file.getFileName().toString(), new OnDisk(file));
        }
    }

    private void scanArchive(Path archivePath) throws IOException {
        archive = new ZipFile(archivePath.toFile(), StandardCharsets.UTF_8);
        List<? extends ZipEntry> entries = archive.stream()
                .filter(entry -> !entry.isDirectory() && !entry.getName().contains("__MACOSX")).toList();
        for (ZipEntry entry : entries) {
            String entryName = entry.getName();
            classify(entryName.substring(entryName.lastIndexOf('/') + 1), new InArchive(archive, entry));
        }
    }

    private void classify(String fileName, Located located) {
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.equals(MANIFEST_FILE)) {
            manifests.add(located);
        } else if (name.equals(REPORTS_FILE)) {
            reports.add(located);
        }
    }
}
