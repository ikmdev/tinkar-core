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
package dev.ikm.tinkar.provider.search.maintenance;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.Terms;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Analyze a Lucene index — capture before/after snapshots, optionally run
 * maintenance operations (force-merge deletes, force-merge to N segments),
 * and emit a Markdown report describing what changed.
 *
 * <p>This class is UI-independent and Maven-plugin-friendly. It can be
 * invoked against:
 * <ul>
 *   <li>A {@link Path} alone — opens its own {@link IndexWriter}, runs the
 *       sequence, closes the writer. Suitable for offline analysis or a
 *       Maven mojo against a stopped data store.</li>
 *   <li>A {@link Path} plus an existing {@link IndexWriter} — uses the live
 *       writer in-place. Suitable for a menu command that operates against
 *       the running data store's index.</li>
 * </ul>
 *
 * <p>The {@link Report} returned is an immutable record graph. Call
 * {@link Report#toMarkdown()} to render the human-readable form. The
 * Markdown is identical whether the analyzer ran a full sequence or a
 * dry-run; the operation table will simply note "skipped" for steps that
 * did not run.
 *
 * <p>Recommended sequence (see {@link Options#recommendedSequence()}):
 * <ol>
 *   <li>Capture <em>baseline</em> snapshot.</li>
 *   <li>{@code forceMergeDeletes(true)} — physically purge soft-deleted
 *       documents from segments that contain them. Cheap when there are
 *       few deletes, very effective when there are many.</li>
 *   <li>{@code forceMerge(1, true)} — consolidate into a single segment.
 *       Slowest step; releases the most space when segment count is
 *       large or the index has accumulated stale segments from
 *       per-document flushing.</li>
 *   <li>Capture <em>final</em> snapshot. Diff against baseline.</li>
 * </ol>
 */
public final class LuceneIndexAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(LuceneIndexAnalyzer.class);
    private static final DateTimeFormatter STAMP_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    /**
     * Mapping of common Lucene 9/10 segment-file extensions to a short
     * human-readable purpose. Used in the per-extension byte-breakdown
     * table to make the report self-explanatory.
     */
    private static final Map<String, String> EXTENSION_PURPOSE = Map.ofEntries(
            Map.entry("cfs",        "Compound segment data"),
            Map.entry("cfe",        "Compound segment entries"),
            Map.entry("si",         "Segment metadata"),
            Map.entry("fnm",        "Field metadata"),
            Map.entry("fdt",        "Stored field values (raw text)"),
            Map.entry("fdx",        "Stored field index"),
            Map.entry("fdm",        "Stored field metadata"),
            Map.entry("tim",        "Term dictionary"),
            Map.entry("tip",        "Term index"),
            Map.entry("tmd",        "Term metadata"),
            Map.entry("doc",        "Postings (doc IDs)"),
            Map.entry("pos",        "Postings (positions)"),
            Map.entry("pay",        "Postings (payloads/offsets)"),
            Map.entry("nvd",        "Norms data"),
            Map.entry("nvm",        "Norms metadata"),
            Map.entry("dvd",        "DocValues data"),
            Map.entry("dvm",        "DocValues metadata"),
            Map.entry("tvd",        "Term vectors data"),
            Map.entry("tvx",        "Term vectors index"),
            Map.entry("tvm",        "Term vectors metadata"),
            Map.entry("kdd",        "Points (BKD tree data)"),
            Map.entry("kdi",        "Points (BKD tree index)"),
            Map.entry("kdm",        "Points metadata"),
            Map.entry("liv",        "Live-docs bitmap (soft deletes)"),
            Map.entry("lock",       "Write lock"),
            Map.entry("(commit)",   "Commit point (segments_N)"),
            Map.entry("(other)",    "Unrecognized")
    );

    private LuceneIndexAnalyzer() { /* static helper */ }

    // -----------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------

    /**
     * What the analyzer should do beyond capturing the baseline.
     *
     * <p>Operations are <em>cumulative</em> in the order: {@code forceMergeDeletes},
     * then {@code forceMerge}. The {@code wipeAndRebuild} operation is
     * <em>exclusive</em> — when enabled, the lighter operations are skipped
     * (no point optimizing what's about to be discarded).
     *
     * @param runForceMergeDeletes whether to run {@code forceMergeDeletes(true)}
     * @param runForceMerge        whether to run {@code forceMerge(maxSegments, true)}
     * @param forceMergeMaxSegments target segment count for force-merge (1 = single segment)
     * @param runWipeAndRebuild    whether to wipe the index and rebuild from the entity store
     *                             in parallel; requires an {@link IndexRebuildCallback} to be
     *                             supplied to the {@code analyze} entry point
     * @param topFieldsToReport    cap for the per-field statistics table; the rest are summarized
     */
    public record Options(
            boolean runForceMergeDeletes,
            boolean runForceMerge,
            int forceMergeMaxSegments,
            boolean runWipeAndRebuild,
            int topFieldsToReport) {

        /** Capture-only: no modifications. Safe on a live index. */
        public static Options dryRun() {
            return new Options(false, false, 1, false, 30);
        }

        /** Full recommended sequence: forceMergeDeletes + forceMerge(1). */
        public static Options recommendedSequence() {
            return new Options(true, true, 1, false, 30);
        }

        /** Just purge soft-deleted documents — no full merge. */
        public static Options purgeDeletesOnly() {
            return new Options(true, false, 1, false, 30);
        }

        /**
         * Wipe the index and rebuild from the entity store in parallel.
         * Requires an {@link IndexRebuildCallback} to be supplied to the
         * analyzer; without one, the operation is recorded as skipped.
         */
        public static Options wipeAndRebuild() {
            return new Options(false, false, 1, true, 30);
        }
    }

    /** Progress callback. {@code fraction < 0} means indeterminate. */
    @FunctionalInterface
    public interface ProgressCallback {
        void update(String stage, double fraction);
        ProgressCallback NOOP = (stage, fraction) -> { };
    }

    /**
     * Re-populate the index from the canonical entity store after the
     * analyzer has wiped it. Implementations typically run a parallel
     * walk over all entities (e.g., {@code RecreateIndex} in the search
     * provider) and commit when done.
     *
     * <p>The {@code writer} passed in has been emptied via
     * {@code deleteAll() + commit() + forceMerge(1, true)} — its index
     * is a single empty segment when this callback is invoked. The
     * callback is responsible for committing after rebuilding.
     */
    @FunctionalInterface
    public interface IndexRebuildCallback {
        void rebuild(IndexWriter writer, ProgressCallback progress) throws Exception;
    }

    // -----------------------------------------------------------------
    // Result records
    // -----------------------------------------------------------------

    /** Per-segment stats (one row per segment in the latest commit). */
    public record SegmentStat(String name, int maxDoc, int delCount, long sizeBytes) {
        public int liveDoc() { return maxDoc - delCount; }
        public double deletedRatio() { return maxDoc == 0 ? 0.0 : (double) delCount / maxDoc; }
    }

    /** Per-field stats aggregated across all leaves. */
    public record FieldStat(
            String name,
            long uniqueTerms,
            long docCount,
            long sumDocFreq,
            long sumTotalTermFreq,
            String indexOptions,
            boolean stored,
            boolean hasNorms,
            boolean hasVectors) { }

    /** Bytes-on-disk grouped by Lucene file extension. */
    public record ExtensionBucket(String extension, int fileCount, long bytes, String purpose) {
        public double percentOfTotal(long totalBytes) {
            return totalBytes == 0 ? 0.0 : 100.0 * bytes / totalBytes;
        }
    }

    /** Snapshot of index state at a moment in time. */
    public record Snapshot(
            ZonedDateTime capturedAt,
            long totalBytesOnDisk,
            int fileCount,
            int segmentCount,
            int totalDocs,
            int liveDocs,
            int deletedDocs,
            ImmutableList<SegmentStat> segments,
            ImmutableList<FieldStat> fields,
            ImmutableList<ExtensionBucket> bytesByExtension) {

        public double deletedRatio() {
            return totalDocs == 0 ? 0.0 : (double) deletedDocs / totalDocs;
        }
    }

    /** Outcome of one operation in the sequence. */
    public record OperationResult(
            String name,
            String description,
            boolean executed,
            String skipReason,    // null when executed
            Duration elapsed,
            Snapshot before,
            Snapshot after) {

        public long bytesReclaimed() {
            return executed ? before.totalBytesOnDisk() - after.totalBytesOnDisk() : 0L;
        }

        public int segmentsCollapsed() {
            return executed ? before.segmentCount() - after.segmentCount() : 0;
        }

        public int filesRemoved() {
            return executed ? before.fileCount() - after.fileCount() : 0;
        }
    }

    /** Severity-tagged advisory derived from the snapshots. */
    public record Recommendation(Severity severity, String title, String detail) {
        public enum Severity { INFO, NOTE, WARN, CRITICAL }
    }

    /** Top-level report. {@link #toMarkdown()} renders the human form. */
    public record Report(
            Path indexPath,
            Options options,
            ZonedDateTime startedAt,
            ZonedDateTime completedAt,
            Snapshot baseline,
            ImmutableList<OperationResult> operations,
            Snapshot finalState,
            ImmutableList<Recommendation> recommendations) {

        public Duration totalElapsed() {
            return Duration.between(startedAt, completedAt);
        }

        public long totalBytesReclaimed() {
            return baseline.totalBytesOnDisk() - finalState.totalBytesOnDisk();
        }

        public String toMarkdown() {
            return MarkdownRenderer.render(this);
        }
    }

    // -----------------------------------------------------------------
    // Entry points
    // -----------------------------------------------------------------

    /**
     * Run the analysis against an FSDirectory at {@code indexPath}, opening
     * a private {@link IndexWriter} for the duration. Use this when no
     * writer is currently open (offline / Maven plugin / standalone tool).
     *
     * @param indexPath the {@code lucene/} directory under the data store root
     * @param options   what to capture and what to run
     * @param progress  progress sink (use {@link ProgressCallback#NOOP} to ignore)
     * @return the immutable report
     * @throws IOException on Lucene I/O failure
     */
    public static Report analyze(Path indexPath, Options options, ProgressCallback progress)
            throws IOException {
        return analyze(indexPath, options, null, progress);
    }

    /**
     * Run the analysis against an FSDirectory at {@code indexPath}, opening
     * a private {@link IndexWriter} for the duration. Same as
     * {@link #analyze(Path, Options, ProgressCallback)} but accepts a
     * rebuild callback used when {@link Options#runWipeAndRebuild()} is set.
     *
     * @param indexPath      the {@code lucene/} directory under the data store root
     * @param options        what to capture and what to run
     * @param rebuildCallback re-population callback for wipe-and-rebuild; may be {@code null}
     *                        if {@link Options#runWipeAndRebuild()} is false
     * @param progress       progress sink
     * @return the immutable report
     * @throws IOException on Lucene I/O failure
     */
    public static Report analyze(Path indexPath, Options options,
                                 IndexRebuildCallback rebuildCallback,
                                 ProgressCallback progress) throws IOException {
        Objects.requireNonNull(indexPath, "indexPath");
        Objects.requireNonNull(options, "options");
        ProgressCallback p = progress == null ? ProgressCallback.NOOP : progress;

        try (Directory dir = FSDirectory.open(indexPath);
             IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
            return analyze(indexPath, dir, writer, options, rebuildCallback, p);
        }
    }

    /**
     * Run the analysis against an already-open {@link IndexWriter}. The
     * caller retains ownership of the writer — this method does not close
     * it. Use this when operating against the live data store's index
     * (the running search provider).
     *
     * @param indexPath the directory backing {@code writer}, used only for
     *                  display in the report
     * @param writer    an open index writer; the analyzer will issue
     *                  {@code forceMergeDeletes}, {@code forceMerge}, and
     *                  {@code commit} against it as configured
     * @param options   what to capture and what to run
     * @param progress  progress sink
     * @return the immutable report
     * @throws IOException on Lucene I/O failure
     */
    public static Report analyze(Path indexPath, IndexWriter writer, Options options,
                                 ProgressCallback progress) throws IOException {
        return analyze(indexPath, writer, options, null, progress);
    }

    /**
     * Run the analysis against an already-open {@link IndexWriter} with an
     * optional rebuild callback. Use this overload to enable
     * {@link Options#runWipeAndRebuild()} against a live writer.
     */
    public static Report analyze(Path indexPath, IndexWriter writer, Options options,
                                 IndexRebuildCallback rebuildCallback,
                                 ProgressCallback progress) throws IOException {
        Objects.requireNonNull(writer, "writer");
        ProgressCallback p = progress == null ? ProgressCallback.NOOP : progress;
        return analyze(indexPath, writer.getDirectory(), writer, options, rebuildCallback, p);
    }

    // -----------------------------------------------------------------
    // Core sequence
    // -----------------------------------------------------------------

    private static Report analyze(Path indexPath, Directory dir, IndexWriter writer,
                                  Options options, IndexRebuildCallback rebuildCallback,
                                  ProgressCallback p) throws IOException {
        ZonedDateTime startedAt = ZonedDateTime.now();
        LOG.info("Lucene index analysis starting at {} for {}", startedAt.format(STAMP_TIME), indexPath);

        p.update("Capturing baseline snapshot", -1);
        Snapshot baseline = capture(dir, writer, options.topFieldsToReport());
        LOG.info("Baseline: {} bytes across {} files in {} segments ({} live docs, {} deleted)",
                baseline.totalBytesOnDisk(), baseline.fileCount(), baseline.segmentCount(),
                baseline.liveDocs(), baseline.deletedDocs());

        List<OperationResult> ops = new ArrayList<>();
        Snapshot current = baseline;

        // Wipe-and-rebuild is exclusive — when enabled, the lighter ops are
        // skipped because there's nothing to optimize before discarding.
        if (options.runWipeAndRebuild()) {
            ops.add(skipped("forceMergeDeletes",
                    "Merge segments containing deleted docs to physically purge them.",
                    "Skipped — wipeAndRebuild will replace all segments.", current));
            ops.add(skipped("forceMerge",
                    "Consolidate to a small number of segments.",
                    "Skipped — wipeAndRebuild will replace all segments.", current));

            if (rebuildCallback == null) {
                ops.add(skipped("wipeAndRebuild",
                        "Wipe the index and rebuild from the entity store in parallel.",
                        "No IndexRebuildCallback supplied.", current));
            } else {
                p.update("Wiping index", -1);
                current = runOperation(ops, "wipeAndRebuild",
                        "Wipe the index (deleteAll + commit + forceMerge(1)), then rebuild "
                                + "from the entity store in parallel via the supplied callback.",
                        current, dir, writer, options.topFieldsToReport(),
                        () -> {
                            // Phase 1: wipe. deleteAll + commit + forceMerge(1, true)
                            // collapses surviving structure into a single empty segment so
                            // the rebuild starts from a true clean slate.
                            writer.deleteAll();
                            writer.commit();
                            writer.forceMerge(1, true);
                            writer.commit();
                            p.update("Rebuilding index in parallel from entity store", -1);
                            try {
                                rebuildCallback.rebuild(writer, p);
                            } catch (IOException ioe) {
                                throw ioe;
                            } catch (Exception ex) {
                                throw new IOException("Rebuild callback failed: " + ex.getMessage(), ex);
                            }
                            // Defensive: ensure committed even if callback forgot.
                            writer.commit();
                        });
            }
        } else {
            // Step 1: forceMergeDeletes — purge soft-deleted documents.
            if (options.runForceMergeDeletes()) {
                p.update("forceMergeDeletes — purging soft-deleted documents", -1);
                current = runOperation(ops, "forceMergeDeletes",
                        "Merge segments containing deleted docs to physically purge them.",
                        current, dir, writer, options.topFieldsToReport(),
                        () -> {
                            writer.forceMergeDeletes(true);
                            writer.commit();
                        });
            } else {
                ops.add(skipped("forceMergeDeletes",
                        "Merge segments containing deleted docs to physically purge them.",
                        "Disabled in Options.", current));
            }

            // Step 2: forceMerge — consolidate to N segments.
            if (options.runForceMerge()) {
                int target = Math.max(1, options.forceMergeMaxSegments());
                String name = "forceMerge(" + target + ")";
                p.update(name + " — consolidating segments (this may take a while)", -1);
                current = runOperation(ops, name,
                        "Consolidate to at most " + target + " segment(s). "
                                + "Rewrites all surviving documents into fewer files.",
                        current, dir, writer, options.topFieldsToReport(),
                        () -> {
                            writer.forceMerge(target, true);
                            writer.commit();
                        });
            } else {
                ops.add(skipped("forceMerge",
                        "Consolidate to a small number of segments.",
                        "Disabled in Options.", current));
            }

            // Step 3: placeholder for wipeAndRebuild, recorded as skipped.
            ops.add(skipped("wipeAndRebuild",
                    "Wipe the index and rebuild from the entity store in parallel.",
                    "Disabled in Options.", current));
        }

        Snapshot finalState = current;
        ZonedDateTime completedAt = ZonedDateTime.now();
        ImmutableList<Recommendation> recs = buildRecommendations(baseline, finalState, ops);

        LOG.info("Lucene index analysis complete in {}; {} bytes reclaimed",
                Duration.between(startedAt, completedAt), baseline.totalBytesOnDisk() - finalState.totalBytesOnDisk());

        return new Report(indexPath, options, startedAt, completedAt,
                baseline, Lists.immutable.ofAll(ops), finalState, recs);
    }

    @FunctionalInterface
    private interface IoOp { void run() throws IOException; }

    private static Snapshot runOperation(List<OperationResult> ops, String name, String description,
                                         Snapshot before, Directory dir, IndexWriter writer,
                                         int topFields, IoOp body) throws IOException {
        long t0 = System.nanoTime();
        LOG.info("Running {}", name);
        body.run();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - t0);
        Snapshot after = capture(dir, writer, topFields);
        LOG.info("{} completed in {} — bytes {} → {} ({} reclaimed), segments {} → {}",
                name, elapsed,
                before.totalBytesOnDisk(), after.totalBytesOnDisk(),
                before.totalBytesOnDisk() - after.totalBytesOnDisk(),
                before.segmentCount(), after.segmentCount());
        ops.add(new OperationResult(name, description, true, null, elapsed, before, after));
        return after;
    }

    private static OperationResult skipped(String name, String description, String reason, Snapshot before) {
        return new OperationResult(name, description, false, reason, Duration.ZERO, before, before);
    }

    // -----------------------------------------------------------------
    // Snapshot capture
    // -----------------------------------------------------------------

    private static Snapshot capture(Directory dir, IndexWriter writer, int topFields) throws IOException {
        ZonedDateTime now = ZonedDateTime.now();

        // Per-file sizes and per-extension aggregation.
        String[] files = dir.listAll();
        long totalBytes = 0;
        Map<String, long[]> byExt = new TreeMap<>(); // ext -> {bytes, count}
        for (String f : files) {
            long len;
            try {
                len = dir.fileLength(f);
            } catch (IOException ignore) {
                // File may have been deleted between listAll and fileLength;
                // skip and continue.
                continue;
            }
            totalBytes += len;
            String ext = classifyFile(f);
            long[] cur = byExt.computeIfAbsent(ext, k -> new long[2]);
            cur[0] += len;
            cur[1] += 1;
        }
        List<ExtensionBucket> buckets = new ArrayList<>(byExt.size());
        for (Map.Entry<String, long[]> e : byExt.entrySet()) {
            buckets.add(new ExtensionBucket(
                    e.getKey(),
                    (int) e.getValue()[1],
                    e.getValue()[0],
                    EXTENSION_PURPOSE.getOrDefault(e.getKey(), "")));
        }
        // Sort descending by bytes for the report.
        buckets.sort((a, b) -> Long.compare(b.bytes(), a.bytes()));

        // Segment metadata from the latest commit.
        List<SegmentStat> segments = new ArrayList<>();
        int totalDocs = 0;
        int deletedDocs = 0;
        try {
            SegmentInfos sis = SegmentInfos.readLatestCommit(dir);
            for (SegmentCommitInfo sci : sis) {
                int max = sci.info.maxDoc();
                int del = sci.getDelCount();
                long size;
                try {
                    size = sci.sizeInBytes();
                } catch (IOException ioe) {
                    size = -1;
                }
                segments.add(new SegmentStat(sci.info.name, max, del, size));
                totalDocs += max;
                deletedDocs += del;
            }
        } catch (IOException ioe) {
            LOG.warn("Could not read SegmentInfos: {}", ioe.getMessage());
        }
        // Sort segments by size descending for the report.
        segments.sort((a, b) -> Long.compare(b.sizeBytes(), a.sizeBytes()));
        int liveDocs = totalDocs - deletedDocs;

        // Field-level stats from a fresh near-real-time reader. Open with
        // applyAllDeletes=true so liveDocs counts reflect pending deletes.
        List<FieldStat> fields;
        try (DirectoryReader reader = DirectoryReader.open(writer, true, false)) {
            fields = captureFieldStats(reader, topFields);
        }

        return new Snapshot(
                now, totalBytes, files.length, segments.size(),
                totalDocs, liveDocs, deletedDocs,
                Lists.immutable.ofAll(segments),
                Lists.immutable.ofAll(fields),
                Lists.immutable.ofAll(buckets));
    }

    private static List<FieldStat> captureFieldStats(DirectoryReader reader, int topFields) throws IOException {
        // Aggregate per field across all leaves. Lucene exposes per-leaf
        // term stats; sum them for a meaningful index-wide view.
        Map<String, long[]> agg = new HashMap<>(); // [uniqueTerms, docCount, sumDocFreq, sumTotalTermFreq]
        Map<String, FieldInfo> fieldInfoMap = new HashMap<>();

        for (LeafReaderContext leaf : reader.leaves()) {
            for (FieldInfo fi : leaf.reader().getFieldInfos()) {
                fieldInfoMap.putIfAbsent(fi.name, fi);
                Terms terms = leaf.reader().terms(fi.name);
                if (terms == null) continue;
                long[] cur = agg.computeIfAbsent(fi.name, k -> new long[4]);
                long size = terms.size();
                if (size > 0) cur[0] += size; // size is -1 when unknown
                long dc = terms.getDocCount();
                if (dc > 0) cur[1] += dc;
                long sdf = terms.getSumDocFreq();
                if (sdf > 0) cur[2] += sdf;
                long sttf = terms.getSumTotalTermFreq();
                if (sttf > 0) cur[3] += sttf;
            }
        }

        List<FieldStat> stats = new ArrayList<>(agg.size());
        for (Map.Entry<String, long[]> e : agg.entrySet()) {
            FieldInfo fi = fieldInfoMap.get(e.getKey());
            stats.add(new FieldStat(
                    e.getKey(),
                    e.getValue()[0],
                    e.getValue()[1],
                    e.getValue()[2],
                    e.getValue()[3],
                    fi == null || fi.getIndexOptions() == null ? "?" : fi.getIndexOptions().toString(),
                    fi != null && fi.getIndexOptions() != IndexOptions.NONE,
                    fi != null && fi.hasNorms(),
                    fi != null && fi.hasTermVectors()));
        }
        // Sort by sumDocFreq descending so the most-populated fields surface first.
        stats.sort((a, b) -> Long.compare(b.sumDocFreq(), a.sumDocFreq()));
        if (topFields > 0 && stats.size() > topFields) {
            stats = new ArrayList<>(stats.subList(0, topFields));
        }
        return stats;
    }

    /**
     * Map a Lucene file name to a stable bucket key. Handles the
     * commit-point ({@code segments_N}), the write lock, ordinary
     * extensioned files, and unrecognized files.
     */
    private static String classifyFile(String name) {
        if (name.startsWith("segments_") || name.equals("segments.gen")) return "(commit)";
        if (name.equals("write.lock")) return "lock";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "(other)";
        String ext = name.substring(dot + 1);
        return EXTENSION_PURPOSE.containsKey(ext) ? ext : "(other)";
    }

    // -----------------------------------------------------------------
    // Recommendations
    // -----------------------------------------------------------------

    private static ImmutableList<Recommendation> buildRecommendations(
            Snapshot baseline, Snapshot finalState, List<OperationResult> ops) {

        List<Recommendation> out = new ArrayList<>();

        // High segment count is a leading indicator of per-document flushing.
        // Lucene's TieredMergePolicy is designed around ~10 segments steady
        // state. Anything north of 50 means the merge policy isn't keeping up.
        if (baseline.segmentCount() > 50) {
            out.add(new Recommendation(
                    Recommendation.Severity.WARN,
                    "High segment count at baseline (" + baseline.segmentCount() + ")",
                    "Lucene's default TieredMergePolicy targets ~10 segments steady state. "
                            + "Counts in the hundreds typically indicate per-document flushing — "
                            + "see Indexer#index where indexWriter.flush() runs after every "
                            + "addDocument outside bulk mode. Consider routing imports through "
                            + "bulk mode (Indexer.setBulkMode(true)) and committing in batches."));
        }

        // Substantial dead bytes reclaimed by force-merge with no deletes
        // implies the index was carrying many tiny segments or overlapping
        // postings rather than actual tombstones.
        long reclaimed = baseline.totalBytesOnDisk() - finalState.totalBytesOnDisk();
        double reclaimedFraction = baseline.totalBytesOnDisk() == 0
                ? 0.0
                : (double) reclaimed / baseline.totalBytesOnDisk();
        if (reclaimedFraction >= 0.30) {
            out.add(new Recommendation(
                    Recommendation.Severity.WARN,
                    "Force-merge reclaimed " + percent(reclaimedFraction) + " of index size",
                    "An index that loses 30%+ to a single force-merge is carrying substantial "
                            + "dead overhead — typically tiny segments and term-dictionary "
                            + "duplication across them. Schedule periodic forceMerge as a "
                            + "maintenance task, or fix the root cause (per-document flush, "
                            + "absent delete-on-update)."));
        }

        // Deletion ratio in baseline. The indexer keeps deleteDocumentIfExists
        // commented out by design: semantic chronologies are append-only and
        // historic versions are legitimately retained. A non-zero ratio just
        // means a maintenance step has issued deletes that haven't been
        // physically purged yet.
        if (baseline.deletedRatio() > 0.05) {
            out.add(new Recommendation(
                    Recommendation.Severity.NOTE,
                    "Soft-delete ratio " + percent(baseline.deletedRatio()) + " at baseline",
                    "The deletion bitmap holds " + baseline.deletedDocs() + " of "
                            + baseline.totalDocs() + " documents. forceMergeDeletes will "
                            + "physically remove these in the next merge."));
        } else if (baseline.deletedDocs() == 0 && baseline.totalDocs() > 1_000_000) {
            // Append-only chronologies are by design — but that puts the
            // burden of idempotency on the indexer's *caller*. If imports
            // re-call index() for versions that were already added in a
            // prior import, every round-trip duplicates documents. Lucene
            // can't deduplicate without a delete; the import path must.
            out.add(new Recommendation(
                    Recommendation.Severity.CRITICAL,
                    "No idempotency guard between import and index ("
                            + format(baseline.totalDocs()) + " documents indexed)",
                    "Append-only chronologies are by design — historic versions are kept "
                            + "intentionally. The risk is at the *caller* of Indexer#index: there "
                            + "is currently no check that a (nid, stampNid) version was already "
                            + "added in a prior import. A pull that re-delivers versions already "
                            + "present locally will re-index them, producing duplicate documents "
                            + "with identical content. Recommended fixes, in order: "
                            + "(1) maintain a persistent (nid, stampNid) → indexed-marker so the "
                            + "import path can short-circuit when nothing changed; "
                            + "(2) add a unique-key TextField (e.g. \"versionKey\" = nid+\"|\"+stampNid) "
                            + "and switch addDocument to updateDocument(Term, Document), so a "
                            + "duplicate import at least replaces in place rather than appending; "
                            + "(3) emit indexing only for versions actually written by the "
                            + "EntityService.put path (genuine new versions), not for every "
                            + "version touched by a change-set load."));
        }

        // Documents-per-live-concept ratio: if average is way above 1, there
        // are many duplicate docs per logical entity.
        if (baseline.liveDocs() > 1_000_000 && baseline.segmentCount() > 0) {
            out.add(new Recommendation(
                    Recommendation.Severity.INFO,
                    "Average " + String.format(Locale.US, "%.1f", (double) baseline.liveDocs() / baseline.segmentCount())
                            + " docs/segment at baseline",
                    "Cross-reference this against the expected live-concept count. A ratio "
                            + "much greater than 1.0× the live-concept count indicates per-version "
                            + "indexing rather than per-concept indexing."));
        }

        // Heavy stored-field weight is worth surfacing because it's the
        // largest reclaimable category.
        ExtensionBucket fdt = baseline.bytesByExtension().detect(b -> "fdt".equals(b.extension()));
        if (fdt != null && fdt.percentOfTotal(baseline.totalBytesOnDisk()) > 40.0) {
            out.add(new Recommendation(
                    Recommendation.Severity.NOTE,
                    "Stored fields dominate at " + percent(fdt.percentOfTotal(baseline.totalBytesOnDisk()) / 100.0),
                    ".fdt files (raw stored values) are the largest category. Audit the schema "
                            + "for unnecessary Field.Store.YES — search-only fields don't need to "
                            + "be stored if the value can be retrieved from the entity store via nid."));
        }

        // High file count per segment indicates non-compound segments. Common
        // and not necessarily bad, but worth flagging at extreme ratios.
        if (baseline.segmentCount() > 0) {
            double filesPerSegment = (double) baseline.fileCount() / baseline.segmentCount();
            if (filesPerSegment > 12 && baseline.fileCount() > 200) {
                out.add(new Recommendation(
                        Recommendation.Severity.INFO,
                        "High file count: " + format(baseline.fileCount()) + " files for "
                                + baseline.segmentCount() + " segment(s)",
                        "Non-compound segments produce many small files per segment. This is "
                                + "expected for large segments but inflates inode/handle pressure. "
                                + "useCompoundFile=true on small segments mitigates this."));
            }
        }

        if (out.isEmpty()) {
            out.add(new Recommendation(
                    Recommendation.Severity.INFO,
                    "No anomalies detected",
                    "Index shape matches expectations for a healthy Lucene index."));
        }

        return Lists.immutable.ofAll(out);
    }

    // -----------------------------------------------------------------
    // Markdown rendering
    // -----------------------------------------------------------------

    /** Markdown emitter. Kept private — render via {@link Report#toMarkdown()}. */
    private static final class MarkdownRenderer {

        static String render(Report r) {
            StringBuilder sb = new StringBuilder(8 * 1024);
            sb.append("# Lucene Index Maintenance\n\n");

            sb.append("- **Index:** `").append(r.indexPath()).append("`\n");
            sb.append("- **Started:** ").append(r.startedAt().format(STAMP_TIME)).append('\n');
            sb.append("- **Completed:** ").append(r.completedAt().format(STAMP_TIME)).append('\n');
            sb.append("- **Total elapsed:** ").append(formatDuration(r.totalElapsed())).append('\n');
            sb.append("- **Options:** forceMergeDeletes=").append(r.options().runForceMergeDeletes())
                    .append(", forceMerge=").append(r.options().runForceMerge())
                    .append(", maxSegments=").append(r.options().forceMergeMaxSegments())
                    .append(", wipeAndRebuild=").append(r.options().runWipeAndRebuild()).append("\n\n");

            appendSummarySection(sb, r);
            appendRecommendationsSection(sb, r);
            appendOperationsSection(sb, r);
            appendSnapshotSection(sb, "Baseline snapshot", r.baseline());
            appendSnapshotSection(sb, "Final snapshot", r.finalState());

            return sb.toString();
        }

        private static void appendSummarySection(StringBuilder sb, Report r) {
            Snapshot a = r.baseline();
            Snapshot b = r.finalState();
            sb.append("## Summary\n\n");
            sb.append("| Metric | Baseline | Final | Change |\n");
            sb.append("|---|---:|---:|---:|\n");
            row(sb, "Total size on disk",
                    formatBytes(a.totalBytesOnDisk()), formatBytes(b.totalBytesOnDisk()),
                    deltaBytes(a.totalBytesOnDisk(), b.totalBytesOnDisk()));
            row(sb, "File count",
                    format(a.fileCount()), format(b.fileCount()),
                    deltaInt(a.fileCount(), b.fileCount()));
            row(sb, "Segment count",
                    format(a.segmentCount()), format(b.segmentCount()),
                    deltaInt(a.segmentCount(), b.segmentCount()));
            row(sb, "Total documents",
                    format(a.totalDocs()), format(b.totalDocs()),
                    deltaInt(a.totalDocs(), b.totalDocs()));
            row(sb, "Live documents",
                    format(a.liveDocs()), format(b.liveDocs()),
                    deltaInt(a.liveDocs(), b.liveDocs()));
            row(sb, "Deleted documents",
                    format(a.deletedDocs()), format(b.deletedDocs()),
                    deltaInt(a.deletedDocs(), b.deletedDocs()));
            row(sb, "Deleted ratio",
                    percent(a.deletedRatio()), percent(b.deletedRatio()), "");
            sb.append('\n');
        }

        private static void appendRecommendationsSection(StringBuilder sb, Report r) {
            sb.append("## Recommendations\n\n");
            for (Recommendation rec : r.recommendations()) {
                String tag = switch (rec.severity()) {
                    case CRITICAL -> "**[CRITICAL]**";
                    case WARN     -> "**[WARN]**";
                    case NOTE     -> "**[NOTE]**";
                    case INFO     -> "**[INFO]**";
                };
                sb.append("- ").append(tag).append(' ').append(escape(rec.title())).append("  \n  ")
                        .append(escape(rec.detail())).append('\n');
            }
            sb.append('\n');
        }

        private static void appendOperationsSection(StringBuilder sb, Report r) {
            sb.append("## Operations\n\n");
            sb.append("| Step | Status | Elapsed | Bytes reclaimed | Segments collapsed | Files removed |\n");
            sb.append("|---|---|---:|---:|---:|---:|\n");
            int i = 1;
            for (OperationResult op : r.operations()) {
                String status = op.executed() ? "ran" : "skipped (" + op.skipReason() + ")";
                sb.append("| ").append(i++).append(". ").append(escape(op.name())).append(" | ")
                        .append(escape(status)).append(" | ")
                        .append(op.executed() ? formatDuration(op.elapsed()) : "—").append(" | ")
                        .append(op.executed() ? formatBytes(op.bytesReclaimed()) : "—").append(" | ")
                        .append(op.executed() ? format(op.segmentsCollapsed()) : "—").append(" | ")
                        .append(op.executed() ? format(op.filesRemoved()) : "—").append(" |\n");
            }
            sb.append('\n');

            // Per-step descriptions below the table for context.
            for (OperationResult op : r.operations()) {
                sb.append("**").append(escape(op.name())).append("** — ").append(escape(op.description()));
                if (!op.executed()) {
                    sb.append(" *(not run: ").append(escape(op.skipReason())).append(")*");
                }
                sb.append('\n');
            }
            sb.append('\n');
        }

        private static void appendSnapshotSection(StringBuilder sb, String title, Snapshot s) {
            sb.append("## ").append(title).append("\n\n");
            sb.append("- Captured: ").append(s.capturedAt().format(STAMP_TIME)).append('\n');
            sb.append("- Total bytes: ").append(formatBytes(s.totalBytesOnDisk()))
                    .append(" (").append(format(s.totalBytesOnDisk())).append(" bytes)\n");
            sb.append("- Files: ").append(format(s.fileCount())).append('\n');
            sb.append("- Segments: ").append(format(s.segmentCount())).append('\n');
            sb.append("- Documents: ").append(format(s.totalDocs()))
                    .append(" total, ").append(format(s.liveDocs())).append(" live, ")
                    .append(format(s.deletedDocs())).append(" deleted\n\n");

            // Bytes by extension.
            sb.append("### Bytes by file extension\n\n");
            sb.append("| Ext | Files | Bytes | % | Purpose |\n");
            sb.append("|---|---:|---:|---:|---|\n");
            for (ExtensionBucket b : s.bytesByExtension()) {
                sb.append("| `").append(b.extension()).append("` | ")
                        .append(format(b.fileCount())).append(" | ")
                        .append(formatBytes(b.bytes())).append(" | ")
                        .append(percent(b.percentOfTotal(s.totalBytesOnDisk()) / 100.0)).append(" | ")
                        .append(escape(b.purpose())).append(" |\n");
            }
            sb.append('\n');

            // Segments — cap at first 20 if there are many.
            sb.append("### Segments");
            int shown = Math.min(20, s.segments().size());
            if (s.segments().size() > shown) {
                sb.append(" (top ").append(shown).append(" of ").append(s.segments().size()).append(" by size)");
            }
            sb.append("\n\n");
            sb.append("| Segment | Max docs | Deleted | Live | Size |\n");
            sb.append("|---|---:|---:|---:|---:|\n");
            for (int i = 0; i < shown; i++) {
                SegmentStat seg = s.segments().get(i);
                sb.append("| `").append(seg.name()).append("` | ")
                        .append(format(seg.maxDoc())).append(" | ")
                        .append(format(seg.delCount())).append(" | ")
                        .append(format(seg.liveDoc())).append(" | ")
                        .append(seg.sizeBytes() < 0 ? "?" : formatBytes(seg.sizeBytes())).append(" |\n");
            }
            sb.append('\n');

            // Top fields.
            sb.append("### Top fields (by sum doc freq)\n\n");
            sb.append("| Field | Unique terms | Doc count | Sum doc freq | Sum total term freq | Index opts | Norms | Vectors |\n");
            sb.append("|---|---:|---:|---:|---:|---|---|---|\n");
            for (FieldStat f : s.fields()) {
                sb.append("| `").append(f.name()).append("` | ")
                        .append(format(f.uniqueTerms())).append(" | ")
                        .append(format(f.docCount())).append(" | ")
                        .append(format(f.sumDocFreq())).append(" | ")
                        .append(format(f.sumTotalTermFreq())).append(" | ")
                        .append(escape(f.indexOptions())).append(" | ")
                        .append(f.hasNorms() ? "yes" : "no").append(" | ")
                        .append(f.hasVectors() ? "yes" : "no").append(" |\n");
            }
            sb.append('\n');
        }

        private static void row(StringBuilder sb, String label, String a, String b, String delta) {
            sb.append("| ").append(escape(label)).append(" | ")
                    .append(a).append(" | ")
                    .append(b).append(" | ")
                    .append(delta).append(" |\n");
        }

        private static String deltaBytes(long a, long b) {
            long d = b - a;
            String sign = d > 0 ? "+" : "";
            String pct = a == 0 ? "" : String.format(Locale.US, " (%s%.1f%%)", sign, 100.0 * d / a);
            return sign + formatBytes(d) + pct;
        }

        private static String deltaInt(long a, long b) {
            long d = b - a;
            String sign = d > 0 ? "+" : "";
            return sign + format(d);
        }

        private static String escape(String s) {
            if (s == null) return "";
            return s.replace("|", "\\|");
        }
    }

    // -----------------------------------------------------------------
    // Formatting helpers
    // -----------------------------------------------------------------

    private static String format(long n) {
        return String.format(Locale.US, "%,d", n);
    }

    private static String percent(double fraction) {
        return String.format(Locale.US, "%.1f%%", fraction * 100.0);
    }

    private static String formatBytes(long bytes) {
        if (bytes == Long.MIN_VALUE) return "?";
        long abs = Math.abs(bytes);
        if (abs < 1024) return bytes + " B";
        String[] units = {"KiB", "MiB", "GiB", "TiB", "PiB"};
        double v = abs;
        int u = -1;
        do {
            v /= 1024.0;
            u++;
        } while (v >= 1024.0 && u < units.length - 1);
        String sign = bytes < 0 ? "-" : "";
        return String.format(Locale.US, "%s%.2f %s", sign, v, units[u]);
    }

    private static String formatDuration(Duration d) {
        if (d == null || d.isZero()) return "0s";
        long s = d.getSeconds();
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        long ms = d.toMillisPart();
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (h > 0 || m > 0) sb.append(m).append("m ");
        sb.append(sec);
        if (h == 0 && m == 0 && sec < 10 && ms != 0) {
            sb.append('.').append(String.format(Locale.US, "%03d", ms));
        }
        sb.append('s');
        return sb.toString();
    }
}
