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

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.schema.ComponentEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A format-3 changeset's component table: every component the changeset carries, in file
 * order, numbered by position from 1, continued by the components it references but does not
 * carry. A record refers to a component by that sequence, so an importer resolves a reference
 * by an array read once it has registered the table, and the table's order is the order the
 * store mints nids in, pattern by pattern. Patterns precede their members, and the pattern
 * pattern names itself, which is how a reader tells a pattern from a member without the
 * record.
 *
 * <p>The entries are {@link dev.ikm.tinkar.schema.ComponentTable} messages of up to
 * {@value #COMPONENTS_PER_MESSAGE} components, length-delimited, in
 * {@value ChangeSetFormat#COMPONENT_TABLE} and then {@value ChangeSetFormat#REFERENCE_TABLE}.
 * Design: {@code notes/design-2026-10-08-changeset-format-3.adoc} in ike-dev.
 */
public final class ComponentTable {
    private static final Logger LOG = LoggerFactory.getLogger(ComponentTable.class);
    /** Components per table message: large enough that a reader's parse stays ahead of its work. */
    public static final int COMPONENTS_PER_MESSAGE = 100_000;

    private ComponentTable() {
    }

    /** One component of the table, with the sequence its position gives it. */
    public record Component(int sequence, int patternSequence, UUID[] uuids, boolean referencedOnly) {
        public PublicId publicId() {
            return PublicIds.of(uuids);
        }

        /** The pattern pattern is the one component whose pattern is itself. */
        public boolean isPatternPattern() {
            return patternSequence == sequence;
        }
    }

    /** Told of each component in table order. */
    @FunctionalInterface
    public interface ComponentConsumer {
        void accept(Component component) throws IOException;
    }

    /**
     * Streams the carried components and then the referenced ones to {@code consumer}, in
     * table order, numbering them from 1.
     *
     * @return the number of components streamed
     */
    public static long forEach(ZipFile zip, ComponentConsumer consumer) throws IOException {
        int sequence = 0;
        for (String name : new String[]{ChangeSetFormat.COMPONENT_TABLE, ChangeSetFormat.REFERENCE_TABLE}) {
            ZipEntry entry = zip.getEntry(name);
            if (entry == null) {
                continue;
            }
            try (InputStream in = new java.io.BufferedInputStream(zip.getInputStream(entry), 1 << 20)) {
                dev.ikm.tinkar.schema.ComponentTable table;
                while ((table = dev.ikm.tinkar.schema.ComponentTable.parseDelimitedFrom(in)) != null) {
                    for (ComponentEntry component : table.getComponentsList()) {
                        sequence++;
                        consumer.accept(new Component(sequence, component.getPatternSequence(),
                                uuids(component.getUuidBitsList()), component.getReferencedOnly()));
                    }
                }
            }
        }
        return sequence;
    }

    /**
     * What registration produced: the nid of each component by its sequence
     * ({@code nids[sequence]}, {@code nids[0]} unused), and the public id of each pattern by
     * its nid, so a reader resolves a semantic's pattern without the pattern's record, which
     * another reader may still be storing.
     */
    public record Registration(long[] nids, Map<Long, PublicId> patternIdsByNid) {
        /** The number of components registered. */
        public long count() {
            return nids.length - 1;
        }
    }

    /**
     * Registers every component of the table with the store, in table order, and returns the
     * nid of each by its sequence: {@code nids[sequence]}, with {@code nids[0]} unused. A
     * component already known to the store keeps its nid; a new one is minted under its
     * pattern, so a fresh store's nids ascend with the table. Patterns are minted before their
     * members because the table lists them first.
     *
     * @param expectedComponents the manifest's component count, to size the result; grown if
     *                           the table holds more
     * @param progress           told the running count, per table message
     * @throws IllegalStateException if a component names a pattern the table has not yet
     *                               defined, or the pattern pattern never names itself
     */
    public static Registration registerNids(ZipFile zip, long expectedComponents, LongConsumer progress) throws IOException {
        long[][] nids = {new long[(int) Math.max(16, Math.min(Integer.MAX_VALUE - 8, expectedComponents + 1))]};
        Map<Integer, PublicId> patternIds = new HashMap<>();
        Map<Long, PublicId> patternIdsByNid = new HashMap<>();
        int[] patternPattern = {-1};
        long[] count = {0};
        long[] lastReported = {0};
        long total = forEach(zip, component -> {
            if (component.sequence() >= nids[0].length) {
                nids[0] = Arrays.copyOf(nids[0], Math.max(nids[0].length * 2, component.sequence() + 1));
            }
            PublicId id = component.publicId();
            long nid;
            if (component.isPatternPattern()) {
                patternPattern[0] = component.sequence();
                nid = PrimitiveData.getEntityKey(id, id).nid();
            } else {
                PublicId pattern = patternIds.get(component.patternSequence());
                if (pattern == null) {
                    throw new IllegalStateException("Component " + component.sequence() + " of " + zip.getName()
                            + " names pattern sequence " + component.patternSequence()
                            + ", which the table has not defined: patterns precede their members");
                }
                nid = PrimitiveData.getEntityKey(pattern, id).nid();
            }
            nids[0][component.sequence()] = nid;
            if (component.patternSequence() == patternPattern[0]) {
                patternIds.put(component.sequence(), id);
                patternIdsByNid.put(nid, id);
            }
            count[0]++;
            if (progress != null && count[0] - lastReported[0] >= COMPONENTS_PER_MESSAGE) {
                lastReported[0] = count[0];
                progress.accept(count[0]);
            }
        });
        if (progress != null) {
            progress.accept(total);
        }
        LOG.info("Registered {} nid(s) from the component table of {}", String.format("%,d", total), zip.getName());
        long[] bySequence = total + 1 == nids[0].length ? nids[0] : Arrays.copyOf(nids[0], (int) total + 1);
        return new Registration(bySequence, patternIdsByNid);
    }

    /** The UUIDs of a component entry: two longs each, most significant bits first. */
    public static UUID[] uuids(List<Long> uuidBits) {
        if (uuidBits.isEmpty() || uuidBits.size() % 2 != 0) {
            throw new IllegalArgumentException("A component carries " + uuidBits.size() + " longs; a UUID takes two");
        }
        UUID[] uuids = new UUID[uuidBits.size() / 2];
        for (int i = 0; i < uuids.length; i++) {
            uuids[i] = new UUID(uuidBits.get(2 * i), uuidBits.get(2 * i + 1));
        }
        return uuids;
    }

    /**
     * Writes a table as delimited messages of up to {@value #COMPONENTS_PER_MESSAGE} components.
     * The caller numbers the components: the sequence of each is its position, counted from 1
     * across the carried table and the reference table together.
     */
    public static final class Writer implements Closeable {
        private final OutputStream out;
        private dev.ikm.tinkar.schema.ComponentTable.Builder pending = dev.ikm.tinkar.schema.ComponentTable.newBuilder();
        private long count;

        public Writer(OutputStream out) {
            this.out = out;
        }

        /** Appends a component; returns how many this writer has written, this one included. */
        public synchronized long add(int patternSequence, PublicId id, boolean referencedOnly) throws IOException {
            ComponentEntry.Builder component = ComponentEntry.newBuilder().setPatternSequence(patternSequence);
            id.forEach(component::addUuidBits); // the id's UUIDs as longs, most significant first
            if (referencedOnly) {
                component.setReferencedOnly(true);
            }
            pending.addComponents(component);
            count++;
            if (pending.getComponentsCount() >= COMPONENTS_PER_MESSAGE) {
                flush();
            }
            return count;
        }

        /** Writes the pending message, if any. */
        public synchronized void flush() throws IOException {
            if (pending.getComponentsCount() > 0) {
                pending.build().writeDelimitedTo(out);
                pending = dev.ikm.tinkar.schema.ComponentTable.newBuilder();
            }
            out.flush();
        }

        public synchronized long count() {
            return count;
        }

        @Override
        public synchronized void close() throws IOException {
            flush();
        }
    }
}
