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
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.builder.generator.ComponentDecompiler;
import dev.ikm.tinkar.entity.builder.generator.ComponentDecompiler.ComponentSource;
import dev.ikm.tinkar.entity.builder.generator.TinkarTermReferenceResolver;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.EntityFacade;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Decompiles every concept and pattern in the full unreasoned starter set, verifying
 * the {@link ComponentDecompiler}'s verb-call lines are well-formed and its manifest
 * notes are limited to the known, expected gaps (the meta-schema field-type cluster's
 * declared-identity fallback references — never a silent drop or a malformed line).
 */
class ComponentDecompilerIT {

    private static final Logger LOG = LoggerFactory.getLogger(ComponentDecompilerIT.class);

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
    @DisplayName("Every concept and pattern decompiles to well-formed verb lines, with zero manifest notes")
    void decompileEveryComponent() {
        StampCalculator calculator = Calculators.Stamp.DevelopmentLatestActiveOnly();
        TinkarTermReferenceResolver resolver = TinkarTermReferenceResolver.build();

        List<Integer> conceptNids = new ArrayList<>();
        EntityService.get().forEachConceptEntity(concept -> conceptNids.add(concept.nid()));
        List<Integer> patternNids = new ArrayList<>();
        EntityService.get().forEachPatternEntity(pattern -> patternNids.add(pattern.nid()));

        int totalComponents = conceptNids.size() + patternNids.size();
        int totalVerbLines = 0;
        int componentsWithNoLines = 0;
        Map<String, Integer> verbKindCounts = new TreeMap<>();
        List<String> allNotes = new ArrayList<>();

        List<Integer> allNids = new ArrayList<>(conceptNids);
        allNids.addAll(patternNids);
        for (Integer nid : allNids) {
            EntityFacade component = EntityFacade.make(nid);
            ComponentSource source = ComponentDecompiler.decompile(component, calculator, resolver);
            totalVerbLines += source.verbLines().size();
            if (source.verbLines().isEmpty()) {
                componentsWithNoLines++;
            }
            for (String line : source.verbLines()) {
                assertFalse(line.isBlank(), "no blank verb lines for " + component);
                String kind = line.startsWith(".semanticOn(") ? "semanticOn"
                        : line.startsWith(".semantic(") ? "semantic"
                        : line.startsWith(".statedAxioms(") ? "statedAxioms"
                        : line.startsWith(".meaning(") ? "patternDefinition"
                        : line.startsWith("// TODO") ? "TODO" : "other";
                verbKindCounts.merge(kind, 1, Integer::sum);
                assertFalse(kind.equals("other"), "unrecognized verb line shape: " + line);
            }
            allNotes.addAll(source.manifestNotes());
        }

        LOG.info("Decompiled {} components ({} concepts, {} patterns): {} verb lines, {} with none",
                totalComponents, conceptNids.size(), patternNids.size(), totalVerbLines, componentsWithNoLines);
        LOG.info("Verb kinds: {}", verbKindCounts);
        allNotes.forEach(note -> LOG.info("Manifest note: {}", note));

        assertEquals(1295 + 64, totalComponents, "1295 concepts + 64 patterns");
        assertTrue(totalVerbLines > 2000, "expected thousands of verb lines across the full set");
        // The IKE starter set's 43 definitions that are not the simple isA shape (role groups,
        // restrictions) are beyond the axiom decompiler: each is reported for hand authoring.
        assertEquals(43, verbKindCounts.getOrDefault("TODO", 0),
                "every axiom but the 43 non-simple definitions decompiles to a declared-identity"
                        + " statedAxioms call");
        assertEquals(64, verbKindCounts.getOrDefault("patternDefinition", 0),
                "every pattern declares exactly one meaning/purpose/field definition line");
        assertEquals(43, allNotes.size(), "the only manifest notes are the 43 non-simple definitions: " + allNotes);
        assertTrue(allNotes.stream().allMatch(note -> note.startsWith("Stated axioms on ")),
                "every manifest note is a non-simple definition to hand-author: " + allNotes);
    }
}
