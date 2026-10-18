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
import dev.ikm.tinkar.integration.graph.RandomDefinitions.Atom;
import dev.ikm.tinkar.integration.graph.RandomDefinitions.Definition;
import dev.ikm.tinkar.integration.graph.RandomDefinitions.Feature;
import dev.ikm.tinkar.integration.graph.RandomDefinitions.Group;
import dev.ikm.tinkar.integration.graph.RandomDefinitions.Ref;
import dev.ikm.tinkar.integration.graph.RandomDefinitions.Role;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.KernelConcept;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static dev.ikm.tinkar.integration.graph.DiTreeShuffle.uuid;
import static dev.ikm.tinkar.integration.graph.RandomDefinitions.CONCEPTS;

/**
 * Chains of versions of one definition whose vertex identities are known, for measuring a
 * correlation matcher (arch-vertex-identity-verification, "Edit-chain oracle";
 * IKE-Network/ike-issues#1310).
 *
 * <p>A chain starts from a random definition of {@link RandomDefinitions}, every vertex given a
 * UUID, and applies one edit per version. Each edit says, by the identity rule
 * (arch-vertex-identity-rule), which vertices keep their UUIDs, which retire and which are born:
 *
 * <ul>
 *   <li>a <em>sole successor</em> (a role's filler, a set's AND) is identified by position, so
 *       changing its concept is an <em>update</em> and its UUID is kept;
 *   <li>an <em>operand</em> (of an AND, or a set under the definition root) is identified by its
 *       key, so changing the concept it names is a <em>replace</em>: one retires, one is born;
 *   <li>a vertex moved to another parent keeps its UUID, its subtree with it;
 *   <li>a vertex born under a parent where a retired vertex had the same key and the same
 *       content takes the retired UUID back, subtree included: the identity ledger's
 *       resurrection, which derivation (arch-vertex-identity-birth) also gives.
 * </ul>
 *
 * <p>The expectations come from the rule and the edit, never from a matcher. Edits whose
 * outcome the rule leaves to the matcher's judgment are not generated: a version isomorphic to
 * the one before (the isomorphism, not the edit, would decide which vertex is which), identical
 * siblings (a normal form never holds the same operand twice), and a vertex born where a
 * retired vertex had its key but other content (resurrection versus birth turns on the
 * bottom-up threshold the oracle exists to set).
 */
final class VertexIdentityChains {

    private VertexIdentityChains() {
    }

    // ----- the identified tree -----

    /** What a vertex means, as far as the model goes. */
    enum Kind {
        ROOT, NECESSARY, SUFFICIENT, AND, CONCEPT, ROLE, FEATURE
    }

    /**
     * A vertex with its UUID. A concept vertex has a {@code concept}; a role an {@code operator}
     * and a {@code type} (a role group has type Role group and an AND as its filler); a feature a
     * {@code type} and a {@code value}, its operator always Equal to.
     */
    record Node(Kind kind, UUID id, ConceptFacade concept, ConceptFacade operator, ConceptFacade type, int value,
                List<Node> children) {

        Node {
            children = List.copyOf(children);
        }

        Node withChildren(List<Node> newChildren) {
            return new Node(kind, id, concept, operator, type, value, newChildren);
        }

        Node withId(UUID newId) {
            return new Node(kind, newId, concept, operator, type, value, children);
        }

        Node withConcept(ConceptFacade newConcept) {
            return new Node(kind, id, newConcept, operator, type, value, children);
        }

        Node withValue(int newValue) {
            return new Node(kind, id, concept, operator, type, newValue, children);
        }

        boolean isGroup() {
            return kind == Kind.ROLE && type.nid() == KernelConcept.ROLE_GROUP.nid();
        }

        boolean isSet() {
            return kind == Kind.NECESSARY || kind == Kind.SUFFICIENT;
        }

        /** Whether this vertex's children are operands, identified by key, rather than a sole successor. */
        boolean hasOperands() {
            return kind == Kind.ROOT || kind == Kind.AND;
        }

        @Override
        public String toString() {
            return VertexIdentityChains.toString(this, "");
        }
    }

