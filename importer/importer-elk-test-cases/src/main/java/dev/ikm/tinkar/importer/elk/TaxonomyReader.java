/*
 * Copyright © 2026 IKE Network (support@ike.network)
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
package dev.ikm.tinkar.importer.elk;

import dev.ikm.tinkar.importer.elk.ElkCase.OutOfScope;
import dev.ikm.tinkar.importer.elk.ElkCase.Taxonomy;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.owl.interfaces.ElkClass;
import org.semanticweb.elk.owl.interfaces.ElkClassExpression;
import org.semanticweb.elk.owl.interfaces.ElkDeclarationAxiom;
import org.semanticweb.elk.owl.interfaces.ElkEquivalentClassesAxiom;
import org.semanticweb.elk.owl.interfaces.ElkEquivalentObjectPropertiesAxiom;
import org.semanticweb.elk.owl.interfaces.ElkObjectProperty;
import org.semanticweb.elk.owl.interfaces.ElkObjectPropertyExpression;
import org.semanticweb.elk.owl.interfaces.ElkSubClassOfAxiom;
import org.semanticweb.elk.owl.interfaces.ElkSubObjectPropertyExpression;
import org.semanticweb.elk.owl.interfaces.ElkSubObjectPropertyOfAxiom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads an ELK expected taxonomy — the classified hierarchy written as OWL: an
 * {@code EquivalentClasses} axiom per node with more than one member and a {@code SubClassOf}
 * axiom per direct edge (or their object-property counterparts) — into each entity's direct
 * parents and equivalents. An entity no edge places under another is a child of the top.
 */
final class TaxonomyReader {

    private TaxonomyReader() {
    }

    static Taxonomy classes(List<ElkAxiom> axioms, Set<String> classes) throws OutOfScope {
        Nodes nodes = new Nodes(ElkCase.THING, classes);
        for (ElkAxiom axiom : axioms) {
            switch (axiom) {
                case ElkDeclarationAxiom _ -> {
                    // The case's input declares what the taxonomy is about.
                }
                case ElkEquivalentClassesAxiom equivalent -> {
                    List<String> members = new ArrayList<>();
                    for (ElkClassExpression member : equivalent.getClassExpressions()) {
                        members.add(className(member));
                    }
                    nodes.equivalent(members);
                }
                case ElkSubClassOfAxiom subClassOf -> {
                    String sub = className(subClassOf.getSubClassExpression());
                    // The bottom node's edges say only which nodes are leaves.
                    if (!sub.equals(ElkCase.NOTHING)) {
                        nodes.edge(sub, className(subClassOf.getSuperClassExpression()));
                    }
                }
                default -> throw new IllegalArgumentException("Unexpected taxonomy axiom " + ElkCase.kind(axiom));
            }
        }
        return nodes.taxonomy();
    }

    static Taxonomy objectProperties(List<ElkAxiom> axioms, Set<String> properties) throws OutOfScope {
        Nodes nodes = new Nodes(ElkCase.TOP_OBJECT_PROPERTY, properties);
        for (ElkAxiom axiom : axioms) {
            switch (axiom) {
                case ElkDeclarationAxiom _ -> {
                    // The case's input declares what the taxonomy is about.
                }
                case ElkEquivalentObjectPropertiesAxiom equivalent -> {
                    List<String> members = new ArrayList<>();
                    for (ElkObjectPropertyExpression member : equivalent.getObjectPropertyExpressions()) {
                        members.add(propertyName(member));
                    }
                    if (members.contains(ElkCase.BOTTOM_OBJECT_PROPERTY)) {
                        throw new OutOfScope("owl:bottomObjectProperty");
                    }
                    nodes.equivalent(members);
                }
                case ElkSubObjectPropertyOfAxiom subPropertyOf -> {
                    String sub = propertyName(subPropertyOf.getSubObjectPropertyExpression());
                    if (!sub.equals(ElkCase.BOTTOM_OBJECT_PROPERTY)) {
                        nodes.edge(sub, propertyName(subPropertyOf.getSuperObjectPropertyExpression()));
                    }
                }
                default -> throw new IllegalArgumentException("Unexpected taxonomy axiom " + ElkCase.kind(axiom));
            }
        }
        return nodes.taxonomy();
    }

