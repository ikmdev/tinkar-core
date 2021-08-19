package org.hl7.tinkar.provider.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.hl7.tinkar.common.service.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Searcher {
    private static final Logger LOG = LoggerFactory.getLogger(Searcher.class);
    DirectoryReader ireader;
    IndexSearcher isearcher;
    QueryParser parser;

    public Searcher() throws IOException {
        this.ireader = Indexer.getDirectoryReader();
        this.isearcher = new IndexSearcher(ireader);
        this.parser = new QueryParser("text", Indexer.analyzer());
    }

    public SearchResult[] search(String queryString, int maxResultSize) throws ParseException, IOException {
        Query query = parser.parse(queryString);
        ScoreDoc[] hits = isearcher.search(query, maxResultSize).scoreDocs;
        SearchResult[] results = new SearchResult[hits.length];
        for (int i = 0; i < hits.length; i++) {
            Document hitDoc = isearcher.doc(hits[i].doc);
            StoredField nidField = (StoredField) hitDoc.getField(Indexer.NID);
            StoredField patternNidField = (StoredField) hitDoc.getField(Indexer.PATTERN_NID);
            StoredField rcNidField = (StoredField) hitDoc.getField(Indexer.RC_NID);
            results[i] = new SearchResult(nidField.numericValue().intValue(), rcNidField.numericValue().intValue(),
                    patternNidField.numericValue().intValue(), hits[i].score);
        }
        return results;
    }

}
