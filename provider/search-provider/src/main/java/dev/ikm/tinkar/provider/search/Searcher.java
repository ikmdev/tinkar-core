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

import dev.ikm.tinkar.common.service.PrimitiveDataSearchResult;
import dev.ikm.tinkar.common.util.time.Stopwatch;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.highlight.Formatter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.NullFragmenter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Searcher {
    private static final Logger LOG = LoggerFactory.getLogger(Searcher.class);
    QueryParser parser;

    public Searcher() throws IOException {
        Stopwatch stopwatch = new Stopwatch();
        LOG.info("Opening lucene searcher");
        this.parser = new QueryParser("text", Indexer.analyzer());
        stopwatch.stop();
        LOG.info("Opened lucene searcher in: " + stopwatch.durationString());
    }


    public PrimitiveDataSearchResult[] search(String queryString, int maxResultSize) throws
            ParseException, IOException, InvalidTokenOffsetsException {
        IndexSearcher isearcher = new IndexSearcher(Indexer.indexReader());
        if (queryString != null & !queryString.isEmpty()) {
            Query query = parser.parse(queryString);
            Formatter formatter = new SimpleHTMLFormatter();
            QueryScorer scorer = new QueryScorer(query);
            Highlighter highlighter = new Highlighter(formatter, scorer);
            highlighter.setTextFragmenter(new NullFragmenter());

            ScoreDoc[] hits = isearcher.search(query, maxResultSize).scoreDocs;
            PrimitiveDataSearchResult[] results = new PrimitiveDataSearchResult[hits.length];
            for (int i = 0; i < hits.length; i++) {
                Document hitDoc = isearcher.doc(hits[i].doc);
                StoredField nidField = (StoredField) hitDoc.getField(Indexer.NID);
                StoredField patternNidField = (StoredField) hitDoc.getField(Indexer.PATTERN_NID);
                StoredField rcNidField = (StoredField) hitDoc.getField(Indexer.RC_NID);
                StoredField fieldIndexField = (StoredField) hitDoc.getField(Indexer.FIELD_INDEX);
                StoredField textField = (StoredField) hitDoc.getField(Indexer.TEXT_FIELD_NAME);
                String highlightedString = highlighter.getBestFragment(Indexer.analyzer(), Indexer.TEXT_FIELD_NAME, textField.stringValue());

                results[i] = new PrimitiveDataSearchResult(nidField.numericValue().intValue(), rcNidField.numericValue().intValue(),
                        patternNidField.numericValue().intValue(), fieldIndexField.numericValue().intValue(), hits[i].score, highlightedString);
            }
            return results;
        }
        return new PrimitiveDataSearchResult[0];
    }

}