    private static Node node(Kind kind, Random uuids, List<Node> children) {
        return new Node(kind, uuid(uuids), null, null, null, 0, children);
    }

    private static Node concept(ConceptFacade concept, Random uuids) {
        return new Node(Kind.CONCEPT, uuid(uuids), concept, null, null, 0, List.of());
    }

    private static Node role(ConceptFacade operator, ConceptFacade type, Node filler, Random uuids) {
        return new Node(Kind.ROLE, uuid(uuids), null, operator, type, 0, List.of(filler));
    }

    /** The definition as an identified tree, every vertex given a fresh UUID. */
    static Node identified(Definition definition, Random uuids) {
        List<Node> sets = new ArrayList<>();
        for (RandomDefinitions.Set set : definition.sets()) {
            Node and = node(Kind.AND, uuids, set.elements().stream().map(atom -> identified(atom, uuids)).toList());
            sets.add(node(set.necessary() ? Kind.NECESSARY : Kind.SUFFICIENT, uuids, List.of(and)));
        }
        return node(Kind.ROOT, uuids, sets);
    }

    static Node identified(Atom atom, Random uuids) {
        return switch (atom) {
            case Ref ref -> concept(ref.concept(), uuids);
            case Role r -> role(r.operator(), r.type(), concept(r.filler(), uuids), uuids);
            case Group group -> role(KernelConcept.EXISTENTIAL_RESTRICTION, KernelConcept.ROLE_GROUP,
                    node(Kind.AND, uuids, group.roles().stream().map(r -> identified(r, uuids)).toList()), uuids);
            case Feature feature -> new Node(Kind.FEATURE, uuid(uuids), null, null, feature.type(), feature.value(),
                    List.of());
        };
    }

    /** The subtree with a fresh UUID on every vertex. */
    static Node refreshed(Node node, Random uuids) {
        return node.withId(uuid(uuids)).withChildren(node.children().stream().map(c -> refreshed(c, uuids)).toList());
    }

    /** Builds the tree, each vertex carrying its node's UUID, children in the nodes' order. */
    static DiTreeEntity build(Node root) {
        LogicalExpressionBuilder builder = new LogicalExpressionBuilder(root.id());
        for (Node set : root.children()) {
            LogicalAxiom.Atom and = atom(builder, set.children().getFirst());
            if (set.kind() == Kind.NECESSARY) {
                builder.NecessarySet(set.id(), and);
            } else {
                builder.SufficientSet(set.id(), and);
            }
        }
        return (DiTreeEntity) builder.build().sourceGraph();
    }

    private static LogicalAxiom.Atom atom(LogicalExpressionBuilder builder, Node node) {
        return switch (node.kind()) {
            case AND -> builder.And(node.id(), node.children().stream().map(c -> atom(builder, c))
                    .toArray(LogicalAxiom.Atom[]::new));
            case CONCEPT -> builder.ConceptAxiom(node.id(), node.concept());
            case ROLE -> builder.Role(node.id(), node.operator(), node.type(), atom(builder, node.children().getFirst()));
            case FEATURE -> builder.FeatureAxiom(node.id(), node.type(), KernelConcept.EQUAL_TO, node.value());
            case ROOT, NECESSARY, SUFFICIENT -> throw new IllegalArgumentException("not an atom: " + node.kind());
        };
    }

    // ----- reading a tree -----

    /** Every vertex, by UUID, in preorder. */
    static Map<UUID, Node> nodes(Node root) {
        Map<UUID, Node> nodes = new LinkedHashMap<>();
        collect(root, nodes);
        return nodes;
    }

    private static void collect(Node node, Map<UUID, Node> nodes) {
        nodes.put(node.id(), node);
        node.children().forEach(child -> collect(child, nodes));
    }

    /** Every vertex's parent UUID, by UUID; the root has none. */
    static Map<UUID, UUID> parents(Node root) {
        Map<UUID, UUID> parents = new HashMap<>();
        for (Node node : nodes(root).values()) {
            node.children().forEach(child -> parents.put(child.id(), node.id()));
        }
        return parents;
    }

