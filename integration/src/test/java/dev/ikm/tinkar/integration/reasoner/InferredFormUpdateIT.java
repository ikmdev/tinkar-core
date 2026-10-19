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
package dev.ikm.tinkar.integration.reasoner;

import dev.ikm.tinkar.common.service.PluggableService;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.view.ViewCoordinateRecord;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculatorWithCache;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.DiTreeIsomorphism;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpressionBuilder;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.reasoner.service.ClassifierResults;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom;
import dev.ikm.tinkar.reasoner.service.ReasonerService;
import dev.ikm.tinkar.terms.ComponentWithNid;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import dev.ikm.tinkar.terms.State;
import network.ike.foundation.ike.bindings.IkeConcept;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reclassifying writes a concept's inferred axioms when, and only when, its necessary normal form
 * changed. The starter set is classified; classified again with nothing changed, which must write
 * no inferred axioms; then a concept is stated to have a second parent, so that its normal form
 * only adds to its inferred form, and classified again, which must write that concept's form;
 * then its parent is stated to have a role, which the concept inherits, and classified again,
 * which must write the concept's form with the inherited role. The writer's earlier decision
 * found such forms "the same" and wrote nothing (IKE-Network/ike-issues#1213).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InferredFormUpdateIT {

    /** The concept given a second parent, and its parents before and after. */
    private static final ConceptFacade EDITED = KernelConcept.ENGLISH_LANGUAGE;
    private static final ConceptFacade PARENT = KernelConcept.LANGUAGE;
    private static final ConceptFacade ADDED_PARENT = KernelConcept.DESCRIPTION_TYPE;

    @BeforeAll
    void startStore() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA);
    }

    @AfterAll
    void stopStore() {
        TestHelper.stopDatabase();
    }

    @Test
    @Order(1)
    void classifyingAgainWithNothingChangedWritesNoInferredAxioms() throws Exception {
        ClassifierResults first = classify().results();
        assertFalse(first.getConceptsWithInferredChanges().isEmpty(), "the first classification writes inferred axioms");

        ClassifierResults again = classify().results();
        assertEquals(0, again.getConceptsWithInferredChanges().size(),
                "inferred axioms written though nothing changed");
    }

    @Test
    @Order(2)
    void aNormalFormThatOnlyAddsAParentIsWritten() throws Exception {
        ViewCalculator view = Calculators.View.Default();
        DiTreeEntity before = latestTree(view, EDITED.nid(), inferredPattern(view)).orElseThrow();
        assertTrue(DiTreeIsomorphism.isomorphic(before, parents(PARENT)),
                "precondition: " + EDITED + " is inferred a kind of " + PARENT + " only");

        stateDefinition(view, EDITED, parents(PARENT, ADDED_PARENT));
        Classified classified = classify();

        assertTrue(classified.results().getConceptsWithInferredChanges().contains(EDITED.nid()),
                "the edited concept's inferred axioms were written");
        ViewCalculator after = ViewCalculatorWithCache.getCalculator(classified.results().getViewCoordinate());
        DiTreeEntity inferred = latestTree(after, EDITED.nid(), inferredPattern(after)).orElseThrow();
        DiTreeEntity normalForm = (DiTreeEntity) classified.reasoner().getNecessaryNormalForm(EDITED.nid()).sourceGraph();
        assertTrue(DiTreeIsomorphism.isomorphic(inferred, normalForm), "the latest inferred form is the normal form");
        assertTrue(DiTreeIsomorphism.isomorphic(inferred, parents(PARENT, ADDED_PARENT)),
                "the inferred form has both parents: " + inferred);
    }

    /** The case IKE-Network/ike-issues#1213 reports: a descendant's inferred form lacked a role its parent gained. */
    @Test
    @Order(3)
    void aRoleTheParentGainsIsWrittenToTheDescendant() throws Exception {
        ViewCalculator view = Calculators.View.Default();
        DiTreeEntity parentStated = latestTree(view, PARENT.nid(), statedPattern(view)).orElseThrow();
        List<Long> parentsOfParent = conceptReferences(parentStated);
        assertEquals(3 + parentsOfParent.size(), parentStated.vertexCount(),
                "precondition: " + PARENT + " is stated with parents only: " + parentStated);
        DiTreeEntity before = latestTree(view, EDITED.nid(), inferredPattern(view)).orElseThrow();
        assertFalse(references(before, IkeConcept.PART_OF.nid()), "precondition: no Part of role yet: " + before);

        LogicalExpressionBuilder builder = new LogicalExpressionBuilder();
        List<LogicalAxiom.Atom> atoms = new ArrayList<>();
        parentsOfParent.forEach(nid -> atoms.add(builder.ConceptAxiom(nid)));
        atoms.add(builder.SomeRole(IkeConcept.PART_OF, builder.ConceptAxiom(KernelConcept.PHENOMENON)));
        builder.NecessarySet(builder.And(atoms.toArray(LogicalAxiom.Atom[]::new)));
        stateDefinition(view, PARENT, (DiTreeEntity) builder.build().sourceGraph());
        Classified classified = classify();

        assertTrue(classified.results().getConceptsWithInferredChanges().contains(EDITED.nid()),
                "the descendant's inferred axioms were written");
        ViewCalculator after = ViewCalculatorWithCache.getCalculator(classified.results().getViewCoordinate());
        DiTreeEntity inferred = latestTree(after, EDITED.nid(), inferredPattern(after)).orElseThrow();
        DiTreeEntity normalForm = (DiTreeEntity) classified.reasoner().getNecessaryNormalForm(EDITED.nid()).sourceGraph();
        assertTrue(DiTreeIsomorphism.isomorphic(inferred, normalForm), "the latest inferred form is the normal form");
        assertTrue(references(inferred, IkeConcept.PART_OF.nid()), "the inherited role is in the inferred form: " + inferred);
    }

    /** The concepts a definition's concept references name. */
    private static List<Long> conceptReferences(DiTreeEntity tree) {
        List<Long> nids = new ArrayList<>();
        for (EntityVertex vertex : tree.vertexMap()) {
            if (vertex.properties().containsKey(KernelConcept.CONCEPT_REFERENCE.nid())
                    && vertex.properties().get(KernelConcept.CONCEPT_REFERENCE.nid()) instanceof ComponentWithNid concept) {
                nids.add(concept.nid());
            }
        }
        return nids;
    }

    /** Whether any vertex of the tree has a property whose value is the component. */
    private static boolean references(DiTreeEntity tree, long nid) {
        for (EntityVertex vertex : tree.vertexMap()) {
            for (Object value : vertex.properties().values()) {
                if (value instanceof ComponentWithNid component && component.nid() == nid) {
                    return true;
                }
            }
        }
        return false;
    }

    private record Classified(ReasonerService reasoner, ClassifierResults results) {
    }

    private static Classified classify() throws Exception {
        ReasonerService reasoner = PluggableService.load(ReasonerService.class).stream()
                .map(ServiceLoader.Provider::get).findFirst().orElseThrow();
        reasoner.init(Calculators.View.Default(), KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN,
                KernelPattern.EL_PLUS_PLUS_INFERRED_AXIOMS_PATTERN);
        reasoner.extractData(quiet());
        reasoner.loadData(quiet());
        reasoner.computeInferences();
        reasoner.buildNecessaryNormalForm();
        return new Classified(reasoner, reasoner.writeInferredResults());
    }

    private static TrackingCallable<Object> quiet() {
        return new TrackingCallable<>() {
            @Override
            protected Object compute() {
                return null;
            }
        };
    }

    /** A definition with the parents as its necessary set and nothing else. */
    private static DiTreeEntity parents(ConceptFacade... parents) {
        LogicalExpressionBuilder builder = new LogicalExpressionBuilder();
        builder.NecessarySet(builder.And(java.util.Arrays.stream(parents)
                .map(builder::ConceptAxiom).toArray(dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom.Atom[]::new)));
        return (DiTreeEntity) builder.build().sourceGraph();
    }

    /** Writes a new version of a concept's stated axioms. */
    private static void stateDefinition(ViewCalculator view, ConceptFacade concept, DiTreeEntity definition) {
        ViewCoordinateRecord coordinate = view.viewCoordinateRecord();
        SemanticEntity<?> stated = EntityService.get().semanticsForComponentOfPattern(concept.nid(), statedPattern(view))
                .findFirst().orElseThrow();
        Transaction transaction = Transaction.make("Restate " + concept);
        StampEntity<?> stamp = transaction.getStamp(State.ACTIVE, coordinate.getAuthorNidForChanges(),
                coordinate.getDefaultModuleNid(), coordinate.getDefaultPathNid());
        SemanticRecord updated = view.stampCalculator().updateFields(stated.nid(),
                Lists.immutable.of(definition), stamp.nid());
        transaction.addComponent(updated);
        EntityService.get().putEntity(updated);
        transaction.commit();
    }

    private static long statedPattern(ViewCalculator view) {
        return view.viewCoordinateRecord().logicCoordinate().statedAxiomsPatternNid();
    }

    private static long inferredPattern(ViewCalculator view) {
        return view.viewCoordinateRecord().logicCoordinate().inferredAxiomsPatternNid();
    }

    @SuppressWarnings("unchecked")
    private static java.util.Optional<DiTreeEntity> latestTree(ViewCalculator view, long conceptNid, long patternNid) {
        return EntityService.get().semanticsForComponentOfPattern(conceptNid, patternNid).findFirst()
                .flatMap(semantic -> {
                    Latest<SemanticEntityVersion> latest = view.latest((SemanticEntity<SemanticEntityVersion>) semantic);
                    return latest.isPresent()
                            ? java.util.Optional.of((DiTreeEntity) latest.get().fieldValues().get(0))
                            : java.util.Optional.empty();
                });
    }
}
