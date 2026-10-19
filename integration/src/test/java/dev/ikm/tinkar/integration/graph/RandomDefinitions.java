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

import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpressionBuilder;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelConcept;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Random logical definitions from a small model, shared by the tree-comparison and
 * vertex-identity tests.
 *
 * <p>Random definitions are well-formed (the isomorphic analysis refuses a vertex outside a
 * known set), and draw on a few concepts so that siblings often look alike, which is where a
 * matcher can go wrong. Concepts are binding enums; the store the test runs against must be
 * loaded before a definition is built.
 */
final class RandomDefinitions {

    static final List<ConceptFacade> CONCEPTS = List.of(KernelConcept.LANGUAGE, KernelConcept.ENGLISH_LANGUAGE,
            KernelConcept.DESCRIPTION_TYPE, KernelConcept.PHENOMENON);
    static final List<ConceptFacade> ROLE_TYPES = List.of(KernelConcept.ROLE_TYPE, KernelConcept.FEATURE_TYPE);
    static final List<ConceptFacade> OPERATORS = List.of(KernelConcept.EXISTENTIAL_RESTRICTION,
            KernelConcept.UNIVERSAL_RESTRICTION);

    private RandomDefinitions() {
    }

    // ----- the model -----

    /** An element of a set's conjunction, or a role's restriction. */
    sealed interface Atom permits Ref, Role, Group, Feature {
    }

    record Ref(ConceptFacade concept) implements Atom {
    }

    /** A role whose restriction is a concept. */
    record Role(ConceptFacade operator, ConceptFacade type, ConceptFacade filler) implements Atom {
    }

    /** A role group: an existential role of type Role group over a conjunction of roles. */
    record Group(List<Role> roles) implements Atom {
    }

    record Feature(ConceptFacade type, int value) implements Atom {
    }

    record Set(boolean necessary, List<Atom> elements) {
    }

    /** A definition: its sets, at most one of them necessary. */
    record Definition(List<Set> sets) {
    }

    /**
     * The definition with every concept as a proxy rather than a binding enum: the form a tree
     * read back from a store has, where the reasoner builds its normal form with enums.
     */
    static Definition asProxies(Definition definition) {
        List<Set> sets = new ArrayList<>();
        for (Set set : definition.sets()) {
            sets.add(new Set(set.necessary(), set.elements().stream().map(RandomDefinitions::asProxies).toList()));
        }
        return new Definition(sets);
    }

    private static Atom asProxies(Atom atom) {
        return switch (atom) {
            case Ref ref -> new Ref(proxy(ref.concept()));
            case Role role -> asProxies(role);
            case Group group -> new Group(group.roles().stream().map(RandomDefinitions::asProxies).toList());
            case Feature feature -> new Feature(proxy(feature.type()), feature.value());
        };
    }

    private static Role asProxies(Role role) {
        return new Role(proxy(role.operator()), proxy(role.type()), proxy(role.filler()));
    }

    private static ConceptFacade proxy(ConceptFacade concept) {
        return EntityProxy.Concept.make(concept.nid());
    }

    /** Builds the definition, with fresh random vertex UUIDs, children in the order given or shuffled. */
    static DiTreeEntity build(Definition definition, Random shuffle) {
        LogicalExpressionBuilder builder = new LogicalExpressionBuilder();
        for (Set set : ordered(definition.sets(), shuffle)) {
            List<LogicalAxiom.Atom> atoms = new ArrayList<>();
            for (Atom atom : ordered(set.elements(), shuffle)) {
                atoms.add(atom(builder, atom, shuffle));
            }
            LogicalAxiom.Atom and = builder.And(atoms.toArray(LogicalAxiom.Atom[]::new));
            if (set.necessary()) {
                builder.NecessarySet(and);
            } else {
                builder.SufficientSet(and);
            }
        }
        return (DiTreeEntity) builder.build().sourceGraph();
    }

