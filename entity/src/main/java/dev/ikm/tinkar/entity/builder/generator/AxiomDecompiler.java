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
package dev.ikm.tinkar.entity.builder.generator;

import dev.ikm.tinkar.common.id.IntIdList;
import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiomSemantic;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Decompiles a stored stated-axiom {@link DiTreeEntity} into the
 * {@code LogicalExpressionBuilder} calls that rebuild it — the body of the ledger
 * builder's {@code statedAxioms(leb -> ...)} verb (IKE-Network/ike-issues#869). Every
 * vertex kind the builder can compose is covered: the logical sets (necessary,
 * sufficient, inclusion, property, data-property, interval-property), the And/Or
 * connectives, concept references, roles under any operator (existential, universal,
 * and role groups, which are existential roles of type {@code ROLE_GROUP}),
 * disjoint-with, features, interval roles, and property-sequence implications. The
 * decompiled form mirrors the stored tree vertex for vertex and child order for child
 * order, so replaying it rebuilds the same expression.
 * <p>
 * Walks raw {@link EntityVertex} nodes by {@link LogicalAxiomSemantic} tag — the same
 * approach the platform's own readers use (the reasoner's {@code ElkSnomedDataBuilder})
 * — rather than the typed {@code LogicalAxiom.Atom} adaptor layer, whose
 * {@code PropertySequenceImplication.implication()} and interval-role bound accessors
 * are stubbed incomplete.
 * <p>
 * The common case — the simple is-a shape, {@code NecessarySet(And(ConceptAxiom*))} or
 * its single-parent variant {@code NecessarySet(ConceptAxiom)} — is also reported as
 * such, with its parent concepts. A tree the builder cannot rebuild exactly (an
 * unknown vertex meaning, a property the builder would not carry, a set nested below
 * the root, a vertex unreachable from the root) is reported, not guessed at: the
 * generator's manifest names it for hand authoring rather than silently emitting
 * something wrong or silently dropping it.
 */
public final class AxiomDecompiler {

    private static final Set<LogicalAxiomSemantic> SETS = EnumSet.of(
            LogicalAxiomSemantic.NECESSARY_SET, LogicalAxiomSemantic.SUFFICIENT_SET,
            LogicalAxiomSemantic.INCLUSION_SET, LogicalAxiomSemantic.PROPERTY_SET,
            LogicalAxiomSemantic.DATA_PROPERTY_SET, LogicalAxiomSemantic.INTERVAL_PROPERTY_SET);

    private AxiomDecompiler() {
    }

    /**
     * Decompiles one stated-axiom tree.
     *
     * @param tree the stated-axiom semantic's latest field value
     * @return a decompiled result carrying the tree's logical sets (and, for the
     *         simple is-a shape, its ordered parent concepts), or a not-decompiled
     *         result carrying a human-readable dump of the tree for hand authoring
     */
    public static Result decompile(DiTreeEntity tree) {
        try {
            int[] visited = {0};
            EntityVertex root = tree.root();
            visited[0]++;
            if (meaningOf(root) != LogicalAxiomSemantic.DEFINITION_ROOT) {
                throw new Unsupported("unexpected root meaning: " + PrimitiveData.text(root.getMeaningNid()));
            }
            expectProperties(root);
            List<LogicalSet> sets = new ArrayList<>();
            for (int setIndex : tree.successors(root.vertexIndex()).toArray()) {
                EntityVertex setVertex = tree.vertex(setIndex);
                visited[0]++;
                LogicalAxiomSemantic kind = meaningOf(setVertex);
                if (!SETS.contains(kind)) {
                    throw new Unsupported("definition root child is " + describe(setVertex) + ", not a logical set");
                }
                expectProperties(setVertex);
                sets.add(new LogicalSet(kind, atomsBelow(tree, setVertex, visited)));
            }
            if (visited[0] != tree.vertexMap().size()) {
                throw new Unsupported((tree.vertexMap().size() - visited[0]) + " vertices unreachable from the root");
            }
            return new Result(List.copyOf(sets), simpleIsAParents(sets), null);
        } catch (Unsupported e) {
            return notDecompiled(tree, e.getMessage());
        }
    }

