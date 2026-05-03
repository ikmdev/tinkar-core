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

    private static IndexWriter getIndexWriter() throws IOException {
        //Create the indexer
        IndexWriterConfig config = new IndexWriterConfig(analyzer());
        config.setCommitOnClose(true);
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

    /**
     * When true, skip per-document flush in {@link #index(Object)}.
     * Set this during bulk operations like {@link RecreateIndex} where
     * the caller manages commits/flushes in batches.
     */
    private boolean bulkMode = false;

    public void setBulkMode(boolean bulkMode) {
        this.bulkMode = bulkMode;
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
     * (nid, fieldIndex, stripped-text) tuple seen across the semantic's
     * versions.
     *
     * <p>Each emitted document carries three single-valued fields:
     * {@link IndexerSchema#NID}, {@link IndexerSchema#FIELD_INDEX}, and
     * {@link IndexerSchema#TEXT}. Doc-per-position guarantees every hit has
     * unambiguous text/fieldIndex/highlight — the ambiguity that v1's
     * multi-valued fields had at read time is gone by construction.
     *
     * <p>Non-{@link SemanticEntity} arguments are silently ignored; the
     * concept/pattern/stamp paths feed indexing through their description
     * semantics.
     *
     * @param object the entity to index; only {@link SemanticEntity} is acted on
     */
    public void index(Object object) {
        if (!(object instanceof SemanticEntity<?> semanticEntity)) {
            return;
        }

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
                    // Same (text, fieldIndex) pair seen twice is redundant.
                    if (!seenTexts.add(i + "\0" + text)) {
                        continue;
                    }
                    Document doc = new Document();
                    doc.add(IndexerSchema.NID.make(semanticEntity.nid()));
                    doc.add(IndexerSchema.FIELD_INDEX.make(i));
                    doc.add(IndexerSchema.TEXT.make(text));
                    indexWriter.addDocument(doc);
                    docsAdded++;
                    LOG.debug("Indexing semantic nid={} fieldIndex={} text='{}'",
                            semanticEntity.nid(), i, text);
                }
            }
            if (docsAdded > 0 && !bulkMode) {
                // One flush per semantic call publishes all positional docs
                // to NRT readers. Skipped in bulk mode where the caller
                // commits in batches.
                indexWriter.flush();
            }
            LOG.debug("Indexed nid={} docs={}", semanticEntity.nid(), docsAdded);
        } catch (IOException e) {
            LOG.error("Exception writing entity {}", object, e);
        }
    }
}
