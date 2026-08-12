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
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.builder.KonceptExtractor;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link KonceptExtractor}'s {@code section:} field (IKE-Network/ike-issues#877), checked
 * against the real starter set: every extracted koncept must carry one, and the grouping
 * it encodes must exactly match an independently invoked
 * {@link TaxonomySectioner#sectionsCoveringFullStore}. This test doesn't call
 * {@code KonceptExtractor}'s own package-private {@code identifier}/{@code label} helpers
 * (it lives in a different module and package) — the cross-check below is purely
 * behavioral: co-membership in a {@link Section} implies the same {@code section:} value,
 * and distinct sections never collide on one.
 */
class KonceptExtractorSectionsIT {

    private static final int SPLIT_THRESHOLD = 60;
    private static final int MAX_DEPTH = 4;
    private static final int RESIDUAL_BATCH_SIZE = 50;

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
    @DisplayName("Every extracted koncept has a section:, and it matches TaxonomySectioner's own grouping")
    void sectionsMatchTaxonomySectioner() {
        String yaml = KonceptExtractor.extractYaml();

        Map<String, UUID> uuidByIdentifier = new HashMap<>();
        Map<String, String> sectionByIdentifier = new HashMap<>();
        parseEntries(yaml, uuidByIdentifier, sectionByIdentifier);
        assertFalse(uuidByIdentifier.isEmpty(), "sanity: the real starter set extracts a non-empty koncept set");

        for (String identifier : uuidByIdentifier.keySet()) {
            assertTrue(sectionByIdentifier.containsKey(identifier),
                    identifier + " is missing a section: field");
        }

        StampCalculator calculator = Calculators.Stamp.DevelopmentLatestActiveOnly();
        // Same graph source KonceptExtractor.sectionsByNid actually uses -- fromStatedAxioms,
        // not fromStatedNavigation, since a freshly authored ledger (no classifier run) has
        // no stated-navigation cache at all. See KonceptExtractor's class javadoc.
        TaxonomySectioner sectioner = TaxonomySectioner.fromStatedAxioms(calculator);
        List<Section> sections = sectioner.sectionsCoveringFullStore(
                TinkarTerm.ROOT_VERTEX.nid(), SPLIT_THRESHOLD, MAX_DEPTH, RESIDUAL_BATCH_SIZE);

        Map<UUID, String> sectionByUuid = new HashMap<>();
        for (Map.Entry<String, String> e : sectionByIdentifier.entrySet()) {
            sectionByUuid.put(uuidByIdentifier.get(e.getKey()), e.getValue());
        }

        // A dual-parented node reached at different depths via different top-level
        // branches can legitimately appear as more than one non-empty Section sharing
        // the same rootNid (TaxonomySectioner's own split-threshold/depth interaction) --
        // harmless for KonceptExtractor, since both map to the same rootNid-derived key
        // anyway. What must never happen is two DIFFERENT root nids producing the same
        // section: value, so the check below groups by value and asserts one root each.
        Set<String> sectionValuesClaimedSoFar = new HashSet<>();
        Map<String, Integer> rootNidByValue = new HashMap<>();
        int nonEmptySectionCount = 0;
        for (Section section : sections) {
            if (section.members().isEmpty()) {
                continue;
            }
            nonEmptySectionCount++;
            Set<String> valuesInThisSection = new HashSet<>();
            for (int memberNid : section.members()) {
                UUID memberUuid = PrimitiveData.publicId(memberNid).asUuidArray()[0];
                String extractedValue = sectionByUuid.get(memberUuid);
                if (extractedValue == null) {
                    continue; // member has no FQN in-store -- naturally excluded, as documented
                }
                valuesInThisSection.add(extractedValue);
            }
            assertEquals(1, valuesInThisSection.size(),
                    "every member of one TaxonomySectioner Section must share one section: value, got "
                            + valuesInThisSection + " for root nid " + section.rootNid());
            String theValue = valuesInThisSection.iterator().next();
            sectionValuesClaimedSoFar.add(theValue);
            Integer priorRootNid = rootNidByValue.putIfAbsent(theValue, section.rootNid());
            if (priorRootNid != null) {
                assertEquals(priorRootNid, section.rootNid(),
                        "section: value \"" + theValue + "\" was produced by two different root nids: "
                                + priorRootNid + " and " + section.rootNid());
            }
        }

        // "Unclassified" is KonceptExtractor's own fallback for a koncept whose nid
        // TaxonomySectioner's residual sweep can't resolve to a materializable entity
        // (see KonceptExtractor#sectionsByNid) -- a legitimate value with no corresponding
        // Section, so every OTHER emitted value must still come from a claimed Section,
        // and every claimed Section's value must actually appear in the extraction.
        Set<String> emittedValues = new HashSet<>(sectionByIdentifier.values());
        emittedValues.remove("Unclassified");
        assertEquals(sectionValuesClaimedSoFar, emittedValues,
                "KonceptExtractor must emit exactly the section: values TaxonomySectioner produced"
                        + " (aside from the Unclassified fallback)");
    }

    private static final Pattern ENTRY_HEADER = Pattern.compile("^(\\w+):$");
    private static final Pattern UUID_LINE = Pattern.compile("^\\s*- ([0-9a-fA-F-]{36})$");
    private static final Pattern SECTION_LINE = Pattern.compile("^ {2}section: \"(.*)\"$");

    /** Parses the flat, hand-emitted YAML for just the fields this test needs. */
    private static void parseEntries(String yaml, Map<String, UUID> uuidByIdentifier,
                                      Map<String, String> sectionByIdentifier) {
        String currentIdentifier = null;
        boolean afterUuidsKey = false;
        for (String line : yaml.split("\n")) {
            Matcher header = ENTRY_HEADER.matcher(line);
            if (header.matches()) {
                currentIdentifier = header.group(1);
                afterUuidsKey = false;
                continue;
            }
            if (currentIdentifier == null) {
                continue;
            }
            Matcher section = SECTION_LINE.matcher(line);
            if (section.matches()) {
                sectionByIdentifier.put(currentIdentifier, section.group(1));
                continue;
            }
            if (line.equals("  uuids:")) {
                afterUuidsKey = true;
                continue;
            }
            if (afterUuidsKey) {
                Matcher uuidLine = UUID_LINE.matcher(line);
                if (uuidLine.matches()) {
                    uuidByIdentifier.putIfAbsent(currentIdentifier, UUID.fromString(uuidLine.group(1)));
                }
                afterUuidsKey = false;
            }
        }
    }
}
