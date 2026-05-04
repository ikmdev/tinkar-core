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

import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.eclipse.collections.api.list.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@link UnifiedHighlighter} that pulls source text from the entity binary
 * store instead of from stored Lucene fields. The v4 index does not store the
 * {@code text} field — only the {@code nid} and {@code fieldOrdinal} ints —
 * so the highlighter has to look up the text per hit before it can compute
 * passages.
 *
 * <p>Per hit the loader:
 * <ol>
 *   <li>Reads {@code nid} and {@code fieldOrdinal} from the doc's stored fields.</li>
 *   <li>Fetches the corresponding {@link SemanticEntity} via
 *       {@link EntityService#getEntityFast(int)}.</li>
 *   <li>Takes the latest version's {@code fieldValues().get(fieldOrdinal)} and
 *       returns it as the source text.</li>
 * </ol>
 *
 * <p>"Latest version" here is the last entry in {@link SemanticEntity#versions()}
 * — chronologically last, no view-coordinate filtering. If that version's
 * text no longer contains the query terms (the wording changed in a newer
 * version that the older index entry was matched against), the highlighter
 * returns the text without {@code <B>} markers — the user sees plain text
 * for a hit whose actual match was on stale wording. This is more honest
 * than v2's behavior of marking up stored stale text.
 *
 * <p>The offset source defaults to {@code ANALYSIS} automatically because
 * the v4 {@code text} field has neither stored offsets in postings nor term
 * vectors. The analyzer re-tokenizes the rehydrated text per hit.
 */
final class EntityStoreBackedHighlighter extends UnifiedHighlighter {
    private static final Logger LOG = LoggerFactory.getLogger(EntityStoreBackedHighlighter.class);

    private static final Set<String> STORED_FIELDS_FOR_REHYDRATION = Set.of(
            IndexerSchema.NID.name(),
            IndexerSchema.INDEXED_FIELD_ORDINAL.name()
    );

    EntityStoreBackedHighlighter(IndexSearcher searcher, Analyzer analyzer) {
        super(searcher, analyzer);
    }

    @Override
    protected List<CharSequence[]> loadFieldValues(String[] fields,
                                                   DocIdSetIterator docIter,
                                                   int cacheCharsThreshold) throws IOException {
        // We only know how to rehydrate the TEXT field. If callers ever ask
        // for something else, fall back to the default stored-fields path —
        // they'll get null/empty since v3 doesn't store anything else useful.
        if (fields.length != 1 || !fields[0].equals(IndexerSchema.TEXT.name())) {
            return super.loadFieldValues(fields, docIter, cacheCharsThreshold);
        }
        StoredFields storedFields = getIndexSearcher().storedFields();
        List<CharSequence[]> out = new ArrayList<>();
        int docId;
        while ((docId = docIter.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
            Document hitDoc = storedFields.document(docId, STORED_FIELDS_FOR_REHYDRATION);
            Integer nid = IndexerSchema.NID.read(hitDoc);
            Integer fieldOrdinal = IndexerSchema.INDEXED_FIELD_ORDINAL.read(hitDoc);
            String text = (nid == null || fieldOrdinal == null)
                    ? ""
                    : rehydrate(nid, fieldOrdinal);
            out.add(new CharSequence[] { text });
        }
        return out;
    }

    /**
     * Read the latest-version text at {@code fieldOrdinal} for the semantic
     * with {@code nid}. Returns the empty string when the entity is missing,
     * is not a {@link SemanticEntity}, has no versions, has no field at the
     * given ordinal, or the field value isn't a {@link String}.
     *
     * @param nid the semantic's nid (from the hit's stored {@code nid} field)
     * @param fieldOrdinal the position in {@code fieldValues()} (from the hit's
     *                     stored {@code fieldOrdinal} field)
     * @return the rehydrated text, never {@code null}
     */
    private static String rehydrate(int nid, int fieldOrdinal) {
        Entity<?> entity = EntityService.get().getEntityFast(nid);
        if (!(entity instanceof SemanticEntity<?> semantic)) {
            LOG.debug("rehydrate: nid {} is not a SemanticEntity (was {})",
                    nid, entity == null ? "null" : entity.getClass().getSimpleName());
            return "";
        }
        @SuppressWarnings("unchecked")
        ImmutableList<SemanticEntityVersion> versions =
                ((SemanticEntity<SemanticEntityVersion>) semantic).versions();
        if (versions.isEmpty()) {
            return "";
        }
        SemanticEntityVersion latest = versions.getLast();
        ImmutableList<Object> fieldValues = latest.fieldValues();
        if (fieldOrdinal < 0 || fieldOrdinal >= fieldValues.size()) {
            return "";
        }
        return fieldValues.get(fieldOrdinal) instanceof String s ? s.strip() : "";
    }
}
