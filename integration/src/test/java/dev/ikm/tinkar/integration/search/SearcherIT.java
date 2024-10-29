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
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.common.util.time.Stopwatch;
import dev.ikm.tinkar.composer.Composer;
import dev.ikm.tinkar.composer.Session;
import dev.ikm.tinkar.composer.assembler.PatternAssembler;
import dev.ikm.tinkar.composer.assembler.SemanticAssembler;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.Coordinates;
import dev.ikm.tinkar.coordinate.navigation.calculator.NavigationCalculatorWithCache;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.LatestVersionSearchResult;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.PatternEntityVersion;
import dev.ikm.tinkar.integration.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.provider.search.Searcher;
import dev.ikm.tinkar.provider.search.TypeAheadSearch;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
        //JTD add code here to modify the specific artifacts used by the search*ExistingIdentifier tests
        //    with the Composer API until we can get updated reasoned starter data
        // temporarily update latest IDENTIFIER_PATTERN
        try {
            Latest<PatternEntityVersion> latestIdPattern = Calculators.View.Default().latestPatternEntityVersion(TinkarTerm.IDENTIFIER_PATTERN);
            if ( latestIdPattern.get().fieldDefinitions().get(1).meaningNid() == TinkarTerm.IDENTIFIER_SOURCE.nid()) {
                Session session = composer.open(State.ACTIVE, /*time,*/ TinkarTerm.USER, TinkarTerm.SOLOR_OVERLAY_MODULE, TinkarTerm.DEVELOPMENT_PATH);
                session.compose((PatternAssembler patternAssembler) -> patternAssembler
                        .pattern(TinkarTerm.IDENTIFIER_PATTERN)
                        .meaning(TinkarTerm.IDENTIFIER_SOURCE)
                        .purpose(TinkarTerm.IDENTIFIER_SOURCE)
                        .fieldDefinition(TinkarTerm.IDENTIFIER_SOURCE, TinkarTerm.IDENTIFIER_SOURCE, TinkarTerm.COMPONENT_FIELD)
                        .fieldDefinition(TinkarTerm.IDENTIFIER_VALUE, TinkarTerm.IDENTIFIER_VALUE, TinkarTerm.STRING)
                );
            }
        } catch (Exception e) {
            LOG.error("Exception creating new IDENTIFIER_PATTERN");
        }
    }

    @AfterAll
    public void afterAll() {
        // delete temporary database
        FileUtil.recursiveDelete(DATASTORE_ROOT);
        TestHelper.stopDatabase();
    }

    @Test
    public void searchAfterNewEntitiesAreWrittenToDatabaseViaStampCoordinateIT() throws Exception {
        //Given an empty database

        //When new entities are written (via setup())

        //Then the searcher should immediately search on the newly added/indexed entities
        var stampCoordinate = Coordinates.Stamp.DevelopmentLatestActiveOnly();
        var searchResults = stampCoordinate.stampCalculator().search("user", 100);

        assertTrue(searchResults.size() > 0, "Missing search results");
    }

    @Test
    public void searchAfterNewEntitiesAreWrittenToDatabaseViaSearcherIT() throws Exception {
        //Given an empty database

        //When new entities are written (via setup())

        //Then the searcher should immediately search on the newly added/indexed entities
        var searcher = new Searcher();
        var searchResults = searcher.search("user", 100);

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
        var searchResults = stampCoordinate.stampCalculator().searchDescendants(TinkarTerm.ROLE, "Component", 100);

        //Then there should only be 6 LatestVersionSearchResults, a grouping of FQN, SYN, DEF for the following concepts:
        // 1) Refrenced component subtype restriction
        // 2) Refrenced component type restriction
        assertEquals(6, searchResults.size(), "Exactly 6 search results should be returned");
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
        var searchResults = stampCoordinate.stampCalculator().searchDescendants(navigationCalculator, TinkarTerm.ROLE, "Component", 100);

        //Then there should only be 6 LatestVersionSearchResults, a grouping of FQN, SYN, DEF for the following concepts:
        // 1) Referenced component subtype restriction
        // 2) Referenced component type restriction
        assertEquals(6, searchResults.size(), "Exactly 6 search results should be returned");
    }

    @Test
    public void typeAheadIndexerTest() throws Exception {
        List<String> suggestions = TypeAheadSearch.get().suggest("r");
        assertEquals(40, suggestions.size());

        // Add a new semantic
        MutableList<String> list = Lists.mutable.empty();
        list.add("rAdded");
        Session session = composer.open(State.ACTIVE, TinkarTerm.USER, TinkarTerm.SOLOR_OVERLAY_MODULE, TinkarTerm.DEVELOPMENT_PATH);
        session.compose((SemanticAssembler semanticAssembler) -> semanticAssembler
                .pattern(TinkarTerm.COMMENT_PATTERN)
                .reference(TinkarTerm.COMMENT)
                .fieldValues((MutableList<Object> values) -> values
                        .withAll(list))
        );
        suggestions = TypeAheadSearch.get().suggest("r");
        assertEquals(41, suggestions.size());
    }

    @Test
    public void typeAheadIndexerTestSearchAndDescendants1() throws Exception {
        // f7495b58-6630-3499-a44e-2052b5fcf06c - Author ID
        // Model concept: [7bbd4210-381c-11e7-9598-0800200c9a66]

        Stopwatch stopwatch = new Stopwatch();
        PublicId ancestorId = EntityService.get().getEntity(UUID.fromString("f7495b58-6630-3499-a44e-2052b5fcf06c")).get().publicId();

        String userInput = "u";
        List<LatestVersionSearchResult> allSearchResults = TypeAheadSearch.get().typeAheadSuggestions(userInput, ancestorId);

        stopwatch.stop();
    }

    @Test
    public void typeAheadIndexerTestSearchAndDescendants2() throws Exception {
        // f7495b58-6630-3499-a44e-2052b5fcf06c - Author ID
        // Model concept: [7bbd4210-381c-11e7-9598-0800200c9a66]

        long startTime = System.nanoTime();

        PublicId ancestorId = EntityService.get().getEntity(UUID.fromString("7bbd4210-381c-11e7-9598-0800200c9a66")).get().publicId();


        String userInput = "a";
        List<LatestVersionSearchResult> allSearchResults = TypeAheadSearch.get().typeAheadSuggestions(userInput, ancestorId);

        long endTime = System.nanoTime();
        long duration = endTime - startTime;
    }

    @Test
    public void typeAheadIndexerTestSearchAndDescendantsFuzzy() throws Exception {
        // f7495b58-6630-3499-a44e-2052b5fcf06c - Author ID
        // Model concept: [7bbd4210-381c-11e7-9598-0800200c9a66]
        PublicId ancestorId = EntityService.get().getEntity(UUID.fromString("f7495b58-6630-3499-a44e-2052b5fcf06c")).get().publicId();

        Stopwatch stopwatch = new Stopwatch();

        String userInput = "u";
        List<LatestVersionSearchResult> allSearchResults = TypeAheadSearch.get().typeAheadFuzzySuggestions(userInput, ancestorId);

        stopwatch.stop();
    }

    @Test
    public void typeAheadIndexerTestSearchAndDescendantsFuzzy2() throws Exception {
        // f7495b58-6630-3499-a44e-2052b5fcf06c - Author ID
        // Model concept: [7bbd4210-381c-11e7-9598-0800200c9a66]
        PublicId ancestorId = EntityService.get().getEntity(UUID.fromString("7bbd4210-381c-11e7-9598-0800200c9a66")).get().publicId();

        long startTime = System.nanoTime();

        String userInput = "a";
        List<LatestVersionSearchResult> allSearchResults = TypeAheadSearch.get().typeAheadFuzzySuggestions(userInput, ancestorId);

        long endTime = System.nanoTime();
        long duration = endTime - startTime;
    }


    @Test
    public void searchConceptsNonExistentMembershipSemantic() throws Exception {
        // test memberPatternId does not exist
        EntityProxy.Concept conceptProxy = EntityProxy.Concept.make(PublicIds.newRandom());
        List<PublicId> conceptIds = Searcher.membersOf(conceptProxy.publicId());
        assertTrue(conceptIds.isEmpty(), "memberPatternId does not exist, should return empty list");
    }

    @Test
    public void searchConceptsNonPatternMembershipSemantic() throws Exception {
        // test memberPatternId exists but is not a pattern
        List<PublicId> conceptIds = Searcher.membersOf(TinkarTerm.ROLE.publicId());
        assertTrue(conceptIds.isEmpty(), "memberPatternId exists but not a pattern, should return empty list");
    }

    @Test
    public void searchConceptsNoTaggedMembershipSemantic() throws Exception {
        // test memberPatternId with no tagged concepts
        List<PublicId> conceptIds = Searcher.membersOf(TinkarTerm.COMMENT_PATTERN);
        assertTrue(conceptIds.isEmpty(), "memberPatternId has no tagged concepts, should return empty list");
    }

    @Test
    public void searchConceptsWithTaggedMembershipSemantic() throws Exception {
        // test memberPatternId with tagged concepts
        List<PublicId> conceptIds = Searcher.membersOf(TinkarTerm.KOMET_BASE_MODEL_COMPONENT_PATTERN);
        assertEquals(1, conceptIds.size(), "there should be 1 tagged concept associated with this pattern");
        conceptIds = Searcher.membersOf(TinkarTerm.EL_PLUS_PLUS_INFERRED_AXIOMS_PATTERN);
        assertEquals(295, conceptIds.size(), "there should be 295 tagged concept associated with this pattern");
    }

    @Test
    public void searchExistingIdentifier() throws Exception {
        //source: TinkarTerm.UNIVERSALLY_UNIQUE_IDENTIFIER
        //identifier: LANGUAGE_NID_FOR_LANGUAGE_COORDINATE
        Optional<PublicId> publicId = Searcher.getPublicId(TinkarTerm.UNIVERSALLY_UNIQUE_IDENTIFIER, TinkarTerm.LANGUAGE_NID_FOR_LANGUAGE_COORDINATE.asUuidArray()[0].toString());
        assertTrue(publicId.isPresent(), "PublicId should be found");
        assertTrue(PublicId.equals(publicId.get(), TinkarTerm.LANGUAGE_NID_FOR_LANGUAGE_COORDINATE), "Concept PublicId should be LANGUAGE_NID_FOR_LANGUAGE_COORDINATE");
    }

    @Test
    public void searchNonExistingIdentifier() throws Exception {
        Optional<PublicId> publicId = Searcher.getPublicId(PublicIds.newRandom(), TinkarTerm.LANGUAGE_NID_FOR_LANGUAGE_COORDINATE.asUuidArray()[0].toString());
        assertFalse(publicId.isPresent(), "Concept should be null for non-existing Identifier Source");
        publicId = Searcher.getPublicId(TinkarTerm.UNIVERSALLY_UNIQUE_IDENTIFIER, "abcxyz");
        assertFalse(publicId.isPresent(), "Concept should be null for non-existing Identifier Value");
        publicId = Searcher.getPublicId(TinkarTerm.UNIVERSALLY_UNIQUE_IDENTIFIER, TinkarTerm.KOMET_BASE_MODEL_COMPONENT_PATTERN.asUuidArray()[0].toString());
        assertFalse(publicId.isPresent(), "Concept should be null for non-semantic uuid");
    }
}
