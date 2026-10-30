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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * An NCBI Gene origin: the genes of some organisms, beside the {@code manifest.json} that says
 * how they were retrieved ({@link OriginManifest}). The manifest's format says what else the
 * origin holds:
 * <ul>
 *   <li>{@link OriginManifest.Format#DATASETS_REPORTS}: the gene reports NCBI Datasets returned,
 *   kept exactly as returned, one per line of {@code genes.jsonl};
 *   {@link NcbiGeneOriginBuilder} writes one;</li>
 *   <li>{@link OriginManifest.Format#BULK_FILES}: rows filtered verbatim from NCBI Gene's bulk
 *   files ({@code gene_info.tsv}, {@code gene2refseq.tsv}, {@code gene_summary.tsv}) and NCBI
 *   Taxonomy's names ({@code taxonomy_names.dmp}), joined into genes by {@link BulkGeneReader};
 *   {@link NcbiGeneBulkOriginBuilder} writes one.</li>
 * </ul>
 * The origin is a directory or a zip, and its files are found by name at any depth, each exactly
 * once, so the builders' layout ({@code ncbi-gene-origin-20261020/manifest.json}) and the files
 * alone both read.
 */
public final class NcbiGeneOrigin implements AutoCloseable {

    /** The manifest's file name. */
    public static final String MANIFEST_FILE = "manifest.json";
    /** The reports' file name: JSON Lines, one Datasets gene report per line. */
    public static final String REPORTS_FILE = "genes.jsonl";
    /** The file names a bulk-file origin holds beside its manifest. */
    public static final List<String> BULK_FILES = List.of(BulkGeneReader.GENE_INFO_FILE,
            BulkGeneReader.GENE2REFSEQ_FILE, BulkGeneReader.GENE_SUMMARY_FILE, BulkGeneReader.TAXONOMY_NAMES_FILE);

    private static final Pattern DATE_IN_NAME = Pattern.compile("(?<!\\d)(\\d{8})(?!\\d)");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A file of the origin that can be opened, and where it was found. */
    interface OriginFile {
        /**
         * Opens the file.
         *
         * @return its content, to be closed
         * @throws IOException when it cannot be opened
         */
        InputStream open() throws IOException;

        /**
         * Where the file was found, for messages.
         *
         * @return the path, or the archive and entry
         */
        String where();
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
    }

    private final Path path;
    private final List<OriginFile> manifests = new ArrayList<>();
    private final List<OriginFile> reports = new ArrayList<>();
    private final Map<String, List<OriginFile>> bulkFiles = new LinkedHashMap<>();
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
            switch (origin.manifest().format()) {
                case DATASETS_REPORTS -> origin.requireOne(REPORTS_FILE, origin.reports);
                case BULK_FILES -> {
                    for (String file : BULK_FILES) {
                        origin.requireOne(file, origin.bulkFiles.getOrDefault(file, List.of()));
                    }
                }
            }
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
            OriginFile located = manifests.getFirst();
            try (InputStream in = located.open()) {
                manifest = OriginManifest.parse(MAPPER.readTree(in));
            } catch (IOException e) {
                throw new ImportException("Cannot read " + located.where(), e);
            }
        }
        return manifest;
    }

    /**
     * The genes, in file order: each Datasets report parsed, or each gene of the bulk files joined.
     * The stream must be closed.
     *
     * @return the genes
     * @throws ImportException when a file, or a gene in it, cannot be read
     */
    public Stream<GeneReport> genes() {
        return switch (manifest().format()) {
            case DATASETS_REPORTS -> rawReports().map(GeneReport::parse);
            case BULK_FILES -> BulkGeneReader.genes(bulkFile(BulkGeneReader.GENE_INFO_FILE),
                    bulkFile(BulkGeneReader.GENE2REFSEQ_FILE), bulkFile(BulkGeneReader.GENE_SUMMARY_FILE),
                    bulkFile(BulkGeneReader.TAXONOMY_NAMES_FILE));
        };
    }

    /**
     * The reports as NCBI Datasets returned them, in file order, for a
     * {@link OriginManifest.Format#DATASETS_REPORTS} origin. The stream must be closed.
     *
     * @return the reports' JSON
     * @throws ImportException when the file cannot be read, or the origin holds bulk files
     */
    public Stream<JsonNode> rawReports() {
        if (manifest().format() != OriginManifest.Format.DATASETS_REPORTS) {
            throw new ImportException("The NCBI Gene origin " + path + " holds bulk files, not Datasets reports");
        }
        OriginFile located = reports.getFirst();
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
     * The genes, read into a list, in file order; {@link #genes()} streams them.
     *
     * @return the genes
     * @throws ImportException when a file, or a gene in it, cannot be read
     */
    public List<GeneReport> reports() {
        try (Stream<GeneReport> genes = genes()) {
            return genes.toList();
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

    private OriginFile bulkFile(String fileName) {
        return bulkFiles.get(fileName).getFirst();
    }

    private void requireOne(String fileName, List<OriginFile> found) {
        if (found.size() != 1) {
            throw new ImportException("Expected one " + fileName + " in the NCBI Gene origin " + path + ", found "
                    + found.stream().map(OriginFile::where).toList());
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

    private void classify(String fileName, OriginFile located) {
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.equals(MANIFEST_FILE)) {
            manifests.add(located);
        } else if (name.equals(REPORTS_FILE)) {
            reports.add(located);
        } else if (BULK_FILES.contains(name)) {
            bulkFiles.computeIfAbsent(name, key -> new ArrayList<>()).add(located);
        }
    }
}
