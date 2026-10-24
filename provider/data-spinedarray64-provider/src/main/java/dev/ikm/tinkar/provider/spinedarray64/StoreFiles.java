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
package dev.ikm.tinkar.provider.spinedarray64;

import dev.ikm.tinkar.common.id.impl.NidLayout;
import dev.ikm.tinkar.entity.changeset.ChangeSetFormat;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Stream;

/**
 * The files of a persistent 64-bit spined-array store (design-2026-10-20-instance-data, "Persistence
 * and the save format"):
 * <pre>
 *   store-format.properties        layout, entity format, engine, spine geometry, created,
 *                                  vertex identity (when the store was created with derived identities)
 *   counters.properties            pattern sequence = next element sequence
 *   absentIdentities.txt           nid uuid… per nid minted and never written
 *   patterns/&lt;sequence&gt;/records/spine-&lt;k&gt;     [int slots]([int length][bytes])*
 *   patterns/&lt;sequence&gt;/citations/spine-&lt;k&gt;   [int slots]([int count][long × count])*
 * </pre>
 * A slot with nothing is written with length or count 0. Every file is written to a temporary
 * file and renamed over the old, so a crash leaves the old spine or the new one, never half of
 * one; a spine with nothing in it is deleted rather than written.
 */
final class StoreFiles {

    static final String FORMAT_FILE = "store-format.properties";
    static final String COUNTERS_FILE = "counters.properties";
    static final String ABSENT_FILE = "absentIdentities.txt";
    static final String PATTERNS = "patterns";
    static final String RECORDS = "records";
    static final String CITATIONS = "citations";
    static final String SPINE_PREFIX = "spine-";
    static final String ENGINE = "64-bit spined array";
    /**
     * The format property naming the vertex-identity derivation version a store was created
     * under; a store without it was created before derivation (IKE-Network/ike-issues#1310).
     */
    static final String VERTEX_IDENTITY = "vertexIdentity";

    private final File root;
    private final File patterns;

    StoreFiles(File root) {
        this.root = root;
        this.patterns = new File(root, PATTERNS);
    }

    File root() {
        return root;
    }

    /** Whether a directory holds a 64-bit spined-array store: it has the format file. */
    static boolean holdsAStore(File directory) {
        return new File(directory, FORMAT_FILE).isFile();
    }

    // ---------- format ----------

    void writeFormat(SpineGeometry geometry) {
        Properties format = new Properties();
        format.setProperty("layout", NidLayout.SIXTY_FOUR_BIT.displayName());
        format.setProperty("entityFormat", Byte.toString(NidLayout.SIXTY_FOUR_BIT.entityFormat()));
        format.setProperty("engine", ENGINE);
        format.setProperty("spine.min", Integer.toString(geometry.min()));
        format.setProperty("spine.max", Integer.toString(geometry.max()));
        format.setProperty("createdAt", Instant.now().toString());
        // Everything a store of this build holds passes through the write path that derives identities.
        format.setProperty(VERTEX_IDENTITY, Integer.toString(ChangeSetFormat.VERTEX_IDENTITY_VERSION));
        writeProperties(new File(root, FORMAT_FILE), format, "The format of a 64-bit spined-array store");
    }

    /**
     * The geometry the store was written with, after checking it is a store of this engine.
     *
     * @throws IllegalStateException if the format file names another layout, entity format or engine
     */
    SpineGeometry readFormat() {
        Properties format = readProperties(new File(root, FORMAT_FILE));
        String layout = format.getProperty("layout");
        String entityFormat = format.getProperty("entityFormat");
        if (!NidLayout.SIXTY_FOUR_BIT.displayName().equals(layout)
                || !Byte.toString(NidLayout.SIXTY_FOUR_BIT.entityFormat()).equals(entityFormat)
                || !ENGINE.equals(format.getProperty("engine"))) {
            throw new IllegalStateException(root + " holds a store of layout " + layout + ", entity format " + entityFormat
                    + " and engine " + format.getProperty("engine") + ", not a 64-bit spined-array store");
        }
        return new SpineGeometry(Integer.parseInt(format.getProperty("spine.min")),
                Integer.parseInt(format.getProperty("spine.max")));
    }

