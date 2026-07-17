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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.queries.spans.SpanFirstQuery;
import org.apache.lucene.queries.spans.SpanTermQuery;

/**
 * A {@link QueryParser} extension that boosts documents where the search term
 * appears at the very beginning of the indexed field (token position 0).
 *
 * <p>For every single-term query the parser would normally produce, this class
 * instead returns a {@link BooleanQuery} with two clauses:
 * <ol>
 *   <li><b>MUST</b> — the standard {@link org.apache.lucene.search.TermQuery},
 *       which ensures only documents actually containing the term are returned.</li>
 *   <li><b>SHOULD</b> — a {@link BoostQuery}-wrapped {@link SpanFirstQuery} that
 *       adds extra score when the term is found within the first
 *       {@value #SPAN_FIRST_END} token position(s) of the field. Documents that
 *       do not match this clause are still returned; they simply do not receive
 *       the bonus score.</li>
 * </ol>
 *
 * <p>The {@code SPAN_FIRST_END} constant controls how many token positions from
 * the start are considered "the beginning". A value of {@code 1} means the term
 * must be the very first token (position 0). Increase this value to give the
 * bonus to matches within the first N tokens.
 *
 * <p>The {@code BEGINNING_BOOST} constant controls the magnitude of the score
 * bonus. Tune this value relative to your corpus and desired ranking behavior.
 *
 * <p><b>Index requirement:</b> The target field must be indexed with term
 * positions (the default for {@link org.apache.lucene.document.TextField}).
 * {@link SpanFirstQuery} reads positional posting lists; a field indexed with
 * {@link org.apache.lucene.index.IndexOptions#DOCS_AND_FREQS} only (no
 * positions) will silently produce no bonus matches.
 */
public class BeginningBoostQueryParser extends QueryParser {

    /**
     * The number of token positions from the start of the field within which a
     * match must end to receive the beginning boost. A value of {@code 1} means
     * only position 0 (the first token) qualifies.
     *
     * <p>{@link SpanFirstQuery}'s {@code end} parameter is exclusive-end: the
     * matching span's end position must be {@code <= end}. For a single token,
     * its end position equals its start position + 1, so {@code end = 1}
     * restricts to position 0.
     */
    private static final int SPAN_FIRST_END = 1;

    /**
     * Multiplier applied to the {@link SpanFirstQuery} score contribution via
     * {@link BoostQuery}. Values greater than {@code 1.0f} push beginning-match
     * documents above otherwise-equal middle-match documents.
     */
    private static final float BEGINNING_BOOST = 3.0f;

    /**
     * Constructs a {@code BeginningBoostQueryParser} with the same arguments
     * as a standard {@link QueryParser}.
     *
     * @param defaultField the default field name for query terms that do not
     *                     specify a field explicitly
     * @param analyzer     the analyzer used to tokenize query text; must be the
     *                     same analyzer used at index time so that term text
     *                     matches what is in the posting lists
     */
    public BeginningBoostQueryParser(String defaultField, Analyzer analyzer) {
        super(defaultField, analyzer);
    }

    /**
     * Overrides the factory method called by the parser whenever it produces a
     * single-term query.
     *
     * <p>The returned {@link BooleanQuery} has two clauses:
     * <ul>
     *   <li>MUST: the base {@link org.apache.lucene.search.TermQuery} from the
     *       parent implementation, ensuring matching semantics are unchanged.</li>
     *   <li>SHOULD: a {@link SpanFirstQuery} restricted to the first
     *       {@value #SPAN_FIRST_END} position(s), wrapped in a
     *       {@link BoostQuery} with factor {@value #BEGINNING_BOOST}.</li>
     * </ul>
     *
     * @param term the term produced by the parser for this query token
     * @return a {@link BooleanQuery} combining the required term match with the
     *         optional beginning-position score bonus
     */
    @Override
    protected Query newTermQuery(Term term, float boost) {
        // Base query: the term must appear somewhere in the field.
        Query baseQuery = super.newTermQuery(term, boost);

        // Span query: the term must appear within the first SPAN_FIRST_END token position(s).
        // SpanFirstQuery(SpanQuery match, int end):
        //   - match: the span to search for (a single term span here)
        //   - end:   the span's end position must be <= this value (exclusive)
        SpanFirstQuery spanFirst = new SpanFirstQuery(new SpanTermQuery(term), SPAN_FIRST_END);

        // Wrap the span query with a boost so it adds score without filtering.
        Query boostedSpanFirst = new BoostQuery(spanFirst, BEGINNING_BOOST);

        return new BooleanQuery.Builder()
                .add(baseQuery,       BooleanClause.Occur.MUST)
                .add(boostedSpanFirst, BooleanClause.Occur.SHOULD)
                .build();
    }
}
