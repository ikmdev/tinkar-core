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

import org.semanticweb.elk.owl.interfaces.ElkAnnotationAxiom;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.owl.interfaces.ElkClass;
import org.semanticweb.elk.owl.interfaces.ElkClassExpression;
import org.semanticweb.elk.owl.interfaces.ElkDataHasValue;
import org.semanticweb.elk.owl.interfaces.ElkDataProperty;
import org.semanticweb.elk.owl.interfaces.ElkDataPropertyExpression;
import org.semanticweb.elk.owl.interfaces.ElkDatatype;
import org.semanticweb.elk.owl.interfaces.ElkDeclarationAxiom;
import org.semanticweb.elk.owl.interfaces.ElkEntity;
import org.semanticweb.elk.owl.interfaces.ElkEquivalentClassesAxiom;
import org.semanticweb.elk.owl.interfaces.ElkEquivalentObjectPropertiesAxiom;
import org.semanticweb.elk.owl.interfaces.ElkLiteral;
import org.semanticweb.elk.owl.interfaces.ElkObject;
import org.semanticweb.elk.owl.interfaces.ElkObjectIntersectionOf;
import org.semanticweb.elk.owl.interfaces.ElkObjectProperty;
import org.semanticweb.elk.owl.interfaces.ElkObjectPropertyChain;
import org.semanticweb.elk.owl.interfaces.ElkObjectPropertyExpression;
import org.semanticweb.elk.owl.interfaces.ElkObjectSomeValuesFrom;
import org.semanticweb.elk.owl.interfaces.ElkReflexiveObjectPropertyAxiom;
import org.semanticweb.elk.owl.interfaces.ElkSubClassOfAxiom;
import org.semanticweb.elk.owl.interfaces.ElkSubDataPropertyOfAxiom;
import org.semanticweb.elk.owl.interfaces.ElkSubObjectPropertyExpression;
import org.semanticweb.elk.owl.interfaces.ElkSubObjectPropertyOfAxiom;
import org.semanticweb.elk.owl.interfaces.ElkTransitiveObjectPropertyAxiom;
import org.semanticweb.elk.owl.parsing.Owl2ParseException;
import org.semanticweb.elk.owl.parsing.Owl2Parser;
import org.semanticweb.elk.owl.parsing.Owl2ParserAxiomProcessor;
import org.semanticweb.elk.owl.parsing.javacc.Owl2FunctionalStyleParserFactory;
import org.semanticweb.elk.owl.iris.ElkPrefix;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One ELK test case, read from its OWL functional-syntax input and its expected taxonomies into
 * the model the reasoner test knowledge base is written from: named classes and properties, the
 * stated sets each one carries, and the expected parents and equivalents of each.
 * <p>
 * Only what the logical-expression vocabulary states is read: named classes, intersection,
 * existential restriction, {@code DataHasValue}, sub-, equivalent, transitive and reflexive object
 * properties, property chains, and sub-data properties. Anything else — individuals and their
 * assertions, nominals, negation, disjunction, universal restriction, disjointness, the bottom
 * class, domains and ranges, inverses — makes the whole case {@link OutOfScope}: importing the rest
 * of it would change what its expected results mean.
 * <p>
 * {@code owl:Thing} becomes the case's own top concept, which every class of the case is stated to
 * be a kind of, and {@code owl:topObjectProperty} the case's own top role, which every object
 * property is stated to be a sub-property of. Within a case, under which every named class falls,
 * that changes no subsumption between its classes.
 *
 * @param id                  the case's path below {@code test_input}, without its extension
 * @param classes             the named classes, in order of first appearance
 * @param objectProperties    the object properties, in order of first appearance
 * @param dataProperties      the data properties, in order of first appearance
 * @param necessary           each class's necessary conjuncts (its {@code SubClassOf} superclasses)
 * @param sufficient          each class's sufficient sets, one per equivalent class expression
 * @param inclusions          each class's inclusion sets: the class expressions it subsumes
 * @param superProperties     each object property's stated super-properties
 * @param transitive          the transitive object properties
 * @param reflexive           the reflexive object properties
 * @param chains              each object property's chains: the property sequences implying it
 * @param superDataProperties each data property's stated super-properties
 * @param classTaxonomy       the expected class taxonomy, when the case has one
 * @param propertyTaxonomy    the expected object property taxonomy, when the case has one
 */