    /** Whether the format file says the store was created with derived vertex identities. */
    boolean vertexIdentityDerived() {
        return readProperties(new File(root, FORMAT_FILE)).getProperty(VERTEX_IDENTITY) != null;
    }

    // ---------- counters ----------

    Map<Integer, Integer> readCounters() {
        File file = new File(root, COUNTERS_FILE);
        Map<Integer, Integer> counters = new TreeMap<>();
        if (!file.isFile()) {
            return counters;
        }
        readProperties(file).forEach((key, value) ->
                counters.put(Integer.parseInt((String) key), Integer.parseInt((String) value)));
        return counters;
    }

    void writeCounters(Map<Integer, Integer> counters) {
        Properties properties = new Properties() {
            // Sorted, so the file reads in pattern order.
            @Override
            public java.util.Set<Map.Entry<Object, Object>> entrySet() {
                java.util.TreeSet<Map.Entry<Object, Object>> sorted = new java.util.TreeSet<>(
                        Comparator.comparingInt(entry -> Integer.parseInt((String) entry.getKey())));
                sorted.addAll(super.entrySet());
                return sorted;
            }
        };
        counters.forEach((pattern, next) -> properties.setProperty(Integer.toString(pattern), Integer.toString(next)));
        writeProperties(new File(root, COUNTERS_FILE), properties, "pattern sequence = next element sequence");
    }

    // ---------- patterns and spines ----------

    /** The pattern sequences that have a directory, ascending. */
    List<Integer> patternSequences() {
        List<Integer> sequences = new ArrayList<>();
        String[] names = patterns.list();
        if (names != null) {
            for (String name : names) {
                try {
                    sequences.add(Integer.parseInt(name));
                } catch (NumberFormatException notAPattern) {
                    // a stray file
                }
            }
        }
        sequences.sort(Integer::compare);
        return sequences;
    }

    /** The spines of one kind a pattern holds on disk. */
    BitSet spinesOnDisk(int pattern, String kind) {
        BitSet spines = new BitSet();
        String[] names = kindDirectory(pattern, kind).list();
        if (names != null) {
            for (String name : names) {
                if (name.startsWith(SPINE_PREFIX) && !name.endsWith(".tmp")) {
                    spines.set(Integer.parseInt(name.substring(SPINE_PREFIX.length())));
                }
            }
        }
        return spines;
    }