    /** The subtree's structure without UUIDs: equal for isomorphic subtrees, children in no particular order. */
    static String canonical(Node node) {
        StringBuilder text = new StringBuilder().append(node.kind()).append(label(node));
        if (!node.children().isEmpty()) {
            text.append(node.children().stream().map(VertexIdentityChains::canonical).sorted().toList());
        }
        return text.toString();
    }

    /** The vertex's own content: what an update changes and a replace does not. */
    private static String label(Node node) {
        return switch (node.kind()) {
            case CONCEPT -> "(" + node.concept().nid() + ")";
            case ROLE -> "(" + node.operator().nid() + "," + node.type().nid() + ")";
            case FEATURE -> "(" + node.type().nid() + "=" + node.value() + ")";
            case ROOT, NECESSARY, SUFFICIENT, AND -> "";
        };
    }

    /**
     * What tells a vertex apart from its siblings (arch-vertex-identity-table): for an operand,
     * its meaning and identifying properties; for a sole successor, its meaning alone. A
     * feature's value identifies nothing; it changes by update.
     */
    static String key(Node node, boolean operand) {
        if (!operand) {
            return node.kind().name();
        }
        return switch (node.kind()) {
            case CONCEPT -> "CONCEPT(" + node.concept().nid() + ")";
            case ROLE -> "ROLE(" + node.operator().nid() + "," + node.type().nid() + ")";
            case FEATURE -> "FEATURE(" + node.type().nid() + ")";
            case ROOT, NECESSARY, SUFFICIENT, AND -> node.kind().name();
        };
    }

    /**
     * Whether the tree is one the rule meets: UUIDs unique, every AND with an operand, at most
     * one necessary set, a group's AND holding roles only, and no two siblings the same.
     */
    static boolean wellFormed(Node root) {
        java.util.Set<UUID> seen = new HashSet<>();
        int[] necessary = {0};
        return wellFormed(root, seen, necessary) && necessary[0] <= 1;
    }

    private static boolean wellFormed(Node node, java.util.Set<UUID> seen, int[] necessary) {
        if (!seen.add(node.id())) {
            return false;
        }
        if (node.kind() == Kind.NECESSARY) {
            necessary[0]++;
        }
        if (node.kind() == Kind.AND && node.children().isEmpty()) {
            return false;
        }
        if (node.isGroup() && node.children().getFirst().children().stream()
                .anyMatch(member -> member.kind() != Kind.ROLE || member.isGroup())) {
            return false;
        }
        if (node.children().stream().map(VertexIdentityChains::canonical).distinct().count() != node.children().size()) {
            return false;
        }
        for (Node child : node.children()) {
            if (!wellFormed(child, seen, necessary)) {
                return false;
            }
        }
        return true;
    }

    // ----- editing a tree -----

    /** The tree with the vertex of a UUID replaced, or removed when the replacement is null. */
    static Node replace(Node node, UUID id, Node replacement) {
        if (node.id().equals(id)) {
            return replacement;
        }
        List<Node> children = new ArrayList<>();
        boolean changed = false;
        for (Node child : node.children()) {
            Node edited = replace(child, id, replacement);
            changed |= edited != child;
            if (edited != null) {
                children.add(edited);
            }
        }
        return changed ? node.withChildren(children) : node;
    }

    /** The tree with a subtree added among the children of the vertex of a UUID. */
    static Node addChild(Node root, UUID parent, Node child) {
        Node target = nodes(root).get(parent);
        List<Node> children = new ArrayList<>(target.children());
        children.add(child);
        return replace(root, parent, target.withChildren(children));
    }

    // ----- edits -----

