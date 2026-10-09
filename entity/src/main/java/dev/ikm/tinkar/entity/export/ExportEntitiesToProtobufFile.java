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
package dev.ikm.tinkar.entity.export;

import dev.ikm.tinkar.entity.changeset.ChangeSetFormat;
import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.common.alert.AlertStreams;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.impl.NidLayout;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.common.service.EntityCountSummary;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.aggregator.DefaultEntityAggregator;
import dev.ikm.tinkar.entity.aggregator.EntityAggregator;
import dev.ikm.tinkar.entity.aggregator.MembershipEntityAggregator;
import dev.ikm.tinkar.entity.aggregator.TemporalEntityAggregator;
import dev.ikm.tinkar.entity.transform.EntityToTinkarSchemaTransformer;
import dev.ikm.tinkar.schema.TinkarMsg;
import dev.ikm.tinkar.common.util.thread.StructuredScopes;
import dev.ikm.tinkar.common.util.thread.SubtaskFailedException;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.changeset.ComponentTable;
import dev.ikm.tinkar.terms.EntityBinding;
import org.eclipse.collections.api.factory.primitive.LongLists;
import org.eclipse.collections.api.factory.primitive.LongObjectMaps;
import org.eclipse.collections.api.list.primitive.MutableLongList;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongIntHashMap;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.GZIPOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ExportEntitiesToProtobufFile extends TrackingCallable<EntityCountSummary> {
    private static final Logger LOG =
            LoggerFactory.getLogger(ExportEntitiesToProtobufFile.class);
    private final File protobufFile;
    private final EntityToTinkarSchemaTransformer entityTransformer =
            EntityToTinkarSchemaTransformer.getInstance();
    // Entities may be delivered concurrently (RocksProvider iterates semantics in
    // parallel), so shared state is concurrent and stream writes are serialized
    // (IKE-Network/ike-issues#1142).
    // The modules and authors of the exported stamps, by nid: a public id is never a hash key.
    private final Set<Long> moduleNids = ConcurrentHashMap.newKeySet();
    private final Set<Long> authorNids = ConcurrentHashMap.newKeySet();
    /** Guards writes to the zip stream and the skip tallies. */
    private final Object writeLock = new Object();
    // Per-type tallies of entities whose transform failed on a dangling reference and
    // were therefore skipped: the manifest must describe the records actually written,
    // never the records intended, or every downstream import fails its count check
    // (IKE-Network/ike-issues#933).
    private long skippedConcepts;
    private long skippedSemantics;
    private long skippedPatterns;
    private long skippedStamps;
    private final EntityAggregator entityAggregator;


    public ExportEntitiesToProtobufFile(File file, EntityAggregator entityAggregator) {
        super(false, true);
        this.protobufFile = file;
        LOG.info("Exporting entities to: " + file);
        this.entityAggregator = entityAggregator;
        if (getTitle()==null || getTitle().isBlank()) {
            updateTitle("Export to Protobuf");
        }
    }

    public ExportEntitiesToProtobufFile(File file) {
        this(file, new DefaultEntityAggregator());
        updateTitle("Full Export to Protobuf");
    }

    public ExportEntitiesToProtobufFile(File file, long fromEpochMillis, long toEpochMillis) {
        this(file, new TemporalEntityAggregator(fromEpochMillis, toEpochMillis));
        updateTitle("Time-Based Export to Protobuf");
    }

    public ExportEntitiesToProtobufFile(File file, List<PublicId> membershipTags) {
        this(file, new MembershipEntityAggregator(membershipTags));
        updateTitle("Tag-Based Export to Protobuf");
    }

    /**
     * Writes a format-3 changeset (IKE-Network/ike-issues#1275): the component table first,
     * then one gzip entry of records per pattern, in table order, written in parallel; then the
     * components referenced but not carried; then the manifest. References are written as
     * sequences into the table. Design: {@code notes/design-2026-10-08-changeset-format-3.adoc}.
     */
    @Override
    public EntityCountSummary compute() {
        updateMessage("Analyzing Entities...");
        updateProgress(-1, 1);
        EntityCountSummary entityCountSummary = null;
        try {
            Buckets buckets = Buckets.collect(entityAggregator);
            EntityCountSummary aggregated = buckets.summary();
            addToTotalWork(aggregated.getTotalCount() * 2L);
            SequenceAllocator sequences = new SequenceAllocator(buckets);
            try (FileOutputStream fos = new FileOutputStream(protobufFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos, 1 << 20);
                 ZipOutputStream zos = new ZipOutputStream(bos)) {
                updateMessage("Writing the component table...");
                writeComponentTable(zos, buckets, sequences);
                updateMessage("Exporting Entities...");
                List<WrittenEntry> written = writeRecordEntries(zos, buckets, sequences);
                writeReferenceTable(zos, sequences);
                long totalSkipped = skippedConcepts + skippedSemantics + skippedPatterns + skippedStamps;
                entityCountSummary = new EntityCountSummary(
                        aggregated.conceptCount() - skippedConcepts,
                        aggregated.semanticCount() - skippedSemantics,
                        aggregated.patternCount() - skippedPatterns,
                        aggregated.stampCount() - skippedStamps);
                if (totalSkipped > 0) {
                    LOG.warn("Export skipped {} dangling entit{} ({} concepts, {} semantics,"
                                    + " {} patterns, {} stamps) — manifest counts describe the"
                                    + " records actually written",
                            totalSkipped, totalSkipped == 1 ? "y" : "ies",
                            skippedConcepts, skippedSemantics, skippedPatterns, skippedStamps);
                }
                LOG.info("Component table lists {} carried and {} referenced component(s)",
                        String.format("%,d", sequences.carriedCount()), String.format("%,d", sequences.referencedCount()));
                ZipEntry manifestEntry = new ZipEntry(ChangeSetFormat.MANIFEST);
                zos.putNextEntry(manifestEntry);
                zos.write(manifestContent(entityCountSummary, written, sequences.count(),
                        publicIds(moduleNids), publicIds(authorNids)).getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                zos.flush();
                zos.finish();
            }
        } catch (Throwable e) {
            LOG.error("Caught " + e + " while Exporting Entities");
            if (!(e instanceof RuntimeException rx && rx.getCause() instanceof InterruptedException)) {
                AlertStreams.dispatchToRoot(e);
                throw new RuntimeException(e);
            }
        } finally {
            updateMessage("In " + durationString());
            updateProgress(1,1);
        }
        logCounts(entityCountSummary);
        return entityCountSummary;
    }

    /** The carried components, in table order, each with the sequence of its pattern. */
    private void writeComponentTable(ZipOutputStream zos, Buckets buckets, SequenceAllocator sequences) throws IOException {
        zos.putNextEntry(new ZipEntry(ChangeSetFormat.COMPONENT_TABLE));
        try (ComponentTable.Writer table = new ComponentTable.Writer(zos)) {
            List<Buckets.Bucket> order = buckets.inTableOrder();
            for (int index = 0; index < order.size(); index++) {
                Buckets.Bucket bucket = order.get(index);
                for (SequenceAllocator.Referenced pattern : sequences.prelude(index)) {
                    table.add(pattern.patternSequence(), pattern.publicId(), true);
                }
                long[] nids = bucket.nids().toArray();
                if (sequences.danglingBuckets.contains(bucket.patternNid())) {
                    // The records of a pattern the store has no record for were skipped as
                    // dangling before too; now the whole entry is, and the table omits them.
                    LOG.error("DANGLING pattern nid={} — skipping its {} record(s)", bucket.patternNid(), nids.length);
                    danglingBuckets.add(bucket.patternNid());
                    for (long nid : nids) {
                        EntityHandle.get(nid).entity().ifPresent(this::tallySkip);
                        completedUnitOfWork();
                    }
                    continue;
                }
                int patternSequence = sequences.sequence(bucket.patternNid());
                for (int from = 0; from < nids.length; from += TABLE_CHUNK) {
                    final int start = from;
                    final int to = Math.min(nids.length, from + TABLE_CHUNK);
                    PublicId[] ids = new PublicId[to - start];
                    // The public ids of a chunk read in parallel: a store read each.
                    java.util.stream.IntStream.range(start, to).parallel().forEach(i -> ids[i - start] = PrimitiveData.publicId(nids[i]));
                    for (int i = start; i < to; i++) {
                        // The pattern pattern names itself.
                        int pattern = nids[i] == sequences.patternPatternNid ? sequences.sequence(nids[i]) : patternSequence;
                        table.add(pattern, ids[i - start], false);
                        completedUnitOfWork();
                    }
                }
            }
        }
        zos.closeEntry();
    }

    private static final int TABLE_CHUNK = 100_000;
    /** The pattern nids of buckets skipped whole, their pattern having no record. */
    private final Set<Long> danglingBuckets = ConcurrentHashMap.newKeySet();

    /** The components referenced but not carried, numbered after the carried ones. */
    private void writeReferenceTable(ZipOutputStream zos, SequenceAllocator sequences) throws IOException {
        List<SequenceAllocator.Referenced> referenced = sequences.referenced();
        if (referenced.isEmpty()) {
            return;
        }
        zos.putNextEntry(new ZipEntry(ChangeSetFormat.REFERENCE_TABLE));
        try (ComponentTable.Writer table = new ComponentTable.Writer(zos)) {
            for (SequenceAllocator.Referenced component : referenced) {
                table.add(component.patternSequence(), component.publicId(), true);
            }
        }
        zos.closeEntry();
    }

    /** A record entry as written: its name, its spool, and what the manifest says about it. */
    private record WrittenEntry(String name, Path spool, long size, long crc, String sha256, long count) {
    }

    /**
     * One gzip entry of records per bucket, each written on its own thread into a spool and
     * then stored, uncompressed by the zip, in table order.
     */
    private List<WrittenEntry> writeRecordEntries(ZipOutputStream zos, Buckets buckets, SequenceAllocator sequences) throws Exception {
        List<Buckets.Bucket> order = buckets.inTableOrder();
        Path spoolDir = Files.createTempDirectory("ike-changeset-");
        WrittenEntry[] written = new WrittenEntry[order.size()];
        try {
            try (StructuredTaskScope<Object, Void, SubtaskFailedException> scope = StructuredScopes.open()) {
                Semaphore permits = new Semaphore(Runtime.getRuntime().availableProcessors());
                for (int i = 0; i < order.size(); i++) {
                    int ordinal = i + 1;
                    Buckets.Bucket bucket = order.get(i);
                    permits.acquire();
                    scope.fork(() -> {
                        try {
                            written[ordinal - 1] = writeEntry(ordinal, bucket, spoolDir, sequences);
                        } finally {
                            permits.release();
                        }
                        return null;
                    });
                }
                scope.join();
            }
            List<WrittenEntry> result = new ArrayList<>(written.length);
            for (WrittenEntry entry : written) {
                ZipEntry zipEntry = new ZipEntry(entry.name());
                zipEntry.setMethod(ZipEntry.STORED);
                zipEntry.setSize(entry.size());
                zipEntry.setCompressedSize(entry.size());
                zipEntry.setCrc(entry.crc());
                zos.putNextEntry(zipEntry);
                Files.copy(entry.spool(), zos);
                zos.closeEntry();
                Files.delete(entry.spool());
                result.add(entry);
            }
            return result;
        } finally {
            try (var leftovers = Files.list(spoolDir)) {
                leftovers.forEach(path -> path.toFile().delete());
            }
            Files.deleteIfExists(spoolDir);
        }
    }

    /** One bucket's records, transformed with sequences for references, into a gzip spool. */
    private WrittenEntry writeEntry(int ordinal, Buckets.Bucket bucket, Path spoolDir, SequenceAllocator sequences) throws Exception {
        String name = ChangeSetFormat.recordEntryName(ordinal, bucket.label());
        Path spool = spoolDir.resolve(String.format("%04d.pb.gz", ordinal));
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        CRC32 crc = new CRC32();
        long count;
        try (OutputStream file = new BufferedOutputStream(Files.newOutputStream(spool), 1 << 20);
             CheckedOutputStream checked = new CheckedOutputStream(file, crc);
             GZIPOutputStream gzip = new GZIPOutputStream(checked, 1 << 16);
             DigestOutputStream out = new DigestOutputStream(gzip, sha256)) {
            count = ScopedValue.where(EntityToTinkarSchemaTransformer.SCOPED_SEQUENCE_OF_NID, sequences)
                    .call(() -> writeRecords(bucket, out));
        }
        return new WrittenEntry(name, spool, Files.size(spool), crc.getValue(), HexFormat.of().formatHex(sha256.digest()), count);
    }

    private long writeRecords(Buckets.Bucket bucket, OutputStream out) throws IOException {
        long[] nids = bucket.nids().toArray();
        long count = 0;
        if (danglingBuckets.contains(bucket.patternNid())) {
            return 0; // tallied and skipped with the table
        }
        for (long nid : nids) {
            Entity<?> entity = EntityHandle.get(nid).orNull();
            if (entity == null) {
                LOG.warn("No entity for nid {} at export time; its table entry stays, its record is absent", nid);
                completedUnitOfWork();
                continue;
            }
            try {
                if (entity instanceof StampEntity stampEntity) {
                    moduleNids.add(stampEntity.moduleNid());
                    authorNids.add(stampEntity.authorNid());
                }
                Entity<?> written = entity instanceof StampRecord stampRecord
                        ? stampRecord.withoutSupersededUncommittedVersions()
                        : entity;
                TinkarMsg pbTinkarMsg = entityTransformer.transform(written);
                pbTinkarMsg.writeDelimitedTo(out);
                count++;
            } catch (RuntimeException e) {
                LOG.error("DANGLING REFERENCE — skipping "
                        + entity.getClass().getSimpleName() + " nid=" + entity.nid()
                        + " publicId=" + entity.publicId() + ": " + e, e);
                AlertStreams.dispatchToRoot(e);
                tallySkip(entity);
            }
            completedUnitOfWork();
        }
        return count;
    }

    /**
     * The nids an export carries, by kind and, for semantics, by pattern: one bucket per record
     * entry, in table order: patterns (the pattern pattern first), stamps, concepts, then each
     * semantic pattern. Buckets are sorted by nid, so the same store exports the same file
     * however its walk is scheduled; the order carries no meaning beyond that.
     */
    static final class Buckets {
        record Bucket(String label, long patternNid, MutableLongList nids) {
        }

        private final MutableLongList patterns = LongLists.mutable.empty();
        private final MutableLongList stamps = LongLists.mutable.empty();
        private final MutableLongList concepts = LongLists.mutable.empty();
        private final MutableLongObjectMap<MutableLongList> semanticsByPattern = LongObjectMaps.mutable.empty();
        private long orphans;
        private long canceled;

        static Buckets collect(EntityAggregator aggregator) {
            Buckets buckets = new Buckets();
            // The aggregator may deliver nids on several threads (a Rocks store walks semantics
            // in parallel); the entity read is outside the lock, the append inside.
            aggregator.aggregate(nid -> {
                Entity<?> entity = EntityHandle.get(nid).orNull();
                synchronized (buckets) {
                    if (entity != null && entity.versions().isEmpty()) {
                        // Every version canceled: the record reads with none (EntityCodec2.read
                        // leaves a canceled version out), so there is nothing to carry. Its
                        // identity travels only if a record refers to it, as a referenced-only
                        // entry of the component table (IKE-Network/ike-issues#1276).
                        buckets.canceled++;
                        return;
                    }
                    switch (entity) {
                        case null -> buckets.orphans++;
                        case PatternEntity<?> p -> buckets.patterns.add(nid);
                        case StampEntity<?> st -> buckets.stamps.add(nid);
                        case ConceptEntity<?> c -> buckets.concepts.add(nid);
                        case SemanticEntity<?> sem -> buckets.semanticsByPattern
                                .getIfAbsentPut(sem.patternNid(), LongLists.mutable::empty).add(nid);
                        default -> throw new IllegalStateException("Unexpected entity " + entity.getClass() + " for nid " + nid);
                    }
                }
            });
            if (buckets.orphans > 0) {
                LOG.info("Skipped {} orphan nid(s) during aggregation (allocated, no entity bytes)", buckets.orphans);
            }
            if (buckets.canceled > 0) {
                LOG.info("Left out {} component(s) whose every version is canceled: no record to carry", buckets.canceled);
            }
            buckets.patterns.sortThis();
            buckets.stamps.sortThis();
            buckets.concepts.sortThis();
            buckets.semanticsByPattern.forEachValue(MutableLongList::sortThis);
            // The pattern pattern first: patterns precede their members, and it names itself.
            long patternPattern = EntityBinding.Pattern.pattern().nid();
            int at = buckets.patterns.indexOf(patternPattern);
            if (at > 0) {
                buckets.patterns.removeAtIndex(at);
                buckets.patterns.addAtIndex(0, patternPattern);
            }
            return buckets;
        }

        EntityCountSummary summary() {
            long semantics = 0;
            for (MutableLongList list : semanticsByPattern.values()) {
                semantics += list.size();
            }
            return new EntityCountSummary(concepts.size(), semantics, patterns.size(), stamps.size());
        }

        List<Bucket> inTableOrder() {
            List<Bucket> order = new ArrayList<>();
            order.add(new Bucket("patterns", EntityBinding.Pattern.pattern().nid(), patterns));
            order.add(new Bucket("stamps", EntityBinding.Stamp.pattern().nid(), stamps));
            order.add(new Bucket("concepts", EntityBinding.Concept.pattern().nid(), concepts));
            long[] patternNids = semanticsByPattern.keySet().toSortedArray();
            for (long patternNid : patternNids) {
                String label;
                try {
                    label = PrimitiveData.publicId(patternNid).asUuidArray()[0].toString();
                } catch (RuntimeException e) {
                    label = "pattern-" + Long.toUnsignedString(patternNid);
                }
                order.add(new Bucket(label, patternNid, semanticsByPattern.get(patternNid)));
            }
            return order;
        }
    }

    /**
     * Sequences by nid: the carried components in table order, then the components referenced
     * but not carried, allocated as the records meet them, each after its pattern.
     */
    static final class SequenceAllocator implements EntityToTinkarSchemaTransformer.SequenceOfNid {
        record Referenced(int sequence, int patternSequence, PublicId publicId) {
        }

        final long patternPatternNid = EntityBinding.Pattern.pattern().nid();
        private final LongIntHashMap carried;
        private final ConcurrentHashMap<Long, Integer> referenced = new ConcurrentHashMap<>();
        private final List<Referenced> referencedInOrder = new ArrayList<>();
        private final int carriedCount;
        private int next;

        /** The patterns listed before a bucket's members, referenced only: the bucket's pattern when no record carries it. */
        private final Map<Integer, List<Referenced>> preludes = new HashMap<>();
        /** The pattern nids of buckets whose pattern no record describes: skipped whole. */
        final Set<Long> danglingBuckets = new HashSet<>();

        SequenceAllocator(Buckets buckets) {
            List<Buckets.Bucket> order = buckets.inTableOrder();
            int count = (int) buckets.summary().getTotalCount();
            carried = new LongIntHashMap(Math.max(16, count + 8));
            int sequence = 0;
            for (int i = 0; i < order.size(); i++) {
                Buckets.Bucket bucket = order.get(i);
                // The patterns the bucket's members name must come first in the table: the
                // three bindings before everything (they need no record), and a semantic
                // pattern no record carries before its semantics.
                List<Referenced> prelude = new ArrayList<>();
                long[] needed = i == 0
                        ? new long[]{patternPatternNid, EntityBinding.Concept.pattern().nid(), EntityBinding.Stamp.pattern().nid()}
                        : new long[]{bucket.patternNid()};
                for (long patternNid : needed) {
                    if (carried.getIfAbsent(patternNid, 0) != 0 || bucket.nids().contains(patternNid)) {
                        continue; // carried, or numbered already
                    }
                    PublicId publicId;
                    if (patternNid == patternPatternNid) {
                        publicId = EntityBinding.Pattern.pattern().publicId();
                    } else if (patternNid == EntityBinding.Concept.pattern().nid()) {
                        publicId = EntityBinding.Concept.pattern().publicId();
                    } else if (patternNid == EntityBinding.Stamp.pattern().nid()) {
                        publicId = EntityBinding.Stamp.pattern().publicId();
                    } else {
                        Entity<?> entity = EntityHandle.get(patternNid).orNull();
                        if (entity == null) {
                            danglingBuckets.add(patternNid);
                            continue;
                        }
                        publicId = entity.publicId();
                    }
                    int own = ++sequence;
                    carried.put(patternNid, own);
                    int patternSequence = patternNid == patternPatternNid ? own : carried.get(patternPatternNid);
                    prelude.add(new Referenced(own, patternSequence, publicId));
                }
                if (!prelude.isEmpty()) {
                    preludes.put(i, prelude);
                }
                if (danglingBuckets.contains(bucket.patternNid())) {
                    continue;
                }
                long[] nids = bucket.nids().toArray();
                for (long nid : nids) {
                    if (carried.getIfAbsent(nid, 0) == 0) {
                        carried.put(nid, ++sequence);
                    }
                }
            }
            carriedCount = sequence;
            next = sequence + 1;
        }

        /** The referenced-only patterns the table lists before bucket {@code index}'s members. */
        List<Referenced> prelude(int index) {
            return preludes.getOrDefault(index, List.of());
        }

        int carriedCount() {
            return carriedCount;
        }

        synchronized int referencedCount() {
            return referencedInOrder.size();
        }

        /** Everything the tables number: members, the patterns listed before them, and the late references. */
        synchronized long count() {
            return carriedCount + referencedInOrder.size();
        }

        synchronized List<Referenced> referenced() {
            return new ArrayList<>(referencedInOrder);
        }

        @Override
        public int sequence(long nid) {
            int sequence = carried.getIfAbsent(nid, 0);
            if (sequence != 0) {
                return sequence;
            }
            Integer known = referenced.get(nid);
            return known != null ? known : allocateReferenced(nid);
        }

        private synchronized int allocateReferenced(long nid) {
            Integer known = referenced.get(nid);
            if (known != null) {
                return known;
            }
            // A kind's binding pattern needs no record in the store: its public id and its
            // pattern are the binding's, and an importer mints under it from the id alone.
            PublicId publicId;
            long patternNid;
            if (nid == patternPatternNid) {
                publicId = EntityBinding.Pattern.pattern().publicId();
                patternNid = nid;
            } else if (nid == EntityBinding.Concept.pattern().nid()) {
                publicId = EntityBinding.Concept.pattern().publicId();
                patternNid = patternPatternNid;
            } else if (nid == EntityBinding.Stamp.pattern().nid()) {
                publicId = EntityBinding.Stamp.pattern().publicId();
                patternNid = patternPatternNid;
            } else {
                Entity<?> entity = EntityHandle.get(nid).orNull();
                if (entity != null) {
                    publicId = entity.publicId();
                    patternNid = patternNidOf(entity);
                } else {
                    // A nid minted and never written: a component canceled before its record,
                    // or a reference the store never resolved. The identity map still names
                    // it, and the nid's layout names its pattern when the layout carries one;
                    // the table lists it referenced only, under pattern 0 when none is known,
                    // so the importer mints the same nid and the reference resolves. A nid the
                    // store cannot name is dangling, and the record referring to it is skipped.
                    try {
                        publicId = PrimitiveData.publicId(nid);
                    } catch (RuntimeException e) {
                        throw new IllegalStateException("Dangling reference: no entity and no public id for nid " + nid, e);
                    }
                    patternNid = patternNidWithoutRecord(nid);
                }
            }
            // The pattern before its member, so that patterns precede members in the table too.
            int patternSequence = patternNid == 0 ? 0 : patternNid == nid ? -1 : sequence(patternNid);
            int sequence = next++;
            if (patternSequence == -1) {
                patternSequence = sequence;
            }
            referenced.put(nid, sequence);
            referencedInOrder.add(new Referenced(sequence, patternSequence, publicId));
            return sequence;
        }

        /** The pattern a nid was minted under, from its layout; 0 when the layout carries none. */
        static long patternNidWithoutRecord(long nid) {
            NidLayout layout = NidLayout.active();
            int patternSequence = layout.decodePatternSequence(nid);
            if (patternSequence == 0) {
                return 0;
            }
            return layout.encode(layout.patternPatternSequence(), patternSequence);
        }

        static long patternNidOf(Entity<?> entity) {
            return switch (entity) {
                case PatternEntity<?> p -> EntityBinding.Pattern.pattern().nid();
                case StampEntity<?> st -> EntityBinding.Stamp.pattern().nid();
                case ConceptEntity<?> c -> EntityBinding.Concept.pattern().nid();
                case SemanticEntity<?> sem -> sem.patternNid();
                default -> throw new IllegalStateException("Unexpected entity " + entity.getClass());
            };
        }
    }

    /** The format-3 manifest: the version, the counts, the record entries with their hashes, the modules and authors. */
    static String manifestContent(EntityCountSummary counts, List<WrittenEntry> entries, long componentCount,
                                  Collection<PublicId> moduleList, Collection<PublicId> authorList) {
        StringBuilder manifest = new StringBuilder()
                .append(ChangeSetFormat.VERSION_ATTRIBUTE).append(": ").append(ChangeSetFormat.CURRENT_VERSION).append("\n")
                .append("Packager-Name: ").append(KernelTerm.KOMET_USER.description()).append("\n")
                .append("Package-Date: ").append(LocalDateTime.now(Clock.systemUTC())).append("\n")
                .append("Total-Count: ").append(counts.getTotalCount()).append("\n")
                .append("Concept-Count: ").append(counts.conceptCount()).append("\n")
                .append("Semantic-Count: ").append(counts.semanticCount()).append("\n")
                .append("Pattern-Count: ").append(counts.patternCount()).append("\n")
                .append("Stamp-Count: ").append(counts.stampCount()).append("\n")
                .append(ChangeSetFormat.COMPONENT_COUNT_ATTRIBUTE).append(": ").append(componentCount).append("\n")
                .append(ChangeSetFormat.RECORD_ENTRIES_ATTRIBUTE).append(": ").append(entries.size()).append("\n");
        for (int i = 0; i < entries.size(); i++) {
            WrittenEntry entry = entries.get(i);
            manifest.append(ChangeSetFormat.entryAttribute(i + 1)).append(": ")
                    .append(entry.name()).append(' ').append(entry.count()).append(' ').append(entry.sha256()).append("\n");
        }
        manifest.append(idsToManifestEntry(moduleList))
                .append(idsToManifestEntry(authorList))
                .append("\n"); // Final new line necessary per Manifest spec
        return manifest.toString();
    }

    /**
     * A format-2 manifest, for the incremental change-set writer, which still writes the
     * identity-index layout; the exporter's own manifest is {@link #manifestContent}.
     */
    public static String generateManifestContent(long entityCount,
                                           long conceptsCount,
                                           long semanticsCount,
                                           long patternsCount,
                                           long stampsCount,
                                           Collection<PublicId> moduleList,
                                           Collection<PublicId> authorList){
        StringBuilder manifestContent = new StringBuilder()
                .append(ChangeSetFormat.VERSION_ATTRIBUTE).append(": ").append(ChangeSetFormat.IDENTITY_INDEX_VERSION).append("\n")
                // TODO: Dynamically populate this user
                .append("Packager-Name: ").append(KernelTerm.KOMET_USER.description()).append("\n")
                .append("Package-Date: ").append(LocalDateTime.now(Clock.systemUTC())).append("\n")
                .append("Total-Count: ").append(entityCount).append("\n")
                .append("Concept-Count: ").append(conceptsCount).append("\n")
                .append("Semantic-Count: ").append(semanticsCount).append("\n")
                .append("Pattern-Count: ").append(patternsCount).append("\n")
                .append("Stamp-Count: ").append(stampsCount).append("\n")
                .append(idsToManifestEntry(moduleList))
                .append(idsToManifestEntry(authorList))
                .append("\n"); // Final new line necessary per Manifest spec
        return manifestContent.toString();
    }

    /**
     * The public ids of components given by nid, each once: for a manifest's module and author
     * entries, collected by nid because a public id is never a hash key.
     *
     * @param nids the components' nids
     * @return their public ids, in nid order
     */
    public static List<PublicId> publicIds(Collection<Long> nids) {
        return nids.stream().sorted().map(PrimitiveData::publicId).toList();
    }

    public static String idsToManifestEntry(Collection<PublicId> publicIds) {
        StringBuilder manifestEntry = new StringBuilder();
        publicIds.forEach((publicId) -> {
            // Convert PublicId to Manifest Entry Name
            String idString = publicId.asUuidList().stream()
                    .map(UUID::toString)
                    .collect(Collectors.joining(","));
            // Get Description
            Optional<Entity<? extends EntityVersion>> entity = EntityHandle.get(PrimitiveData.nid(publicId)).entity().filter(e -> !e.canceled());
            String manifestDescription = "Description Undefined";
            if (entity.isPresent()) {
                manifestDescription = entity.get().description();
            }
            // Create Manifest Entry
            manifestEntry.append("\n")
                    .append("Name: ").append(idString).append("\n")
                    .append("Description: ").append(manifestDescription).append("\n");
        });
        return manifestEntry.toString();
    }

    /**
     * Tallies one dangling-reference skip under the entity's own type, so the manifest
     * deduction matches the aggregator's per-type counting exactly
     * (IKE-Network/ike-issues#933).
     *
     * @param entity the entity whose transform failed
     */
    private void tallySkip(Entity<?> entity) {
        synchronized (writeLock) {
            tallySkipLocked(entity);
        }
    }

    private void tallySkipLocked(Entity<?> entity) {
        switch (entity) {
            case StampEntity<?> stamp -> skippedStamps++;
            case dev.ikm.tinkar.entity.ConceptEntity<?> concept -> skippedConcepts++;
            case dev.ikm.tinkar.entity.SemanticEntity<?> semantic -> skippedSemantics++;
            case dev.ikm.tinkar.entity.PatternEntity<?> pattern -> skippedPatterns++;
            default -> skippedSemantics++;
        }
    }

    private void logCounts(EntityCountSummary summary) {
        LOG.info("Exported " + summary.getTotalCount() + " total entities.");
        LOG.info("Exported " + summary.conceptCount() + " concepts.");
        LOG.info("Exported " + summary.semanticCount() + " semantics.");
        LOG.info("Exported " + summary.patternCount() + " patterns.");
        LOG.info("Exported " + summary.stampCount() + " stamps.");
    }
}
