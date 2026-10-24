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

import com.google.protobuf.Descriptors.FieldDescriptor;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.common.util.io.CountingInputStream;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.schema.PublicId;
import dev.ikm.tinkar.schema.TinkarMsg;
import dev.ikm.tinkar.terms.EntityBinding;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import org.eclipse.collections.impl.list.mutable.primitive.LongArrayList;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.jar.Manifest;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * A format-1 or format-2 change set written again in format {@value ChangeSetFormat#CURRENT_VERSION}:
 * a component table, one record entry per pattern in table order, references by sequence, a
 * reference table of the components referred to but not carried, and a manifest that
 * describes every entry. Nothing of the content changes, with one exception: a component the
 * incremental writer wrote at several commits is carried once, by its last record, which
 * holds every version the earlier ones did.
 *
 * <p>Two passes over the source. The first numbers the components: each record's own
 * component goes to the bucket of its kind or pattern, every reference not carried is noted
 * with the pattern its field implies, a stamp field or a pattern field. The second rewrites
 * each record's references to sequences and spools it to its bucket's entry.
 *
 * <p>Given a resolver, a compaction also names the pattern of every reference no field
 * implies, by asking the resolver, which reads it from knowledge bases that hold the
 * component: a UUID names the same component in every one of them. Without one, such a
 * reference is written with no pattern, as the store that wrote it knew none, and a store
 * that mints by pattern cannot load it without a base. With a resolver, a format-3 change set
 * is rewritten too: expanded to references by UUID, then compacted.
 *
 * <p>Given modules, a compaction keeps only the versions stamped in them, as a merge of a
 * knowledge base's extension does: a stamp in another module goes, a component left with no
 * version goes, and so does every reference only they made. A session file carries what Komet
 * wrote on its own beside the extension's content; this leaves the extension's content alone.
 */
public final class ChangeSetCompaction extends TrackingCallable<ChangeSetCompaction.Result> {

    public record Result(File target, long records, long repeatsSuperseded, long components, long referencedOnly,
                         long referencesLeftAsUuids, int entries, List<UUID> withoutPattern) {

        public Result {
            withoutPattern = List.copyOf(withoutPattern);
        }

        public String text() {
            StringBuilder text = new StringBuilder(target.getName()).append(": ")
                    .append(String.format("%,d", records)).append(" record(s) in ").append(entries).append(" entries, ")
                    .append(String.format("%,d", components)).append(" component(s) of which ")
                    .append(String.format("%,d", referencedOnly)).append(" referenced only");
            if (repeatsSuperseded > 0) {
                text.append("; ").append(String.format("%,d", repeatsSuperseded)).append(" repeated record(s) superseded by a later one");
            }
            if (referencesLeftAsUuids > 0) {
                text.append("; ").append(String.format("%,d", referencesLeftAsUuids)).append(" reference(s) left as UUIDs");
            }
            if (!withoutPattern.isEmpty()) {
                text.append("; ").append(String.format("%,d", withoutPattern.size()))
                        .append(" reference(s) with no pattern, none known to the knowledge bases: ")
                        .append(withoutPattern.subList(0, Math.min(5, withoutPattern.size())))
                        .append(withoutPattern.size() > 5 ? " …" : "");
            }
            return text.toString();
        }
    }

    /** What a referenced-only component's field implies about its pattern. */
    private static final int UNKNOWN = 1;
    private static final int STAMP = 2;
    private static final int PATTERN = 3;
    /** A concept: the schema types a stamp's status, author, module and path, a pattern's purpose
     * and meaning, and a field definition's meaning, data type and purpose as concepts. */
    private static final int CONCEPT = 4;

    private final File source;
    private final File target;
    private final Function<UUID[], Optional<UUID[]>> patternOf;
    private final java.util.Set<UUID> modules;

    public ChangeSetCompaction(File source, File target) {
        this(source, target, null);
    }

    public ChangeSetCompaction(File source, File target, Function<UUID[], Optional<UUID[]>> patternOf) {
        this(source, target, patternOf, java.util.Set.of());
    }

    /**
     * A compaction that names the pattern of every reference it can.
     *
     * @param source    the change set to read, in any format
     * @param target    the format-3 change set to write
     * @param patternOf the pattern of a component by its UUIDs, empty when unknown; null for none
     * @param modules   the modules whose versions are kept; empty keeps every version
     */
    public ChangeSetCompaction(File source, File target, Function<UUID[], Optional<UUID[]>> patternOf,
                               java.util.Set<UUID> modules) {
        super(false, true);
        this.source = source;
        this.target = target;
        this.patternOf = patternOf;
        this.modules = java.util.Set.copyOf(modules);
        updateTitle("Compact change set " + source.getName());
    }

    /**
     * The pattern of a component as the running store knows it: the concept, stamp or pattern
     * pattern for those kinds, a semantic's own pattern. Empty for a component the store does not
     * hold; never minted.
     *
     * @return the resolver
     */
    public static Function<UUID[], Optional<UUID[]>> patternsFromStore() {
        return uuids -> {
            dev.ikm.tinkar.common.id.PublicId id = PublicIds.of(uuids);
            if (!PrimitiveData.get().hasPublicId(id)) {
                return Optional.empty();
            }
            Optional<Object> chronology = EntityService.get().getChronology(PrimitiveData.nid(id)).map(Object.class::cast);
            return chronology.map(component -> switch (component) {
                case ConceptEntity<?> concept -> EntityBinding.Concept.pattern().publicId();
                case StampEntity<?> stamp -> EntityBinding.Stamp.pattern().publicId();
                case PatternEntity<?> pattern -> EntityBinding.Pattern.pattern().publicId();
                case SemanticEntity<?> semantic -> semantic.pattern().publicId();
                default -> null;
            }).map(dev.ikm.tinkar.common.id.PublicId::asUuidArray);
        };
    }

    /** The carried components of one entry: a kind, or a semantic pattern. */
    private static final class Bucket {
        final String label;
        final UUID[] pattern;
        final IntArrayList ordinals = new IntArrayList();
        int patternSequence;
        ChangeSetWriter.Spool spool;

        Bucket(String label, UUID[] pattern) {
            this.label = label;
            this.pattern = pattern;
        }
    }

    /** What the first pass learns, and the sequences the ordering gives. */
    private final class Numbering {
        /** Every UUID of every carried component, to its ordinal: first-seen order, from 1. */
        final UuidSequenceMap carried = new UuidSequenceMap(1 << 16);
        /** The UUID words of each carried component, by ordinal: {@code ownBits[ownStart[ordinal] ..< ownStart[ordinal + 1]]}. */
        final LongArrayList ownBits = new LongArrayList();
        final IntArrayList ownStart = new IntArrayList();
        /** The index of each carried component's last record, by ordinal; records count from 1. */
        final IntArrayList lastRecord = new IntArrayList();
        final Bucket patterns = new Bucket("patterns", uuids(EntityBinding.Pattern.pattern().publicId()));
        final Bucket stamps = new Bucket("stamps", uuids(EntityBinding.Stamp.pattern().publicId()));
        final Bucket concepts = new Bucket("concepts", uuids(EntityBinding.Concept.pattern().publicId()));
        final Map<UUID, Bucket> semantics = new LinkedHashMap<>();
        /** Every UUID of every component referred to while not carried, to its index: from 1. */
        final UuidSequenceMap referencedIndex = new UuidSequenceMap(1 << 12);
        final List<long[]> referencedBits = new ArrayList<>();
        final IntArrayList referencedPattern = new IntArrayList();
        /** The pattern the resolver named for a referenced component no field implies one for, by index. */
        final Map<Integer, UUID[]> resolvedPattern = new LinkedHashMap<>();
        /** The first UUID of each such component the resolver could not name. */
        final List<UUID> withoutPattern = new ArrayList<>();
        long records;
        long repeats;
        // The ordering: rows of the table, a carried ordinal (> 0) or a referenced index (< 0), each with its pattern sequence.
        final IntArrayList rows = new IntArrayList();
        final IntArrayList rowPattern = new IntArrayList();
        int componentRows;
        int[] sequenceOfOrdinal;
        int[] sequenceOfReferenced;
        List<Bucket> order;

        Numbering() {
            ownStart.add(0);
            ownStart.add(0);
            lastRecord.add(0);
        }

        int ordinalOf(UUID[] uuids) {
            return carried.get(uuids[0].getMostSignificantBits(), uuids[0].getLeastSignificantBits());
        }

        /** Files a record's component on first sight; notes a repeat's later record otherwise. */
        void file(TinkarMsg record, UUID[] uuids, int index) {
            int ordinal = ordinalOf(uuids);
            if (ordinal != 0) {
                lastRecord.set(ordinal, index);
                repeats++;
                return;
            }
            ordinal = lastRecord.size();
            lastRecord.add(index);
            for (UUID uuid : uuids) {
                carried.put(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits(), ordinal);
                ownBits.add(uuid.getMostSignificantBits());
                ownBits.add(uuid.getLeastSignificantBits());
            }
            ownStart.add(ownBits.size());
            bucketOf(record).ordinals.add(ordinal);
        }

        Bucket bucketOf(TinkarMsg record) {
            return switch (record.getValueCase()) {
                case PATTERN_CHRONOLOGY -> patterns;
                case STAMP_CHRONOLOGY -> stamps;
                case CONCEPT_CHRONOLOGY -> concepts;
                case SEMANTIC_CHRONOLOGY -> {
                    UUID[] pattern = SchemaIds.uuids(record.getSemanticChronology().getPatternForSemanticPublicId());
                    yield semantics.computeIfAbsent(pattern[0], first -> new Bucket(first.toString(), pattern));
                }
                case VALUE_NOT_SET -> throw new IllegalStateException("Tinkar message value not set");
            };
        }

        /** Notes a reference to a component not carried so far, with what its field implies. */
        void refer(FieldDescriptor field, UUID[] uuids) {
            if (ordinalOf(uuids) != 0) {
                return;
            }
            int code = switch (field == null ? "" : field.getName()) {
                case "stamp_chronology_public_id" -> STAMP;
                case "pattern_for_semantic_public_id" -> PATTERN;
                case "status_public_id", "author_public_id", "module_public_id", "path_public_id",
                     "referenced_component_purpose_public_id", "referenced_component_meaning_public_id",
                     "meaning_public_id", "data_type_public_id", "purpose_public_id" -> CONCEPT;
                default -> UNKNOWN;
            };
            int index = referencedIndex.get(uuids[0].getMostSignificantBits(), uuids[0].getLeastSignificantBits());
            if (index == 0) {
                index = ensureReferenced(uuids);
            }
            if (code != UNKNOWN && referencedPattern.get(index - 1) == UNKNOWN) {
                referencedPattern.set(index - 1, code);
            }
        }

        int ensureReferenced(UUID[] uuids) {
            int index = referencedIndex.get(uuids[0].getMostSignificantBits(), uuids[0].getLeastSignificantBits());
            if (index != 0) {
                return index;
            }
            long[] bits = new long[uuids.length * 2];
            for (int i = 0; i < uuids.length; i++) {
                bits[2 * i] = uuids[i].getMostSignificantBits();
                bits[2 * i + 1] = uuids[i].getLeastSignificantBits();
            }
            referencedBits.add(bits);
            referencedPattern.add(UNKNOWN);
            index = referencedBits.size();
            for (UUID uuid : uuids) {
                referencedIndex.put(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits(), index);
            }
            return index;
        }

        UUID[] ownUuids(int ordinal) {
            int from = ownStart.get(ordinal);
            int to = ownStart.get(ordinal + 1);
            UUID[] uuids = new UUID[(to - from) / 2];
            for (int i = 0; i < uuids.length; i++) {
                uuids[i] = new UUID(ownBits.get(from + 2 * i), ownBits.get(from + 2 * i + 1));
            }
            return uuids;
        }

        /**
         * Orders the table: the pattern pattern first, naming itself; the stamp and concept
         * patterns, referenced only when not carried; the patterns, the stamps, the concepts;
         * each semantic pattern's bucket, preceded by its pattern when that is not carried; then
         * every other referenced-only component, for the reference table.
         */
        void order() {
            sequenceOfOrdinal = new int[lastRecord.size()];
            if (patternOf != null) {
                resolvePatterns();
            }
            // Every pattern placed as a prelude is a referenced component when not carried: the
            // binding patterns, and each semantic bucket's pattern. Listed now, so the sequences
            // are sized once.
            for (Bucket bucket : all()) {
                if (ordinalOf(bucket.pattern) == 0) {
                    ensureReferenced(bucket.pattern);
                }
            }
            sequenceOfReferenced = new int[referencedBits.size() + 1];
            order = new ArrayList<>();
            if (repeats > 0) {
                // A repeated component is carried by its last record, and the table lists it where that is.
                for (Bucket bucket : all()) {
                    byLastRecord(bucket.ordinals);
                }
            }
            int patternPattern = ordinalOf(patterns.pattern);
            if (patternPattern != 0) {
                int at = patterns.ordinals.indexOf(patternPattern);
                patterns.ordinals.removeAtIndex(at);
                patterns.ordinals.addAtIndex(0, patternPattern);
                addCarried(patternPattern, 1);
            } else {
                addReferenced(ensureReferenced(patterns.pattern), 1);
            }
            patterns.patternSequence = 1;
            stamps.patternSequence = placePattern(stamps.pattern);
            concepts.patternSequence = placePattern(concepts.pattern);
            for (Bucket bucket : List.of(patterns, stamps, concepts)) {
                if (!bucket.ordinals.isEmpty()) {
                    order.add(bucket);
                }
                addMembers(bucket, patternPattern);
            }
            for (Bucket bucket : semantics.values()) {
                bucket.patternSequence = placePattern(bucket.pattern);
                order.add(bucket);
                addMembers(bucket, 0);
            }
            componentRows = rows.size();
            for (int index = 1; index <= referencedBits.size(); index++) {
                long[] bits = referencedBits.get(index - 1);
                if (sequenceOfReferenced[index] == 0 && carried.get(bits[0], bits[1]) == 0) {
                    // Referred to before its record came, and still not carried: the reference table.
                    int code = referencedPattern.get(index - 1);
                    UUID[] resolved = resolvedPattern.get(index);
                    addReferenced(index, code == STAMP ? stamps.patternSequence : code == PATTERN ? 1
                            : code == CONCEPT ? concepts.patternSequence
                            : resolved != null ? placePattern(resolved) : 0);
                }
            }
        }

        /**
         * Asks the resolver for the pattern of every referenced component no field implies one
         * for, and lists each pattern named as referenced, so the sequences are sized once and the
         * pattern is placed before its member.
         */
        private void resolvePatterns() {
            int known = referencedBits.size();
            for (int index = 1; index <= known; index++) {
                long[] bits = referencedBits.get(index - 1);
                if (referencedPattern.get(index - 1) != UNKNOWN || carried.get(bits[0], bits[1]) != 0) {
                    continue;
                }
                UUID[] uuids = new UUID[bits.length / 2];
                for (int i = 0; i < uuids.length; i++) {
                    uuids[i] = new UUID(bits[2 * i], bits[2 * i + 1]);
                }
                Optional<UUID[]> pattern = patternOf.apply(uuids);
                if (pattern.isEmpty()) {
                    withoutPattern.add(uuids[0]);
                    continue;
                }
                resolvedPattern.put(index, pattern.get());
                if (ordinalOf(pattern.get()) == 0) {
                    int patternIndex = ensureReferenced(pattern.get());
                    if (referencedPattern.get(patternIndex - 1) == UNKNOWN) {
                        referencedPattern.set(patternIndex - 1, PATTERN);
                    }
                }
            }
        }

        private List<Bucket> all() {
            List<Bucket> all = new ArrayList<>(List.of(patterns, stamps, concepts));
            all.addAll(semantics.values());
            return all;
        }

        private void byLastRecord(IntArrayList ordinals) {
            long[] keyed = new long[ordinals.size()];
            for (int i = 0; i < keyed.length; i++) {
                int ordinal = ordinals.get(i);
                keyed[i] = ((long) lastRecord.get(ordinal) << 32) | ordinal;
            }
            Arrays.sort(keyed);
            for (int i = 0; i < keyed.length; i++) {
                ordinals.set(i, (int) keyed[i]);
            }
        }

        /** The sequence of a pattern: its row when carried, else a referenced-only row added here. */
        private int placePattern(UUID[] pattern) {
            int ordinal = ordinalOf(pattern);
            if (ordinal != 0) {
                if (sequenceOfOrdinal[ordinal] == 0) {
                    throw new IllegalStateException("Pattern " + pattern[0] + " is carried but not yet listed; patterns precede their members");
                }
                return sequenceOfOrdinal[ordinal];
            }
            int index = ensureReferenced(pattern);
            if (sequenceOfReferenced[index] == 0) {
                addReferenced(index, 1);
            }
            return sequenceOfReferenced[index];
        }

        private void addMembers(Bucket bucket, int skip) {
            for (int i = 0; i < bucket.ordinals.size(); i++) {
                int ordinal = bucket.ordinals.get(i);
                if (ordinal != skip) {
                    addCarried(ordinal, bucket.patternSequence);
                }
            }
        }

        private void addCarried(int ordinal, int patternSequence) {
            rows.add(ordinal);
            rowPattern.add(patternSequence);
            sequenceOfOrdinal[ordinal] = rows.size();
        }

        private void addReferenced(int index, int patternSequence) {
            rows.add(-index);
            rowPattern.add(patternSequence);
            sequenceOfReferenced[index] = rows.size();
        }

        /** The one sequence every UUID of a reference maps to, or 0 when they map to none or to several. */
        int sequenceOf(UUID[] uuids) {
            int sequence = 0;
            for (UUID uuid : uuids) {
                int ordinal = carried.get(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
                int found;
                if (ordinal != 0) {
                    found = sequenceOfOrdinal[ordinal];
                } else {
                    int index = referencedIndex.get(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
                    found = index == 0 ? 0 : sequenceOfReferenced[index];
                }
                if (found == 0 || (sequence != 0 && found != sequence)) {
                    return 0;
                }
                sequence = found;
            }
            return sequence;
        }

        dev.ikm.tinkar.common.id.PublicId publicIdOfRow(int row) {
            int value = rows.get(row);
            if (value > 0) {
                return PublicIds.of(ownUuids(value));
            }
            long[] bits = referencedBits.get(-value - 1);
            UUID[] uuids = new UUID[bits.length / 2];
            for (int i = 0; i < uuids.length; i++) {
                uuids[i] = new UUID(bits[2 * i], bits[2 * i + 1]);
            }
            return PublicIds.of(uuids);
        }
    }

    private static UUID[] uuids(dev.ikm.tinkar.common.id.PublicId id) {
        return id.asUuidArray();
    }

    @Override
    protected Result compute() throws Exception {
        int sourceVersion;
        try (ZipFile zip = new ZipFile(source)) {
            Manifest manifest = ChangeSetFormat.manifest(zip)
                    .orElseThrow(() -> new IllegalArgumentException(source.getName() + " has no manifest, " + ChangeSetFormat.MANIFEST));
            sourceVersion = ChangeSetFormat.version(manifest.getMainAttributes(), source.getName());
        }
        if (sourceVersion < ChangeSetFormat.CURRENT_VERSION) {
            return compact(source);
        }
        if (patternOf == null && modules.isEmpty()) {
            throw new IllegalArgumentException(source.getName() + " is in format version " + sourceVersion + " already");
        }
        // Format 3 with references that may name no pattern: expanded to references by UUID, then compacted.
        Path expanded = Files.createTempFile("ike-compaction-", "-expanded.zip");
        try {
            new ChangeSetExpansion(source, expanded.toFile()).call();
            return compact(expanded.toFile());
        } finally {
            Files.deleteIfExists(expanded);
        }
    }

    private Result compact(File source) throws IOException {
        try (ZipFile zip = new ZipFile(source)) {
            Manifest manifest = ChangeSetFormat.manifest(zip)
                    .orElseThrow(() -> new IllegalArgumentException(source.getName() + " has no manifest, " + ChangeSetFormat.MANIFEST));
            List<ChangeSetFormat.RecordEntry> entries = ChangeSetFormat.recordEntries(zip, manifest);
            long total = 0;
            for (ChangeSetFormat.RecordEntry entry : entries) {
                total += Math.max(0, entry.entry().getCompressedSize());
            }
            long span = Math.max(1, 2 * total);
            updateProgress(0, span);

            KeptStamps kept = modules.isEmpty() ? null : keptStamps(zip, entries);

            // Pass 1: number the components.
            Numbering numbering = new Numbering();
            readRecords(zip, entries, kept, 0, span, "Numbering", (record, index) -> {
                PublicId own = ChangeSetFormat.componentOf(record);
                UUID[] uuids = SchemaIds.uuids(own);
                numbering.file(record, uuids, index);
                PublicIdRewriter.forEachPublicId(record, (field, id) -> {
                    if (id != own && SchemaIds.hasUuids(id)) {
                        numbering.refer(field, SchemaIds.uuids(id));
                    }
                });
            });
            updateMessage("Ordering the table...");
            numbering.order();

            // Pass 2: rewrite the references and spool each record to its entry.
            Path spoolDirectory = Files.createTempDirectory("ike-compaction-");
            long[] byKind = new long[4];
            long[] leftAsUuids = {0};
            long[] written = {0};
            UnaryOperator<PublicId> compact = id -> {
                if (!SchemaIds.hasUuids(id)) {
                    return id;
                }
                int sequence = numbering.sequenceOf(SchemaIds.uuids(id));
                if (sequence == 0) {
                    leftAsUuids[0]++;
                    return id;
                }
                return PublicId.newBuilder().setSequence(sequence).build();
            };
            List<ChangeSetWriter.Entry> writtenEntries = new ArrayList<>();
            try {
                for (int i = 0; i < numbering.order.size(); i++) {
                    Bucket bucket = numbering.order.get(i);
                    bucket.spool = new ChangeSetWriter.Spool(spoolDirectory, i + 1, bucket.label);
                }
                readRecords(zip, entries, kept, total, span, "Compacting", (record, index) -> {
                    UUID[] uuids = SchemaIds.uuids(ChangeSetFormat.componentOf(record));
                    int ordinal = numbering.ordinalOf(uuids);
                    if (numbering.lastRecord.get(ordinal) != index) {
                        return; // a repeat; the component's last record carries it
                    }
                    TinkarMsg rewritten = PublicIdRewriter.rewriteReferences(record, compact);
                    if (ChangeSetFormat.componentOf(rewritten).getUuidBitsCount() == 0) {
                        rewritten = PublicIdRewriter.withOwnId(rewritten, SchemaIds.toSchema(uuids));
                    }
                    numbering.bucketOf(record).spool.write(rewritten);
                    byKind[ChangeSetWriter.kindIndex(rewritten)]++;
                    written[0]++;
                });
                for (Bucket bucket : numbering.order) {
                    writtenEntries.add(bucket.spool.finish());
                }
                updateMessage("Assembling " + target.getName() + "...");
                Path parent = target.getAbsoluteFile().toPath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(target.toPath()), 1 << 20))) {
                    zos.putNextEntry(new ZipEntry(ChangeSetFormat.COMPONENT_TABLE));
                    ComponentTable.Writer table = new ComponentTable.Writer(zos);
                    for (int row = 0; row < numbering.componentRows; row++) {
                        table.add(numbering.rowPattern.get(row), numbering.publicIdOfRow(row), numbering.rows.get(row) < 0);
                    }
                    table.flush();
                    zos.closeEntry();
                    for (ChangeSetWriter.Entry entry : writtenEntries) {
                        ChangeSetWriter.store(zos, entry);
                    }
                    zos.putNextEntry(new ZipEntry(ChangeSetFormat.REFERENCE_TABLE));
                    ComponentTable.Writer references = new ComponentTable.Writer(zos);
                    for (int row = numbering.componentRows; row < numbering.rows.size(); row++) {
                        references.add(numbering.rowPattern.get(row), numbering.publicIdOfRow(row), true);
                    }
                    references.flush();
                    zos.closeEntry();
                    zos.putNextEntry(new ZipEntry(ChangeSetFormat.MANIFEST));
                    zos.write(ChangeSetWriter.manifestContent(ChangeSetWriter.counts(byKind), writtenEntries, numbering.rows.size(),
                            ChangeSetWriter.sections(manifest),
                            ChangeSetFormat.vertexIdentityDerived(manifest.getMainAttributes())).getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
            } finally {
                for (Bucket bucket : numbering.order) {
                    if (bucket.spool != null) {
                        bucket.spool.close();
                    }
                }
                try (var leftovers = Files.list(spoolDirectory)) {
                    for (Path leftover : leftovers.toList()) {
                        Files.deleteIfExists(leftover);
                    }
                }
                Files.deleteIfExists(spoolDirectory);
            }
            updateProgress(1, 1);
            updateMessage("Compacted " + String.format("%,d", written[0]) + " record(s)");
            return new Result(target, written[0], numbering.repeats, numbering.rows.size(),
                    numbering.rows.size() - numbering.componentRows + referencedOnlyAmong(numbering),
                    leftAsUuids[0], writtenEntries.size(), numbering.withoutPattern);
        }
    }

    private static long referencedOnlyAmong(Numbering numbering) {
        long count = 0;
        for (int row = 0; row < numbering.componentRows; row++) {
            if (numbering.rows.get(row) < 0) {
                count++;
            }
        }
        return count;
    }

    private interface RecordVisitor {
        void visit(TinkarMsg record, int index) throws IOException;
    }

    /** The UUIDs of the stamps in the kept modules. */
    private record KeptStamps(java.util.Set<UUID> uuids) {
        boolean has(PublicId stamp) {
            for (UUID uuid : SchemaIds.uuids(stamp)) {
                if (uuids.contains(uuid)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** A pass over the stamps: those with a version in one of the modules are kept. */
    private KeptStamps keptStamps(ZipFile zip, List<ChangeSetFormat.RecordEntry> entries) throws IOException {
        java.util.Set<UUID> stamps = new java.util.HashSet<>();
        readRecords(zip, entries, null, 0, 1, "Selecting stamps in", (record, index) -> {
            if (!record.hasStampChronology()) {
                return;
            }
            dev.ikm.tinkar.schema.StampChronology stamp = record.getStampChronology();
            boolean inModule = inModules(stamp.getFirstStampVersion().getModulePublicId())
                    || (stamp.hasSecondStampVersion() && inModules(stamp.getSecondStampVersion().getModulePublicId()));
            if (inModule) {
                stamps.addAll(List.of(SchemaIds.uuids(stamp.getPublicId())));
            }
        });
        return new KeptStamps(stamps);
    }

    private boolean inModules(PublicId module) {
        for (UUID uuid : SchemaIds.uuids(module)) {
            if (modules.contains(uuid)) {
                return true;
            }
        }
        return false;
    }

    /** A record with only its versions on kept stamps, or null when none is left. */
    private static TinkarMsg keptPart(TinkarMsg record, KeptStamps kept) {
        return switch (record.getValueCase()) {
            case STAMP_CHRONOLOGY -> kept.has(record.getStampChronology().getPublicId()) ? record : null;
            case CONCEPT_CHRONOLOGY -> {
                var chronology = record.getConceptChronology();
                var versions = chronology.getConceptVersionsList().stream()
                        .filter(version -> kept.has(version.getStampChronologyPublicId())).toList();
                yield versions.isEmpty() ? null : TinkarMsg.newBuilder().setConceptChronology(
                        chronology.toBuilder().clearConceptVersions().addAllConceptVersions(versions)).build();
            }
            case PATTERN_CHRONOLOGY -> {
                var chronology = record.getPatternChronology();
                var versions = chronology.getPatternVersionsList().stream()
                        .filter(version -> kept.has(version.getStampChronologyPublicId())).toList();
                yield versions.isEmpty() ? null : TinkarMsg.newBuilder().setPatternChronology(
                        chronology.toBuilder().clearPatternVersions().addAllPatternVersions(versions)).build();
            }
            case SEMANTIC_CHRONOLOGY -> {
                var chronology = record.getSemanticChronology();
                var versions = chronology.getSemanticVersionsList().stream()
                        .filter(version -> kept.has(version.getStampChronologyPublicId())).toList();
                yield versions.isEmpty() ? null : TinkarMsg.newBuilder().setSemanticChronology(
                        chronology.toBuilder().clearSemanticVersions().addAllSemanticVersions(versions)).build();
            }
            case VALUE_NOT_SET -> throw new IllegalStateException("Tinkar message value not set");
        };
    }

    /**
     * Reads every record of the entries in order, numbering them from 1, with progress from {@code from} across half of
     * {@code span}; with kept stamps, a record is seen with only its versions on them, and not at all when none is.
     */
    private void readRecords(ZipFile zip, List<ChangeSetFormat.RecordEntry> entries, KeptStamps kept, long from, long span,
                             String verb, RecordVisitor visitor) throws IOException {
        int index = 0;
        long done = from;
        for (ChangeSetFormat.RecordEntry entry : entries) {
            updateMessage(verb + " " + entry.entry().getName() + "...");
            CountingInputStream counting = new CountingInputStream(new BufferedInputStream(zip.getInputStream(entry.entry()), 1 << 20));
            try (InputStream in = entry.entry().getName().endsWith(ChangeSetFormat.GZIP_SUFFIX)
                    ? new BufferedInputStream(new GZIPInputStream(counting, 1 << 16), 1 << 20)
                    : new BufferedInputStream(counting, 1 << 20)) {
                TinkarMsg record;
                while ((record = TinkarMsg.parseDelimitedFrom(in)) != null) {
                    if (kept != null) {
                        record = keptPart(record, kept);
                        if (record == null) {
                            continue;
                        }
                    }
                    visitor.visit(record, ++index);
                    if (index % 10_000 == 0) {
                        updateProgress(done + counting.getBytesRead(), span);
                    }
                }
            }
            done += Math.max(0, entry.entry().getCompressedSize());
            updateProgress(done, span);
        }
    }
}