    /** One edit per version, each with the identity outcome the rule gives it. */
    enum Edit {
        /** A role's filler names another concept: the filler is updated, its UUID kept. */
        RETARGET_FILLER,
        /** A feature's value changes: the value identifies nothing, so the feature is updated. */
        UPDATE_FEATURE_VALUE,
        /** A parent (a concept operand of a set's AND) is swapped: the old retires, the new is born. */
        RETARGET_OPERAND,
        /**
         * A role or role group moves between the necessary set and a sufficient set: it keeps its
         * UUID, its subtree with it. A set created to receive it is born.
         */
        MOVE_BETWEEN_SETS,
        /** An operand is added to a set's AND or a group's AND: born, unless the ledger resurrects it. */
        ADD_OPERAND,
        /** An operand is deleted: it and its subtree retire. */
        DELETE_OPERAND,
        /** A deleted operand is restored in place, as it was: it and its subtree take their retired UUIDs back. */
        RESTORE_OPERAND
    }

    /** A version of the definition and the edit that made it; the first version has no edit. */
    record Version(Edit edit, Node root) {
    }

    /** What happened to one vertex of a version, relative to the version before. */
    enum Outcome {
        /** The same parent and the same content. */
        UNCHANGED,
        /** The same parent, other non-identifying content: a filler's concept, a feature's value. */
        UPDATED,
        /** Another parent. */
        MOVED,
        /** Beneath a vertex that moved, its parent unchanged. */
        MOVED_WITH_PARENT,
        /** A UUID no earlier version holds. */
        NEWBORN,
        /** A UUID an earlier version holds and the version before does not. */
        RESURRECTED
    }

    /** A deleted operand and the AND it was deleted from. */
    private record Grave(UUID parent, Node subtree) {
    }

    /**
     * A chain of versions: a random first version, then up to {@code edits} edits, fewer only if
     * none applies.
     */
    static List<Version> chain(Random random, int edits) {
        Node first;
        do {
            first = identified(RandomDefinitions.randomDefinition(random), random);
        } while (!wellFormed(first));
        List<Version> versions = new ArrayList<>();
        versions.add(new Version(null, first));
        List<Grave> graves = new ArrayList<>();
        Edit[] kinds = Edit.values();
        for (int e = 0; e < edits; e++) {
            Version next = null;
            for (int attempt = 0; attempt < 200 && next == null; attempt++) {
                Edit kind = kinds[random.nextInt(kinds.length)];
                next = attempt(versions, kind, graves, random);
            }
            if (next == null) {
                break;
            }
            versions.add(next);
        }
        return versions;
    }

    /** One try at an edit: the next version, or null when the edit does not apply or the rule leaves it open. */
    private static Version attempt(List<Version> versions, Edit kind, List<Grave> graves, Random random) {
        Node root = versions.getLast().root();
        Map<UUID, Node> nodes = nodes(root);
        Map<UUID, UUID> parents = parents(root);
        Grave buried = null;
        Node edited;
        switch (kind) {
            case RETARGET_FILLER -> {
                Node filler = any(nodes.values().stream()
                        .filter(n -> n.kind() == Kind.CONCEPT && nodes.get(parents.get(n.id())).kind() == Kind.ROLE)
                        .toList(), random);
                edited = filler == null ? null : replace(root, filler.id(), filler.withConcept(otherConcept(filler.concept(), random)));
            }
            case UPDATE_FEATURE_VALUE -> {
                Node feature = any(nodes.values().stream().filter(n -> n.kind() == Kind.FEATURE).toList(), random);
                edited = feature == null ? null
                        : replace(root, feature.id(), feature.withValue((feature.value() + 1 + random.nextInt(3)) % 4));
            }
            case RETARGET_OPERAND -> {
                Node parent = any(nodes.values().stream()
                        .filter(n -> n.kind() == Kind.CONCEPT && nodes.get(parents.get(n.id())).kind() == Kind.AND)
                        .toList(), random);
                edited = parent == null ? null
                        : replace(root, parent.id(), concept(otherConcept(parent.concept(), random), random));
            }
            case MOVE_BETWEEN_SETS -> edited = move(root, nodes, parents, random);
            case ADD_OPERAND -> {
                Node and = any(nodes.values().stream().filter(n -> n.kind() == Kind.AND).toList(), random);
                if (and == null) {
                    edited = null;
                } else {
                    boolean inGroup = nodes.get(parents.get(and.id())).isGroup();
                    Atom atom = inGroup ? RandomDefinitions.randomRole(random) : RandomDefinitions.randomAtom(random);
                    edited = addChild(root, and.id(), identified(atom, random));
                }
            }
            case DELETE_OPERAND -> {
                Node operand = any(nodes.values().stream()
                        .filter(n -> parents.containsKey(n.id()) && nodes.get(parents.get(n.id())).kind() == Kind.AND
                                && nodes.get(parents.get(n.id())).children().size() > 1)
                        .toList(), random);
                if (operand == null) {
                    edited = null;
                } else {
                    buried = new Grave(parents.get(operand.id()), operand);
                    edited = replace(root, operand.id(), null);
                }
            }
            case RESTORE_OPERAND -> {
                Grave raised = any(graves.stream().filter(g -> nodes.containsKey(g.parent())).toList(), random);
                edited = raised == null ? null : addChild(root, raised.parent(), raised.subtree());
            }
            default -> throw new IllegalStateException();
        }
        if (edited == null || !wellFormed(edited) || canonical(edited).equals(canonical(root))) {
            return null;
        }
        Optional<Node> resolved = resolve(versions, edited);
        if (resolved.isEmpty()) {
            return null;
        }
        if (buried != null) {
            graves.add(buried);
        }
        // A grave whose operand is live again, restored or resurrected by the ledger, is spent:
        // its content may change before it is next deleted, and that deletion leaves its own grave.
        java.util.Set<UUID> live = nodes(resolved.get()).keySet();
        graves.removeIf(grave -> live.contains(grave.subtree().id()));
        return new Version(kind, resolved.get());
    }

