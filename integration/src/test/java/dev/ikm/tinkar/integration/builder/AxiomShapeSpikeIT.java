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
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiomSemantic;
import dev.ikm.tinkar.integration.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The axiom-shape spike (IKE-Network/ike-issues#869): classifies every stated-axiom
 * DiTree in the full unreasoned starter set by shape, so the axiom decompiler (source
 * generator → builder-DSL) is scoped to what the artifact actually contains rather than
 * a general EL++ printer built ahead of evidence. Exploratory by design: the report is
 * the deliverable, and the assertions pin only the shape distribution's existence.
 */
class AxiomShapeSpikeIT {

    private static final Logger LOG = LoggerFactory.getLogger(AxiomShapeSpikeIT.class);

    private static final Map<String, Integer> SHAPES = new TreeMap<>();
    private static final List<String> NON_SIMPLE_EXAMPLES = new ArrayList<>();

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
    @DisplayName("Every stated-axiom DiTree in the full starter set classified by shape")
    void classifyAxiomShapes() {
        List<Integer> semanticNids = new ArrayList<>();
        PrimitiveData.get().forEachSemanticNid(semanticNids::add);

        int total = 0;
        for (Integer semanticNid : semanticNids) {
            SemanticEntity<SemanticEntityVersion> semantic = EntityHandle.get(semanticNid).expectSemantic();
            if (semantic.patternNid() != TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid()) {
                continue;
            }
            total++;
            SemanticEntityVersion latest = semantic.versions().getLast();
            DiTreeEntity tree = (DiTreeEntity) latest.fieldValues().get(0);
            String shape = classify(tree);
            SHAPES.merge(shape, 1, Integer::sum);
            if (!shape.equals("simple isA (NecessarySet(And(ConceptAxiom*)))")
                    && !shape.equals("simple isA, single parent (NecessarySet(ConceptAxiom))")
                    && NON_SIMPLE_EXAMPLES.size() < 15) {
                NON_SIMPLE_EXAMPLES.add(shape + " — component "
                        + PrimitiveData.text(semantic.referencedComponentNid()));
            }
        }

        StringBuilder report = new StringBuilder("\n═══ Axiom shape spike report (#869) ═══\n");
        report.append("Total stated-axiom semantics: ").append(total).append('\n');
        SHAPES.forEach((shape, count) -> report.append(String.format("  %-70s %5d%n", shape, count)));
        report.append("Non-simple examples (up to 15):\n");
        NON_SIMPLE_EXAMPLES.forEach(example -> report.append("  ").append(example).append('\n'));
        LOG.info(report.toString());

        assertFalse(SHAPES.isEmpty(), "the spike exists to find shapes — none found means it looked away");
        assertTrue(total > 300, "expected roughly one stated-axiom semantic per concept");
    }

    /** Classifies one stated-axiom tree's top-level shape from its root's children. */
    private static String classify(DiTreeEntity tree) {
        EntityVertex root = tree.root();
        if (meaningOf(root) != LogicalAxiomSemantic.DEFINITION_ROOT) {
            return "unexpected root meaning: " + PrimitiveData.text(root.getMeaningNid());
        }
        ImmutableIntList topSets = tree.successors(root.vertexIndex());
        if (topSets.size() != 1) {
            return "multiple top-level sets (" + topSets.size() + ")";
        }
        EntityVertex setVertex = vertexAt(tree, topSets.get(0));
        LogicalAxiomSemantic setMeaning = meaningOf(setVertex);
        if (setMeaning != LogicalAxiomSemantic.NECESSARY_SET && setMeaning != LogicalAxiomSemantic.SUFFICIENT_SET) {
            return "top-level set is " + setMeaning;
        }
        String setKind = setMeaning == LogicalAxiomSemantic.SUFFICIENT_SET ? "SufficientSet" : "NecessarySet";
        ImmutableIntList setChildren = tree.successors(setVertex.vertexIndex());
        if (setChildren.size() == 1 && meaningOf(vertexAt(tree, setChildren.get(0))) == LogicalAxiomSemantic.CONCEPT) {
            return setKind.equals("NecessarySet")
                    ? "simple isA, single parent (NecessarySet(ConceptAxiom))"
                    : "single-parent " + setKind + "(ConceptAxiom) — not isA shape";
        }
        if (setChildren.size() != 1) {
            return setKind + " with " + setChildren.size() + " direct children (expected one And)";
        }
        EntityVertex connective = vertexAt(tree, setChildren.get(0));
        LogicalAxiomSemantic connectiveMeaning = meaningOf(connective);
        if (connectiveMeaning != LogicalAxiomSemantic.AND) {
            return setKind + "(" + connectiveMeaning + ") — not an And";
        }
        ImmutableIntList andChildren = tree.successors(connective.vertexIndex());
        boolean allConcepts = andChildren.allSatisfy(index -> meaningOf(vertexAt(tree, index)) == LogicalAxiomSemantic.CONCEPT
                && tree.successors(index).isEmpty());
        if (allConcepts && setKind.equals("NecessarySet")) {
            return "simple isA (NecessarySet(And(ConceptAxiom*)))";
        }
        if (allConcepts) {
            return "SufficientSet(And(ConceptAxiom*)) — not isA shape";
        }
        Map<LogicalAxiomSemantic, Integer> childKinds = new TreeMap<>();
        for (int index = 0; index < andChildren.size(); index++) {
            LogicalAxiomSemantic kind = meaningOf(vertexAt(tree, andChildren.get(index)));
            childKinds.merge(kind, 1, Integer::sum);
        }
        return setKind + "(And(mixed: " + childKinds + "))";
    }

    private static EntityVertex vertexAt(DiTreeEntity tree, int vertexIndex) {
        return tree.vertex(vertexIndex);
    }

    private static LogicalAxiomSemantic meaningOf(EntityVertex vertex) {
        try {
            return LogicalAxiomSemantic.get(vertex.getMeaningNid());
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