    private static List<Node> atomsBelow(DiTreeEntity tree, EntityVertex parent, int[] visited) {
        ImmutableIntList children = tree.successors(parent.vertexIndex());
        List<Node> atoms = new ArrayList<>(children.size());
        for (int childIndex : children.toArray()) {
            atoms.add(atom(tree, tree.vertex(childIndex), visited));
        }
        return List.copyOf(atoms);
    }

    private static Node atom(DiTreeEntity tree, EntityVertex vertex, int[] visited) {
        visited[0]++;
        LogicalAxiomSemantic kind = meaningOf(vertex);
        if (kind == null || kind == LogicalAxiomSemantic.DEFINITION_ROOT || SETS.contains(kind)) {
            throw new Unsupported(describe(vertex) + " where the builder takes only an atom");
        }
        if (kind != LogicalAxiomSemantic.AND && kind != LogicalAxiomSemantic.OR
                && kind != LogicalAxiomSemantic.ROLE && !tree.successors(vertex.vertexIndex()).isEmpty()) {
            throw new Unsupported(describe(vertex) + " is a terminal atom but carries successors");
        }
        return switch (kind) {
            case AND, OR -> {
                expectProperties(vertex);
                yield new Connective(kind, atomsBelow(tree, vertex, visited));
            }
            case CONCEPT -> {
                expectProperties(vertex, KernelTerm.CONCEPT_REFERENCE);
                yield new ConceptReference(concept(vertex, KernelTerm.CONCEPT_REFERENCE));
            }
            case ROLE -> {
                expectProperties(vertex, KernelTerm.ROLE_TYPE, KernelTerm.ROLE_OPERATOR);
                List<Node> restriction = atomsBelow(tree, vertex, visited);
                if (restriction.size() != 1) {
                    throw new Unsupported("Role with " + restriction.size() + " restrictions (expected one)");
                }
                yield new Role(concept(vertex, KernelTerm.ROLE_OPERATOR), concept(vertex, KernelTerm.ROLE_TYPE),
                        restriction.getFirst());
            }
            case DISJOINT_WITH -> {
                expectProperties(vertex, KernelTerm.DISJOINT_WITH);
                yield new DisjointWith(concept(vertex, KernelTerm.DISJOINT_WITH));
            }
            case FEATURE -> {
                expectProperties(vertex, KernelTerm.FEATURE_TYPE, KernelTerm.CONCRETE_DOMAIN_OPERATOR,
                        KernelTerm.LITERAL_VALUE);
                Object literal = vertex.propertyFast(KernelTerm.LITERAL_VALUE);
                literalSource(literal); // Fails fast on a literal type the builder source cannot carry.
                yield new Feature(concept(vertex, KernelTerm.FEATURE_TYPE),
                        concept(vertex, KernelTerm.CONCRETE_DOMAIN_OPERATOR), literal);
            }
            case INTERVAL_ROLE -> {
                expectProperties(vertex, KernelTerm.INTERVAL_ROLE_TYPE, KernelTerm.INTERVAL_LOWER_BOUND,
                        KernelTerm.LOWER_BOUND_OPEN, KernelTerm.INTERVAL_UPPER_BOUND, KernelTerm.UPPER_BOUND_OPEN,
                        KernelTerm.UNIT_OF_MEASURE);
                yield new IntervalRole(concept(vertex, KernelTerm.INTERVAL_ROLE_TYPE),
                        property(vertex, KernelTerm.INTERVAL_LOWER_BOUND, BigDecimal.class),
                        property(vertex, KernelTerm.LOWER_BOUND_OPEN, Boolean.class),
                        property(vertex, KernelTerm.INTERVAL_UPPER_BOUND, BigDecimal.class),
                        property(vertex, KernelTerm.UPPER_BOUND_OPEN, Boolean.class),
                        concept(vertex, KernelTerm.UNIT_OF_MEASURE));
            }
            case PROPERTY_SEQUENCE_IMPLICATION -> {
                expectProperties(vertex, KernelTerm.PROPERTY_SEQUENCE, KernelTerm.PROPERTY_SEQUENCE_IMPLICATION);
                IntIdList sequence = property(vertex, KernelTerm.PROPERTY_SEQUENCE, IntIdList.class);
                List<ConceptFacade> properties = new ArrayList<>(sequence.size());
                sequence.forEach(nid -> properties.add(EntityProxy.Concept.make(nid)));
                yield new PropertySequenceImplication(List.copyOf(properties),
                        concept(vertex, KernelTerm.PROPERTY_SEQUENCE_IMPLICATION));
            }
            default -> throw new Unsupported(describe(vertex) + " has no builder verb");
        };
    }

