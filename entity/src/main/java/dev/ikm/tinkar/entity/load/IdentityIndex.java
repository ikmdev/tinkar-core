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
package dev.ikm.tinkar.entity.load;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.schema.PatternMembers;
import dev.ikm.tinkar.schema.TinkarMsg;
import dev.ikm.tinkar.terms.EntityBinding;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * A changeset's identity index: the pattern of every component it carries, so that a store whose
 * nids encode the pattern (Rocks KB) can assign every nid before reading a single record.
 *
 * <p>Without it, such a store has to read a changeset twice: a record can reference a component
 * that appears later in the file — a semantic's referenced component, or a component in a field
 * value — and the store cannot assign that component a nid without knowing its pattern. The first
 * read only collects identities. With the index, those identities come from a small table instead,
 * and the records are read once.
 *
 * <p>The index is the optional zip entry {@value #ENTRY}: {@link PatternMembers} messages,
 * length-delimited. Readers skip every entry under {@code META-INF/}; see {@link #isMetadata}.
 */
public final class IdentityIndex {

    private static final Logger LOG = LoggerFactory.getLogger(IdentityIndex.class);

    /** The zip entry holding the index. */
    public static final String ENTRY = "META-INF/identities.pb";

    /** Members per {@link PatternMembers} message: bounds message size while sharing the pattern id. */
    private static final int MEMBERS_PER_MESSAGE = 1_000;

    private IdentityIndex() {
    }

    /**
     * Whether a zip entry holds metadata rather than records. Everything under {@code META-INF/} —
     * the manifest, this index, and anything added later — is metadata a record reader must skip.
     */
    public static boolean isMetadata(String entryName) {
        return entryName.startsWith("META-INF/");
    }

    /**
     * The pattern a record's component is an element of.
     *
     * <p>Semantics always named theirs. Concepts, patterns and stamps carry one since
     * tinkar-schema#43; a record written before then leaves it out, which means the well-known
     * default for its type.
     */
    public static PublicId patternOf(TinkarMsg record) {
        return switch (record.getValueCase()) {
            case SEMANTIC_CHRONOLOGY -> toPublicId(record.getSemanticChronology().getPatternForSemanticPublicId());
            case CONCEPT_CHRONOLOGY -> record.getConceptChronology().hasPatternForConceptPublicId()
                    ? toPublicId(record.getConceptChronology().getPatternForConceptPublicId())
                    : EntityBinding.Concept.pattern().publicId();
            case PATTERN_CHRONOLOGY -> record.getPatternChronology().hasPatternForPatternPublicId()
                    ? toPublicId(record.getPatternChronology().getPatternForPatternPublicId())
                    : EntityBinding.Pattern.pattern().publicId();
            case STAMP_CHRONOLOGY -> record.getStampChronology().hasPatternForStampPublicId()
                    ? toPublicId(record.getStampChronology().getPatternForStampPublicId())
                    : EntityBinding.Stamp.pattern().publicId();
            case VALUE_NOT_SET -> throw new IllegalStateException("Tinkar message value not set");
        };
    }

    /** The public id of a record's component, as the record carries it. */
    public static dev.ikm.tinkar.schema.PublicId componentOf(TinkarMsg record) {
        return switch (record.getValueCase()) {
            case CONCEPT_CHRONOLOGY -> record.getConceptChronology().getPublicId();
            case SEMANTIC_CHRONOLOGY -> record.getSemanticChronology().getPublicId();
            case PATTERN_CHRONOLOGY -> record.getPatternChronology().getPublicId();
            case STAMP_CHRONOLOGY -> record.getStampChronology().getPublicId();
            case VALUE_NOT_SET -> throw new IllegalStateException("Tinkar message value not set");
        };
    }

    /**
     * Assigns a nid to every component the index of {@code changeset} lists.
     *
     * @param progress told the running count of components registered; may be null
     * @return how many components were registered, or -1 if the changeset has no index
     * @throws IOException if the file cannot be read
     */
    public static long registerNids(File changeset, LongConsumer progress) throws IOException {
        try (ZipFile zip = new ZipFile(changeset)) {
            ZipEntry entry = zip.getEntry(ENTRY);
            if (entry == null) {
                return -1;
            }
            AtomicLong registered = new AtomicLong();
            try (InputStream in = zip.getInputStream(entry)) {
                PatternMembers members;
                while ((members = PatternMembers.parseDelimitedFrom(in)) != null) {
                    PublicId pattern = toPublicId(members.getPatternPublicId());
                    members.getComponentPublicIdsList().parallelStream().forEach(component ->
                            Entity.nidForSemantic(pattern, toPublicId(component)));
                    long total = registered.addAndGet(members.getComponentPublicIdsCount());
                    if (progress != null) {
                        progress.accept(total);
                    }
                }
            }
            LOG.info("Registered {} nid(s) from the identity index of {}",
                    String.format("%,d", registered.get()), changeset.getName());
            return registered.get();
        }
    }

    /**
     * Collects a changeset's identities as its records are written, then writes them as the index
     * entry. Thread-safe: records may be added from several threads, as the exporter does.
     *
     * <p>Spools to a temporary file rather than holding identities in memory, since a full export
     * lists every component in the store.
     */
    public static final class Writer implements Closeable {

        private final Path spool;
        private final OutputStream out;
        /** Members not yet spooled, per pattern — keyed by the pattern's UUIDs, in insertion order. */
        private final Map<String, PatternMembers.Builder> pending = new LinkedHashMap<>();
        private long count;

        public Writer() throws IOException {
            this.spool = Files.createTempFile("tinkar-identities-", ".pb");
            this.out = new BufferedOutputStream(Files.newOutputStream(spool));
        }

        /** Records the identity of {@code record}'s component. */
        public synchronized void add(TinkarMsg record) throws IOException {
            PublicId pattern = patternOf(record);
            String key = pattern.idString();
            PatternMembers.Builder members = pending.get(key);
            if (members == null) {
                members = PatternMembers.newBuilder().setPatternPublicId(toSchemaPublicId(pattern));
                pending.put(key, members);
            }
            members.addComponentPublicIds(componentOf(record));
            count++;
            if (members.getComponentPublicIdsCount() >= MEMBERS_PER_MESSAGE) {
                members.build().writeDelimitedTo(out);
                pending.remove(key);
            }
        }

        /** Components recorded so far. */
        public synchronized long count() {
            return count;
        }

        /** Writes the index as the {@value #ENTRY} entry of {@code zip}, which must have no entry open. */
        public synchronized void writeTo(ZipOutputStream zip) throws IOException {
            for (PatternMembers.Builder members : pending.values()) {
                members.build().writeDelimitedTo(out);
            }
            pending.clear();
            out.flush();
            zip.putNextEntry(new ZipEntry(ENTRY));
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

    private static PublicId toPublicId(dev.ikm.tinkar.schema.PublicId pbPublicId) {
        return PublicIds.of(pbPublicId.getUuidsList().stream().map(UUID::fromString).toArray(UUID[]::new));
    }

    private static dev.ikm.tinkar.schema.PublicId toSchemaPublicId(PublicId publicId) {
        List<String> uuids = new ArrayList<>();
        publicId.asUuidList().forEach(uuid -> uuids.add(uuid.toString()));
        return dev.ikm.tinkar.schema.PublicId.newBuilder().addAllUuids(uuids).build();
    }
}
