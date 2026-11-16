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
package dev.ikm.tinkar.importer.loinc;

import dev.ikm.tinkar.importer.ImportException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A LOINC release on disk: a zip as Regenstrief distributes it (or IKM's origin zip of it) or the
 * directory it unpacks to. Finds the three files the importer reads by their names, wherever they
 * sit, and reads them as CSV.
 */
final class LoincRelease implements AutoCloseable {

    /** The table of LOINC codes. */
    static final String LOINC_TABLE = "Loinc.csv";
    /** The table of parts. */
    static final String PART_TABLE = "Part.csv";
    /** The component hierarchy. */
    static final String COMPONENT_HIERARCHY = "ComponentHierarchyBySystem.csv";

    private final Path path;
    private final ZipFile zip;
    private final Map<String, ZipEntry> zipEntries = new HashMap<>();
    private final Map<String, Path> files = new HashMap<>();

    private LoincRelease(Path path, ZipFile zip) {
        this.path = path;
        this.zip = zip;
    }

    /**
     * Opens a release.
     *
     * @param path a zip or a directory
     * @return the release
     * @throws ImportException when the path holds none of the files, or more than one of any
     */
    static LoincRelease open(Path path) {
        try {
            LoincRelease release;
            if (Files.isDirectory(path)) {
                release = new LoincRelease(path, null);
                try (Stream<Path> walk = Files.walk(path)) {
                    walk.filter(Files::isRegularFile).forEach(file ->
                            release.index(path.relativize(file).toString().replace('\\', '/'), file, null));
                }
            } else {
                ZipFile zip = new ZipFile(path.toFile(), StandardCharsets.UTF_8);
                release = new LoincRelease(path, zip);
                zip.stream().filter(entry -> !entry.isDirectory()).forEach(entry -> {
                    release.index(entry.getName(), null, entry);
                });
            }
            for (String required : List.of(LOINC_TABLE, PART_TABLE, COMPONENT_HIERARCHY)) {
                if (!release.files.containsKey(required) && !release.zipEntries.containsKey(required)) {
                    release.close();
                    throw new ImportException("The LOINC release " + path + " has no " + required);
                }
            }
            return release;
        } catch (IOException e) {
            throw new ImportException("Cannot read the LOINC release " + path, e);
        }
    }

    /** The folder each file lives in, where a release carries more than one copy (Loinc.csv is also under PanelsAndForms). */
    private static final Map<String, String> HOME = Map.of(
            LOINC_TABLE, "LoincTable", PART_TABLE, "PartFile", COMPONENT_HIERARCHY, "ComponentHierarchyBySystem");

    private final Map<String, String> indexed = new HashMap<>();

    private void index(String fullName, Path file, ZipEntry entry) {
        String name = fullName.substring(fullName.lastIndexOf('/') + 1);
        if (!name.equals(LOINC_TABLE) && !name.equals(PART_TABLE) && !name.equals(COMPONENT_HIERARCHY)) {
            return;
        }
        String previous = indexed.get(name);
        if (previous != null) {
            boolean thisHome = inHome(name, fullName);
            if (thisHome == inHome(name, previous)) {
                throw new ImportException("The LOINC release " + path + " has more than one " + name + ": "
                        + previous + " and " + fullName);
            }
            if (!thisHome) {
                return;
            }
        }
        indexed.put(name, fullName);
        files.remove(name);
        zipEntries.remove(name);
        if (file != null) {
            files.put(name, file);
        } else {
            zipEntries.put(name, entry);
        }
    }

    private static boolean inHome(String name, String fullName) {
        return fullName.startsWith(HOME.get(name) + "/") || fullName.contains("/" + HOME.get(name) + "/");
    }

    /**
     * When a file was last written, as the release carries it: the best date a release without a
     * date in its name has.
     *
     * @param name one of the file names above
     * @return the instant
     */
    Instant lastModified(String name) {
        try {
            if (zip != null) {
                return zipEntries.get(name).getLastModifiedTime().toInstant();
            }
            return Files.getLastModifiedTime(files.get(name)).toInstant();
        } catch (IOException e) {
            throw new ImportException("Cannot read the date of " + name + " in " + path, e);
        }
    }

    /**
     * Reads a file as CSV, one record at a time. The first record is the header; a record is
     * passed as a map from header name to value, so a column moving between releases does not
     * matter, only its name.
     *
     * @param name     one of the file names above
     * @param consumer receives each record after the header
     * @return the number of records
     */
    long read(String name, Consumer<Map<String, String>> consumer) {
        try (InputStream in = zip != null ? zip.getInputStream(zipEntries.get(name)) : Files.newInputStream(files.get(name));
             Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16)) {
            Csv csv = new Csv(reader);
            List<String> header = csv.next();
            if (header == null) {
                throw new ImportException(name + " in " + path + " is empty");
            }
            if (!header.isEmpty() && header.getFirst().startsWith("﻿")) {
                header.set(0, header.getFirst().substring(1));
            }
            long count = 0;
            for (List<String> row = csv.next(); row != null; row = csv.next()) {
                if (row.size() == 1 && row.getFirst().isEmpty()) {
                    continue;
                }
                Map<String, String> record = new HashMap<>(header.size() * 2);
                for (int i = 0; i < header.size() && i < row.size(); i++) {
                    record.put(header.get(i), row.get(i).strip());
                }
                consumer.accept(record);
                count++;
            }
            return count;
        } catch (IOException e) {
            throw new ImportException("Cannot read " + name + " in " + path, e);
        }
    }

    /** The name the release is reported under. */
    String name() {
        return path.getFileName().toString();
    }

    @Override
    public void close() {
        if (zip != null) {
            try {
                zip.close();
            } catch (IOException e) {
                throw new ImportException("Cannot close " + path, e);
            }
        }
    }

    /** RFC 4180: quoted fields, doubled quotes, line breaks inside quotes. */
    private static final class Csv {
        private final Reader reader;

        Csv(Reader reader) {
            this.reader = reader;
        }

        /** The next record, or null at the end. */
        List<String> next() throws IOException {
            List<String> fields = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean quoted = false;
            boolean any = false;
            for (int c = reader.read(); c != -1; c = reader.read()) {
                any = true;
                if (quoted) {
                    if (c == '"') {
                        int following = reader.read();
                        if (following == '"') {
                            field.append('"');
                        } else {
                            quoted = false;
                            if (following == -1) {
                                break;
                            }
                            c = following;
                            if (c == ',') {
                                fields.add(field.toString());
                                field.setLength(0);
                            } else if (c == '\n') {
                                fields.add(field.toString());
                                return fields;
                            } else if (c != '\r') {
                                field.append((char) c);
                            }
                        }
                    } else {
                        field.append((char) c);
                    }
                } else if (c == '"' && field.isEmpty()) {
                    quoted = true;
                } else if (c == ',') {
                    fields.add(field.toString());
                    field.setLength(0);
                } else if (c == '\n') {
                    fields.add(field.toString());
                    return fields;
                } else if (c != '\r') {
                    field.append((char) c);
                }
            }
            if (!any) {
                return null;
            }
            fields.add(field.toString());
            return fields;
        }
    }
}