    /**
     * The parents of the simple is-a shape — one NecessarySet holding either a lone
     * concept reference or an And of one or more concept references — or empty for
     * any other shape.
     */
    private static List<ConceptFacade> simpleIsAParents(List<LogicalSet> sets) {
        if (sets.size() != 1 || sets.getFirst().kind() != LogicalAxiomSemantic.NECESSARY_SET
                || sets.getFirst().elements().size() != 1) {
            return List.of();
        }
        Node sole = sets.getFirst().elements().getFirst();
        if (sole instanceof ConceptReference(ConceptFacade concept)) {
            return List.of(concept);
        }
        if (sole instanceof Connective(LogicalAxiomSemantic kind, List<Node> elements)
                && kind == LogicalAxiomSemantic.AND && !elements.isEmpty()) {
            List<ConceptFacade> parents = new ArrayList<>(elements.size());
            for (Node element : elements) {
                if (!(element instanceof ConceptReference(ConceptFacade concept))) {
                    return List.of();
                }
                parents.add(concept);
            }
            return List.copyOf(parents);
        }
        return List.of();
    }

    /**
     * Requires the vertex to carry exactly the given properties: a missing one would
     * replay as a null the builder never stores, and an extra one would be silently
     * dropped, since no builder verb carries it.
     */
    private static void expectProperties(EntityVertex vertex, EntityProxy.Concept... expected) {
        Set<Integer> expectedNids = new HashSet<>();
        for (EntityProxy.Concept property : expected) {
            expectedNids.add(property.nid());
        }
        Set<Integer> actualNids = new HashSet<>();
        vertex.properties().forEachKeyValue((nid, value) -> {
            if (value != null) {
                actualNids.add(nid);
            }
        });
        if (!actualNids.equals(expectedNids)) {
            throw new Unsupported(describe(vertex) + " carries properties "
                    + actualNids.stream().map(PrimitiveData::text).sorted().toList() + ", expected "
                    + expectedNids.stream().map(PrimitiveData::text).sorted().toList());
        }
    }

    private static ConceptFacade concept(EntityVertex vertex, EntityProxy.Concept property) {
        return property(vertex, property, ConceptFacade.class);
    }

    private static <T> T property(EntityVertex vertex, EntityProxy.Concept property, Class<T> type) {
        Object value = vertex.propertyFast(property);
        if (!type.isInstance(value)) {
            throw new Unsupported(describe(vertex) + " property " + PrimitiveData.text(property.nid()) + " is "
                    + (value == null ? "null" : value.getClass().getName()) + ", not " + type.getSimpleName());
        }
        return type.cast(value);
    }

    private static String describe(EntityVertex vertex) {
        LogicalAxiomSemantic kind = meaningOf(vertex);
        return kind != null ? kind.toString() : "vertex meaning " + PrimitiveData.text(vertex.getMeaningNid());
    }

    private static Result notDecompiled(DiTreeEntity tree, String reason) {
        StringBuilder dump = new StringBuilder(reason).append('\n');
        for (EntityVertex vertex : tree.vertexMap()) {
            dump.append("  ").append(vertex.toGraphFormatString("", "", tree)).append('\n');
        }
        return new Result(null, List.of(), dump.toString());
    }

    private static LogicalAxiomSemantic meaningOf(EntityVertex vertex) {
        try {
            return LogicalAxiomSemantic.get(vertex.getMeaningNid());
        } catch (IllegalStateException e) {
            return null;
        }
    }

    /**
     * The Java source for a feature's literal value, typed exactly as stored — the
     * builder takes the literal as {@code Object}, so the boxed type the source
     * produces is the type the replayed vertex carries. {@code valueOf} of the
     * canonical string keeps every float and double exact, non-finite values included.
     */
    private static String literalSource(Object literal) {
        return switch (literal) {
            case String text -> '"' + BindingReferenceResolver.escapeForJavaStringLiteral(text) + '"';
            case Integer number -> "Integer.valueOf(" + number + ")";
            case Long number -> "Long.valueOf(" + number + "L)";
            case Float number -> "Float.valueOf(\"" + number + "\")";
            case Double number -> "Double.valueOf(\"" + number + "\")";
            case BigDecimal number -> "new java.math.BigDecimal(\"" + number + "\")";
            case Boolean bool -> "Boolean.valueOf(" + bool + ")";
            case null -> throw new Unsupported("feature literal is null");
            default -> throw new Unsupported("feature literal type " + literal.getClass().getName()
                    + " has no source form");
        };
    }