    AtomicReferenceArray<byte[]> readRecords(int pattern, int spine) {
        File file = spineFile(pattern, RECORDS, spine);
        if (!file.isFile()) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file.toPath()), 1 << 16))) {
            int slots = in.readInt();
            byte[][] values = new byte[slots][];
            for (int i = 0; i < slots; i++) {
                int length = in.readInt();
                if (length > 0) {
                    values[i] = new byte[length];
                    in.readFully(values[i]);
                }
            }
            return new AtomicReferenceArray<>(values);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }

    AtomicReferenceArray<long[]> readCitations(int pattern, int spine) {
        File file = spineFile(pattern, CITATIONS, spine);
        if (!file.isFile()) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file.toPath()), 1 << 16))) {
            int slots = in.readInt();
            long[][] values = new long[slots][];
            for (int i = 0; i < slots; i++) {
                int count = in.readInt();
                if (count > 0) {
                    long[] citing = new long[count];
                    for (int j = 0; j < count; j++) {
                        citing[j] = in.readLong();
                    }
                    values[i] = citing;
                }
            }
            return new AtomicReferenceArray<>(values);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }

    void writeRecords(int pattern, int spine, AtomicReferenceArray<byte[]> slots) {
        File file = spineFile(pattern, RECORDS, spine);
        boolean any = false;
        for (int i = 0; i < slots.length() && !any; i++) {
            any = slots.get(i) != null;
        }
        if (!any) {
            delete(file);
            return;
        }
        writeAtomically(file, out -> {
            out.writeInt(slots.length());
            for (int i = 0; i < slots.length(); i++) {
                byte[] value = slots.get(i);
                if (value == null) {
                    out.writeInt(0);
                } else {
                    out.writeInt(value.length);
                    out.write(value);
                }
            }
        });
    }

    void writeCitations(int pattern, int spine, AtomicReferenceArray<long[]> slots) {
        File file = spineFile(pattern, CITATIONS, spine);
        boolean any = false;
        for (int i = 0; i < slots.length() && !any; i++) {
            any = slots.get(i) != null;
        }
        if (!any) {
            delete(file);
            return;
        }
        writeAtomically(file, out -> {
            out.writeInt(slots.length());
            for (int i = 0; i < slots.length(); i++) {
                long[] citing = slots.get(i);
                if (citing == null) {
                    out.writeInt(0);
                } else {
                    out.writeInt(citing.length);
                    for (long nid : citing) {
                        out.writeLong(nid);
                    }
                }
            }
        });
    }

    /** Deletes a pattern's directory: the range delete of a dropped pattern. */
    void deletePattern(int pattern) {
        Path directory = new File(patterns, Integer.toString(pattern)).toPath();
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete " + directory, e);
        }
    }

    // ---------- absent identities ----------

    Map<Long, UUID[]> readAbsentIdentities() {
        File file = new File(root, ABSENT_FILE);
        Map<Long, UUID[]> absent = new TreeMap<>();
        if (!file.isFile()) {
            return absent;
        }
        try {
            for (String line : Files.readAllLines(file.toPath())) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 2) {
                    continue;
                }
                UUID[] uuids = new UUID[parts.length - 1];
                for (int i = 1; i < parts.length; i++) {
                    uuids[i - 1] = UUID.fromString(parts[i]);
                }
                absent.put(Long.parseLong(parts[0]), uuids);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
        return absent;
    }

    void writeAbsentIdentities(Map<Long, UUID[]> absent) {
        File file = new File(root, ABSENT_FILE);
        if (absent.isEmpty()) {
            delete(file);
            return;
        }
        StringBuilder lines = new StringBuilder();
        new TreeMap<>(absent).forEach((nid, uuids) -> {
            lines.append(nid);
            for (UUID uuid : uuids) {
                lines.append(' ').append(uuid);
            }
            lines.append('\n');
        });
        writeAtomically(file, out -> out.write(lines.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ---------- helpers ----------

    private File kindDirectory(int pattern, String kind) {
        return new File(new File(patterns, Integer.toString(pattern)), kind);
    }

    private File spineFile(int pattern, String kind, int spine) {
        return new File(kindDirectory(pattern, kind), SPINE_PREFIX + spine);
    }

    @FunctionalInterface
    private interface Body {
        void write(DataOutputStream out) throws IOException;
    }

    private static void writeAtomically(File file, Body body) {
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new UncheckedIOException(new IOException("Could not create " + parent));
        }
        Path temporary = new File(parent, file.getName() + ".tmp").toPath();
        try {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary), 1 << 16))) {
                body.write(out);
            }
            try {
                Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + file, e);
        }
    }

    private static void delete(File file) {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete " + file, e);
        }
    }

    private static Properties readProperties(File file) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file.toPath())) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
        return properties;
    }

    private static void writeProperties(File file, Properties properties, String comment) {
        writeAtomically(file, out -> {
            try (Writer writer = new java.io.OutputStreamWriter(out, java.nio.charset.StandardCharsets.ISO_8859_1)) {
                properties.store(writer, comment);
            }
        });
    }
}
