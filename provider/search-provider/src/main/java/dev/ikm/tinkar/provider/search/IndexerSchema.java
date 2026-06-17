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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.store.Directory;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Typed descriptors for the Lucene document shape used by {@link Indexer} and
 * {@link Searcher}, plus the on-disk schema-version mechanism that lets
 * {@code SearchProvider} detect a stale index and trigger a recreate.
 *
 * <p>Each public {@link Descriptor} statically captures (a) the Lucene field
 * name and (b) how to read a typed value back from a {@link Document}. Field
 * lookups go through the descriptor instead of bare string constants so the
 * compiler catches type mismatches and renames.
 *
 * <p>The schema version is a single {@code int} written into Lucene's commit
 * user data on every commit. It is bumped whenever the document shape changes
 * in a way that makes pre-existing indexes mis-rank or mis-decode. {@link
 * #VERSION} is the value written by this build; {@link #readVersion(Directory)}
 * returns whatever was on disk so callers can compare and recreate as needed.
 *
 * <p>This class is the read-side / metadata anchor introduced as A0 in
 * {@code Indexer-evaluation.md}. The write-side migration (Indexer.index using
 * descriptors, IntField replacing StoredField+IntPoint pairs, doc-per-position)
 * is A.
 */
public final class IndexerSchema {

    /**
     * Current schema version written by this build. Bump on any breaking
     * change to document shape (field names, field types, doc granularity,
     * stored vs. indexed-only).
     *
     * <p>v4 (this build): the per-position int field is renamed on the wire
     * from {@code "fieldIndex"} to {@code "fieldOrdinal"} for clarity —
     * "ordinal" is the canonical term for "position in an ordered sequence"
     * and disambiguates from the unrelated MVVM property key
     * {@code FormViewModel.FIELD_INDEX}. Document shape is otherwise identical
     * to v3.
     *
     * <p>v3 (deprecated): {@code text} indexed-only ({@link Field.Store#NO});
     * {@link Searcher} rehydrates text per hit via {@link UnifiedHighlighter}
     * re-analysis. Stored fields {@code nid} and {@code fieldIndex}.
     *
     * <p>v2 (deprecated): doc-per-(nid, fieldIndex) with single-valued
     * {@code nid}, {@code fieldIndex} ({@link IntField}), and {@code text}
     * stored as {@link TextField} with {@link Field.Store#YES}.
     *
     * <p>v1 (deprecated): one document per semantic with multi-valued
     * {@code text}/{@code fieldIndex}, plus dead {@code rcNid},
     * {@code patternNid}, and {@code nidPoint} fields.
     *
     * <p>Indexes at v0 (legacy, no version key), v1, v2, or v3 are
     * auto-recreated against v4 on next startup via the trigger in
     * {@code SearchProvider}.
     */
    public static final int VERSION = 4;

    /** Lucene commit-user-data key under which {@link #VERSION} is stored. */
    public static final String VERSION_KEY = "ike.indexer.schemaVersion";

    /**
     * Sentinel returned by {@link #readVersion(Directory)} when an existing
     * index has no {@link #VERSION_KEY} entry — i.e., it was written before
     * the schema-version mechanism existed (pre-A0). Pre-feature indexes
     * carry latent shape bugs (the §3.1 fieldIndex reuse bug, ambiguous
     * multi-valued read-back) and cannot be trusted to mean what their fields
     * say they mean, so they read back as {@code 0} to force a recreate
     * against the current {@link #VERSION}.
     */
    private static final int LEGACY_INDEX_VERSION = 0;

    /** Indexed (BKD point + doc-values) and stored {@code int} nid of the source semantic. */
    public static final IntDescriptor NID = new IntDescriptor("nid");

    /** Indexed (BKD point + doc-values) and stored {@code int} ordinal — the position
     *  of the matched value within the semantic's {@code fieldValues()} list.
     *
     *  <p>Wire-format field name {@code "fieldOrdinal"} (renamed from v3's
     *  {@code "fieldIndex"} for clarity — see schema notes on {@link #VERSION}).
     *  The Java constant {@code INDEXED_FIELD_ORDINAL} disambiguates from
     *  {@code FormViewModel.FIELD_INDEX}, the unrelated MVVM property key in
     *  {@code komet/kview}. */
    public static final IntDescriptor INDEXED_FIELD_ORDINAL = new IntDescriptor("fieldOrdinal");

    /** Analyzed full-text content for the (nid, fieldIndex) tuple. v3 indexes
     *  but does not store the text — rehydrated from the entity binary store
     *  at search time via {@link UnifiedHighlighter}'s re-analysis offset
     *  source. {@link TextDescriptor#read(Document)} returns {@code null}
     *  on v3 documents; only the field name is consulted on the read side. */
    public static final TextDescriptor TEXT = new TextDescriptor("text");

    /**
     * The set of field names that {@link Searcher#search(String, int)} must
     * load when materializing a hit. Used as the {@code fieldsToLoad} argument
     * to {@code IndexSearcher.storedFields().document(int, Set)}.
     *
     * <p>v3: {@code text} is no longer stored, so it's not in this set. Hit
     * decoding pulls only {@code nid} and {@code fieldIndex} from the index;
     * the text comes from the entity binary store via the
     * {@link UnifiedHighlighter} loader override.
     */
    public static final Set<String> FIELDS_TO_LOAD = Set.of(
            NID.name(),
            INDEXED_FIELD_ORDINAL.name()
    );

    private IndexerSchema() {
    }

    /**
     * Attach {@link #VERSION} to the writer's live commit data so every
     * subsequent commit persists the current schema version.
     *
     * <p>{@link IndexWriter#setLiveCommitData} replaces (does not merge) any
     * previously set commit data. We call this exactly once per writer
     * lifetime, immediately after construction; nothing else in this codebase
     * writes commit user data, so the replace semantics are safe.
     *
     * @param writer the {@link IndexWriter} to tag with the current schema version
     */
    public static void attachVersion(IndexWriter writer) {
        writer.setLiveCommitData(
                Map.of(VERSION_KEY, Integer.toString(VERSION)).entrySet()
        );
    }

    /**
     * Read the schema version persisted in the index's commit user data.
     *
     * <p>Three on-disk states map to return values:
     * <ul>
     *   <li><b>Index exists with {@link #VERSION_KEY}</b> — return the stored int.</li>
     *   <li><b>Index exists but has no {@link #VERSION_KEY}</b> — return
     *       {@value #LEGACY_INDEX_VERSION}. Pre-feature indexes fall here and
     *       must recreate ({@code 0 < VERSION}).</li>
     *   <li><b>No index on disk</b> — return {@link #VERSION}. The "no index"
     *       case is handled by {@code SearchProvider}'s existing
     *       data-exists / index-missing trigger; returning the current version
     *       here keeps the schema-version comparison from also firing.</li>
     * </ul>
     *
     * <p>Real I/O errors (corrupt segments, permission denied) propagate as
     * {@link IOException}.
     *
     * @param directory the Lucene {@link Directory} to inspect
     * @return the on-disk schema version, with the sentinels described above
     * @throws IOException if Lucene cannot read the directory
     */
    public static int readVersion(Directory directory) throws IOException {
        if (!DirectoryReader.indexExists(directory)) {
            return VERSION;
        }
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            String stored = reader.getIndexCommit().getUserData().get(VERSION_KEY);
            return stored == null ? LEGACY_INDEX_VERSION : Integer.parseInt(stored);
        }
    }

    /**
     * Sealed contract for a typed Lucene document field. Permits exactly the
     * descriptor types this schema supports, so call sites get exhaustive
     * pattern matching and the compiler catches new shapes that haven't been
     * handled.
     *
     * @param <T> the Java type returned by {@link #read(Document)}
     */
    public sealed interface Descriptor<T> permits IntDescriptor, TextDescriptor {
        /**
         * @return the Lucene field name this descriptor reads and writes
         */
        String name();

        /**
         * Build a fresh Lucene field carrying the given typed value, suitable
         * for {@code Document.add(...)}. A new instance must be returned on
         * every call — Lucene fields hold their value by reference, and
         * sharing instances across documents (or across positions in the same
         * document) is the §3.1 bug A retires.
         *
         * @param value the typed value to store and index
         * @return a fresh {@link IndexableField} for this descriptor's shape
         */
        IndexableField make(T value);

        /**
         * Read this descriptor's value from a hit document.
         *
         * @param doc the document returned by an {@code IndexSearcher}
         * @return the typed value, or {@code null} if the field is absent
         */
        T read(Document doc);
    }

    /**
     * Descriptor for an {@link IntField}: indexed as a BKD point, exposed as
     * doc-values, and stored. Reads via {@link IndexableField#numericValue()}.
     */
    public record IntDescriptor(String name) implements Descriptor<Integer> {
        /**
         * @param value the int value (auto-unboxed)
         * @return a fresh {@link IntField} with {@link Field.Store#YES}
         */
        @Override
        public IndexableField make(Integer value) {
            return new IntField(name, value, Field.Store.YES);
        }

        /**
         * @param doc the hit document
         * @return the stored int as an {@link Integer}, or {@code null} if absent
         */
        @Override
        public Integer read(Document doc) {
            IndexableField field = doc.getField(name);
            return field == null ? null : field.numericValue().intValue();
        }
    }

    /**
     * Descriptor for an analyzed-but-not-stored {@link TextField}: the term
     * stream is indexed for inverted-index lookup, but the text itself is
     * not retained in the segment. Read access exists only because the
     * {@link Descriptor} interface requires it; v3 callers should not use
     * it — text comes from the entity binary store via re-analysis.
     */
    public record TextDescriptor(String name) implements Descriptor<String> {
        /**
         * @param value the text to analyze and index
         * @return a fresh {@link TextField} with {@link Field.Store#NO}
         */
        @Override
        public IndexableField make(String value) {
            return new TextField(name, value, Field.Store.NO);
        }

        /**
         * @param doc the hit document
         * @return {@code null} on v3 documents (field is not stored); legacy
         *         v2 documents would return the stored string here
         */
        @Override
        public String read(Document doc) {
            return doc.get(name);
        }
    }
}