    private static String className(ElkClassExpression expression) {
        if (expression instanceof ElkClass elkClass) {
            return elkClass.getIri().getFullIriAsString();
        }
        throw new IllegalArgumentException("A taxonomy names classes, not " + ElkCase.kind(expression));
    }

    private static String propertyName(ElkSubObjectPropertyExpression expression) {
        if (expression instanceof ElkObjectProperty property) {
            return property.getIri().getFullIriAsString();
        }
        throw new IllegalArgumentException("A taxonomy names properties, not " + ElkCase.kind(expression));
    }

    /** The taxonomy's nodes, as equivalence classes, and the direct edges between them. */
    private static final class Nodes {
        private final String top;
        private final Set<String> entities;
        private final Map<String, String> representative = new HashMap<>();
        private final List<String[]> edges = new ArrayList<>();

        Nodes(String top, Set<String> entities) {
            this.top = top;
            this.entities = entities;
        }

        String find(String entity) {
            String parent = representative.getOrDefault(entity, entity);
            if (parent.equals(entity)) {
                return entity;
            }
            String root = find(parent);
            representative.put(entity, root);
            return root;
        }

        void equivalent(List<String> members) throws OutOfScope {
            if (members.contains(top)) {
                throw new OutOfScope("an entity equivalent to the top");
            }
            for (String member : members.subList(1, members.size())) {
                representative.put(find(member), find(members.getFirst()));
            }
        }

        void edge(String sub, String sup) {
            edges.add(new String[]{sub, sup});
        }

        Taxonomy taxonomy() {
            // owl:Nothing's node, when the taxonomy names it: its members are unsatisfiable.
            String bottomNode = representative.containsKey(ElkCase.NOTHING) || representative.containsValue(ElkCase.NOTHING)
                    ? find(ElkCase.NOTHING) : null;
            Set<String> unsatisfiable = new LinkedHashSet<>();
            Map<String, Set<String>> members = new HashMap<>();
            for (String entity : entities) {
                members.computeIfAbsent(find(entity), _ -> new LinkedHashSet<>()).add(entity);
            }
            Map<String, Set<String>> parentNodes = new HashMap<>();
            for (String[] edge : edges) {
                parentNodes.computeIfAbsent(find(edge[0]), _ -> new LinkedHashSet<>()).add(find(edge[1]));
            }
            Map<String, Set<String>> parents = new LinkedHashMap<>();
            Map<String, Set<String>> equivalents = new LinkedHashMap<>();
            for (String entity : entities) {
                String node = find(entity);
                if (node.equals(bottomNode)) {
                    unsatisfiable.add(entity);
                    parents.put(entity, Set.of());
                    Set<String> others = new LinkedHashSet<>(members.get(node));
                    others.remove(entity);
                    if (!others.isEmpty()) {
                        equivalents.put(entity, others);
                    }
                    continue;
                }
                Set<String> entityParents = new LinkedHashSet<>();
                for (String parentNode : parentNodes.getOrDefault(node, Set.of())) {
                    if (parentNode.equals(top)) {
                        entityParents.add(top);
                    } else {
                        entityParents.addAll(members.getOrDefault(parentNode, Set.of(parentNode)));
                    }
                }
                if (entityParents.isEmpty()) {
                    entityParents.add(top);
                }
                parents.put(entity, entityParents);
                Set<String> others = new LinkedHashSet<>(members.get(node));
                others.remove(entity);
                if (!others.isEmpty()) {
                    equivalents.put(entity, others);
                }
            }
            return new Taxonomy(parents, equivalents, unsatisfiable);
        }
    }
}
