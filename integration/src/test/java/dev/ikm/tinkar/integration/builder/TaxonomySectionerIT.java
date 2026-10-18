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
package dev.ikm.tinkar.integration.builder;


import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.Coordinates;
import dev.ikm.tinkar.coordinate.navigation.calculator.NavigationCalculator;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.builder.generator.TaxonomySectioner;
import dev.ikm.tinkar.entity.builder.generator.TaxonomySectioner.Section;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.KernelConcept;
import network.ike.foundation.ike.bindings.IkeConcept;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression-locks {@link TaxonomySectioner} against the IKE starter set's stated navigation,
 * which only its reasoned file carries: the bucket layout under the platform root, first
 * found by hand in the manual scan (StarterSetSectionSpikeIT, IKE-Network/ike-issues#873).
 *
 * <p>What each section should hold is counted from the store as loaded, through the
 * platform's stated navigation calculator, a reading of the taxonomy independent of the
 * sectioner's own scan of the navigation semantics. The starter set grows as it gains
 * content, and the claim is the layout of whatever it holds, so nothing here is pinned to
 * its size.
 */
class TaxonomySectionerIT {

    private static final Logger LOG = LoggerFactory.getLogger(TaxonomySectionerIT.class);

    @BeforeAll
    static void loadUnreasonedStarterSet() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
    }

    @AfterAll
    static void stop() {
        TestHelper.stopDatabase();
    }

    @Test
    @DisplayName("Depth-1 sections under the platform root")
    void depthOneSectionsMatchManualScan() {
        StampCalculator calculator = Calculators.Stamp.DevelopmentLatestActiveOnly();
        TaxonomySectioner sectioner = TaxonomySectioner.fromStatedNavigation(calculator);
        NavigationCalculator navigation = statedNavigation();

        long platformRoot = KernelConcept.ROOT_VERTEX.nid();
        Set<Long> rootChildren = children(navigation, platformRoot);
        assertEquals(rootChildren, new HashSet<>(sectioner.childrenOf(platformRoot)),
                "the platform root's stated-nav children seed the depth-1 sections");
        assertTrue(rootChildren.contains(IkeConcept.MODEL_CONCEPT.nid()), "Model concept is under the root");

        Map<Long, List<Integer>> expected = new TreeMap<>();
        rootChildren.forEach(child -> expected.computeIfAbsent(child, nid -> new ArrayList<>())
                .add(subtreeSize(navigation, child)));
        Map<Long, List<Integer>> actual = sizes(sectioner.sectionsUnder(platformRoot, Integer.MAX_VALUE, 1));
        LOG.info("Depth-1 sections: {}", named(actual));
        assertEquals(named(expected), named(actual),
                "one section per child of the root, holding the child and every stated-nav descendant");
    }

    @Test
    @DisplayName("Oversized sections split one level deeper")
    void oversizedSectionsSplit() {
        StampCalculator calculator = Calculators.Stamp.DevelopmentLatestActiveOnly();
        TaxonomySectioner sectioner = TaxonomySectioner.fromStatedNavigation(calculator);
        NavigationCalculator navigation = statedNavigation();
        int threshold = 60;
        int maxDepth = 2;

        long platformRoot = KernelConcept.ROOT_VERTEX.nid();
        Map<Long, List<Integer>> expected = new TreeMap<>();
        for (long child : children(navigation, platformRoot)) {
            expectSplit(navigation, child, threshold, maxDepth, 1, expected);
        }
        Map<Long, List<Integer>> actual = sizes(sectioner.sectionsUnder(platformRoot, threshold, maxDepth));
        LOG.info("Split sections (threshold {}): {}", threshold, named(actual));

        // A section larger than the threshold splits into its children's sections, which
        // maxDepth stops from splitting again; one at or under it is not split.
        assertTrue(subtreeSize(navigation, IkeConcept.MODEL_CONCEPT.nid()) > threshold,
                "Model concept is large enough to split, so splitting is exercised");
        assertEquals(named(expected), named(actual));
        // Regression guard: a splitting node must still get its own singleton section
        // (a prior bug dropped the splitting node's identity entirely — "Model
        // concept" would silently vanish from every generated section, taking
        // IkeConcept.MODEL_CONCEPT itself with it).
        assertEquals(List.of(1), actual.get(IkeConcept.MODEL_CONCEPT.nid()),
                "the splitting node itself must appear as its own singleton section");
    }

    // ----- the independent count -----

    private static NavigationCalculator statedNavigation() {
        return Calculators.Navigation.stated(Coordinates.Stamp.DevelopmentLatestActiveOnly(),
                Lists.immutable.of(Coordinates.Language.UsEnglishRegularName()));
    }

    private static Set<Long> children(NavigationCalculator navigation, long nid) {
        Set<Long> children = new HashSet<>();
        for (long child : navigation.unsortedChildrenOf(nid).toArray()) {
            children.add(child);
        }
        return children;
    }

    /** A concept and its stated-nav descendants, each once: what a section rooted there holds. */
    private static int subtreeSize(NavigationCalculator navigation, long nid) {
        Set<Long> seen = new HashSet<>();
        Deque<Long> pending = new ArrayDeque<>(List.of(nid));
        while (!pending.isEmpty()) {
            long next = pending.pop();
            if (seen.add(next)) {
                children(navigation, next).forEach(pending::push);
            }
        }
        return seen.size();
    }

    /**
     * The sections the sectioner's contract gives a concept: its whole subtree when that is
     * at or under the threshold, when splitting has reached maxDepth, or when it has no
     * children; otherwise a singleton section of its own and its children's sections.
     */
    private static void expectSplit(NavigationCalculator navigation, long nid, int threshold, int maxDepth,
                                    int depth, Map<Long, List<Integer>> expected) {
        int size = subtreeSize(navigation, nid);
        Set<Long> children = children(navigation, nid);
        if (size <= threshold || depth >= maxDepth || children.isEmpty()) {
            expected.computeIfAbsent(nid, key -> new ArrayList<>()).add(size);
            return;
        }
        expected.computeIfAbsent(nid, key -> new ArrayList<>()).add(1);
        for (long child : children) {
            expectSplit(navigation, child, threshold, maxDepth, depth + 1, expected);
        }
    }

    private static Map<Long, List<Integer>> sizes(List<Section> sections) {
        Map<Long, List<Integer>> sizes = new TreeMap<>();
        sections.forEach(section -> sizes.computeIfAbsent(section.rootNid(), nid -> new ArrayList<>())
                .add(section.members().size()));
        return sizes;
    }

    /** Sizes by section name, each list sorted, for comparison and for a readable failure. */
    private static Map<String, List<Integer>> named(Map<Long, List<Integer>> sizes) {
        Map<String, List<Integer>> named = new TreeMap<>();
        sizes.forEach((nid, list) -> named.put(PrimitiveData.text(nid) + " " + nid,
                list.stream().sorted().toList()));
        return named;
    }
}
