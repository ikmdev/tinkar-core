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

import dev.ikm.tinkar.common.alert.AlertStreams;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.PrimitiveDataSearchResult;
import dev.ikm.tinkar.common.util.time.Stopwatch;
import dev.ikm.tinkar.coordinate.Coordinates;
import dev.ikm.tinkar.coordinate.stamp.calculator.LatestVersionSearchResult;
import dev.ikm.tinkar.entity.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.search.highlight.Formatter;
import org.apache.lucene.search.spell.LuceneDictionary;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.analyzing.AnalyzingSuggester;
import org.apache.lucene.search.suggest.analyzing.FuzzySuggester;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.eclipse.collections.api.list.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class TypeAheadSearch {

    public static final String NID = "nid";

    public static final String TEXT_FIELD_NAME = "text";

    private static AnalyzingSuggester suggester;
    private static FuzzySuggester fuzzySuggester;


    public static void buildSuggester() throws IOException {
        Stopwatch stopwatch = new Stopwatch();
        DirectoryReader reader = DirectoryReader.open(Indexer.indexWriter);
        LuceneDictionary dict = new LuceneDictionary(reader, TEXT_FIELD_NAME);
        suggester = new AnalyzingSuggester(Indexer.indexDirectory, "suggest", Indexer.analyzer);
        suggester.build(dict);
        System.out.println("INDEXER STOPWATCH: " + stopwatch.durationString());
    }

    public static List<String> suggest(String term) throws IOException {
        List<Lookup.LookupResult> lookup = suggester.lookup(term, false, 100);
        List<String> suggestions = lookup.stream().map(a -> a.key.toString()).collect(Collectors.toList());
        return suggestions;
    }

    public static void buildFuzzySuggester() throws IOException {
        Stopwatch stopwatch = new Stopwatch();
        DirectoryReader reader = DirectoryReader.open(Indexer.indexWriter);
        LuceneDictionary dict = new LuceneDictionary(reader, TEXT_FIELD_NAME);
        fuzzySuggester = new FuzzySuggester(Indexer.indexDirectory, "suggest", Indexer.analyzer);
        fuzzySuggester.build(dict);
        System.out.println("INDEXER STOPWATCH: " + stopwatch.durationString());
    }

    public static List<String> fuzzySuggest(String term) throws IOException {
        List<Lookup.LookupResult> lookup = fuzzySuggester.lookup(term, false, 100);
        List<String> suggestions = lookup.stream().map(a -> a.key.toString()).collect(Collectors.toList());
        return suggestions;
    }

    public static void buildSearchIndex(String term) throws IOException, ParseException, InvalidTokenOffsetsException {
        DirectoryReader reader = DirectoryReader.open(Indexer.indexWriter);
        LuceneDictionary dict = new LuceneDictionary(reader, TEXT_FIELD_NAME);
        IndexSearcher indexSearcher = new IndexSearcher(reader);
        QueryParser parser = new QueryParser("text", Indexer.analyzer());
        Query query = parser.parse(term);
        Formatter formatter = new SimpleHTMLFormatter();
        QueryScorer scorer = new QueryScorer(query);
        Highlighter highlighter = new Highlighter(formatter, scorer);
        ScoreDoc[] hits = indexSearcher.search(query, 100).scoreDocs;
        System.out.println(hits);
        PrimitiveDataSearchResult[] results = new PrimitiveDataSearchResult[hits.length];

        for (int i = 0; i < hits.length; i++) {
            Document hitDoc = indexSearcher.doc(hits[i].doc);
            StoredField nidField = (StoredField) hitDoc.getField(Indexer.NID);
            StoredField patternNidField = (StoredField) hitDoc.getField(Indexer.PATTERN_NID);
            StoredField rcNidField = (StoredField) hitDoc.getField(Indexer.RC_NID);
            StoredField fieldIndexField = (StoredField) hitDoc.getField(Indexer.FIELD_INDEX);
            StoredField textField = (StoredField) hitDoc.getField(Indexer.TEXT_FIELD_NAME);
            String highlightedString = highlighter.getBestFragment(Indexer.analyzer(), Indexer.TEXT_FIELD_NAME, textField.stringValue());

            results[i] = new PrimitiveDataSearchResult(nidField.numericValue().intValue(), rcNidField.numericValue().intValue(),
                    patternNidField.numericValue().intValue(), fieldIndexField.numericValue().intValue(), hits[i].score, highlightedString);
            System.out.println(results[i]);
        }
        System.out.println(results);
        System.out.println(Arrays.toString(results));
    }

    public static List<String> buildSuggestionsFromDescendants(PublicId ancestorId, List<String> suggestions){
        int[] descendantList = Searcher.defaultNavigationCalculator().descendentsOf(EntityService.get().nidForPublicId(ancestorId)).toArray();
        List<Entity<?>> entityList = new ArrayList<>();
        for (int descendantNid : descendantList) {
            EntityService.get().getEntity(descendantNid).ifPresent(entityList::add);
        }
        Iterator<String> iterator = suggestions.iterator();
        while(iterator.hasNext()){
            String suggestion = iterator.next();
            boolean found = entityList.stream().anyMatch(entity -> entity.entityToString().contains(suggestion));
            if(!found) {
                iterator.remove();
            }
        }

        return suggestions;
    }

    public static List<LatestVersionSearchResult> typeAheadSuggestions(String userInput, PublicId ancestorId) throws Exception {
        buildSuggester();
        List<String> suggestions = TypeAheadSearch.suggest(userInput);
        TypeAheadSearch.buildSuggestionsFromDescendants(ancestorId, suggestions);

        List<LatestVersionSearchResult> allSearchResults = new ArrayList<>();

        var stampCoordinate = Coordinates.Stamp.DevelopmentLatestActiveOnly();
        for(String s : suggestions){
            ImmutableList<LatestVersionSearchResult> searchResults = stampCoordinate.stampCalculator().searchDescendants(ancestorId, s, 100);
            allSearchResults.addAll((Collection<? extends LatestVersionSearchResult>) searchResults);
        }

        return allSearchResults;
    }

    public static List<LatestVersionSearchResult> typeAheadFuzzySuggestions(String userInput, PublicId ancestorId) throws Exception {
        buildFuzzySuggester();
        List<String> suggestions = TypeAheadSearch.fuzzySuggest(userInput);
        TypeAheadSearch.buildSuggestionsFromDescendants(ancestorId, suggestions);

        List<LatestVersionSearchResult> allSearchResults = new ArrayList<>();

        var stampCoordinate = Coordinates.Stamp.DevelopmentLatestActiveOnly();
        for(String s : suggestions){
            ImmutableList<LatestVersionSearchResult> searchResults = stampCoordinate.stampCalculator().searchDescendants(ancestorId, s, 100);
            allSearchResults.addAll((Collection<? extends LatestVersionSearchResult>) searchResults);
        }

        return allSearchResults;
    }

}
