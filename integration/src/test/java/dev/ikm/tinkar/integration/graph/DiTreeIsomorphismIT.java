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
package dev.ikm.tinkar.integration.graph;

import dev.ikm.tinkar.common.util.time.MultipleEndpointTimer;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.DiTreeIsomorphism;
import dev.ikm.tinkar.entity.graph.isomorphic.IsomorphicResults;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.graph.RandomDefinitions.Definition;
import dev.ikm.tinkar.integration.graph.RandomDefinitions.Feature;
import dev.ikm.tinkar.integration.graph.RandomDefinitions.Group;
import dev.ikm.tinkar.integration.graph.RandomDefinitions.Ref;
import dev.ikm.tinkar.integration.graph.RandomDefinitions.Role;
import dev.ikm.tinkar.integration.graph.RandomDefinitions.Set;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.terms.ComponentWithNid;
import dev.ikm.tinkar.terms.KernelConcept;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Objects;
import java.util.Random;

import static dev.ikm.tinkar.integration.graph.RandomDefinitions.asProxies;
import static dev.ikm.tinkar.integration.graph.RandomDefinitions.build;
import static dev.ikm.tinkar.integration.graph.RandomDefinitions.mutate;
import static dev.ikm.tinkar.integration.graph.RandomDefinitions.parentsOnly;
import static dev.ikm.tinkar.integration.graph.RandomDefinitions.randomDefinition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link DiTreeIsomorphism} against the definition of tree isomorphism, and against the decision
 * the reasoner's writer made before it: whether a concept's newly computed necessary normal form
 * is the same as its latest inferred form, which decides whether a new inferred version is written.
 *
 * <p>Expressions are built from the small model of {@link RandomDefinitions}. The
 * oracle is a brute-force search that tries every pairing of children; it is slow and plainly
 * right.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DiTreeIsomorphismIT {

    private static final long SEED = 20261016L;
    private static final int RANDOM_CASES = 4_000;

    private final MultipleEndpointTimer<IsomorphicResults.EndPoints> timer =
            new MultipleEndpointTimer<>(IsomorphicResults.EndPoints.class);

    @BeforeAll
    void startStore() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
    }

    @AfterAll
    void stopStore() {
        TestHelper.stopDatabase();
    }

    // ----- the decision before: the writer's two isomorphic analyses -----

    /** Whether the writer, before, found the new form the same as the latest: write when false. */
    private boolean previousDecisionSame(DiTreeEntity latest, DiTreeEntity normalForm) {
        DiTreeEntity correlated = latest.makeCorrelatedTree(normalForm, KernelConcept.LANGUAGE.nid(), timer.startNew());
        return correlated.equals(latest);
    }

    // ----- the oracle -----

    /** The same meaning and properties, a component value the same as any other form of it. */
    private static boolean oracleSameVertex(EntityVertex a, EntityVertex b) {
        if (a.getMeaningNid() != b.getMeaningNid() || a.properties().size() != b.properties().size()) {
            return false;
        }
        for (long key : a.properties().keySet().toArray()) {
            if (!b.properties().containsKey(key)) {
                return false;
            }
            Object valueA = a.properties().get(key);
            Object valueB = b.properties().get(key);
            boolean same = valueA instanceof ComponentWithNid componentA && valueB instanceof ComponentWithNid componentB
                    ? componentA.nid() == componentB.nid()
                    : Objects.equals(valueA, valueB);
            if (!same) {
                return false;
            }
        }
        return true;
    }

    /** Isomorphism by brute force: every pairing of children is tried. */
    private static boolean oracleIsomorphic(DiTreeEntity a, int va, DiTreeEntity b, int vb) {
        return oracleSameVertex(a.vertex(va), b.vertex(vb))
                && a.successors(va).size() == b.successors(vb).size()
                && pairChildren(a, a.successors(va), b, b.successors(vb), 0, new boolean[b.successors(vb).size()], true);
    }

    /** Whether a embeds into b root to root: equivalent vertices, edges kept, children mapped one to one. */
    private static boolean oracleEmbeds(DiTreeEntity a, int va, DiTreeEntity b, int vb) {
        return oracleSameVertex(a.vertex(va), b.vertex(vb))
                && a.successors(va).size() <= b.successors(vb).size()
                && pairChildren(a, a.successors(va), b, b.successors(vb), 0, new boolean[b.successors(vb).size()], false);
    }

    private static boolean pairChildren(DiTreeEntity a, ImmutableIntList childrenA, DiTreeEntity b,
                                        ImmutableIntList childrenB, int next, boolean[] used, boolean isomorphic) {
        if (next == childrenA.size()) {
            return true;
        }
        for (int j = 0; j < childrenB.size(); j++) {
            if (!used[j]) {
                boolean pairs = isomorphic
                        ? oracleIsomorphic(a, childrenA.get(next), b, childrenB.get(j))
                        : oracleEmbeds(a, childrenA.get(next), b, childrenB.get(j));
                if (pairs) {
                    used[j] = true;
                    if (pairChildren(a, childrenA, b, childrenB, next + 1, used, isomorphic)) {
                        return true;
                    }
                    used[j] = false;
                }
            }
        }
        return false;
    }

    /**
     * Whether every vertex of a maps to an equivalent vertex of b, root to root and edges kept,
     * two vertices of a allowed to map to one of b: what the isomorphic analysis finds when it
     * reports every reference vertex matched, since it never checks that a comparison vertex is
     * not matched twice.
     */
    private static boolean oracleMapsInto(DiTreeEntity a, int va, DiTreeEntity b, int vb) {
        if (!oracleSameVertex(a.vertex(va), b.vertex(vb))) {
            return false;
        }
        for (int childA : a.successors(va).toArray()) {
            boolean mapped = false;
            for (int childB : b.successors(vb).toArray()) {
                if (oracleMapsInto(a, childA, b, childB)) {
                    mapped = true;
                    break;
                }
            }
            if (!mapped) {
                return false;
            }
        }
        return true;
    }

    private static boolean oracleMapsInto(DiTreeEntity a, DiTreeEntity b) {
        return oracleMapsInto(a, a.root().vertexIndex(), b, b.root().vertexIndex());
    }

    private static boolean oracleIsomorphic(DiTreeEntity a, DiTreeEntity b) {
        return oracleIsomorphic(a, a.root().vertexIndex(), b, b.root().vertexIndex());
    }

    private static boolean oracleEmbeds(DiTreeEntity a, DiTreeEntity b) {
        return oracleEmbeds(a, a.root().vertexIndex(), b, b.root().vertexIndex());
    }

    // ----- hand-picked cases -----

    @Test
    void theSameDefinitionBuiltTwiceIsTheSameTree() {
        Definition definition = new Definition(List.of(
                new Set(true, List.of(new Ref(KernelConcept.LANGUAGE),
                        new Group(List.of(new Role(KernelConcept.EXISTENTIAL_RESTRICTION, KernelConcept.ROLE_TYPE, KernelConcept.PHENOMENON)))))));
        DiTreeEntity one = build(definition, null);
        DiTreeEntity two = build(definition, null);
        assertTrue(DiTreeIsomorphism.isomorphic(one, two));
        assertEquals(DiTreeIsomorphism.hash(one), DiTreeIsomorphism.hash(two));
    }

    @Test
    void childrenInAnotherOrderAreTheSameTree() {
        Definition definition = parentsOnly(KernelConcept.LANGUAGE, KernelConcept.DESCRIPTION_TYPE, KernelConcept.PHENOMENON);
        DiTreeEntity one = build(definition, null);
        DiTreeEntity shuffled = build(definition, new Random(7));
        assertTrue(DiTreeIsomorphism.isomorphic(one, shuffled));
        assertEquals(DiTreeIsomorphism.hash(one), DiTreeIsomorphism.hash(shuffled));
    }

    /**
     * The reasoner builds its normal form with binding enums; a form read back from the store
     * holds proxies. They are the same tree. {@link EntityVertex#equivalent} disagrees with itself
     * across the two, which is why the comparison does not rest on it.
     */
    @Test
    void enumsAndProxiesOfOneConceptAreTheSameTree() {
        Definition definition = new Definition(List.of(new Set(true, List.of(new Ref(KernelConcept.LANGUAGE),
                new Role(KernelConcept.EXISTENTIAL_RESTRICTION, KernelConcept.ROLE_TYPE, KernelConcept.PHENOMENON)))));
        DiTreeEntity enums = build(definition, null);
        DiTreeEntity proxies = build(asProxies(definition), null);
        assertTrue(DiTreeIsomorphism.isomorphic(enums, proxies));
        assertTrue(DiTreeIsomorphism.isomorphic(proxies, enums));
        assertEquals(DiTreeIsomorphism.hash(enums), DiTreeIsomorphism.hash(proxies));

        EntityVertex roleWithEnums = enums.vertexMap().detect(v -> v.properties().size() == 2);
        EntityVertex roleWithProxies = proxies.vertexMap().detect(v -> v.properties().size() == 2);
        assertTrue(roleWithProxies.equivalent(roleWithEnums), "a proxy equals an enum of its concept");
        assertFalse(roleWithEnums.equivalent(roleWithProxies), "an enum equals only itself");
    }

    @Test
    void anotherParentIsAnotherTree() {
        assertFalse(DiTreeIsomorphism.isomorphic(build(parentsOnly(KernelConcept.LANGUAGE), null),
                build(parentsOnly(KernelConcept.PHENOMENON), null)));
    }

    @Test
    void anotherRoleOperatorIsAnotherTree() {
        Definition existential = new Definition(List.of(new Set(true, List.of(
                new Role(KernelConcept.EXISTENTIAL_RESTRICTION, KernelConcept.ROLE_TYPE, KernelConcept.LANGUAGE)))));
        Definition universal = new Definition(List.of(new Set(true, List.of(
                new Role(KernelConcept.UNIVERSAL_RESTRICTION, KernelConcept.ROLE_TYPE, KernelConcept.LANGUAGE)))));
        assertFalse(DiTreeIsomorphism.isomorphic(build(existential, null), build(universal, null)));
    }

    @Test
    void anotherLiteralIsAnotherTree() {
        Definition one = new Definition(List.of(new Set(true, List.of(new Feature(KernelConcept.FEATURE_TYPE, 100)))));
        Definition two = new Definition(List.of(new Set(true, List.of(new Feature(KernelConcept.FEATURE_TYPE, 200)))));
        assertFalse(DiTreeIsomorphism.isomorphic(build(one, null), build(two, null)));
    }

    @Test
    void aRoleMovedToAnotherGroupIsAnotherTree() {
        Role a = new Role(KernelConcept.EXISTENTIAL_RESTRICTION, KernelConcept.ROLE_TYPE, KernelConcept.LANGUAGE);
        Role b = new Role(KernelConcept.EXISTENTIAL_RESTRICTION, KernelConcept.ROLE_TYPE, KernelConcept.PHENOMENON);
        Definition together = new Definition(List.of(new Set(true, List.of(new Group(List.of(a, b)), new Group(List.of(a))))));
        Definition apart = new Definition(List.of(new Set(true, List.of(new Group(List.of(a)), new Group(List.of(a, b))))));
        Definition moved = new Definition(List.of(new Set(true, List.of(new Group(List.of(a, a)), new Group(List.of(b))))));
        assertTrue(DiTreeIsomorphism.isomorphic(build(together, null), build(apart, null)));
        assertFalse(DiTreeIsomorphism.isomorphic(build(together, null), build(moved, null)));
    }

    @Test
    void identicalSiblingsCountAsTwo() {
        Role a = new Role(KernelConcept.EXISTENTIAL_RESTRICTION, KernelConcept.ROLE_TYPE, KernelConcept.LANGUAGE);
        Definition once = new Definition(List.of(new Set(true, List.of(new Group(List.of(a)), new Ref(KernelConcept.PHENOMENON)))));
        Definition twice = new Definition(List.of(new Set(true, List.of(new Group(List.of(a)), new Group(List.of(a)),
                new Ref(KernelConcept.PHENOMENON)))));
        assertFalse(DiTreeIsomorphism.isomorphic(build(once, null), build(twice, null)));
        assertTrue(DiTreeIsomorphism.isomorphic(build(twice, null), build(twice, new Random(3))));
    }

    @Test
    void aSupersetIsAnotherTreeEitherWay() {
        DiTreeEntity one = build(parentsOnly(KernelConcept.LANGUAGE), null);
        DiTreeEntity more = build(parentsOnly(KernelConcept.LANGUAGE, KernelConcept.DESCRIPTION_TYPE), null);
        assertFalse(DiTreeIsomorphism.isomorphic(one, more));
        assertFalse(DiTreeIsomorphism.isomorphic(more, one));
    }

    /**
     * The defect the double analysis had: a new normal form that only adds to the latest
     * inferred form (a stated parent or role added) was found "the same", so no inferred
     * version was written. Kept as a record of why the decision changed.
     */
    @Test
    void theDecisionBeforeMissedAFormThatOnlyAdds() {
        DiTreeEntity latest = build(parentsOnly(KernelConcept.LANGUAGE), null);
        DiTreeEntity normalForm = build(parentsOnly(KernelConcept.LANGUAGE, KernelConcept.DESCRIPTION_TYPE), null);
        assertTrue(previousDecisionSame(latest, normalForm), "the decision before found a superset the same");
        assertFalse(DiTreeIsomorphism.isomorphic(latest, normalForm), "the decision now writes it");
    }

    // ----- random cases -----

    /**
     * Random pairs, half of them the same definition built in another order, half a definition
     * and one change to it: the comparison now must agree with the oracle on every pair. The
     * decision before may disagree only by finding "the same" where the latest form maps into
     * the new one without being it: the new form only adds (the defect above), or two identical
     * siblings of the latest form were both matched to one vertex of the new form.
     */
    @Test
    void randomPairsAgreeWithTheOracle() {
        Random random = new Random(SEED);
        int same = 0;
        int different = 0;
        int previousMissedASuperset = 0;
        int previousMergedSiblings = 0;
        for (int i = 0; i < RANDOM_CASES; i++) {
            Definition definition = randomDefinition(random);
            Definition other = random.nextBoolean() ? definition : mutate(definition, random);
            // As in the writer: the latest form, read from a store, holds proxies (half the time
            // here, so enums against enums is covered too); the normal form holds enums.
            DiTreeEntity latest = build(random.nextBoolean() ? asProxies(definition) : definition, random);
            DiTreeEntity normalForm = build(other, random);

            boolean truth = oracleIsomorphic(latest, normalForm);
            if (truth) {
                same++;
            } else {
                different++;
            }
            assertEquals(truth, DiTreeIsomorphism.isomorphic(latest, normalForm),
                    () -> describe(latest, normalForm));
            assertEquals(truth, DiTreeIsomorphism.isomorphic(normalForm, latest), "symmetric");
            if (truth) {
                assertEquals(DiTreeIsomorphism.hash(latest), DiTreeIsomorphism.hash(normalForm), "isomorphic trees hash alike");
            }

            boolean previous = previousDecisionSame(latest, normalForm);
            if (previous != truth) {
                if (previous && oracleEmbeds(latest, normalForm)) {
                    previousMissedASuperset++;
                } else if (previous && oracleMapsInto(latest, normalForm)) {
                    previousMergedSiblings++;
                } else {
                    fail("The decision before disagreed with the oracle other than by a mapping into the new form: "
                            + describe(latest, normalForm) + " before=" + previous + " truth=" + truth);
                }
            }
        }
        System.out.printf("DiTreeIsomorphismIT: %,d pairs, %,d the same, %,d different; the decision before "
                + "missed %,d forms that only added and %,d whose identical siblings it matched to one vertex%n",
                RANDOM_CASES, same, different, previousMissedASuperset, previousMergedSiblings);
        assertTrue(same > RANDOM_CASES / 4 && different > RANDOM_CASES / 4, "both outcomes are exercised");
    }

    private static String describe(DiTreeEntity a, DiTreeEntity b) {
        return "\n" + a + "\n" + b;
    }
}
