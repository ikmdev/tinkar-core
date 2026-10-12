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
package dev.ikm.tinkar.importer.snomed;

import dev.ikm.tinkar.importer.ImportException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * An RF2 release as its publisher distributes it, a zip or the directory it unpacks to, read in
 * place. Files are found by RF2 file-naming convention wherever they sit in the release, so the
 * International edition, an extension, and an origin archive that wraps either all read the same.
 */
final class Rf2Release implements AutoCloseable {

    /** The kinds of RF2 file the importer reads. */
    enum Kind {
        CONCEPT("sct2_Concept_"),
        DESCRIPTION("sct2_Description_"),
        TEXT_DEFINITION("sct2_TextDefinition_"),
        LANGUAGE("der2_cRefset_Language"),
        IDENTIFIER("sct2_Identifier_"),
        OWL_EXPRESSION("sct2_sRefset_OWLExpression");

        private final String prefix;

        Kind(String prefix) {
            this.prefix = prefix;
        }

        /**
         * Whether a file name is this kind's file of a release type, exactly: the prefix, the
         * type word, then {@code _} or {@code -}. A derivative release's files insert their own
         * word ({@code sct2_Concept_GMDNMapFull_...}) and are not the edition's.
         */
        boolean names(String fileName, Type type) {
            return fileName.startsWith(prefix + type.word + "_") || fileName.startsWith(prefix + type.word + "-");
        }
    }

    /** Full, the whole history; snapshot, the latest version of each component. */
    enum Type {
        FULL("Full"),
        SNAPSHOT("Snapshot");

        private final String word;

        Type(String word) {
            this.word = word;
        }

        static Type of(String value) {
            for (Type type : values()) {
                if (type.word.equalsIgnoreCase(value.strip())) {
                    return type;
                }
            }
            throw new ImportException("The release type is full or snapshot, not " + value);
        }
    }

    /** One RF2 file: its kind and where it is. */
    record File(Kind kind, String name) {
    }

    private static final Pattern RELEASE_NAME = Pattern.compile("(SnomedCT_[A-Za-z0-9]+_[A-Z]+_[A-Z0-9_]*?\\d{8}T\\d{6}Z)");
    private static final Pattern FILE_DATE = Pattern.compile("_(\\d{8})\\.txt$");

    private final Path path;
    private final ZipFile zip;
    private final Type type;
    private final List<File> files;

    private Rf2Release(Path path, ZipFile zip, Type type, List<File> files) {
        this.path = path;
        this.zip = zip;
        this.type = type;
        this.files = files;
    }

    /**
     * Opens a release.
     *
     * @param path a release zip, or the directory it unpacks to
     * @param type full or snapshot
     * @return the release
     */
    static Rf2Release open(Path path, Type type) {
        try {
            List<String> names;
            ZipFile zip = null;
            if (Files.isDirectory(path)) {
                try (Stream<Path> walk = Files.walk(path)) {
                    names = walk.filter(Files::isRegularFile).map(p -> path.relativize(p).toString().replace('\\', '/')).toList();
                }
            } else {
                zip = new ZipFile(path.toFile(), StandardCharsets.UTF_8);
                names = zip.stream().filter(e -> !e.isDirectory()).map(ZipEntry::getName).toList();
            }
            List<File> files = new ArrayList<>();
            for (String name : names) {
                String fileName = name.substring(name.lastIndexOf('/') + 1);
                if (!fileName.endsWith(".txt") || !inTypeFolder(name, type)) {
                    continue;
                }
                for (Kind kind : Kind.values()) {
                    if (kind.names(fileName, type)) {
                        files.add(new File(kind, name));
                    }
                }
            }
            files.sort(Comparator.comparing(File::kind).thenComparing(File::name));
            if (files.stream().noneMatch(f -> f.kind() == Kind.CONCEPT)) {
                if (zip != null) {
                    zip.close();
                }
                throw new ImportException("No RF2 " + type.word + " concept file in " + path);
            }
            return new Rf2Release(path, zip, type, List.copyOf(files));
        } catch (IOException e) {
            throw new ImportException("Cannot read the RF2 release " + path, e);
        }
    }

    private static boolean inTypeFolder(String name, Type type) {
        for (String segment : name.split("/")) {
            if (segment.equals(type.word)) {
                return true;
            }
            if (segment.equals("Delta") || segment.equals(type == Type.FULL ? "Snapshot" : "Full")) {
                return false;
            }
        }
        return true;
    }

    Type type() {
        return type;
    }

    List<File> files(Kind kind) {
        return files.stream().filter(f -> f.kind() == kind).toList();
    }

    /**
     * The release's name as its publisher gives it ({@code SnomedCT_InternationalRF2_PRODUCTION_20250801T120000Z}),
     * or, failing that, the effective date in its concept file's name.
     *
     * @return the version
     */
    String version() {
        for (String candidate : List.of(path.getFileName().toString(), files.getFirst().name())) {
            Matcher matcher = RELEASE_NAME.matcher(candidate);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        Matcher date = FILE_DATE.matcher(files(Kind.CONCEPT).getFirst().name());
        return date.find() ? date.group(1) : path.getFileName().toString();
    }

    /**
     * The language code an RF2 description or language file is for, from its name
     * ({@code sct2_Description_Full-en_INT_20250801.txt} is {@code en}).
     *
     * @param file the file
     * @return the code, if the name carries one
     */
    static Optional<String> languageOf(File file) {
        Matcher matcher = Pattern.compile("-([a-z]{2,3})(?:-[A-Za-z]+)?_").matcher(file.name());
        return matcher.find() ? Optional.of(matcher.group(1).toLowerCase(Locale.ROOT)) : Optional.empty();
    }

    /**
     * Reads a file's rows, header skipped, each split on tabs.
     *
     * @param file the file
     * @param row  what to do with each row
     * @return the number of rows
     */
    long rows(File file, Consumer<String[]> row) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                zip == null ? Files.newInputStream(path.resolve(file.name())) : zip.getInputStream(zip.getEntry(file.name())),
                StandardCharsets.UTF_8), 1 << 16)) {
            String line = reader.readLine();
            long count = 0;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    row.accept(line.split("\t", -1));
                    count++;
                }
            }
            return count;
        } catch (IOException e) {
            throw new UncheckedIOException("Reading " + file.name() + " in " + path, e);
        }
    }

    @Override
    public void close() {
        if (zip != null) {
            try {
                zip.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