    /** A tree shape the builder cannot rebuild exactly; caught and reported by {@link #decompile}. */
    private static final class Unsupported extends RuntimeException {
        Unsupported(String reason) {
            super(reason, null, false, false);
        }
    }

    /** One vertex of a decompiled logical expression, below the definition root. */
    public sealed interface Node permits LogicalSet, Connective, ConceptReference, Role, DisjointWith, Feature,
            IntervalRole, PropertySequenceImplication {

        /**
         * The {@code LogicalExpressionBuilder} call that rebuilds this vertex and
         * everything below it, as Java source on a builder named {@code leb}.
         *
         * @param reference the source expression for a concept reference
         * @return the builder call's source
         */
        String builderSource(Function<? super ConceptFacade, String> reference);
    }

    /**
     * A logical set — a direct child of the definition root.
     *
     * @param kind     which set: necessary, sufficient, inclusion, property,
     *                 data-property, or interval-property
     * @param elements the set's atoms, in stored order
     */
    public record LogicalSet(LogicalAxiomSemantic kind, List<Node> elements) implements Node {
        @Override
        public String builderSource(Function<? super ConceptFacade, String> reference) {
            String verb = switch (kind) {
                case NECESSARY_SET -> "NecessarySet";
                case SUFFICIENT_SET -> "SufficientSet";
                case INCLUSION_SET -> "InclusionSet";
                case PROPERTY_SET -> "PropertySet";
                case DATA_PROPERTY_SET -> "DataPropertySet";
                case INTERVAL_PROPERTY_SET -> "IntervalPropertySet";
                default -> throw new IllegalStateException("not a logical set: " + kind);
            };
            return "leb." + verb + "(" + joined(elements, reference) + ")";
        }
    }

    /**
     * An And or Or connective.
     *
     * @param kind     {@code AND} or {@code OR}
     * @param elements the connected atoms, in stored order (possibly none)
     */
    public record Connective(LogicalAxiomSemantic kind, List<Node> elements) implements Node {
        @Override
        public String builderSource(Function<? super ConceptFacade, String> reference) {
            return (kind == LogicalAxiomSemantic.AND ? "leb.And(" : "leb.Or(") + joined(elements, reference) + ")";
        }
    }

    /**
     * A concept reference.
     *
     * @param concept the referenced concept
     */
    public record ConceptReference(ConceptFacade concept) implements Node {
        @Override
        public String builderSource(Function<? super ConceptFacade, String> reference) {
            return "leb.ConceptAxiom(" + reference.apply(concept) + ")";
        }
    }

    /**
     * A role restriction — existential ({@code SomeRole}, which with role type
     * {@code ROLE_GROUP} is a role group), universal ({@code AllRole}), or under any
     * other operator ({@code Role}).
     *
     * @param operator    the role operator
     * @param type        the role type
     * @param restriction the restriction atom
     */
    public record Role(ConceptFacade operator, ConceptFacade type, Node restriction) implements Node {
        @Override
        public String builderSource(Function<? super ConceptFacade, String> reference) {
            String restrictionSource = restriction.builderSource(reference);
            if (operator.nid() == KernelTerm.EXISTENTIAL_RESTRICTION.nid()) {
                return "leb.SomeRole(" + reference.apply(type) + ", " + restrictionSource + ")";
            }
            if (operator.nid() == KernelTerm.UNIVERSAL_RESTRICTION.nid()) {
                return "leb.AllRole(" + reference.apply(type) + ", " + restrictionSource + ")";
            }
            return "leb.Role(" + reference.apply(operator) + ", " + reference.apply(type) + ", "
                    + restrictionSource + ")";
        }
    }

