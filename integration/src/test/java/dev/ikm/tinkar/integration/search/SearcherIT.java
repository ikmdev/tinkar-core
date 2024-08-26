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
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.Coordinates;
import dev.ikm.tinkar.coordinate.navigation.calculator.NavigationCalculatorWithCache;
import dev.ikm.tinkar.integration.TestConstants;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.provider.search.Searcher;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SearcherIT extends TestHelper {

    private static final File SAP_DATASTORE_ROOT = TestConstants.createFilePathInTargetFromClassName.apply(
            SearcherIT.class);

    @BeforeAll
    public void setup() {
        loadSpinedArrayDataBase(SAP_DATASTORE_ROOT);
    }

    @AfterAll
    public void teardown() {
        PrimitiveData.stop();
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
        // 1) Refrenced component subtype restriction
        // 2) Refrenced component type restriction
        assertEquals(6, searchResults.size(), "Exactly 6 search results should be returned");
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
}
