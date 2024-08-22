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
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    public void descendantsWithConstraintTestNoChildrenEmptyList() throws Exception {
        List<PublicId> constraintAncestors = new ArrayList<>();
        PublicId ancestorId = PublicIds.of("3642d9a3-8e23-5289-836b-366c0b1e2900");
        List<PublicId> descendants = Searcher.getDescendantsWithConstraint(ancestorId, constraintAncestors);
        assertEquals(0, descendants.size());
    }

    @Test
    public void descendantsWithConstraintTestNoChildrenNonEmptyList() throws Exception {
        List<PublicId> constraintAncestors = new ArrayList<>();
        PublicId constraintId = PublicIds.of("7bbd4210-381c-11e7-9598-0800200c9a66");
        constraintAncestors.add(constraintId);
        PublicId ancestorId = PublicIds.of("3642d9a3-8e23-5289-836b-366c0b1e2900");
        List<PublicId> descendants = Searcher.getDescendantsWithConstraint(ancestorId, constraintAncestors);
        assertEquals(0, descendants.size());
    }

    @Test
    public void descendantsWithConstraintTestChildrenEmptyList() throws Exception {
        List<PublicId> constraintAncestors = new ArrayList<>();
        PublicId ancestorId = PublicIds.of("f7495b58-6630-3499-a44e-2052b5fcf06c");
        List<PublicId> descendants = Searcher.getDescendantsWithConstraint(ancestorId, constraintAncestors);
        assertEquals(8, descendants.size());
    }

    @Test
    public void descendantsWithConstraintTestChildrenNonEmptyList() throws Exception {
        List<PublicId> constraintAncestors = new ArrayList<>();
        PublicId constraintId = PublicIds.of("f7495b58-6630-3499-a44e-2052b5fcf06c");
        constraintAncestors.add(constraintId);
        PublicId ancestorId = PublicIds.of("7bbd4210-381c-11e7-9598-0800200c9a66");
        List<PublicId> descendants = Searcher.getDescendantsWithConstraint(ancestorId, constraintAncestors);
        assertEquals(181, descendants.size());
    }


    @Test
    public void descendantsWithConstraintConstraintNotAConcept() throws Exception {
        List<PublicId> constraintAncestors = new ArrayList<>();
        PublicId constraintId = PublicIds.of("9c25d708-b9e9-4e97-b9f2-cba5dc917975");
        constraintAncestors.add(constraintId);
        PublicId ancestorId = PublicIds.of("7bbd4210-381c-11e7-9598-0800200c9a66");
        List<PublicId> descendants = Searcher.getDescendantsWithConstraint(ancestorId, constraintAncestors);
        assertEquals(181, descendants.size());
    }

    @Test
    public void descendantsWithConstraintConstraintDNE() throws Exception {
        List<PublicId> constraintAncestors = new ArrayList<>();
        PublicId constraintId = PublicIds.of("abcde-6630-3499-a44e-2052b5fcf06c");
        constraintAncestors.add(constraintId);
        PublicId ancestorId = PublicIds.of(UUID.fromString("f7495b58-6630-3499-a44e-2052b5fcf06c"));
        List<PublicId> descendants = Searcher.getDescendantsWithConstraint(ancestorId, constraintAncestors);
        assertEquals(8, descendants.size());
    }

    @Test
    public void descendantsWithConstraintConstraints() throws Exception {
        List<PublicId> constraintAncestors = new ArrayList<>();
        PublicId constraintId = TinkarTerm.ROLE_OPERATOR.publicId();
        constraintAncestors.add(constraintId);
        PublicId ancestorId = TinkarTerm.ROLE.publicId();
        List<PublicId> descendants = Searcher.getDescendantsWithConstraint(ancestorId, constraintAncestors);
        System.out.println(descendants);
        assertEquals(2, descendants.size());
    }

}
