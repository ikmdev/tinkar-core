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
package dev.ikm.tinkar.integration.coordinate;

import dev.ikm.tinkar.common.id.LongIdSet;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.Coordinates;
import dev.ikm.tinkar.coordinate.navigation.calculator.Edge;
import dev.ikm.tinkar.coordinate.navigation.calculator.NavigationCalculator;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.KernelConcept;
import network.ike.foundation.ike.bindings.IkeConcept;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link ConceptFacade} overloads of {@link NavigationCalculator} answer the same question
 * as their nid overloads, against the reasoned IKE starter set in a real ephemeral store.
 * Two of those defaults once answered the opposite question: {@code ancestorsOf(ConceptFacade)}
 * returned the concept's descendants, and {@code parentEdges(ConceptFacade)} its child edges.
 *
 * <p>Each check runs on the stated and inferred navigation calculators and on the default view
 * calculator, which reaches the same defaults through {@code NavigationCalculatorDelegate}.
 * Each first confirms that the probe concept tells the two answers apart, so a fixture in which
 * they coincide fails rather than passing vacuously.
 */
class NavigationCalculatorFacadeOverloadsIT {

    /** A concept with both parents and children in the starter set's taxonomy. */
    private static final ConceptFacade PROBE = IkeConcept.MODEL_CONCEPT;

    @BeforeAll
    static void loadReasonedStarterSet() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
    }

    @AfterAll
    static void stop() {
        TestHelper.stopDatabase();
    }

    @Test
    @DisplayName("ancestorsOf(ConceptFacade) answers ancestorsOf(nid), not descendentsOf(nid)")
    void ancestorsOfFacadeAnswersAncestors() {
        calculators().forEach((name, calculator) -> {
            long[] ancestors = sorted(calculator.ancestorsOf(PROBE.nid()));
            long[] descendants = sorted(calculator.descendentsOf(PROBE.nid()));
            assertTrue(calculator.ancestorsOf(PROBE.nid()).contains(KernelConcept.ROOT_VERTEX.nid()),
                    name + ": the probe's ancestors reach the root");
            assertTrue(descendants.length > 0, name + ": the probe has descendants");
            assertFalse(Arrays.equals(ancestors, descendants),
                    name + ": the probe's ancestors and descendants differ");

            assertArrayEquals(ancestors, sorted(calculator.ancestorsOf(PROBE)),
                    name + ": ancestorsOf(ConceptFacade) returns the ancestors");
        });
    }

    @Test
    @DisplayName("parentEdges(ConceptFacade) answers parentEdges(nid), not childEdges(nid)")
    void parentEdgesFacadeAnswersParentEdges() {
        calculators().forEach((name, calculator) -> {
            long[] parents = destinations(calculator.parentEdges(PROBE.nid()));
            long[] children = destinations(calculator.childEdges(PROBE.nid()));
            assertTrue(parents.length > 0, name + ": the probe has parent edges");
            assertTrue(children.length > 0, name + ": the probe has child edges");
            assertFalse(Arrays.equals(parents, children),
                    name + ": the probe's parent and child edges lead to different concepts");

            assertArrayEquals(parents, destinations(calculator.parentEdges(PROBE)),
                    name + ": parentEdges(ConceptFacade) returns the parent edges");
        });
    }

    /** The calculators whose facade overloads are checked, by name for the failure message. */
    private static Map<String, NavigationCalculator> calculators() {
        Map<String, NavigationCalculator> calculators = new LinkedHashMap<>();
        calculators.put("stated navigation", Calculators.Navigation.stated(
                Coordinates.Stamp.DevelopmentLatestActiveOnly(),
                Lists.immutable.of(Coordinates.Language.UsEnglishRegularName())));
        calculators.put("inferred navigation", Calculators.Navigation.inferred(
                Coordinates.Stamp.DevelopmentLatestActiveOnly(),
                Lists.immutable.of(Coordinates.Language.UsEnglishRegularName())));
        calculators.put("default view", Calculators.View.Default());
        return calculators;
    }

    private static long[] sorted(LongIdSet nids) {
        long[] array = nids.toArray();
        Arrays.sort(array);
        return array;
    }

    private static long[] destinations(ImmutableList<Edge> edges) {
        long[] array = edges.collectLong(Edge::destinationNid).toArray();
        Arrays.sort(array);
        return array;
    }
}
