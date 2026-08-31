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
package dev.ikm.tinkar.integration.search;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveDataSearchResult;
import dev.ikm.tinkar.common.service.SearchService;
import dev.ikm.tinkar.common.service.ServiceLifecycleManager;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.composer.Composer;
import dev.ikm.tinkar.composer.Session;
import dev.ikm.tinkar.composer.template.Synonym;
import dev.ikm.tinkar.coordinate.Coordinates;
import dev.ikm.tinkar.coordinate.navigation.calculator.NavigationCalculatorWithCache;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.provider.search.Searcher;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SearcherIT {
    private static final Logger LOG = LoggerFactory.getLogger(SearcherIT.class);
    private static final File DATASTORE_ROOT = TestConstants.createFilePathInTargetFromClassName.apply(
            SearcherIT.class);
    private final Composer composer = new Composer("SearcherIT");
    @BeforeAll
    public void beforeAll() {
        TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, DATASTORE_ROOT);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
    }

    @AfterAll
    public void afterAll() {
        TestHelper.stopDatabase();
        // delete temporary database
        FileUtil.recursiveDelete(DATASTORE_ROOT);
    }

    @Test
    public void searchAfterNewEntitiesAreWrittenToDatabaseViaStampCoordinateIT() throws Exception {
        //Given an empty database

        //When new entities are written (via setup())

        //Then the searcher should immediately search on the newly added/indexed entities
        var stampCoordinate = Coordinates.Stamp.DevelopmentLatestActiveOnly();
        var searchResults = stampCoordinate.stampCalculator().search("user", 100);

        assertTrue(searchResults.notEmpty(), "Missing search results");
    }

    @Test
    public void searchAfterNewEntitiesAreWrittenToDatabaseViaSearcherIT() throws Exception {
        //Given an empty database

        //When new entities are written (via setup())

        //Then the searcher should immediately search on the newly added/indexed entities
        // Access SearchService through ServiceLifecycleManager instead of directly instantiating Searcher
        var searchService = ServiceLifecycleManager.get()
                .getRunningService(SearchService.class)
                .orElseThrow(() -> new IllegalStateException("SearchService not available - ensure services are started"));
        var searchResults = searchService.search("user", 100);

        assertTrue(searchResults.length > 0, "Missing search results");
    }

    @Test
    public void searchFromDescendantsOfConceptWithDefaultCalculatorIT() throws Exception {
        //Role: [46ae9325-dd24-5008-8fda-80cf1f0977c7]
        //    Role group: [a63f4bf2-a040-11e5-8994-feff819cdc9f]
        //    Role operator: [f9860cb8-a7c7-5743-9d7c-ffc6e8a24a0f]
        //        Refrenced component subtype restriction: [8af1045e-1122-5072-9f29-ce7da9337915]
        //        Refrenced component type restriction: [902f97b6-2ef4-59d7-b6f9-01278a00061c]
        //        Universal restriction: [fc18c082-c6ad-52d2-b568-cc9568ace6c9]
        //    Role type: [76320274-be2a-5ba0-b3e8-e6d2e383ee6a]

        //Given a datastore loaded with tinkar starter data (via setup()) and a navigatorCalculator/stampCalculator for Primordial Path
        var stampCoordinate = Coordinates.Stamp.DevelopmentLatestActiveOnly();

        //When I search "Component" for only the descendants of Role
        var searchResults = stampCoordinate.stampCalculator().searchDescendants(TinkarTerm.ROLE, "Feature", 100);

        //Then there should only be 2 LatestVersionSearchResults, a grouping of FQN, SYN for the following concepts:
        // 1) Feature Role Type
        assertEquals(2, searchResults.size(), "Exactly 2 search results should be returned");
    }

    @Test
    public void searchFromDescendantsOfConceptWithCustomCalculatorIT() throws Exception {
        //Role: [46ae9325-dd24-5008-8fda-80cf1f0977c7]
        //    Role group: [a63f4bf2-a040-11e5-8994-feff819cdc9f]
        //    Role operator: [f9860cb8-a7c7-5743-9d7c-ffc6e8a24a0f]
        //        Refrenced component subtype restriction: [8af1045e-1122-5072-9f29-ce7da9337915]
        //        Refrenced component type restriction: [902f97b6-2ef4-59d7-b6f9-01278a00061c]
        //        Universal restriction: [fc18c082-c6ad-52d2-b568-cc9568ace6c9]
        //    Role type: [76320274-be2a-5ba0-b3e8-e6d2e383ee6a]

        //Given a datastore loaded with tinkar starter data (via setup()) and a navigatorCalculator/stampCalculator for Primordial Path
        var stampCoordinate = Coordinates.Stamp.DevelopmentLatestActiveOnly();
        var languageCoordinate = Coordinates.Language.UsEnglishRegularName();
        var navigationCoordinate = Coordinates.Navigation.inferred().toNavigationCoordinateRecord();
        var navigationCalculator = NavigationCalculatorWithCache.getCalculator(stampCoordinate, Lists.immutable.of(languageCoordinate), navigationCoordinate);

        //When I search "Component" for only the descendants of Role
        var searchResults = stampCoordinate.stampCalculator().searchDescendants(navigationCalculator, TinkarTerm.ROLE, "Feature", 100);

        //Then there should only be 2 LatestVersionSearchResults, a grouping of FQN, SYN for the following concepts:
        // 1) Feature Role Type
        assertEquals(2, searchResults.size(), "Exactly 2 search results should be returned");
    }

    @Test
    public void searchConceptsNonExistentMembershipSemantic() {
        // test memberPatternId does not exist
        EntityProxy.Concept conceptProxy = EntityProxy.Concept.make(PublicIds.newRandom());
        List<PublicId> conceptIds = Searcher.membersOf(conceptProxy.publicId());
        assertTrue(conceptIds.isEmpty(), "memberPatternId does not exist, should return empty list");
    }

    @Test
    public void searchConceptsNonPatternMembershipSemantic() {
        // test memberPatternId exists but is not a pattern
        List<PublicId> conceptIds = Searcher.membersOf(TinkarTerm.ROLE.publicId());
        assertTrue(conceptIds.isEmpty(), "memberPatternId exists but not a pattern, should return empty list");
    }

    @Test
    public void searchConceptsNoTaggedMembershipSemantic() {
        // test memberPatternId with no tagged concepts
        List<PublicId> conceptIds = Searcher.membersOf(TinkarTerm.COMMENT_PATTERN);
        assertTrue(conceptIds.isEmpty(), "memberPatternId has no tagged concepts, should return empty list");
    }

    @Test
    public void searchConceptsWithTaggedMembershipSemantic() {
        // test memberPatternId with tagged concepts
        List<PublicId> conceptIds = Searcher.membersOf(TinkarTerm.KOMET_BASE_MODEL_COMPONENT_PATTERN);
        assertEquals(6, conceptIds.size(), "there should be 6 tagged concept associated with this pattern");
        conceptIds = Searcher.membersOf(TinkarTerm.EL_PLUS_PLUS_INFERRED_AXIOMS_PATTERN);
        assertEquals(379, conceptIds.size(), "there should be 379 tagged concept associated with this pattern");
    }

    @Test
    public void searchExistingIdentifier() {
        //source: TinkarTerm.UNIVERSALLY_UNIQUE_IDENTIFIER
        //identifier: LANGUAGE_NID_FOR_LANGUAGE_COORDINATE
        Optional<PublicId> publicId = Searcher.getPublicId(TinkarTerm.UNIVERSALLY_UNIQUE_IDENTIFIER, TinkarTerm.LANGUAGE_NID_FOR_LANGUAGE_COORDINATE.asUuidArray()[0].toString());
        assertTrue(publicId.isPresent(), "PublicId should be found");
        assertTrue(PublicId.equals(publicId.get(), TinkarTerm.LANGUAGE_NID_FOR_LANGUAGE_COORDINATE), "Concept PublicId should be LANGUAGE_NID_FOR_LANGUAGE_COORDINATE");
    }

    @Test
    public void searchNonExistingIdentifier() {
        Optional<PublicId> publicId = Searcher.getPublicId(PublicIds.newRandom(), TinkarTerm.LANGUAGE_NID_FOR_LANGUAGE_COORDINATE.asUuidArray()[0].toString());
        assertFalse(publicId.isPresent(), "Concept should be null for non-existing Identifier Source");
        publicId = Searcher.getPublicId(TinkarTerm.UNIVERSALLY_UNIQUE_IDENTIFIER, "abcxyz");
        assertFalse(publicId.isPresent(), "Concept should be null for non-existing Identifier Value");
        publicId = Searcher.getPublicId(TinkarTerm.UNIVERSALLY_UNIQUE_IDENTIFIER, TinkarTerm.KOMET_BASE_MODEL_COMPONENT_PATTERN.asUuidArray()[0].toString());
        assertFalse(publicId.isPresent(), "Concept should be null for non-semantic uuid");
    }

    @Test
    public void searchWhitespaceDoesNotThrowNPE() {
        SearchService searchService = ServiceLifecycleManager.get()
                .getRunningService(SearchService.class)
                .orElseThrow(() -> new IllegalStateException("SearchService not available"));

        assertDoesNotThrow(() -> {
            var results = searchService.search(" ", 100);
            assertEquals(0, results.length);
        });
    }

    @Test
    public void searchEmptyDoesNotThrowNPE() {
        SearchService searchService = ServiceLifecycleManager.get()
                .getRunningService(SearchService.class)
                .orElseThrow(() -> new IllegalStateException("SearchService not available"));

        assertDoesNotThrow(() -> {
            var results = searchService.search("", 100);
            assertEquals(0, results.length);
        });
    }

    @Test
    public void highlightWhitespaceDoesNotThrowNPE() {
        SearchService searchService = ServiceLifecycleManager.get()
                .getRunningService(SearchService.class)
                .orElseThrow(() -> new IllegalStateException("SearchService not available"));

        assertDoesNotThrow(() -> {
            String text = "Some sample text";
            String result = searchService.highlight(" ", text);
            assertEquals(text, result);
        });
    }

    @Test
    public void searchTrailingWhitespaceWorks() {
        SearchService searchService = ServiceLifecycleManager.get()
                .getRunningService(SearchService.class)
                .orElseThrow(() -> new IllegalStateException("SearchService not available"));

        assertDoesNotThrow(() -> {
            // "user " should be trimmed to "user" and return results
            var results = searchService.search("user ", 100);
            assertTrue(results.length > 0, "Should find results for 'user ' (trimmed to 'user')");
        });
    }

    @Test
    public void highlightEmptyDoesNotThrowNPE() {
        SearchService searchService = ServiceLifecycleManager.get()
                .getRunningService(SearchService.class)
                .orElseThrow(() -> new IllegalStateException("SearchService not available"));

        assertDoesNotThrow(() -> {
            String text = "Some sample text";
            String result = searchService.highlight("", text);
            assertEquals(text, result);
        });
    }

    @Test
    public void searchUnicodeWhitespaceDoesNotThrowNPE() {
        SearchService searchService = ServiceLifecycleManager.get()
                .getRunningService(SearchService.class)
                .orElseThrow(() -> new IllegalStateException("SearchService not available"));

        assertDoesNotThrow(() -> {
            // \u2003 is a Unicode em space
            var results = searchService.search("\u2003", 100);
            assertEquals(0, results.length);
        });
    }

    @Test
    public void searchTrailingUnicodeWhitespaceWorks() {
        SearchService searchService = ServiceLifecycleManager.get()
                .getRunningService(SearchService.class)
                .orElseThrow(() -> new IllegalStateException("SearchService not available"));

        assertDoesNotThrow(() -> {
            // "user\u2003" should be stripped to "user" and return results
            var results = searchService.search("user\u2003", 100);
            assertTrue(results.length > 0, "Should find results for 'user\u2003' (stripped to 'user')");
        });
    }

    /**
     * Composes a new Synonym description with the given text on {@link TinkarTerm#USER},
     * returning the {@link EntityProxy.Semantic} proxy so callers can identify the resulting
     * search hit by nid.
     */
    private EntityProxy.Semantic composeSynonym(String text) {
        EntityProxy.Semantic semanticProxy = EntityProxy.Semantic.make(PublicIds.newRandom());
        Session session = composer.open(State.ACTIVE, TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);
        session.compose(new Synonym()
                        .semantic(semanticProxy)
                        .language(TinkarTerm.ENGLISH_LANGUAGE)
                        .caseSignificance(TinkarTerm.DESCRIPTION_NOT_CASE_SENSITIVE)
                        .text(text),
                TinkarTerm.USER);
        composer.commitSession(session);
        return semanticProxy;
    }

    private Optional<PrimitiveDataSearchResult> findByNid(PrimitiveDataSearchResult[] results, int nid) {
        return Arrays.stream(results).filter(r -> r.nid() == nid).findFirst();
    }

    @Test
    public void exactTokenRanksAbovePrefixOnlyTokenForSimpleQuery() throws Exception {
        // Given two new descriptions: one containing the exact query token, and
        // one containing the token only as a prefix of a longer word
        EntityProxy.Semantic exactSemantic = composeSynonym("zzzexactalpha");
        EntityProxy.Semantic prefixOnlySemantic = composeSynonym("zzzexactalphabetic");

        SearchService searchService = ServiceLifecycleManager.get()
                .getRunningService(SearchService.class)
                .orElseThrow(() -> new IllegalStateException("SearchService not available"));

        // When searching the simple query "zzzexactalpha"
        PrimitiveDataSearchResult[] results = searchService.search("zzzexactalpha", 100);

        // Then both descriptions are found, and the exact match outranks the prefix-only match
        Optional<PrimitiveDataSearchResult> exactHit = findByNid(results, exactSemantic.nid());
        Optional<PrimitiveDataSearchResult> prefixHit = findByNid(results, prefixOnlySemantic.nid());

        assertTrue(exactHit.isPresent(), "Exact-match description should be found");
        assertTrue(prefixHit.isPresent(), "Prefix-only description should still be found");
        assertTrue(exactHit.get().score() > prefixHit.get().score(),
                "Exact match score (" + exactHit.get().score() + ") should be greater than prefix-only match score (" + prefixHit.get().score() + ")");
    }

    @Test
    public void simplePrefixQueryStillFindsLongerTerm() throws Exception {
        // Given a description containing a longer word starting with the query token
        EntityProxy.Semantic prefixOnlySemantic = composeSynonym("zzzprefixalphabetic");

        SearchService searchService = ServiceLifecycleManager.get()
                .getRunningService(SearchService.class)
                .orElseThrow(() -> new IllegalStateException("SearchService not available"));

        // When searching a true prefix of that word (not itself an indexed token)
        PrimitiveDataSearchResult[] results = searchService.search("zzzprefixalph", 100);

        // Then prefix (type-ahead style) matching still finds the longer term
        assertTrue(findByNid(results, prefixOnlySemantic.nid()).isPresent(),
                "Simple prefix query should still find the longer indexed term");
    }

    @Test
    public void advancedWildcardQueryStillWorks() throws Exception {
        // Given a description containing a word matching an explicit wildcard query
        EntityProxy.Semantic semantic = composeSynonym("zzzwildcardalpha");

        SearchService searchService = ServiceLifecycleManager.get()
                .getRunningService(SearchService.class)
                .orElseThrow(() -> new IllegalStateException("SearchService not available"));

        // When searching with explicit advanced Lucene wildcard syntax
        PrimitiveDataSearchResult[] results = assertDoesNotThrow(() -> searchService.search("zzzwildcardalph*", 100));

        // Then the wildcard query still parses and finds the match, unaffected by the
        // boosted exact+prefix wrapper (which only applies to simple queries)
        assertTrue(findByNid(results, semantic.nid()).isPresent(),
                "Explicit wildcard query should still find the matching term");
    }

    @Test
    public void highlightWithSimpleQueryStillMarksMatchingText() throws Exception {
        SearchService searchService = ServiceLifecycleManager.get()
                .getRunningService(SearchService.class)
                .orElseThrow(() -> new IllegalStateException("SearchService not available"));

        String result = searchService.highlight("zzzhighlightalpha", "Zzzhighlightalpha zzzhighlightalphabetic beta");

        assertTrue(result.contains("<B>Zzzhighlightalpha</B>"), "Exact term should be highlighted: " + result);
    }
}