    /**
     * A disjoint-with axiom.
     *
     * @param concept the concept disjoint with the defined one
     */
    public record DisjointWith(ConceptFacade concept) implements Node {
        @Override
        public String builderSource(Function<? super ConceptFacade, String> reference) {
            return "leb.DisjointWithAxiom(" + reference.apply(concept) + ")";
        }
    }

    /**
     * A feature — a concrete-domain restriction on a literal value.
     *
     * @param type     the feature type
     * @param operator the concrete-domain operator
     * @param literal  the literal value, of a type {@code literalSource} renders
     */
    public record Feature(ConceptFacade type, ConceptFacade operator, Object literal) implements Node {
        @Override
        public String builderSource(Function<? super ConceptFacade, String> reference) {
            return "leb.FeatureAxiom(" + reference.apply(type) + ", " + reference.apply(operator) + ", "
                    + literalSource(literal) + ")";
        }
    }

    /**
     * An interval role.
     *
     * @param type       the interval role type
     * @param lowerBound the interval's lower bound
     * @param lowerOpen  whether the lower bound is open
     * @param upperBound the interval's upper bound
     * @param upperOpen  whether the upper bound is open
     * @param units      the unit of measure
     */
    public record IntervalRole(ConceptFacade type, BigDecimal lowerBound, boolean lowerOpen, BigDecimal upperBound,
                               boolean upperOpen, ConceptFacade units) implements Node {
        @Override
        public String builderSource(Function<? super ConceptFacade, String> reference) {
            return "leb.IntervalRole(" + reference.apply(type) + ", new java.math.BigDecimal(\"" + lowerBound
                    + "\"), " + lowerOpen + ", new java.math.BigDecimal(\"" + upperBound + "\"), " + upperOpen
                    + ", " + reference.apply(units) + ")";
        }
    }

    /**
     * A property-sequence implication — a role chain.
     *
     * @param sequence    the chained properties, in order
     * @param implication the property the chain implies
     */
    public record PropertySequenceImplication(List<ConceptFacade> sequence, ConceptFacade implication)
            implements Node {
        @Override
        public String builderSource(Function<? super ConceptFacade, String> reference) {
            String properties = sequence.stream().map(reference).collect(Collectors.joining(", "));
            return "leb.PropertySequenceImplicationAxiom(org.eclipse.collections.api.factory.Lists.immutable"
                    + ".<dev.ikm.tinkar.terms.ConceptFacade>of(" + properties + "), "
                    + reference.apply(implication) + ")";
        }
    }

    private static String joined(List<Node> nodes, Function<? super ConceptFacade, String> reference) {
        return nodes.stream().map(node -> node.builderSource(reference)).collect(Collectors.joining(", "));
    }

    /**
     * The outcome of decompiling one stated-axiom tree.
     *
     * @param sets           the definition root's logical sets, in stored order, when
     *                       decompiled; null when the tree needs hand authoring
     * @param parents        the ordered parent concepts, when the tree is the simple
     *                       is-a shape; empty otherwise
     * @param diagnosticDump a human-readable dump of the tree, for hand authoring, when
     *                       not decompiled; null otherwise
     */
    public record Result(List<LogicalSet> sets, List<ConceptFacade> parents, String diagnosticDump) {

        /** @return {@code true} when the builder source rebuilds the whole tree */
        public boolean decompiled() {
            return sets != null;
        }

        /** @return {@code true} when the tree is the simple is-a shape; its parents are {@link #parents()} */
        public boolean simpleIsA() {
            return !parents.isEmpty();
        }

        /**
         * The {@code statedAxioms} consumer that rebuilds the tree, as Java source:
         * {@code leb -> leb.NecessarySet(...)} for one set, a block lambda calling each
         * set's verb in stored order for several.
         *
         * @param reference the source expression for a concept reference
         * @return the lambda's source
         * @throws IllegalStateException if the tree was not decompiled
         */
        public String builderLambda(Function<? super ConceptFacade, String> reference) {
            if (!decompiled()) {
                throw new IllegalStateException("not decompiled: " + diagnosticDump);
            }
            if (sets.size() == 1) {
                return "leb -> " + sets.getFirst().builderSource(reference);
            }
            return sets.stream().map(set -> set.builderSource(reference) + ";")
                    .collect(Collectors.joining(" ", "leb -> { ", sets.isEmpty() ? "}" : " }"));
        }
    }
}