public record ElkCase(String id,
                      Set<String> classes,
                      Set<String> objectProperties,
                      Set<String> dataProperties,
                      Map<String, List<Expr>> necessary,
                      Map<String, List<List<Expr>>> sufficient,
                      Map<String, List<List<Expr>>> inclusions,
                      Map<String, Set<String>> superProperties,
                      Set<String> transitive,
                      Set<String> reflexive,
                      Map<String, List<List<String>>> chains,
                      Map<String, Set<String>> superDataProperties,
                      Optional<Taxonomy> classTaxonomy,
                      Optional<Taxonomy> propertyTaxonomy) {

    /** {@code owl:Thing}, read as the case's top concept. */
    public static final String THING = "http://www.w3.org/2002/07/owl#Thing";
    /** {@code owl:Nothing}, which no case in scope may name. */
    public static final String NOTHING = "http://www.w3.org/2002/07/owl#Nothing";
    /** {@code owl:topObjectProperty}, read as the case's top role. */
    public static final String TOP_OBJECT_PROPERTY = "http://www.w3.org/2002/07/owl#topObjectProperty";
    /** {@code owl:bottomObjectProperty}, which no case in scope may name. */
    public static final String BOTTOM_OBJECT_PROPERTY = "http://www.w3.org/2002/07/owl#bottomObjectProperty";

    /** A class expression the logical-expression vocabulary states. */
    public sealed interface Expr {
    }

    /**
     * A named class; {@link #THING} for the case's top.
     *
     * @param iri the class's IRI
     */
    public record Named(String iri) implements Expr {
    }

    /**
     * An existential restriction.
     *
     * @param property the object property's IRI
     * @param filler   the restriction's filler
     */
    public record Some(String property, Expr filler) implements Expr {
    }

    /**
     * An intersection of two or more class expressions.
     *
     * @param conjuncts the conjuncts, in order
     */
    public record And(List<Expr> conjuncts) implements Expr {
    }

    /**
     * A data property's value: {@code DataHasValue}, a feature equal to the literal.
     *
     * @param property the data property's IRI
     * @param value    the literal as the value type the reasoner reads
     */
    public record HasValue(String property, Object value) implements Expr {
    }

    /**
     * An expected taxonomy: for each named entity of the case, its direct parents and the entities
     * it is equivalent to. A parent of {@link #THING} or {@link #TOP_OBJECT_PROPERTY} is the case's
     * top.
     *
     * @param parents     each entity's direct parents, every member of each parent node
     * @param equivalents each entity's equivalents, never itself; absent when it has none
     */
    public record Taxonomy(Map<String, Set<String>> parents, Map<String, Set<String>> equivalents) {
    }

    /** A case that uses something outside the vocabulary, with what it uses. */
    public static final class OutOfScope extends Exception {
        private final String construct;

        OutOfScope(String construct) {
            super(construct);
            this.construct = construct;
        }

        /** @return the construct that puts the case out of scope */
        public String construct() {
            return construct;
        }
    }

    /**
     * Reads a case.
     *
     * @param id               the case's path below {@code test_input}, without its extension
     * @param owl              the case's input, in OWL functional syntax
     * @param taxonomy         its expected class taxonomy, if it has one
     * @param propertyTaxonomy its expected object property taxonomy, if it has one
     * @return the case
     * @throws OutOfScope when the case uses something the vocabulary does not state
     */
    public static ElkCase read(String id, String owl, Optional<String> taxonomy, Optional<String> propertyTaxonomy)
            throws OutOfScope {
        Reader reader = new Reader();
        for (ElkAxiom axiom : parse(id, owl)) {
            reader.axiom(axiom);
        }
        for (String iri : reader.classes) {
            if (reader.objectProperties.contains(iri) || reader.dataProperties.contains(iri)) {
                throw new OutOfScope("punning");
            }
        }
        Optional<Taxonomy> classTaxonomy = Optional.empty();
        if (taxonomy.isPresent()) {
            classTaxonomy = Optional.of(TaxonomyReader.classes(parse(id, taxonomy.get()), reader.classes));
        }
        Optional<Taxonomy> roleTaxonomy = Optional.empty();
        if (propertyTaxonomy.isPresent()) {
            roleTaxonomy = Optional.of(TaxonomyReader.objectProperties(parse(id, propertyTaxonomy.get()),
                    reader.objectProperties));
        }
        return new ElkCase(id, reader.classes, reader.objectProperties, reader.dataProperties, reader.necessary,
                reader.sufficient, reader.inclusions, reader.superProperties, reader.transitive, reader.reflexive,
                reader.chains, reader.superDataProperties, classTaxonomy, roleTaxonomy);
    }

    /**
     * The local name of an IRI: what follows its last {@code #} or {@code /}.
     *
     * @param iri the IRI
     * @return its local name
     */
    public static String localName(String iri) {
        int cut = Math.max(iri.lastIndexOf('#'), iri.lastIndexOf('/'));
        return cut < 0 ? iri : iri.substring(cut + 1);
    }

    static List<ElkAxiom> parse(String id, String text) {
        Owl2Parser parser = new Owl2FunctionalStyleParserFactory().getParser(new StringReader(text));
        List<ElkAxiom> axioms = new ArrayList<>();
        try {
            parser.accept(new Owl2ParserAxiomProcessor() {
                @Override
                public void visit(ElkAxiom elkAxiom) {
                    axioms.add(elkAxiom);
                }

                @Override
                public void visit(ElkPrefix elkPrefix) {
                    // Prefixes are resolved by the parser.
                }

                @Override
                public void finish() {
                    // Every axiom is collected as it is read.
                }
            });
        } catch (Owl2ParseException e) {
            throw new IllegalArgumentException("ELK test case " + id + " does not parse", e);
        }
        return axioms;
    }

    /** The name a construct is reported by: its ELK interface, without the implementation suffix. */
    static String kind(ElkObject object) {
        String name = object.getClass().getSimpleName();
        return name.endsWith("Impl") ? name.substring(0, name.length() - 4) : name;
    }

    private static final class Reader {
        final Set<String> classes = new LinkedHashSet<>();
        final Set<String> objectProperties = new LinkedHashSet<>();
        final Set<String> dataProperties = new LinkedHashSet<>();
        final Map<String, List<Expr>> necessary = new LinkedHashMap<>();
        final Map<String, List<List<Expr>>> sufficient = new LinkedHashMap<>();
        final Map<String, List<List<Expr>>> inclusions = new LinkedHashMap<>();
        final Map<String, Set<String>> superProperties = new LinkedHashMap<>();
        final Set<String> transitive = new LinkedHashSet<>();
        final Set<String> reflexive = new LinkedHashSet<>();
        final Map<String, List<List<String>>> chains = new LinkedHashMap<>();
        final Map<String, Set<String>> superDataProperties = new LinkedHashMap<>();

        void axiom(ElkAxiom axiom) throws OutOfScope {
            switch (axiom) {
                case ElkDeclarationAxiom declaration -> declare(declaration.getEntity());
                case ElkSubClassOfAxiom subClassOf -> subClassOf(subClassOf);
                case ElkEquivalentClassesAxiom equivalent -> equivalentClasses(equivalent);
                case ElkSubObjectPropertyOfAxiom subPropertyOf -> subObjectPropertyOf(subPropertyOf);
                case ElkEquivalentObjectPropertiesAxiom equivalent -> {
                    List<String> properties = new ArrayList<>();
                    for (ElkObjectPropertyExpression property : equivalent.getObjectPropertyExpressions()) {
                        properties.add(objectProperty(property));
                    }
                    // Each a sub-property of the next, the last of the first: a cycle of inclusions.
                    for (int i = 0; i < properties.size(); i++) {
                        String sub = properties.get(i);
                        String sup = properties.get((i + 1) % properties.size());
                        if (!sub.equals(sup)) {
                            superProperties.computeIfAbsent(sub, _ -> new LinkedHashSet<>()).add(sup);
                        }
                    }
                }
                case ElkTransitiveObjectPropertyAxiom t -> transitive.add(objectProperty(t.getProperty()));
                case ElkReflexiveObjectPropertyAxiom r -> reflexive.add(objectProperty(r.getProperty()));
                case ElkSubDataPropertyOfAxiom subDataPropertyOf -> {
                    String sub = dataProperty(subDataPropertyOf.getSubDataPropertyExpression());
                    String sup = dataProperty(subDataPropertyOf.getSuperDataPropertyExpression());
                    superDataProperties.computeIfAbsent(sub, _ -> new LinkedHashSet<>()).add(sup);
                }
                case ElkAnnotationAxiom _ -> {
                    // Annotations state nothing the reasoner reads.
                }
                default -> throw new OutOfScope(kind(axiom));
            }
        }

        private void declare(ElkEntity entity) throws OutOfScope {
            switch (entity) {
                case ElkClass elkClass -> namedClass(elkClass);
                case ElkObjectProperty property -> objectProperty(property);
                case ElkDataProperty property -> dataProperty(property);
                case ElkDatatype _ -> {
                    // A datatype is a literal's type, read where a literal is.
                }
                default -> {
                    if (!kind(entity).startsWith("ElkAnnotationProperty")) {
                        throw new OutOfScope(kind(entity));
                    }
                }
            }
        }

        private void subClassOf(ElkSubClassOfAxiom axiom) throws OutOfScope {
            ElkClassExpression sub = axiom.getSubClassExpression();
            ElkClassExpression sup = axiom.getSuperClassExpression();
            Expr superExpr = expr(sup);
            if (sub instanceof ElkClass subClass) {
                String subIri = namedClass(subClass);
                if (subIri.equals(THING)) {
                    throw new OutOfScope("SubClassOf(owl:Thing, ...)");
                }
                if (!(superExpr instanceof Named named && named.iri().equals(THING))) {
                    necessary.computeIfAbsent(subIri, _ -> new ArrayList<>()).addAll(conjuncts(superExpr));
                }
            } else if (sup instanceof ElkClass supClass && !supClass.getIri().getFullIriAsString().equals(THING)) {
                inclusions.computeIfAbsent(namedClass(supClass), _ -> new ArrayList<>()).add(conjuncts(expr(sub)));
            } else if (!(superExpr instanceof Named named && named.iri().equals(THING))) {
                throw new OutOfScope("SubClassOf between class expressions");
            }
        }

        private void equivalentClasses(ElkEquivalentClassesAxiom axiom) throws OutOfScope {
            List<String> named = new ArrayList<>();
            List<Expr> complex = new ArrayList<>();
            for (ElkClassExpression member : axiom.getClassExpressions()) {
                Expr expr = expr(member);
                if (expr instanceof Named n) {
                    if (n.iri().equals(THING)) {
                        throw new OutOfScope("EquivalentClasses with owl:Thing");
                    }
                    named.add(n.iri());
                } else {
                    complex.add(expr);
                }
            }
            if (named.isEmpty()) {
                throw new OutOfScope("EquivalentClasses between class expressions");
            }
            // The first named class carries the definitions: each other member is sufficient for it.
            String anchor = named.getFirst();
            for (String other : named.subList(1, named.size())) {
                if (!other.equals(anchor)) {
                    sufficient.computeIfAbsent(anchor, _ -> new ArrayList<>()).add(List.of(new Named(other)));
                }
            }
            for (Expr expr : complex) {
                sufficient.computeIfAbsent(anchor, _ -> new ArrayList<>()).add(conjuncts(expr));
            }
        }

        private void subObjectPropertyOf(ElkSubObjectPropertyOfAxiom axiom) throws OutOfScope {
            String sup = objectProperty(axiom.getSuperObjectPropertyExpression());
            ElkSubObjectPropertyExpression sub = axiom.getSubObjectPropertyExpression();
            if (sub instanceof ElkObjectPropertyChain chain) {
                List<String> sequence = new ArrayList<>();
                for (ElkObjectPropertyExpression link : chain.getObjectPropertyExpressions()) {
                    sequence.add(objectProperty(link));
                }
                chains.computeIfAbsent(sup, _ -> new ArrayList<>()).add(sequence);
            } else if (sub instanceof ElkObjectPropertyExpression property) {
                String subIri = objectProperty(property);
                if (subIri.equals(TOP_OBJECT_PROPERTY)) {
                    throw new OutOfScope("SubObjectPropertyOf(owl:topObjectProperty, ...)");
                }
                if (!sup.equals(TOP_OBJECT_PROPERTY)) {
                    superProperties.computeIfAbsent(subIri, _ -> new LinkedHashSet<>()).add(sup);
                }
            } else {
                throw new OutOfScope(kind(sub));
            }
        }

        private Expr expr(ElkClassExpression expression) throws OutOfScope {
            return switch (expression) {
                case ElkClass elkClass -> new Named(namedClass(elkClass));
                case ElkObjectIntersectionOf and -> {
                    List<Expr> conjuncts = new ArrayList<>();
                    for (ElkClassExpression member : and.getClassExpressions()) {
                        conjuncts.addAll(conjuncts(expr(member)));
                    }
                    yield new And(List.copyOf(conjuncts));
                }
                case ElkObjectSomeValuesFrom some -> {
                    String property = objectProperty(some.getProperty());
                    if (property.equals(TOP_OBJECT_PROPERTY)) {
                        throw new OutOfScope("ObjectSomeValuesFrom(owl:topObjectProperty, ...)");
                    }
                    yield new Some(property, expr(some.getFiller()));
                }
                case ElkDataHasValue hasValue -> new HasValue(dataProperty(hasValue.getProperty()),
                        literal(hasValue.getFiller()));
                default -> throw new OutOfScope(kind(expression));
            };
        }

        private static List<Expr> conjuncts(Expr expr) {
            return expr instanceof And and ? and.conjuncts() : List.of(expr);
        }

        private String namedClass(ElkClass elkClass) throws OutOfScope {
            String iri = elkClass.getIri().getFullIriAsString();
            if (iri.equals(NOTHING)) {
                throw new OutOfScope("owl:Nothing");
            }
            if (!iri.equals(THING)) {
                classes.add(iri);
            }
            return iri;
        }

        private String objectProperty(ElkObjectPropertyExpression expression) throws OutOfScope {
            if (!(expression instanceof ElkObjectProperty property)) {
                throw new OutOfScope(kind(expression));
            }
            String iri = property.getIri().getFullIriAsString();
            if (iri.equals(BOTTOM_OBJECT_PROPERTY)) {
                throw new OutOfScope("owl:bottomObjectProperty");
            }
            if (!iri.equals(TOP_OBJECT_PROPERTY)) {
                objectProperties.add(iri);
            }
            return iri;
        }

        private String dataProperty(ElkDataPropertyExpression expression) throws OutOfScope {
            if (!(expression instanceof ElkDataProperty property)) {
                throw new OutOfScope(kind(expression));
            }
            String iri = property.getIri().getFullIriAsString();
            if (iri.startsWith("http://www.w3.org/2002/07/owl#")) {
                throw new OutOfScope(localName(iri));
            }
            dataProperties.add(iri);
            return iri;
        }

        private static Object literal(ElkLiteral literal) throws OutOfScope {
            String lexical = literal.getLexicalForm();
            String type = localName(literal.getDatatype().getIri().getFullIriAsString());
            try {
                return switch (type) {
                    case "string", "PlainLiteral" -> lexical;
                    case "integer", "int", "nonNegativeInteger", "positiveInteger", "short", "byte" ->
                            Integer.valueOf(lexical);
                    case "long" -> Long.valueOf(lexical);
                    case "decimal" -> new BigDecimal(lexical);
                    case "double" -> Double.valueOf(lexical);
                    case "float" -> Float.valueOf(lexical);
                    default -> throw new OutOfScope("literal of type " + type);
                };
            } catch (NumberFormatException e) {
                throw new OutOfScope("literal " + lexical + " of type " + type);
            }
        }
    }
}
