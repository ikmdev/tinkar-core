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
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.queries.spans.SpanNearQuery;
import org.apache.lucene.queries.spans.SpanQuery;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.QueryParserUtil;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Builds the {@link Query} used by {@link Searcher} from a raw user query string.
 *
 * <p>A simple query (no Lucene special syntax) is expanded into a boosted
 * {@link BooleanQuery} containing exact, prefix, fuzzy, and proximity clauses
 * with differentiated boosts so exact matches outrank prefix and fuzzy matches
 * while type-ahead/partial matching is preserved. A query that already uses advanced
 * Lucene syntax (wildcards, phrases, boolean operators, etc.) is passed through
 * to {@link StandardQueryParser} unchanged.
 */
final class SearchQueryFactory {
    // Strictly decreasing weights / boosts so more specific match types outrank
    // less specific ones regardless of Lucene's raw per-clause scores:
    // phrase > exact term > phrase prefix > term prefix > parser fallback > fuzzy
    private static final float EXACT_PHRASE_BOOST = 5.0f;
    private static final float EXACT_MATCH_BOOST = 4.0f;
    private static final float PHRASE_PREFIX_BOOST = 3.0f;
    private static final float PREFIX_MATCH_BOOST = 2.0f;
    private static final float FUZZY_MATCH_BOOST = 0.5f;
    private static final float FALLBACK_MATCH_BOOST = 1.0f;

    private static final int FUZZY_MAX_EDITS = 1;      // 1 is safer/stricter, 2 is broader
    private static final int FUZZY_PREFIX_LENGTH = 1;  // require first char to match
    private static final int FUZZY_MAX_EXPANSIONS = 64;
    private static final boolean FUZZY_TRANSPOSITIONS = true;

    private static final int PHRASE_PREFIX_SLOP = 1;
    private static final boolean PHRASE_PREFIX_IN_ORDER = true;

    // StandardQueryParser recognizes these as boolean operators even though
    // QueryParserUtil.escape() does not treat them as reserved characters, so
    // "heart AND lung" would otherwise be misdetected as a simple query.
    private static final Pattern BOOLEAN_OPERATOR = Pattern.compile("\\b(AND|OR|NOT|TO)\\b");

    private final StandardQueryParser parser;

    SearchQueryFactory(StandardQueryParser parser) {
        this.parser = Objects.requireNonNull(parser);
    }

    Optional<Query> buildQuery(String queryString) throws QueryNodeException {
        if (queryString == null || queryString.isBlank()) {
            return Optional.empty();
        }

        String q = queryString.strip();

        if (isSimpleQuery(q)) {
            return Optional.of(buildBoostedSimpleQuery(q));
        }

        return Optional.of(parser.parse(q, IndexerSchema.TEXT.name()));
    }

    private boolean isSimpleQuery(String q) {
        return QueryParserUtil.escape(q).equals(q) && !BOOLEAN_OPERATOR.matcher(q).find();
    }

    private Query buildBoostedSimpleQuery(String q) throws QueryNodeException {
        String field = IndexerSchema.TEXT.name();
        List<String> terms = analyzeTerms(field, q);

        if (terms.isEmpty()) {
            return parser.parse(q, field);
        }

        if (terms.size() == 1) {
            return buildSingleTokenQuery(field, terms.getFirst());
        }

        return buildMultiTokenQuery(field, q, terms);
    }

    private Query buildSingleTokenQuery(String field, String termText) {
        Term term = new Term(field, termText);
        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        // 1. Exact Term
        builder.add(new BoostQuery(new TermQuery(term), EXACT_MATCH_BOOST), BooleanClause.Occur.SHOULD);

        // 2. Prefix Match
        // Default PrefixQuery rewrite is constant-score (every match ties).
        // TopTermsScoringBooleanQueryRewrite configuration avoids this by scoring each
        // expansion by its own term stats so closer/more common completions rank higher.
        PrefixQuery prefixQuery = new PrefixQuery(term, new MultiTermQuery.TopTermsScoringBooleanQueryRewrite(1000));
        builder.add(new BoostQuery(prefixQuery, PREFIX_MATCH_BOOST), BooleanClause.Occur.SHOULD);

        // 3. Fuzzy Match
        FuzzyQuery fuzzyQuery = new FuzzyQuery(term, FUZZY_MAX_EDITS, FUZZY_PREFIX_LENGTH, FUZZY_MAX_EXPANSIONS, FUZZY_TRANSPOSITIONS);
        builder.add(new BoostQuery(fuzzyQuery, FUZZY_MATCH_BOOST), BooleanClause.Occur.SHOULD);

        return builder.build();
    }

