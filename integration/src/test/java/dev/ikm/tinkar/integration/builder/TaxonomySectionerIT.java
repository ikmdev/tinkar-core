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

import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.builder.generator.TaxonomySectioner;
import dev.ikm.tinkar.entity.builder.generator.TaxonomySectioner.Section;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression-locks {@link TaxonomySectioner} against the full unreasoned starter set —
 * reproduces the bucket layout the manual scan (StarterSetSectionSpikeIT,
 * IKE-Network/ike-issues#873) found by hand, proving the extracted utility is
 * structurally equivalent, not just independently plausible.
 */
class TaxonomySectionerIT {

    private static final Logger LOG = LoggerFactory.getLogger(TaxonomySectionerIT.class);

    @BeforeAll
    static void loadUnreasonedStarterSet() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA);
    }

    @AfterAll
    static void stop() {
        TestHelper.stopDatabase();
    }

    @Test
    @DisplayName("Depth-1 sections under the platform root match the #873 manual scan")
    void depthOneSectionsMatchManualScan() {
        StampCalculator calculator = Calculators.Stamp.DevelopmentLatestActiveOnly();
        TaxonomySectioner sectioner = TaxonomySectioner.fromStatedNavigation(calculator);

        int platformRoot = TinkarTerm.ROOT_VERTEX.nid();
        assertTrue(sectioner.childrenOf(platformRoot).size() >= 9,
                "the platform root's stated-nav children seed the depth-1 sections");

        List<Section> sections = sectioner.sectionsUnder(platformRoot, Integer.MAX_VALUE, 1);

        Map<String, Integer> sizes = new TreeMap<>();
        sections.forEach(section -> sizes.put(section.name(), section.members().size()));
        LOG.info("Depth-1 sections: {}", sizes);

        // Sizes pinned from the #873 manual scan report (StarterSetSectionSpikeIT), with
        // one deliberate correction: the manual scan read stated-navigation via
        // versions().getLast() — list-position "latest", which the store does not
        // guarantee once a chronology has merged (see reference_stamp_calculator_
        // latest_state). This coordinate-based traversal recovers two module concepts
        // ("Sandbox module", "Sandbox Path module") the manual scan's getLast() missed
        // by resolving a stale stated-navigation version — 7 is the correct count,
        // verified directly against the store (all seven are genuine module concepts).
        assertEquals(3, sizes.get("Annotation type"));
        assertEquals(11, sizes.get("Author"));
        assertEquals(1, sizes.get("Creative Commons BY license"));
        assertEquals(194, sizes.get("Model concept"));
        assertEquals(7, sizes.get("Module"));
        assertEquals(89, sizes.get("Object"));
        assertEquals(5, sizes.get("Path"));
        assertEquals(4, sizes.get("Phenomenon"));
        assertEquals(6, sizes.get("Status"));
        assertEquals(9, sizes.size(), "the platform root has exactly nine depth-1 sections");
    }

    @Test
    @DisplayName("Oversized sections split one level deeper, matching the #873 breakdown")
    void oversizedSectionsSplit() {
        StampCalculator calculator = Calculators.Stamp.DevelopmentLatestActiveOnly();
        TaxonomySectioner sectioner = TaxonomySectioner.fromStatedNavigation(calculator);

        List<Section> sections = sectioner.sectionsUnder(TinkarTerm.ROOT_VERTEX.nid(), 60, 2);
        Map<String, Integer> sizes = new TreeMap<>();
        sections.forEach(section -> sizes.merge(section.name(), section.members().size(), Integer::sum));
        LOG.info("Split sections (threshold 60): {}", sizes);

        // "Model concept" (194) splits into its one child, "Tinkar Model concept" (193),
        // which itself exceeds the threshold but maxDepth=2 stops further splitting.
        assertEquals(193, sizes.get("Tinkar Model concept"));
        // "Object" (89) splits into its children; "Object properties" (79) is the
        // dominant one, matching the manual scan's breakdown.
        assertEquals(79, sizes.get("Object properties"));
        assertEquals(1, sizes.get("Any component"));
        // Sections at or under the threshold are not split further.
        assertEquals(11, sizes.get("Author"));
        assertEquals(6, sizes.get("Status"));
        // Regression guard: a splitting node must still get its own singleton section
        // (a prior bug dropped the splitting node's identity entirely — "Model
        // concept" and "Object" would silently vanish from every generated section,
        // taking TinkarTerm.MODEL_CONCEPT itself with them).
        assertEquals(1, sizes.get("Model concept"),
                "the splitting node itself must appear as its own singleton section");
        assertEquals(1, sizes.get("Object"),
                "the splitting node itself must appear as its own singleton section");
    }
}