    private static LogicalAxiom.Atom atom(LogicalExpressionBuilder builder, Atom atom, Random shuffle) {
        return switch (atom) {
            case Ref ref -> builder.ConceptAxiom(ref.concept());
            case Role role -> builder.Role(role.operator(), role.type(), builder.ConceptAxiom(role.filler()));
            case Group group -> builder.Role(KernelConcept.EXISTENTIAL_RESTRICTION, KernelConcept.ROLE_GROUP,
                    builder.And(ordered(group.roles(), shuffle).stream()
                            .map(role -> atom(builder, role, shuffle)).toArray(LogicalAxiom.Atom[]::new)));
            case Feature feature -> builder.FeatureAxiom(feature.type(), KernelConcept.EQUAL_TO, feature.value());
        };
    }

    private static <T> List<T> ordered(List<T> list, Random shuffle) {
        if (shuffle == null) {
            return list;
        }
        List<T> copy = new ArrayList<>(list);
        Collections.shuffle(copy, shuffle);
        return copy;
    }

    static Definition parentsOnly(ConceptFacade... parents) {
        List<Atom> elements = new ArrayList<>();
        for (ConceptFacade parent : parents) {
            elements.add(new Ref(parent));
        }
        return new Definition(List.of(new Set(true, elements)));
    }

    // ----- random definitions -----

    static ConceptFacade pick(List<ConceptFacade> list, Random random) {
        return list.get(random.nextInt(list.size()));
    }

    static Role randomRole(Random random) {
        return new Role(pick(OPERATORS, random), pick(ROLE_TYPES, random), pick(CONCEPTS, random));
    }

    static Atom randomAtom(Random random) {
        return switch (random.nextInt(4)) {
            case 0 -> new Ref(pick(CONCEPTS, random));
            case 1 -> randomRole(random);
            case 2 -> {
                List<Role> roles = new ArrayList<>();
                for (int i = 0, n = 1 + random.nextInt(3); i < n; i++) {
                    roles.add(randomRole(random));
                }
                yield new Group(roles);
            }
            default -> new Feature(pick(ROLE_TYPES, random), random.nextInt(3));
        };
    }

    static Definition randomDefinition(Random random) {
        List<Set> sets = new ArrayList<>();
        for (int i = 0, n = 1 + random.nextInt(2); i < n; i++) {
            List<Atom> elements = new ArrayList<>();
            for (int j = 0, m = 1 + random.nextInt(4); j < m; j++) {
                elements.add(randomAtom(random));
            }
            sets.add(new Set(i == 0, elements));
        }
        return new Definition(sets);
    }

    /** One change to a definition, which may or may not change the tree; an oracle decides. */
    static Definition mutate(Definition definition, Random random) {
        List<Set> sets = new ArrayList<>(definition.sets());
        int s = random.nextInt(sets.size());
        Set set = sets.get(s);
        List<Atom> elements = new ArrayList<>(set.elements());
        int e = random.nextInt(elements.size());
        switch (random.nextInt(6)) {
            case 0 -> elements.add(randomAtom(random));                                  // adds
            case 1 -> {                                                                   // removes
                if (elements.size() > 1) {
                    elements.remove(e);
                } else {
                    elements.set(e, randomAtom(random));
                }
            }
            case 2 -> elements.set(e, randomAtom(random));                               // replaces
            case 3 -> elements.add(elements.get(e));                                      // duplicates
            case 4 -> {                                                                   // changes inside a group
                if (elements.get(e) instanceof Group group) {
                    List<Role> roles = new ArrayList<>(group.roles());
                    if (random.nextBoolean() || roles.size() == 1) {
                        roles.add(randomRole(random));
                    } else {
                        roles.remove(random.nextInt(roles.size()));
                    }
                    elements.set(e, new Group(roles));
                } else {
                    elements.add(new Group(List.of(randomRole(random))));
                }
            }
            default -> {                                                                  // adds a set
                sets.add(new Set(false, List.of(randomAtom(random))));
            }
        }
        sets.set(s, new Set(set.necessary(), elements));
        return new Definition(sets);
    }
}