    // Scored as phrases, not independent terms, so word adjacency counts meaning
    // an exact phrase match is preferred over a match with both words far apart.
    private Query buildMultiTokenQuery(String field, String rawQuery, List<String> terms) throws QueryNodeException {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        // 1. Exact Phrase Match
        PhraseQuery.Builder phraseBuilder = new PhraseQuery.Builder();
        for (String term : terms) {
            phraseBuilder.add(new Term(field, term));
        }
        builder.add(new BoostQuery(phraseBuilder.build(), EXACT_PHRASE_BOOST), BooleanClause.Occur.SHOULD);

        // 2. Phrase Prefix Match (SpanNear)
        buildSpanNearPhrasePrefixQuery(field, terms).ifPresent(spanQuery ->
                builder.add(new BoostQuery(spanQuery, PHRASE_PREFIX_BOOST), BooleanClause.Occur.SHOULD));

        // 3. Fallback: safety net for cases the phrase/span clauses miss
        // (e.g. stopwords dropped by the analyzer).
        Query fallbackQuery = parser.parse(rawQuery, field);
        builder.add(new BoostQuery(fallbackQuery, FALLBACK_MATCH_BOOST), BooleanClause.Occur.SHOULD);

        return builder.build();
    }

    // Models a user still typing the last word: leading terms get fuzzy (typo)
    // tolerance, the last term gets prefix (type-ahead) matching.
    private Optional<Query> buildSpanNearPhrasePrefixQuery(String field, List<String> terms) {
        if (terms.isEmpty()) {
            return Optional.empty();
        }

        List<SpanQuery> clauses = new ArrayList<>();
        for (int i = 0; i < terms.size() - 1; i++) {
            FuzzyQuery fuzzyQuery = new FuzzyQuery(
                    new Term(field, terms.get(i)),
                    FUZZY_MAX_EDITS,
                    FUZZY_PREFIX_LENGTH,
                    FUZZY_MAX_EXPANSIONS,
                    FUZZY_TRANSPOSITIONS
            );
            clauses.add(new SpanMultiTermQueryWrapper<>(fuzzyQuery));
        }

        String lastTermPrefix = terms.getLast();
        PrefixQuery lastTermPrefixQuery = new PrefixQuery(
                new Term(field, lastTermPrefix),
                new MultiTermQuery.TopTermsScoringBooleanQueryRewrite(1000)
        );
        clauses.add(new SpanMultiTermQueryWrapper<>(lastTermPrefixQuery));

        return Optional.of(new SpanNearQuery(
                clauses.toArray(SpanQuery[]::new),
                PHRASE_PREFIX_SLOP,
                PHRASE_PREFIX_IN_ORDER
        ));
    }

    private List<String> analyzeTerms(String fieldName, String text) {
        Analyzer analyzer = parser.getAnalyzer();
        // Analyzer fallback chain to avoid failing during tests.
        if (analyzer == null) {
            analyzer = Indexer.analyzer();
        }
        if (analyzer == null) {
            analyzer = new StandardAnalyzer();
        }

        List<String> terms = new ArrayList<>();
        try (TokenStream tokenStream = analyzer.tokenStream(fieldName, text)) {
            CharTermAttribute termAttribute = tokenStream.addAttribute(CharTermAttribute.class);
            tokenStream.reset();

            while (tokenStream.incrementToken()) {
                terms.add(termAttribute.toString());
            }

            tokenStream.end();
            return terms;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
