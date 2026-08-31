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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchQueryRankingTest {

    private Directory directory;
    private StandardAnalyzer analyzer;
    private IndexWriter writer;
    private SearchQueryFactory factory;

    @BeforeEach
    void setUp() throws Exception {
        directory = new ByteBuffersDirectory();
        analyzer = new StandardAnalyzer();
        writer = new IndexWriter(directory, new IndexWriterConfig(analyzer));

        StandardQueryParser parser = new StandardQueryParser();
        parser.setAnalyzer(analyzer);
        factory = new SearchQueryFactory(parser);
    }

    @AfterEach
    void tearDown() throws Exception {
        writer.close();
        directory.close();
    }

    private void addDoc(String text) throws Exception {
        Document doc = new Document();
        doc.add(new TextField(IndexerSchema.TEXT.name(), text, Field.Store.YES));
        writer.addDocument(doc);
    }

    @Test
    void multiTokenPhrasePrefixRanksOverPartialMatch() throws Exception {
        addDoc("fully mobile device");
        addDoc("fully qualified name");
        addDoc("qualified specialist");
        writer.commit();

        try (DirectoryReader reader = DirectoryReader.open(writer)) {
            IndexSearcher searcher = new IndexSearcher(reader);

            Optional<Query> queryOpt = factory.buildQuery("fully qualif");
            assertTrue(queryOpt.isPresent());

            TopDocs topDocs = searcher.search(queryOpt.get(), 10);
            assertTrue(topDocs.totalHits.value() >= 2, "Expected hits for both docs");

            Document topDoc = searcher.storedFields().document(topDocs.scoreDocs[0].doc);
            assertEquals("fully qualified name", topDoc.get(IndexerSchema.TEXT.name()),
                    "Phrase-prefix match should rank first above partial 'fully' match");
        }
    }

    @Test
    void singleTokenPrefixRanksTargetTermTop() throws Exception {
        addDoc("definition of concept");
        addDoc("definitive guide");
        addDoc("deferred payment");
        writer.commit();

        try (DirectoryReader reader = DirectoryReader.open(writer)) {
            IndexSearcher searcher = new IndexSearcher(reader);

            Optional<Query> queryOpt = factory.buildQuery("definitio");
            assertTrue(queryOpt.isPresent());

            TopDocs topDocs = searcher.search(queryOpt.get(), 10);
            assertTrue(topDocs.totalHits.value() >= 1);

            Document topDoc = searcher.storedFields().document(topDocs.scoreDocs[0].doc);
            assertEquals("definition of concept", topDoc.get(IndexerSchema.TEXT.name()),
                    "Prefix match for 'definitio*' should rank 'definition' at top");
        }
    }

    @Test
    void uppercaseInputIsAnalyzerNormalizedInPrefixSearch() throws Exception {
        addDoc("definition of concept");
        writer.commit();

        try (DirectoryReader reader = DirectoryReader.open(writer)) {
            IndexSearcher searcher = new IndexSearcher(reader);

            Optional<Query> queryOpt = factory.buildQuery("DEFINITIO");
            assertTrue(queryOpt.isPresent());

            TopDocs topDocs = searcher.search(queryOpt.get(), 10);
            assertEquals(1, topDocs.totalHits.value(), "Analyzer normalization should match lowercased index terms");
        }
    }

    @Test
    void exactMatchRanksAbovePrefixMatch() throws Exception {
        addDoc("heart failure");
        addDoc("hearty meal");
        writer.commit();

        try (DirectoryReader reader = DirectoryReader.open(writer)) {
            IndexSearcher searcher = new IndexSearcher(reader);

            Optional<Query> queryOpt = factory.buildQuery("heart");
            assertTrue(queryOpt.isPresent());

            TopDocs topDocs = searcher.search(queryOpt.get(), 10);
            assertTrue(topDocs.totalHits.value() >= 2);

            Document topDoc = searcher.storedFields().document(topDocs.scoreDocs[0].doc);
            assertEquals("heart failure", topDoc.get(IndexerSchema.TEXT.name()),
                    "Exact term match should rank above prefix-only match");
        }
    }
}