    /**
     * Moves a role or role group out of a set's AND that keeps another operand, into a set of
     * the other kind: the necessary set from a sufficient one, a sufficient set from the
     * necessary one; a set is created when there is none to receive it.
     */
    private static Node move(Node root, Map<UUID, Node> nodes, Map<UUID, UUID> parents, Random random) {
        Node moving = any(nodes.values().stream().filter(n -> {
            if (n.kind() != Kind.ROLE) {
                return false;
            }
            Node and = nodes.get(parents.get(n.id()));
            return and.kind() == Kind.AND && nodes.get(parents.get(and.id())).isSet() && and.children().size() > 1;
        }).toList(), random);
        if (moving == null) {
            return null;
        }
        Node from = nodes.get(parents.get(parents.get(moving.id())));
        Kind toKind = from.kind() == Kind.NECESSARY ? Kind.SUFFICIENT : Kind.NECESSARY;
        Node to = any(root.children().stream().filter(set -> set.kind() == toKind).toList(), random);
        Node removed = replace(root, moving.id(), null);
        if (to == null) {
            Node set = node(toKind, random, List.of(node(Kind.AND, random, List.of(moving))));
            return addChild(removed, root.id(), set);
        }
        return addChild(removed, to.children().getFirst().id(), moving);
    }

