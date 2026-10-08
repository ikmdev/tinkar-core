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

import network.ike.foundation.ike.bindings.IkeTerms;
import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.builder.generator.TaxonomySectioner;
import dev.ikm.tinkar.entity.builder.generator.TaxonomySectioner.Section;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
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

/**
 * Regression-locks {@link TaxonomySectioner} against the IKE starter set's stated navigation,
 * which only its reasoned file carries: the bucket layout under the platform root, first
 * found by hand in the manual scan (StarterSetSectionSpikeIT, IKE-Network/ike-issues#873).
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

        long platformRoot = KernelTerm.ROOT_VERTEX.nid();
        assertEquals(2, sectioner.childrenOf(platformRoot).size(),
                "the platform root's stated-nav children, Model concept and Phenomenon, seed the depth-1 sections");

        List<Section> sections = sectioner.sectionsUnder(platformRoot, Integer.MAX_VALUE, 1);

        Map<String, Integer> sizes = new TreeMap<>();
        sections.forEach(section -> sizes.put(section.name(), section.members().size()));
        LOG.info("Depth-1 sections: {}", sizes);

        // Sizes pinned from the IKE starter set's stated navigation.
        assertEquals(1289, sizes.get("Model concept"));
        assertEquals(4, sizes.get("Phenomenon"));
        assertEquals(2, sizes.size(), "the platform root has exactly two depth-1 sections");
    }

    @Test
    @DisplayName("Oversized sections split one level deeper")
    void oversizedSectionsSplit() {
        StampCalculator calculator = Calculators.Stamp.DevelopmentLatestActiveOnly();
        TaxonomySectioner sectioner = TaxonomySectioner.fromStatedNavigation(calculator);

        List<Section> sections = sectioner.sectionsUnder(KernelTerm.ROOT_VERTEX.nid(), 60, 2);
        Map<String, Integer> sizes = new TreeMap<>();
        sections.forEach(section -> sizes.merge(section.name(), section.members().size(), Integer::sum));
        LOG.info("Split sections (threshold 60): {}", sizes);

        // "Model concept" splits into its children; the largest exceed the threshold, but
        // maxDepth=2 stops further splitting.
        assertEquals(293, sizes.get("IKE base model concept"));
        assertEquals(298, sizes.get("Expression language model"));
        assertEquals(466, sizes.get("ELM node catalog"));
        // Sections at or under the threshold are not split further.
        assertEquals(5, sizes.get("Author"));
        assertEquals(6, sizes.get("Status"));
        assertEquals(4, sizes.get("Phenomenon"));
        // Regression guard: a splitting node must still get its own singleton section
        // (a prior bug dropped the splitting node's identity entirely — "Model
        // concept" would silently vanish from every generated section, taking
        // IkeTerms.MODEL_CONCEPT itself with it).
        assertEquals(1, sizes.get("Model concept"),
                "the splitting node itself must appear as its own singleton section");
    }
}
