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
package dev.ikm.tinkar.provider.search;

import dev.ikm.tinkar.common.util.time.Stopwatch;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.eclipse.collections.api.list.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class Indexer {
    private static final Logger LOG = LoggerFactory.getLogger(Indexer.class);
    private static final File defaultDataDirectory = new File("target/lucene/");
    private static DirectoryReader indexReader;
    private static Directory indexDirectory;
    private static Analyzer analyzer;
    private static IndexWriter indexWriter;
    private final Path indexPath;

    public Indexer() throws IOException {
        Indexer.indexDirectory = new ByteBuffersDirectory();
        Indexer.analyzer = new StandardAnalyzer();
        Indexer.indexWriter = Indexer.getIndexWriter();
        IndexerSchema.attachVersion(Indexer.indexWriter);
        Indexer.indexReader = DirectoryReader.open(Indexer.indexWriter, true, false);
        this.indexPath = null;
    }

    /** Private constructor used by {@link #wrapActiveState()}. Does not touch static state. */
    private Indexer(Path indexPath, boolean wrapActiveMarker) {
        this.indexPath = indexPath;
    }

    /**
     * Return an {@link Indexer} instance that points at the currently-active
     * static state ({@code indexWriter}, {@code indexDirectory}, {@code analyzer},
     * {@code indexReader}). Use this when a maintenance utility needs an
     * Indexer handle (for {@link #setBulkMode(boolean)} or a {@code RecreateIndex})
     * but must not reinitialize the singleton.
     *
     * <p>The returned instance shares the static state with whatever Indexer
     * originally opened the directory. Calling {@link #close()} on the
     * returned instance closes the underlying writer just as if you'd called
     * it on the original — same singleton, same effect.
     *
     * @return a transient Indexer wrapping the active state
     * @throws IllegalStateException if the static state is not initialized
     *                               (no Lucene index is currently open)
     */
    public static Indexer wrapActiveState() {
        if (Indexer.indexWriter == null || Indexer.indexDirectory == null) {
            throw new IllegalStateException(
                    "No active Lucene index — Indexer.indexWriter() is null. "
                            + "Open a data store first.");
        }
        return new Indexer((Path) null, true);
    }

    /**
     * Lucene RAM buffer size in MB. The writer auto-flushes a segment to disk
     * when buffered pending docs hit this size. Default is 256 MB — meaningfully
     * larger than Lucene's stock 16 MB default to reduce segment churn during
     * full rebuilds (see {@link RecreateIndex}). A v4 doc is ~250 bytes including
     * overhead, so 256 MB carries roughly 1.5M docs per flush — a typical
     * rebuild emits 6-12 segments instead of dozens to a hundred at the stock
     * default, and {@code TieredMergePolicy} has correspondingly less work.
     *
     * <p>Live writes (post-rebuild) fill this buffer slowly and pay no extra
     * cost from the larger size — RAM is grown into, not pre-allocated.
     *
     * <p>Override via system property for memory-constrained deployments.
     */
    private static final double RAM_BUFFER_SIZE_MB =
            Double.parseDouble(System.getProperty("lucene.index.ram.buffer.mb", "256"));

    private static IndexWriter getIndexWriter() throws IOException {
        //Create the indexer
        IndexWriterConfig config = new IndexWriterConfig(analyzer());
        config.setCommitOnClose(true);
        config.setRAMBufferSizeMB(RAM_BUFFER_SIZE_MB);
        return new IndexWriter(indexDirectory(), config);
    }

    public static Analyzer analyzer() {
        return analyzer;
    }
    public static IndexWriter indexWriter() {
        return indexWriter;
    }
    public static Directory indexDirectory() {
        return indexDirectory;
    }
    public static DirectoryReader indexReader() {
        return indexReader;
    }

    public Indexer(Path indexPath) throws IOException {
        Stopwatch stopwatch = new Stopwatch();
        LOG.info("Opening lucene indexer");
        this.indexPath = indexPath;
        Indexer.indexDirectory = FSDirectory.open(this.indexPath);
        Indexer.analyzer = new StandardAnalyzer();
        Indexer.indexWriter = Indexer.getIndexWriter();
        IndexerSchema.attachVersion(Indexer.indexWriter);
        Indexer.indexReader = DirectoryReader.open(Indexer.indexWriter);
        stopwatch.stop();
        LOG.info("Opened lucene index in: " + stopwatch.durationString());
    }

    public void commit() throws IOException {
        Stopwatch stopwatch = new Stopwatch();
        LOG.info("Committing lucene index");
        indexWriter.commit();
        stopwatch.stop();
        LOG.info("Committed lucene index in: {}", stopwatch.durationString());
    }

    public void close() throws IOException {
        Stopwatch stopwatch = new Stopwatch();
        LOG.info("Closing lucene index");
        if (Indexer.indexReader != null) {
            Indexer.indexReader.close();
            Indexer.indexReader = null;
        }
        if (Indexer.indexWriter != null) {
            Indexer.indexWriter.close();
            Indexer.indexWriter = null;
        }
        if (Indexer.indexDirectory != null) {
            Indexer.indexDirectory.close();
            Indexer.indexDirectory = null;
        }
        Indexer.analyzer = null;
        stopwatch.stop();
        LOG.info("Closed lucene index in: " + stopwatch.durationString());
    }

    /**
     * Index a semantic by emitting one Lucene document per distinct
     * {@code (nid, fieldOrdinal, stripped-text)} tuple seen across the semantic's
     * versions, replacing any prior docs for this nid.
     *
     * <p>Every call buffers a {@code deleteDocuments} for the semantic's nid
     * before adding new docs. This makes the operation idempotent at the index
     * level: re-indexing the same content leaves the same Lucene state, and
     * indexing a new version of an evolving semantic doesn't accumulate
     * superseded docs from earlier versions. Suitable for live writes where
     * a doc for the nid may already exist.
     *
     * <p><b>Do not call from a full-rebuild loop.</b> RecreateIndex starts with
     * {@code IndexWriter.deleteAll()} and indexes ~tens of millions of entities
     * against an initially-empty writer; using {@link #index} would buffer
     * tens of millions of no-match {@code PointRangeQuery} delete entries in
     * Lucene's BufferedUpdates queue. The queue grows between flushes and
     * resolution at flush time scales with both queue length and segment count,
     * producing O(n²) work that can multiply rebuild time by orders of
     * magnitude. Use {@link #indexFresh(SemanticEntity)} for the recreate path.
     *
     * <p>Each emitted document carries three single-valued fields:
     * {@link IndexerSchema#NID}, {@link IndexerSchema#INDEXED_FIELD_ORDINAL},
     * and {@link IndexerSchema#TEXT}. Doc-per-position guarantees every hit has
     * unambiguous text/ordinal/highlight — the ambiguity that v1's
     * multi-valued fields had at read time is gone by construction.
     *
     * <p>Concept, pattern, and stamp entities are filtered out one level up
     * (in {@code SearchProvider.index} and {@code RecreateIndex.compute}) —
     * concept names live in description semantics and reach this method via
     * their semantics, not directly.
     *
     * <p>Many semantics carry no indexable text (navigation, identifier,
     * membership, image, numeric-only, etc. — anything whose
     * {@code fieldValues()} contains no {@code String}); for those this method
     * still buffers the delete-by-NID (a no-op when no prior docs exist) and
     * returns {@code 0}.
     *
     * @param semanticEntity the semantic to index; must not be {@code null}
     * @return the number of Lucene documents added to the writer; {@code 0}
     *         when the semantic has no indexable text or on I/O error
     */
    public int index(SemanticEntity<?> semanticEntity) {
        try {
            // Replace any prior docs for this nid. Cheap as a single live-write
            // operation; pathological in a tight loop — see indexFresh().
            indexWriter.deleteDocuments(
                    IntField.newExactQuery(IndexerSchema.NID.name(), semanticEntity.nid()));
        } catch (IOException e) {
            LOG.error("Exception buffering delete-by-nid for entity {}", semanticEntity, e);
            return 0;
        }
        return indexInternal(semanticEntity);
    }

    /**
     * Index a semantic without buffering a delete-by-NID first. Intended for
     * the {@link RecreateIndex} hot path, where the writer was just
     * {@code deleteAll()}'d and there are no prior docs for any nid; the delete
     * would resolve to a no-match query and contribute only buffer-and-resolve
     * overhead. Live writes should use {@link #index(SemanticEntity)} instead;
     * skipping the delete there would let stale per-nid docs accumulate from
     * earlier indexings.
     *
     * <p>Same return semantics and same per-doc shape as {@link #index} —
     * the only difference is the absence of the delete buffering.
     *
     * @param semanticEntity the semantic to index; must not be {@code null}
     * @return the number of Lucene documents added to the writer; {@code 0}
     *         when the semantic has no indexable text or on I/O error
     */
    public int indexFresh(SemanticEntity<?> semanticEntity) {
        return indexInternal(semanticEntity);
    }

    /**
     * Shared add-doc loop. Both {@link #index} and {@link #indexFresh} delegate
     * here; the only difference between them is whether they buffer a
     * delete-by-NID before calling this method.
     */
    private int indexInternal(SemanticEntity<?> semanticEntity) {
        Set<String> seenTexts = new HashSet<>();
        int docsAdded = 0;
        try {
            for (SemanticEntityVersion version : ((SemanticEntity<SemanticEntityVersion>) semanticEntity).versions()) {
                ImmutableList<Object> fields = version.fieldValues();
                for (int i = 0; i < fields.size(); i++) {
                    if (!(fields.get(i) instanceof String rawText)) {
                        continue;
                    }
                    String text = rawText.strip();
                    // Dedup uniformly across all positions and versions.
                    // Same (text, fieldOrdinal) pair seen twice is redundant.
                    if (!seenTexts.add(i + "\0" + text)) {
                        continue;
                    }
                    Document doc = new Document();
                    doc.add(IndexerSchema.NID.make(semanticEntity.nid()));
                    doc.add(IndexerSchema.INDEXED_FIELD_ORDINAL.make(i));
                    doc.add(IndexerSchema.TEXT.make(text));
                    indexWriter.addDocument(doc);
                    docsAdded++;
                    LOG.debug("Indexing semantic nid={} fieldOrdinal={} text='{}'",
                            semanticEntity.nid(), i, text);
                }
            }
        } catch (IOException e) {
            LOG.error("Exception writing entity {}", semanticEntity, e);
        }
        return docsAdded;
    }
}
