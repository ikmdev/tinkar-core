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

import dev.ikm.tinkar.common.service.TinkExecutor;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.coordinate.navigation.calculator.NavigationCalculator;
import dev.ikm.tinkar.coordinate.stamp.calculator.LatestVersionSearchResult;
import dev.ikm.tinkar.terms.ConceptFacade;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.spell.LuceneDictionary;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.analyzing.AnalyzingSuggester;
import org.apache.lucene.search.suggest.analyzing.FuzzySuggester;
import org.eclipse.collections.api.list.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class TypeAheadSearch {
    private static final Logger LOG = LoggerFactory.getLogger(TypeAheadSearch.class);
    private static final String TEXT_FIELD_NAME = "text";

    private AnalyzingSuggester suggester;
    private FuzzySuggester fuzzySuggester;
    private DirectoryReader reader;

    private static TypeAheadSearch typeAheadSearch = null;
    private final AtomicLong lastBuildTime = new AtomicLong();
    private final Object buildLock = new Object();
    private final long BUILD_WAIT = 5000;

    public static synchronized TypeAheadSearch get() {
        if (typeAheadSearch == null) {
            typeAheadSearch = new TypeAheadSearch();
            try {
                typeAheadSearch.buildSuggester();
            } catch (IOException e) {
                LOG.error("Caught Exception building suggester for TypeAheadSearch {}", e.getMessage());
            }
        }
        return typeAheadSearch;
    }

    private TypeAheadSearch() {
    }

    public Future<Void> buildSuggester() throws IOException {
        if (System.currentTimeMillis() - lastBuildTime.get() > BUILD_WAIT) {
            synchronized (buildLock) {
                if (System.currentTimeMillis() - lastBuildTime.get() > BUILD_WAIT) {
                    try {
                        return TinkExecutor.threadPool().submit(new BuildSuggester());
                    } finally {
                        lastBuildTime.set(System.currentTimeMillis());
                    }
                }
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    public void close() {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                LOG.error("Caught Exception closing reader {}", e.getMessage());
            }
        }
    }

    private List<String> suggest(String term, int maxResults) throws IOException {
        List<Lookup.LookupResult> lookup = suggester.lookup(term, false, maxResults);
        return lookup.stream().map(a -> a.key.toString()).collect(Collectors.toList());
    }

    /**
     * Returns List of ConceptFacades with Semantics matching the userInput using the TypeAhead search function
     * using the default NavigationCalculator from {@link Searcher#defaultNavigationCalculator()}
     *
     * @param   userInput String userInput
     * @param   maxResults int maxResults
     * @return  List of ConceptFacades
     */
    public List<ConceptFacade> typeAheadSuggestions(String userInput, int maxResults) {
        return typeAheadSuggestions(Searcher.defaultNavigationCalculator(), userInput, maxResults);
    }

    /**
     * Returns List of ConceptFacades with Semantics matching the userInput using the TypeAhead search function
     *
     * @param   navCalc NavigationCalculator navCalc
     * @param   userInput String userInput
     * @param   maxResults int maxResults
     * @return  List of ConceptFacades
     */
    public List<ConceptFacade> typeAheadSuggestions(NavigationCalculator navCalc, String userInput, int maxResults) {

        List<String> suggestions = null;
        try {
            suggestions = suggest(userInput, maxResults);
        } catch (IOException e) {
            LOG.error("Encountered exception {}", e.getMessage());
            return Collections.emptyList();
        }

        List<ConceptFacade> conceptList = new ArrayList<>();
        suggestions.forEach((suggestion) -> {
            try {
                ImmutableList<LatestVersionSearchResult> results = navCalc.search(suggestion, 1);
                results.forEach(latestVersionSearchResult -> {
                    latestVersionSearchResult.latestVersion().ifPresent(semanticEntityVersion -> {
                        if (semanticEntityVersion.referencedComponent() instanceof ConceptFacade conceptFacade) {
                            conceptList.add(conceptFacade);
                        }
                    });
                });
            } catch (Exception e) {
                LOG.error("Encountered exception {}", e.getMessage());
            }

        });
        return conceptList;
    }

    private class BuildSuggester extends TrackingCallable<Void> {

        public BuildSuggester() {
            super(false, true);
            LOG.info("Building Type Ahead Suggester");
            updateTitle("Building Type Ahead Suggester");
        }

        /**
         * Executes the process of building the "Type Ahead" Search Suggester.
         * The method manages progress tracking, lifecycle events, and logs the overall process duration.
         *
         * @return Returns {@code null} upon completion of the index rebuilding process.
         * @throws Exception if an error occurs during the "Type Ahead" Search Suggestion building phase.
         */
        @Override
        protected Void compute() throws IOException {
            if (!Indexer.indexWriter().isOpen()) {
                LOG.info("IndexWriter is already closed. Cannot build TypeAheadSearch suggester");
                return null;
            }
            LOG.info("Build Type Ahead Suggester started");
            updateMessage("Building Type Ahead Suggester...");
            updateProgress(-1, 1);

            reader = DirectoryReader.open(Indexer.indexWriter());
            LuceneDictionary dict = new LuceneDictionary(reader, TEXT_FIELD_NAME);
            AnalyzingSuggester analyzingSuggester = new AnalyzingSuggester(Indexer.indexDirectory(), "suggest", Indexer.analyzer());
            analyzingSuggester.build(dict);
            suggester = analyzingSuggester;

            updateProgress(1, 1);

            LOG.info("Type Ahead Suggester build completed. Total duration:  {}", this.durationString());
            updateMessage("Build time: " + this.durationString());
            return null;
        }
    }
}