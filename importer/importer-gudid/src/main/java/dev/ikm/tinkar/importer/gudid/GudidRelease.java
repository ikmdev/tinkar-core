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
package dev.ikm.tinkar.importer.gudid;

import dev.ikm.tinkar.importer.ImportException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
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
 * The files of a GUDID release, wherever they are: an unpacked directory, an archive, or a
 * directory or archive holding the archives as published (AccessGUDID's delimited release and
 * the FDA's {@code foiclass.zip}). Files are found by name at any depth, so IKM's origin layout
 * ({@code src/gudid/*.txt}, {@code src/foi/foiclass.txt}) and the publishers' own both read.
 * Each file must be found exactly once.
 */
final class GudidRelease implements AutoCloseable {

    /** The files the importer reads, in the order it reads them (IKM's order). */
    enum ReleaseFile {
        FOI_CLASS("foiclass.txt", Charset.forName("windows-1252")),
        DEVICE("device.txt", StandardCharsets.UTF_8),
        PRODUCT_CODES("productcodes.txt", StandardCharsets.UTF_8),
        IDENTIFIERS("identifiers.txt", StandardCharsets.UTF_8),
        PREMARKET_SUBMISSIONS("premarketsubmissions.txt", StandardCharsets.UTF_8),
        GMDN_TERMS("gmdnterms.txt", StandardCharsets.UTF_8);

        /** The file's name, lower case; matched without regard to case. */
        final String fileName;
        /** The file's encoding: the FDA's classification file is Windows-1252, AccessGUDID's UTF-8. */
        final Charset charset;

        ReleaseFile(String fileName, Charset charset) {
            this.fileName = fileName;
            this.charset = charset;
        }
    }

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

    private static final Pattern DATE_IN_NAME = Pattern.compile("(?<!\\d)(\\d{8})(?!\\d)");

    private final Path path;
    private final Map<ReleaseFile, List<Located>> found = new EnumMap<>(ReleaseFile.class);
    private final List<String> names = new ArrayList<>();
    private final List<ZipFile> archives = new ArrayList<>();
    private final List<Path> extracted = new ArrayList<>();

    private GudidRelease(Path path) {
        this.path = path;
        names.add(path.getFileName().toString());
    }

    /**
     * Finds a release's files.
     *
     * @param path the release: a directory or a zip
     * @return the release, to be closed when read
     * @throws ImportException when the release cannot be read, or a file is missing or found twice
     */
    static GudidRelease open(Path path) {
        GudidRelease release = new GudidRelease(path);
        try {
            if (Files.isDirectory(path)) {
                release.scanDirectory(path);
            } else {
                release.scanArchive(path);
            }
            for (ReleaseFile file : ReleaseFile.values()) {
                List<Located> where = release.found.getOrDefault(file, List.of());
                if (where.size() != 1) {
                    throw new ImportException("Expected one " + file.fileName + " in the GUDID release " + path
                            + ", found " + where.stream().map(Located::where).toList());
                }
            }
            return release;
        } catch (IOException e) {
            release.close();
            throw new ImportException("Cannot read the GUDID release " + path, e);
        } catch (RuntimeException e) {
            release.close();
            throw e;
        }
    }

    /**
     * The rows of a file after its header, each split on {@code |} with empty fields kept. The
     * stream must be closed.
     *
     * @param file the file
     * @return its rows
     */
    Stream<String[]> rows(ReleaseFile file) {
        Located located = found.get(file).getFirst();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(located.open(), file.charset), 1 << 16);
            return reader.lines().skip(1).filter(line -> !line.isEmpty()).map(line -> line.split("\\|", -1))
                    .onClose(() -> {
                        try {
                            reader.close();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (IOException e) {
            throw new ImportException("Cannot read " + located.where(), e);
        }
    }

    /**
     * Where a file was found, for messages.
     *
     * @param file the file
     * @return its location
     */
    String where(ReleaseFile file) {
        return found.get(file).getFirst().where();
    }

    /**
     * The release date its names carry: the first {@code yyyyMMdd} in the release's own name, an
     * archive's within it, or a directory's holding its files, as AccessGUDID names its releases
     * ({@code AccessGUDID_Delimited_Full_Release_20261001.zip}).
     *
     * @return the date, if a name carries one
     */
    Optional<LocalDate> dateInNames() {
        for (String name : names) {
            Matcher matcher = DATE_IN_NAME.matcher(name);
            while (matcher.find()) {
                try {
                    return Optional.of(LocalDate.parse(matcher.group(1), DateTimeFormatter.BASIC_ISO_DATE));
                } catch (DateTimeParseException notADate) {
                    // eight digits that are not a date: keep looking
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The release's name, for the report: the source's own name.
     *
     * @return the name
     */
    String name() {
        return path.getFileName().toString();
    }

    @Override
    public void close() {
        for (ZipFile archive : archives) {
            try {
                archive.close();
            } catch (IOException ignored) {
                // closing a read-only archive: nothing to lose
            }
        }
        for (Path file : extracted) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // a temporary copy: the system cleans it eventually
            }
        }
    }

    private void scanDirectory(Path directory) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(directory)) {
            files = walk.filter(Files::isRegularFile).filter(p -> !p.toString().contains("__MACOSX")).toList();
        }
        for (Path file : files) {
            String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (fileName.endsWith(".zip")) {
                scanArchive(file);
            } else {
                releaseFile(fileName).ifPresent(kind -> {
                    found.computeIfAbsent(kind, k -> new ArrayList<>()).add(new OnDisk(file));
                    names.add(file.getParent().getFileName().toString());
                });
            }
        }
    }

    private void scanArchive(Path archivePath) throws IOException {
        ZipFile zip = new ZipFile(archivePath.toFile(), StandardCharsets.UTF_8);
        archives.add(zip);
        names.add(archivePath.getFileName().toString());
        List<? extends ZipEntry> entries = zip.stream()
                .filter(e -> !e.isDirectory() && !e.getName().contains("__MACOSX")).toList();
        for (ZipEntry entry : entries) {
            String entryName = entry.getName();
            String fileName = entryName.substring(entryName.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
            if (fileName.endsWith(".zip")) {
                Path copy = Files.createTempFile("gudid-", ".zip");
                extracted.add(copy);
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
                }
                names.add(fileName);
                scanArchive(copy);
            } else {
                releaseFile(fileName).ifPresent(kind ->
                        found.computeIfAbsent(kind, k -> new ArrayList<>()).add(new InArchive(zip, entry)));
            }
        }
    }

    private static Optional<ReleaseFile> releaseFile(String lowerCaseName) {
        for (ReleaseFile file : ReleaseFile.values()) {
            if (file.fileName.equals(lowerCaseName)) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }
}
