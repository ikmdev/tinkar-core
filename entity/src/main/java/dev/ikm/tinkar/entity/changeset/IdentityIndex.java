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
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.schema.PatternMembers;
import dev.ikm.tinkar.schema.TinkarMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * A changeset's identity index: the pattern of every component it carries, so that a store
 * whose nids encode the pattern (Rocks) can assign every nid before reading a single record.
 *
 * <p>Without it, such a store has to read a changeset twice: a record can reference a
 * component that appears later in the file — a semantic's referenced component, or a
 * component in a field value — and the store cannot assign that component a nid without
 * knowing its pattern. With the index, those identities come from a small table, and the
 * records are read once.
 *
 * <p>The index is the zip entry {@value ChangeSetFormat#IDENTITY_INDEX}: {@link PatternMembers}
 * messages, length-delimited, listing each component once. Every changeset of format version
 * 2 carries it. Adapted from Patrick Chou's {@code feature/pattern_public_id} of ikmdev/tinkar-core.
 */
public final class IdentityIndex {

    private static final Logger LOG = LoggerFactory.getLogger(IdentityIndex.class);

    /** Members per {@link PatternMembers} message: bounds message size while sharing the pattern id. */
    private static final int MEMBERS_PER_MESSAGE = 1_000;

    private IdentityIndex() {
    }

    /**
     * Assigns a nid to every component the index of {@code changeSet} lists.
     *
     * @param progress told the running count of components registered; may be null
     * @return how many components were registered, or -1 if the changeset has no index
     * @throws IOException if the file cannot be read
     */
    public static long registerNids(File changeSet, LongConsumer progress) throws IOException {
        try (ZipFile zip = new ZipFile(changeSet)) {
            ZipEntry entry = zip.getEntry(ChangeSetFormat.IDENTITY_INDEX);
            if (entry == null) {
                return -1;
            }
            AtomicLong registered = new AtomicLong();
            try (InputStream in = zip.getInputStream(entry)) {
                PatternMembers members;
                while ((members = PatternMembers.parseDelimitedFrom(in)) != null) {
                    PublicId pattern = SchemaIds.toPublicId(members.getPatternPublicId());
                    members.getComponentPublicIdsList().parallelStream().forEach(component ->
                            PrimitiveData.getEntityKey(pattern, SchemaIds.toPublicId(component)));
                    long total = registered.addAndGet(members.getComponentPublicIdsCount());
                    if (progress != null) {
                        progress.accept(total);
                    }
                }
            }
            LOG.info("Registered {} nid(s) from the identity index of {}",
                    String.format("%,d", registered.get()), changeSet.getName());
            return registered.get();
        }
    }

    /**
     * Collects a changeset's identities as its records are written, then writes them as the
     * index entry. Thread-safe: records may be added from several threads, as the exporter
     * adds them.
     *
     * <p>Spools to a temporary file rather than holding identities in memory, since a full
     * export lists every component in the store.
     */
    public static final class Writer implements Closeable {

        private final Path spool;
        private final OutputStream out;
        /** Members not yet spooled, per pattern, keyed by the pattern's id string, in insertion order. */
        private final Map<String, PatternMembers.Builder> pending = new LinkedHashMap<>();
        /** Every UUID of every component listed, when a component may be written more than once; else null. */
        private final Set<UUID> listed;
        private long count;

        /**
         * @param repeats whether the writer may write a component more than once — the
         *                changeset writer, which writes a component at each commit — so that
         *                the index lists it once
         */
        public Writer(boolean repeats) throws IOException {
            this.spool = Files.createTempFile("ike-identities-", ".pb");
            this.out = new BufferedOutputStream(Files.newOutputStream(spool));
            this.listed = repeats ? new HashSet<>() : null;
        }

        /** Records the identity of {@code record}'s component. */
        public synchronized void add(TinkarMsg record) throws IOException {
            dev.ikm.tinkar.schema.PublicId component = ChangeSetFormat.componentOf(record);
            if (listed != null) {
                UUID[] uuids = SchemaIds.uuids(component);
                // A component that shares a UUID with one listed is that component.
                boolean seen = false;
                for (UUID uuid : uuids) {
                    seen |= !listed.add(uuid);
                }
                if (seen) {
                    return;
                }
            }
            PublicId pattern = ChangeSetFormat.patternOf(record);
            PatternMembers.Builder members = pending.computeIfAbsent(pattern.idString(),
                    key -> PatternMembers.newBuilder().setPatternPublicId(SchemaIds.toSchema(pattern)));
            members.addComponentPublicIds(component);
            count++;
            if (members.getComponentPublicIdsCount() >= MEMBERS_PER_MESSAGE) {
                members.build().writeDelimitedTo(out);
                pending.remove(pattern.idString());
            }
        }

        /** Components listed so far. */
        public synchronized long count() {
            return count;
        }

        /** Writes the index as the {@value ChangeSetFormat#IDENTITY_INDEX} entry of {@code zip}, which must have no entry open. */
        public synchronized void writeTo(ZipOutputStream zip) throws IOException {
            for (PatternMembers.Builder members : pending.values()) {
                members.build().writeDelimitedTo(out);
            }
            pending.clear();
            out.flush();
            zip.putNextEntry(new ZipEntry(ChangeSetFormat.IDENTITY_INDEX));
            Files.copy(spool, zip);
            zip.closeEntry();
        }

        @Override
        public synchronized void close() throws IOException {
            try {
                out.close();
            } finally {
                Files.deleteIfExists(spool);
            }
        }
    }
}
