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

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.queries.spans.SpanNearQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchQueryFactoryTest {

    private final SearchQueryFactory factory = newFactory();

    private static SearchQueryFactory newFactory() {
        StandardQueryParser parser = new StandardQueryParser();
        parser.setAnalyzer(new StandardAnalyzer());
        return new SearchQueryFactory(parser);
    }

    private static boolean isAllShouldBoostedBooleanQuery(Query query, int expectedClauses) {
        if (!(query instanceof BooleanQuery booleanQuery)) {
            return false;
        }
        if (booleanQuery.clauses().size() != expectedClauses) {
            return false;
        }
        for (BooleanClause clause : booleanQuery.clauses()) {
            if (clause.occur() != BooleanClause.Occur.SHOULD) {
                return false;
            }
            if (!(clause.query() instanceof BoostQuery)) {
                return false;
            }
        }
        return true;
    }

    @Test
    void nullQueryReturnsEmpty() throws Exception {
        assertTrue(factory.buildQuery(null).isEmpty());
    }

    @Test
    void emptyQueryReturnsEmpty() throws Exception {
        assertTrue(factory.buildQuery("").isEmpty());
    }

    @Test
    void whitespaceOnlyQueryReturnsEmpty() throws Exception {
        assertTrue(factory.buildQuery("   ").isEmpty());
    }

    @Test
    void singleTokenQueryBuildsBoostedBooleanQuery() throws Exception {
        Optional<Query> query = factory.buildQuery("heart");

        assertTrue(query.isPresent());
        assertTrue(isAllShouldBoostedBooleanQuery(query.get(), 3));

        BooleanQuery booleanQuery = (BooleanQuery) query.get();
        List<BooleanClause> clauses = booleanQuery.clauses();

        Query c0 = ((BoostQuery) clauses.get(0).query()).getQuery();
        float b0 = ((BoostQuery) clauses.get(0).query()).getBoost();
        Query c1 = ((BoostQuery) clauses.get(1).query()).getQuery();
        float b1 = ((BoostQuery) clauses.get(1).query()).getBoost();
        Query c2 = ((BoostQuery) clauses.get(2).query()).getQuery();
        float b2 = ((BoostQuery) clauses.get(2).query()).getBoost();

        assertTrue(c0 instanceof TermQuery);
        assertEquals(4.0f, b0);

        assertTrue(c1 instanceof PrefixQuery);
        assertEquals(2.0f, b1);

        assertTrue(c2 instanceof FuzzyQuery);
        assertEquals(0.5f, b2);
    }

    @Test
    void multiTokenQueryBuildsBoostedBooleanQuery() throws Exception {
        Optional<Query> query = factory.buildQuery("heart rate");

        assertTrue(query.isPresent());
        assertTrue(isAllShouldBoostedBooleanQuery(query.get(), 3));

        BooleanQuery booleanQuery = (BooleanQuery) query.get();
        List<BooleanClause> clauses = booleanQuery.clauses();

        Query c0 = ((BoostQuery) clauses.get(0).query()).getQuery();
        float b0 = ((BoostQuery) clauses.get(0).query()).getBoost();
        Query c1 = ((BoostQuery) clauses.get(1).query()).getQuery();
        float b1 = ((BoostQuery) clauses.get(1).query()).getBoost();
        float b2 = ((BoostQuery) clauses.get(2).query()).getBoost();

        assertTrue(c0 instanceof PhraseQuery);
        assertEquals(5.0f, b0);

        assertTrue(c1 instanceof SpanNearQuery);
        assertEquals(3.0f, b1);

        assertEquals(1.0f, b2);
    }

    @Test
    void surroundingWhitespaceIsStrippedBeforeBuildingBoostedQuery() throws Exception {
        Optional<Query> query = factory.buildQuery(" heart ");

        assertTrue(query.isPresent());
        assertTrue(isAllShouldBoostedBooleanQuery(query.get(), 3));
    }

    @Test
    void wildcardQueryPassesThroughWithoutBoostedWrapper() throws Exception {
        Optional<Query> query = factory.buildQuery("heart*");

        assertTrue(query.isPresent());
        assertFalse(isAllShouldBoostedBooleanQuery(query.get(), 3));
    }

    @Test
    void phraseQueryPassesThroughWithoutBoostedWrapper() throws Exception {
        Optional<Query> query = factory.buildQuery("\"heart rate\"");

        assertTrue(query.isPresent());
        assertFalse(isAllShouldBoostedBooleanQuery(query.get(), 3));
    }

    @Test
    void booleanSyntaxQueryPassesThroughWithoutBoostedWrapper() throws Exception {
        Optional<Query> query = factory.buildQuery("heart AND lung");

        assertTrue(query.isPresent());
        assertFalse(isAllShouldBoostedBooleanQuery(query.get(), 3));
    }
}
