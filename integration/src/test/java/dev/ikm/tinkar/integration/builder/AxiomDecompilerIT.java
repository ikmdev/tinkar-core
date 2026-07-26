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
import dev.ikm.tinkar.entity.builder.generator.AxiomDecompiler;
import dev.ikm.tinkar.entity.builder.generator.AxiomDecompiler.Result;
import dev.ikm.tinkar.entity.builder.generator.TinkarTermReferenceResolver;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.integration.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Decompiles every stated-axiom semantic in the full unreasoned starter set via
 * {@link StampCalculator}-resolved latest-active versions. The manual axiom-shape spike
 * (AxiomShapeSpikeIT, IKE-Network/ike-issues#869) found one outlier using raw
 * {@code versions().getLast()} traversal — "Concept versions field" appeared
 * role-based, needing hand authoring. This IT disproves that: the concept's
 * stated-axiom semantic carries four real ACTIVE versions, and {@code getLast()}
 * (list-position, not time) grabbed a superseded one; the calculator-resolved true
 * latest is a simple isA, like every other stated-axiom semantic in the set. All 379
 * decompile via the isA fast path — the not-simple fallback remains in
 * {@link AxiomDecompiler} for other starter sets, exercised here by none.
 */
class AxiomDecompilerIT {

    private static final Logger LOG = LoggerFactory.getLogger(AxiomDecompilerIT.class);

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
    @DisplayName("Every stated-axiom semantic's true latest state decompiles to isA")
    void decompileEveryStatedAxiom() {
        StampCalculator calculator = Calculators.Stamp.DevelopmentLatestActiveOnly();
        TinkarTermReferenceResolver resolver = TinkarTermReferenceResolver.build();

        int[] simpleIsACount = {0};
        int[] notSimpleCount = {0};
        int[] totalParents = {0};
        int[] fallbackParents = {0};
        boolean[] sawFormerOutlier = {false};
        List<String> notSimpleDumps = new ArrayList<>();
        List<String> fallbackExamples = new ArrayList<>();

        calculator.forEachSemanticVersionOfPattern(TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN,
                (semanticVersion, patternVersion) -> {
                    DiTreeEntity tree = (DiTreeEntity) semanticVersion.fieldValues().get(0);
                    Result result = AxiomDecompiler.decompile(tree);
                    if ("Concept versions field".equals(
                            dev.ikm.tinkar.common.service.PrimitiveData.text(semanticVersion.referencedComponentNid()))) {
                        sawFormerOutlier[0] = true;
                        assertTrue(result.simpleIsA(),
                                "'Concept versions field' — the manual spike's former outlier — must decompile"
                                        + " to isA via the calculator-resolved true latest version");
                    }
                    if (result.simpleIsA()) {
                        simpleIsACount[0]++;
                        totalParents[0] += result.parents().size();
                        assertFalse(result.parents().isEmpty(), "a simple-isA result always carries at least one parent");
                        for (ConceptFacade parent : result.parents()) {
                            TinkarTermReferenceResolver.Resolved resolved = resolver.resolve(parent);
                            // Every emitted source expression must at least be well-formed
                            // Java — both forms are exercised end-to-end by the section
                            // emitter's own compile-and-load IT (#869 verification step).
                            assertTrue(resolved.sourceExpression().startsWith("TinkarTerm.")
                                            || resolved.sourceExpression().startsWith("EntityProxy.Concept.make("),
                                    "unexpected source expression form: " + resolved.sourceExpression());
                            if (!resolved.isTinkarTermConstant()) {
                                fallbackParents[0]++;
                                if (fallbackExamples.size() < 10) {
                                    fallbackExamples.add(parent.description() + " -> " + resolved.sourceExpression());
                                }
                            }
                        }
                    } else {
                        notSimpleCount[0]++;
                        notSimpleDumps.add(result.diagnosticDump());
                    }
                });

        LOG.info("Decompiled: {} simple isA ({} total parents, {} via declared-identity fallback),"
                        + " {} needing hand authoring",
                simpleIsACount[0], totalParents[0], fallbackParents[0], notSimpleCount[0]);
        fallbackExamples.forEach(example -> LOG.info("Fallback parent: {}", example));
        notSimpleDumps.forEach(dump -> LOG.info("Needs hand authoring:\n{}", dump));

        assertEquals(379, simpleIsACount[0]);
        assertEquals(0, notSimpleCount[0]);
        assertTrue(sawFormerOutlier[0], "expected to encounter 'Concept versions field' in the full scan");
        assertTrue(fallbackParents[0] > 0,
                "expected the declared-identity fallback to actually be exercised by the meta-schema"
                        + " field-type cluster (Concept field, Component field, etc.) — a resolver"
                        + " regression that stopped fallback emission entirely would otherwise pass silently");
    }
}