    private static <T> T any(List<T> candidates, Random random) {
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    private static ConceptFacade otherConcept(ConceptFacade concept, Random random) {
        ConceptFacade other;
        do {
            other = RandomDefinitions.pick(CONCEPTS, random);
        } while (other.nid() == concept.nid());
        return other;
    }

    // ----- the identity ledger -----

    /** A UUID as it was last seen: its subtree and its parent. */
    private record Retired(Node subtree, UUID parent) {
    }

    /** Every UUID any version holds, as last seen. */
    private static Map<UUID, Retired> ledger(List<Version> history) {
        Map<UUID, Retired> ledger = new HashMap<>();
        for (Version version : history) {
            Map<UUID, UUID> parents = parents(version.root());
            nodes(version.root()).forEach((id, node) -> ledger.put(id, new Retired(node, parents.get(id))));
        }
        return ledger;
    }

    /**
     * The edited tree with the ledger applied to its newborns: a topmost newborn (one whose parent
     * an earlier version holds) takes back the UUIDs of a retired vertex last seen under the same
     * parent with the same key and the same content (equal keys are told apart by content, and
     * the same content is the strongest overlap there is). Empty when retired vertices there have
     * the newborn's key but none its content: whether that is a resurrection is the matcher's
     * judgment, on a threshold the oracle exists to set.
     */
    static Optional<Node> resolve(List<Version> history, Node edited) {
        Map<UUID, Retired> ledger = ledger(history);
        Map<UUID, Node> nodes = nodes(edited);
        java.util.Set<UUID> live = nodes.keySet();
        Map<UUID, UUID> parents = parents(edited);
        Node resolved = edited;
        for (Node node : nodes.values()) {
            UUID parent = parents.get(node.id());
            if (parent == null || ledger.containsKey(node.id()) || !ledger.containsKey(parent)) {
                continue;
            }
            boolean operand = nodes.get(parent).hasOperands();
            String key = key(node, operand);
            List<Retired> sameKey = ledger.entrySet().stream()
                    .filter(entry -> !live.contains(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .filter(retired -> parent.equals(retired.parent()) && key(retired.subtree(), operand).equals(key))
                    .toList();
            if (sameKey.isEmpty()) {
                continue;
            }
            String shape = canonical(node);
            List<Retired> sameContent = sameKey.stream().filter(retired -> canonical(retired.subtree()).equals(shape)).toList();
            if (sameContent.size() != 1) {
                return Optional.empty();
            }
            resolved = replace(resolved, node.id(), withIdsOf(node, sameContent.getFirst().subtree()));
        }
        return Optional.of(resolved);
    }

    /** The subtree carrying the UUIDs of an isomorphic one, children paired by structure. */
    private static Node withIdsOf(Node node, Node source) {
        List<Node> children = new ArrayList<>();
        List<Node> unpaired = new ArrayList<>(source.children());
        for (Node child : node.children()) {
            String shape = canonical(child);
            Node pair = unpaired.stream().filter(s -> canonical(s).equals(shape)).findFirst().orElseThrow();
            unpaired.remove(pair);
            children.add(withIdsOf(child, pair));
        }
        return node.withId(source.id()).withChildren(children);
    }

    // ----- expected outcomes -----

    /** What happened to each vertex of a version, by its UUID, relative to the versions before it. */
    static Map<UUID, Outcome> outcomes(List<Version> history, Node version) {
        Map<UUID, Retired> ledger = ledger(history);
        Node previous = history.getLast().root();
        Map<UUID, Node> previousNodes = nodes(previous);
        Map<UUID, UUID> previousParents = parents(previous);
        Map<UUID, Outcome> outcomes = new LinkedHashMap<>();
        outcomes(version, null, false, ledger, previousNodes, previousParents, outcomes);
        return outcomes;
    }

    private static void outcomes(Node node, UUID parent, boolean ancestorMoved, Map<UUID, Retired> ledger,
                                 Map<UUID, Node> previousNodes, Map<UUID, UUID> previousParents,
                                 Map<UUID, Outcome> outcomes) {
        Outcome outcome;
        if (!ledger.containsKey(node.id())) {
            outcome = Outcome.NEWBORN;
        } else if (!previousNodes.containsKey(node.id())) {
            outcome = Outcome.RESURRECTED;
        } else if (parent != null && !parent.equals(previousParents.get(node.id()))) {
            outcome = Outcome.MOVED;
        } else if (ancestorMoved) {
            outcome = Outcome.MOVED_WITH_PARENT;
        } else if (!label(node).equals(label(previousNodes.get(node.id())))) {
            outcome = Outcome.UPDATED;
        } else {
            outcome = Outcome.UNCHANGED;
        }
        outcomes.put(node.id(), outcome);
        boolean moved = ancestorMoved || outcome == Outcome.MOVED;
        node.children().forEach(child -> outcomes(child, node.id(), moved, ledger, previousNodes, previousParents, outcomes));
    }

    // ----- text -----

    private static String toString(Node node, String indent) {
        StringBuilder text = new StringBuilder().append(indent).append(node.kind()).append(label(node))
                .append(' ').append(node.id().toString(), 0, 8).append('\n');
        node.children().forEach(child -> text.append(toString(child, indent + "  ")));
        return text.toString();
    }
}
