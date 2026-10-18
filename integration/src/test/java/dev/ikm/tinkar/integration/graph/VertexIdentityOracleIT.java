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
import dev.ikm.tinkar.entity.graph.DiTreeCorrelation;
import dev.ikm.tinkar.entity.graph.DiTreeCorrelation.Phase;
import dev.ikm.tinkar.entity.graph.DiTreeIsomorphism;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.graph.VertexIdentityChains.Edit;
import dev.ikm.tinkar.integration.graph.VertexIdentityChains.Kind;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.integration.graph.VertexIdentityChains.Node;
import dev.ikm.tinkar.integration.graph.VertexIdentityChains.Outcome;
import dev.ikm.tinkar.integration.graph.VertexIdentityChains.Version;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.eclipse.collections.api.list.ImmutableList;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static dev.ikm.tinkar.integration.graph.DiTreeShuffle.shuffle;
import static dev.ikm.tinkar.integration.graph.DiTreeShuffle.strip;
import static dev.ikm.tinkar.integration.graph.DiTreeShuffle.uuid;
import static dev.ikm.tinkar.integration.graph.DiTreeShuffle.uuidAt;
import static dev.ikm.tinkar.integration.graph.DiTreeShuffle.withUuids;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The edit-chain oracle for vertex identity (arch-vertex-identity-verification;
 * IKE-Network/ike-issues#1310): chains of versions from {@link VertexIdentityChains}, whose
 * identities the identity rule fixes, against a {@link Correlator}.
 *
 * <p>Each version after the first is a case: the earlier versions as history, and the version
 * as an incoming tree with its vertex indexes and child order shuffled and, unless the case
 * carries its identities, its UUIDs replaced by fresh ones. A vertex is recovered when the
 * correlation gives it the UUID the rule expects, or, for a newborn, any UUID history never
 * held. Recovery is reported per edit, per outcome and, for the phased matcher, per phase.
 *
 * <p>The generator and the oracle are tested here and must pass. The baseline,
 * {@link Correlator#CARRIED_ONLY}, must score exactly what phase 1 alone can; the phased
 * matcher, {@link DiTreeCorrelation} as {@link Correlator#PHASED}, must recover every
 * identity, and give the same UUIDs to the same inputs shuffled.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VertexIdentityOracleIT {

    private static final long SEED = 20261017L;
    private static final int CHAINS = 300;
    private static final int EDITS = 12;

    /** The correlation the strict tests measure. */
    private static final Correlator MATCHER = Correlator.PHASED;

    /**
     * What the oracle measures: given the stored versions of one definition, oldest first, and
     * an incoming tree, the incoming tree with each vertex holding the UUID it continues or one
     * history never held.
     */
    @FunctionalInterface
    interface Correlator {

        DiTreeEntity correlate(List<DiTreeEntity> history, DiTreeEntity incoming);

        /**
         * Phase 1 alone, the baseline: every UUID the incoming tree carries is kept, so a tree
         * that carries its identities is correlated perfectly and one that carries none comes
         * out all newborns.
         */
        Correlator CARRIED_ONLY = (history, incoming) -> incoming;

        /** The phased matcher. */
        Correlator PHASED = (history, incoming) -> DiTreeCorrelation.correlate(history, incoming).tree();
    }

    private List<List<Version>> chains;

    @BeforeAll
    void startStore() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
        chains = chains(SEED);
    }

    @AfterAll
    void stopStore() {
        TestHelper.stopDatabase();
    }

    private static List<List<Version>> chains(long seed) {
        Random random = new Random(seed);
        List<List<Version>> chains = new ArrayList<>();
        for (int i = 0; i < CHAINS; i++) {
            chains.add(VertexIdentityChains.chain(random, EDITS));
        }
        return chains;
    }

    // ----- cases -----

    /**
     * One version to correlate.
     *
     * @param edit         the edit that made it
     * @param history      the versions before it, shuffled, with their UUIDs
     * @param incoming     the version, shuffled, its UUIDs fresh unless carried
     * @param expected     by incoming vertex index, the UUID the rule gives the vertex
     * @param outcomes     by incoming vertex index, what happened to the vertex
     * @param historyUuids every UUID the history holds
     */
    record Case(Edit edit, List<DiTreeEntity> history, DiTreeEntity incoming, UUID[] expected, Outcome[] outcomes,
                Set<UUID> historyUuids) {
    }

    private List<Case> cases(boolean carried, Random random) {
        List<Case> cases = new ArrayList<>();
        for (List<Version> chain : chains) {
            for (int i = 1; i < chain.size(); i++) {
                cases.add(caseOf(chain.get(i).edit(), chain.subList(0, i), chain.get(i).root(), carried, random));
            }
        }
        return cases;
    }

    /** The case of a version made by an edit, the versions before it as history. */
    private static Case caseOf(Edit edit, List<Version> before, Node root, boolean carried, Random random) {
        List<DiTreeEntity> history = new ArrayList<>();
        Set<UUID> historyUuids = new HashSet<>();
        for (Version version : before) {
            history.add(shuffle(VertexIdentityChains.build(version.root()), random).tree());
            historyUuids.addAll(VertexIdentityChains.nodes(version.root()).keySet());
        }
        DiTreeEntity truth = VertexIdentityChains.build(root);
        DiTreeShuffle.Shuffled shuffled = shuffle(carried ? truth : strip(truth, random), random);
        int[] original = shuffled.originalIndex();
        Map<UUID, Outcome> byUuid = VertexIdentityChains.outcomes(before, root);
        UUID[] expected = new UUID[original.length];
        Outcome[] outcomes = new Outcome[original.length];
        for (int j = 0; j < original.length; j++) {
            expected[j] = uuidAt(truth, original[j]);
            outcomes[j] = byUuid.get(expected[j]);
        }
        return new Case(edit, history, shuffled.tree(), expected, outcomes, historyUuids);
    }

    // ----- cases for the container phase -----

    /*
     * No edit of the chains both moves a subtree and changes it, so every vertex they make is
     * settled before the container phase runs. These cases, built from the chains' versions,
     * are the evidence for its threshold; each is labelled with the edit it extends.
     */

    /**
     * A set changes meaning, necessary to sufficient or back, as LogicalExpressionBuilder's
     * changeSetType does: under the rule that is a replace (arch-vertex-identity-birth, "Editors
     * Do Not Mint"), so the set is born and its AND moves into it, keeping its identity and its
     * subtree's. Labelled MOVE_BETWEEN_SETS.
     */
    private List<Case> setTypeChanges(Random random) {
        List<Case> cases = new ArrayList<>();
        for (List<Version> chain : chains) {
            Node root = chain.getLast().root();
            Node set = root.children().get(random.nextInt(root.children().size()));
            Kind flipped = set.kind() == Kind.NECESSARY ? Kind.SUFFICIENT : Kind.NECESSARY;
            Node born = new Node(flipped, uuid(random), null, null, null, 0, set.children());
            Node edited = VertexIdentityChains.replace(root, set.id(), born);
            if (VertexIdentityChains.wellFormed(edited)) {
                VertexIdentityChains.resolve(chain, edited).ifPresent(resolved ->
                        cases.add(caseOf(Edit.MOVE_BETWEEN_SETS, chain, resolved, false, random)));
            }
        }
        return cases;
    }

    /**
     * Two role groups of one AND, of three members or more, each have a member's filler
     * retargeted in the same version: the groups share a key, so they pair by the overlap of
     * their matched descendants (assignment), and every UUID is kept, the fillers updated.
     *
     * <p>As in the chains, an edit the rule leaves to judgment is not made: a changed group or
     * member that looks like one history holds (two edits at once can turn one into another,
     * which the anchors then read as a move, or into one that retired), and two groups that
     * kept the same members, which nothing tells apart. Groups of two, each keeping half its
     * members, are judgment as for a move: when the kept halves are twins the groups have no
     * matched descendants to pair by, and are born again. Labelled RETARGET_FILLER.
     */
    private static List<Case> twoGroupsChange(Random random) {
        List<Case> cases = new ArrayList<>();
        // Groups of three are rare enough to need chains of their own.
        for (int c = 0; c < 4 * CHAINS; c++) {
            List<Version> chain = VertexIdentityChains.chain(random, EDITS);
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < chain.size(); i++) {
                VertexIdentityChains.nodes(chain.get(i).root()).values()
                        .forEach(node -> seen.add(VertexIdentityChains.canonical(node)));
                Node root = chain.get(i).root();
                for (Node and : VertexIdentityChains.nodes(root).values()) {
                    List<Node> groups = and.kind() != Kind.AND ? List.of() : and.children().stream()
                            .filter(child -> child.isGroup() && child.children().getFirst().children().size() >= 3).toList();
                    for (int a = 0; a < groups.size(); a++) {
                        for (int b = a + 1; b < groups.size(); b++) {
                            Node edited = root;
                            boolean judgment = false;
                            List<List<String>> kept = new ArrayList<>();
                            for (Node group : List.of(groups.get(a), groups.get(b))) {
                                List<Node> members = group.children().getFirst().children();
                                Node member = members.get(random.nextInt(members.size()));
                                kept.add(members.stream().filter(m -> m != member)
                                        .map(VertexIdentityChains::canonical).sorted().toList());
                                Node filler = member.children().getFirst();
                                edited = VertexIdentityChains.replace(edited, filler.id(),
                                        filler.withConcept(otherConcept(filler.concept(), random)));
                                Map<UUID, Node> now = VertexIdentityChains.nodes(edited);
                                judgment |= seen.contains(VertexIdentityChains.canonical(now.get(group.id())))
                                        || seen.contains(VertexIdentityChains.canonical(now.get(member.id())));
                            }
                            judgment |= kept.get(0).equals(kept.get(1));
                            if (!judgment && VertexIdentityChains.wellFormed(edited)) {
                                cases.add(caseOf(Edit.RETARGET_FILLER, chain.subList(0, i + 1), edited, false, random));
                            }
                        }
                    }
                }
            }
        }
        return cases;
    }

    private static ConceptFacade otherConcept(ConceptFacade concept, Random random) {
        ConceptFacade other;
        do {
            other = RandomDefinitions.pick(RandomDefinitions.CONCEPTS, random);
        } while (other.nid() == concept.nid());
        return other;
    }

    /**
     * A role group moves between sets and, in the same version, one of its members' fillers is
     * retargeted. The container phase continues a vertex when most of its matched descendants
     * lie in the earlier one's subtree (arch-vertex-identity-correlation), so:
     *
     * <ul>
     *   <li>with three members or more, most are unchanged: the group, its AND and its members
     *       keep their UUIDs, the filler updated;
     *   <li>with two, half are: the group, its AND and the changed member are born again, and
     *       the unchanged member moves into the new group, as an unchanged subtree does.
     * </ul>
     *
     * <p>The two bound the container threshold from both sides. Labelled MOVE_BETWEEN_SETS.
     *
     * @param most whether the groups have three members or more, rather than two
     */
    private List<Case> movesThatChange(Random random, boolean most) {
        List<Case> cases = new ArrayList<>();
        for (List<Version> chain : chains) {
            for (int i = 1; i < chain.size(); i++) {
                if (chain.get(i).edit() != Edit.MOVE_BETWEEN_SETS) {
                    continue;
                }
                List<Version> before = chain.subList(0, i);
                Node root = chain.get(i).root();
                Map<UUID, Node> nodes = VertexIdentityChains.nodes(root);
                VertexIdentityChains.outcomes(before, root).forEach((id, outcome) -> {
                    Node moved = nodes.get(id);
                    if (outcome != Outcome.MOVED || !moved.isGroup()
                            || (most ? moved.children().getFirst().children().size() < 3
                                     : moved.children().getFirst().children().size() != 2)) {
                        return;
                    }
                    Node and = moved.children().getFirst();
                    List<Node> members = and.children();
                    int changed = random.nextInt(members.size());
                    Node member = members.get(changed);
                    Node filler = member.children().getFirst();
                    Node retargeted = member.withChildren(List.of(filler.withConcept(otherConcept(filler.concept(), random))));
                    Node group;
                    if (most) {
                        List<Node> kept = new ArrayList<>(members);
                        kept.set(changed, retargeted);
                        group = moved.withChildren(List.of(and.withChildren(kept)));
                    } else {
                        List<Node> reborn = new ArrayList<>(members);
                        reborn.set(changed, VertexIdentityChains.refreshed(retargeted, random));
                        group = moved.withId(uuid(random)).withChildren(List.of(and.withId(uuid(random)).withChildren(reborn)));
                    }
                    Node edited = VertexIdentityChains.replace(root, moved.id(), group);
                    if (VertexIdentityChains.wellFormed(edited)) {
                        cases.add(caseOf(Edit.MOVE_BETWEEN_SETS, before, edited, false, random));
                    }
                });
            }
        }
        return cases;
    }

    // ----- the oracle -----

    /**
     * Whether each vertex of an answer is recovered, by incoming vertex index; null when the
     * answer is not the incoming tree with other UUIDs.
     */
    private static boolean[] recovered(Case c, DiTreeEntity answer) {
        DiTreeEntity incoming = c.incoming();
        int size = incoming.vertexMap().size();
        if (answer == null || answer.vertexMap().size() != size
                || answer.root().vertexIndex() != incoming.root().vertexIndex()
                || !DiTreeIsomorphism.isomorphic(answer, incoming)) {
            return null;
        }
        Map<UUID, Integer> uses = new HashMap<>();
        for (int j = 0; j < size; j++) {
            if (answer.vertex(j).getMeaningNid() != incoming.vertex(j).getMeaningNid()
                    || !answer.successors(j).toSortedList().equals(incoming.successors(j).toSortedList())) {
                return null;
            }
            uses.merge(uuidAt(answer, j), 1, Integer::sum);
        }
        boolean[] recovered = new boolean[size];
        for (int j = 0; j < size; j++) {
            UUID got = uuidAt(answer, j);
            UUID expected = c.expected()[j];
            boolean unique = uses.get(got) == 1;
            recovered[j] = unique && (c.historyUuids().contains(expected)
                    ? got.equals(expected)
                    : !c.historyUuids().contains(got));
        }
        return recovered;
    }

    private static boolean exact(boolean[] recovered) {
        if (recovered == null) {
            return false;
        }
        for (boolean vertex : recovered) {
            if (!vertex) {
                return false;
            }
        }
        return true;
    }

    /** Recovery over many cases, per edit and per outcome. */
    private static final class Score {
        final Map<Edit, int[]> byEdit = new EnumMap<>(Edit.class);          // versions, exact
        final Map<Outcome, int[]> byOutcome = new EnumMap<>(Outcome.class); // vertices, recovered
        final Map<Phase, int[]> byPhase = new EnumMap<>(Phase.class);       // vertices, recovered
        int malformed;

        /** Which phase gave each vertex its UUID, and whether that UUID is the one the rule expects. */
        void addPhases(boolean[] recovered, ImmutableList<Phase> phases) {
            for (int j = 0; j < phases.size(); j++) {
                int[] counts = byPhase.computeIfAbsent(phases.get(j), p -> new int[2]);
                counts[0]++;
                if (recovered != null && recovered[j]) {
                    counts[1]++;
                }
            }
        }

        void add(Case c, boolean[] recovered) {
            int[] edit = byEdit.computeIfAbsent(c.edit(), e -> new int[2]);
            edit[0]++;
            if (exact(recovered)) {
                edit[1]++;
            }
            if (recovered == null) {
                malformed++;
            }
            for (int j = 0; j < c.outcomes().length; j++) {
                int[] outcome = byOutcome.computeIfAbsent(c.outcomes()[j], o -> new int[2]);
                outcome[0]++;
                if (recovered != null && recovered[j]) {
                    outcome[1]++;
                }
            }
        }

        int versions() {
            return byEdit.values().stream().mapToInt(counts -> counts[0]).sum();
        }

        int exactVersions() {
            return byEdit.values().stream().mapToInt(counts -> counts[1]).sum();
        }

        double recovery(Outcome outcome) {
            int[] counts = byOutcome.get(outcome);
            return counts == null || counts[0] == 0 ? Double.NaN : (double) counts[1] / counts[0];
        }

        String report(String label) {
            StringBuilder text = new StringBuilder(String.format("VertexIdentityOracleIT, %s: %,d versions, "
                    + "%,d exact, %,d malformed%n", label, versions(), exactVersions(), malformed));
            text.append(String.format("  %-22s %9s %9s%n", "edit", "versions", "exact"));
            byEdit.forEach((edit, counts) -> text.append(String.format("  %-22s %,9d %8.1f%%%n", edit, counts[0],
                    100.0 * counts[1] / counts[0])));
            text.append(String.format("  %-22s %9s %9s%n", "outcome", "vertices", "recovered"));
            byOutcome.forEach((outcome, counts) -> text.append(String.format("  %-22s %,9d %8.1f%%%n", outcome,
                    counts[0], 100.0 * counts[1] / counts[0])));
            if (!byPhase.isEmpty()) {
                text.append(String.format("  %-22s %9s %9s%n", "phase", "vertices", "right"));
                byPhase.forEach((phase, counts) -> text.append(String.format("  %-22s %,9d %8.1f%%%n", phase,
                        counts[0], 100.0 * counts[1] / counts[0])));
            }
            return text.toString();
        }
    }

    private static Score score(List<Case> cases, Function<Case, DiTreeEntity> answer) {
        Score score = new Score();
        for (Case c : cases) {
            score.add(c, recovered(c, answer.apply(c)));
        }
        return score;
    }

    /** The phased matcher's score, with each vertex's phase, at a container threshold. */
    private static Score scorePhased(List<Case> cases, double containerDice) {
        Score score = new Score();
        for (Case c : cases) {
            DiTreeCorrelation.Correlation correlation =
                    DiTreeCorrelation.correlate(c.history(), c.incoming(), containerDice);
            boolean[] recovered = recovered(c, correlation.tree());
            score.add(c, recovered);
            score.addPhases(recovered, correlation.phases());
        }
        return score;
    }

    private static Function<Case, DiTreeEntity> correlatedBy(Correlator correlation) {
        return c -> correlation.correlate(c.history(), c.incoming());
    }

    /** The answer the rule gives: what a perfect matcher returns. */
    private static DiTreeEntity truth(Case c) {
        return withUuids(c.incoming(), j -> c.expected()[j]);
    }

    // ----- the generator -----

    @Test
    void chainsAreWellFormedAndRepeatable() {
        for (List<Version> chain : chains) {
            for (Version version : chain) {
                assertTrue(VertexIdentityChains.wellFormed(version.root()), version.root()::toString);
                DiTreeEntity tree = VertexIdentityChains.build(version.root());
                Map<UUID, Node> nodes = VertexIdentityChains.nodes(version.root());
                assertEquals(nodes.size(), tree.vertexMap().size(), "a vertex per node");
                for (int i = 0; i < tree.vertexMap().size(); i++) {
                    assertTrue(nodes.containsKey(uuidAt(tree, i)), "every vertex carries its node's UUID");
                }
            }
        }
        assertEquals(chains, chains(SEED), "the same seed makes the same chains, UUIDs included");
    }

    @Test
    void everyEditAndOutcomeIsExercised() {
        Map<Edit, Integer> edits = new EnumMap<>(Edit.class);
        Map<Outcome, Integer> outcomes = new EnumMap<>(Outcome.class);
        for (List<Version> chain : chains) {
            for (int i = 1; i < chain.size(); i++) {
                edits.merge(chain.get(i).edit(), 1, Integer::sum);
                VertexIdentityChains.outcomes(chain.subList(0, i), chain.get(i).root()).values()
                        .forEach(outcome -> outcomes.merge(outcome, 1, Integer::sum));
            }
        }
        System.out.println("VertexIdentityOracleIT: edits " + edits + "; outcomes " + outcomes);
        for (Edit edit : Edit.values()) {
            assertTrue(edits.getOrDefault(edit, 0) >= 50, "exercised: " + edit + " " + edits);
        }
        for (Outcome outcome : Outcome.values()) {
            assertTrue(outcomes.getOrDefault(outcome, 0) >= 50, "exercised: " + outcome + " " + outcomes);
        }
    }

    /**
     * The expectations obey the identity rule and each edit's promise: a UUID's meaning never
     * changes, an operand's key never changes under a kept UUID, and each edit retires, bears,
     * moves and updates exactly what it says.
     */
    @Test
    void expectedIdentitiesFollowTheRule() {
        for (List<Version> chain : chains) {
            Map<UUID, Kind> kinds = new HashMap<>();
            Map<UUID, String> operandKeys = new HashMap<>();
            for (int i = 0; i < chain.size(); i++) {
                Node root = chain.get(i).root();
                Map<UUID, Node> nodes = VertexIdentityChains.nodes(root);
                Map<UUID, UUID> parents = VertexIdentityChains.parents(root);
                for (Node node : nodes.values()) {
                    Kind before = kinds.putIfAbsent(node.id(), node.kind());
                    assertTrue(before == null || before == node.kind(), "a UUID's meaning never changes");
                    if (parents.containsKey(node.id()) && nodes.get(parents.get(node.id())).hasOperands()) {
                        String key = VertexIdentityChains.key(node, true);
                        String keyBefore = operandKeys.putIfAbsent(node.id(), key);
                        assertTrue(keyBefore == null || keyBefore.equals(key),
                                "an identifying property never changes under a kept UUID");
                    }
                }
                if (i > 0) {
                    checkEdit(chain.subList(0, i), chain.get(i));
                }
            }
        }
    }

    private static void checkEdit(List<Version> before, Version version) {
        Map<UUID, Outcome> outcomes = VertexIdentityChains.outcomes(before, version.root());
        Map<Outcome, Integer> counts = new EnumMap<>(Outcome.class);
        outcomes.values().forEach(outcome -> counts.merge(outcome, 1, Integer::sum));
        Set<UUID> retired = new HashSet<>(VertexIdentityChains.nodes(before.getLast().root()).keySet());
        retired.removeAll(outcomes.keySet());
        Map<UUID, Node> nodes = VertexIdentityChains.nodes(version.root());
        int updated = counts.getOrDefault(Outcome.UPDATED, 0);
        int moved = counts.getOrDefault(Outcome.MOVED, 0);
        int born = counts.getOrDefault(Outcome.NEWBORN, 0);
        int resurrected = counts.getOrDefault(Outcome.RESURRECTED, 0);
        String context = version.edit() + " " + counts + " retired " + retired.size() + "\n" + version.root();
        switch (version.edit()) {
            case RETARGET_FILLER, UPDATE_FEATURE_VALUE -> {
                assertEquals(1, updated, context);
                Kind kind = version.edit() == Edit.RETARGET_FILLER ? Kind.CONCEPT : Kind.FEATURE;
                outcomes.forEach((id, outcome) -> assertTrue(outcome != Outcome.UPDATED || nodes.get(id).kind() == kind, context));
                assertTrue(moved == 0 && born == 0 && resurrected == 0 && retired.isEmpty(), context);
            }
            case RETARGET_OPERAND -> {
                assertEquals(1, born + resurrected, context);
                assertEquals(1, retired.size(), context);
                assertTrue(updated == 0 && moved == 0, context);
            }
            case MOVE_BETWEEN_SETS -> {
                assertEquals(1, moved, context);
                outcomes.forEach((id, outcome) -> assertTrue(outcome != Outcome.MOVED || nodes.get(id).kind() == Kind.ROLE, context));
                assertTrue(born == 0 || born == 2, "nothing is born but a set to receive it, and its AND: " + context);
                assertTrue(updated == 0 && resurrected == 0 && retired.isEmpty(), context);
            }
            case ADD_OPERAND -> {
                assertTrue(born + resurrected >= 1, context);
                assertTrue(updated == 0 && moved == 0 && retired.isEmpty(), context);
            }
            case DELETE_OPERAND -> {
                assertFalse(retired.isEmpty(), context);
                assertTrue(updated == 0 && moved == 0 && born == 0 && resurrected == 0, context);
            }
            case RESTORE_OPERAND -> {
                assertTrue(resurrected >= 1, context);
                assertTrue(updated == 0 && moved == 0 && born == 0 && retired.isEmpty(), context);
            }
        }
    }

    /**
     * A resurrected subtree is what the ledger gives the same subtree arriving with fresh
     * UUIDs, so a restore and a re-add of what was deleted agree.
     */
    @Test
    void resurrectionIsWhatTheLedgerGives() {
        Random random = new Random(SEED);
        int resurrections = 0;
        for (List<Version> chain : chains) {
            for (int i = 1; i < chain.size(); i++) {
                Node root = chain.get(i).root();
                Set<UUID> resurrected = new HashSet<>();
                VertexIdentityChains.outcomes(chain.subList(0, i), root).forEach((id, outcome) -> {
                    if (outcome == Outcome.RESURRECTED) {
                        resurrected.add(id);
                    }
                });
                if (!resurrected.isEmpty()) {
                    resurrections++;
                    Node renewed = renew(root, resurrected, random);
                    int at = i;
                    assertEquals(root, VertexIdentityChains.resolve(chain.subList(0, i), renewed).orElse(null),
                            () -> chain.get(at).edit() + "\n" + chain.subList(0, at) + "\n" + root);
                }
            }
        }
        assertTrue(resurrections >= 50, "resurrections exercised: " + resurrections);
    }

    private static Node renew(Node node, Set<UUID> ids, Random random) {
        Node renewed = ids.contains(node.id()) ? node.withId(uuid(random)) : node;
        return renewed.withChildren(node.children().stream().map(child -> renew(child, ids, random)).toList());
    }

    // ----- the oracle -----

    @Test
    void theOracleScoresTheTrueAnswerPerfectly() {
        List<Case> cases = cases(false, new Random(SEED));
        Score score = score(cases, VertexIdentityOracleIT::truth);
        assertEquals(score.versions(), score.exactVersions(), () -> score.report("the true answer"));
    }

    /** Each wrong answer is caught in every case it can be made in. */
    @Test
    void theOracleCatchesWrongAnswers() {
        Random random = new Random(SEED);
        List<Case> cases = cases(false, random);
        int swapped = 0;
        int bornOld = 0;
        int keptFresh = 0;
        for (Case c : cases) {
            UUID[] answer = c.expected().clone();
            int[] kept = keptIndexes(c);
            // two kept vertices swap UUIDs
            if (kept.length >= 2) {
                UUID[] wrong = answer.clone();
                wrong[kept[0]] = answer[kept[1]];
                wrong[kept[1]] = answer[kept[0]];
                assertFalse(exact(recovered(c, withUuids(c.incoming(), j -> wrong[j]))), "a swap is caught");
                swapped++;
            }
            // a newborn takes a UUID history holds
            for (int j = 0; j < answer.length; j++) {
                if (!c.historyUuids().contains(answer[j])) {
                    UUID[] wrong = answer.clone();
                    wrong[j] = c.historyUuids().iterator().next();
                    assertFalse(exact(recovered(c, withUuids(c.incoming(), k -> wrong[k]))), "a false continuity is caught");
                    bornOld++;
                    break;
                }
            }
            // a kept vertex is given a fresh UUID
            if (kept.length >= 1) {
                UUID[] wrong = answer.clone();
                wrong[kept[0]] = uuid(random);
                assertFalse(exact(recovered(c, withUuids(c.incoming(), j -> wrong[j]))), "a lost identity is caught");
                keptFresh++;
            }
        }
        assertTrue(swapped > 0 && bornOld > 0 && keptFresh > 0);
    }

    private static int[] keptIndexes(Case c) {
        List<Integer> kept = new ArrayList<>();
        for (int j = 0; j < c.expected().length; j++) {
            if (c.historyUuids().contains(c.expected()[j])) {
                kept.add(j);
            }
        }
        return kept.stream().mapToInt(Integer::intValue).toArray();
    }

    // ----- the placeholder -----

    @Test
    void thePlaceholderRecoversCarriedIdentities() {
        Score score = score(cases(true, new Random(SEED)), correlatedBy(Correlator.CARRIED_ONLY));
        assertEquals(score.versions(), score.exactVersions(), () -> score.report("carried, placeholder"));
    }

    /**
     * Trees that carry no identity come out of the placeholder all newborns: every newborn is
     * recovered, and nothing else is. The report is the baseline the matcher improves on.
     */
    @Test
    void thePlaceholderRecoversOnlyNewbornsFromStrippedTrees() {
        Score score = score(cases(false, new Random(SEED)), correlatedBy(Correlator.CARRIED_ONLY));
        System.out.print(score.report("stripped, placeholder"));
        assertEquals(0, score.malformed);
        for (Outcome outcome : Outcome.values()) {
            assertEquals(outcome == Outcome.NEWBORN ? 1.0 : 0.0, score.recovery(outcome), outcome::name);
        }
    }

    // ----- the matcher -----

    /** The matcher recovers every identity the rule fixes, from trees that carry none. */
    @Test
    void theMatcherRecoversEveryIdentity() {
        List<Case> cases = cases(false, new Random(SEED));
        Score phased = scorePhased(cases, DiTreeCorrelation.CONTAINER_DICE);
        System.out.print(phased.report("stripped, matcher"));
        Score score = score(cases, correlatedBy(MATCHER));
        assertEquals(score.versions(), score.exactVersions(), () -> score.report("stripped, matcher"));
    }

    /**
     * The matcher recovers every identity in the cases that reach the container phase: a set
     * whose meaning changed, and a group that moved and changed, mostly or by half; and the
     * cases that reach assignment: two groups of one AND that both changed.
     */
    @Test
    void theMatcherRecoversMovesThatChange() {
        for (String family : List.of("set type changes", "moves that change most", "moves that change half",
                "two groups change")) {
            List<Case> cases = switch (family) {
                case "set type changes" -> setTypeChanges(new Random(SEED));
                case "two groups change" -> twoGroupsChange(new Random(SEED));
                case "moves that change most" -> movesThatChange(new Random(SEED), true);
                default -> movesThatChange(new Random(SEED), false);
            };
            Score score = scorePhased(cases, DiTreeCorrelation.CONTAINER_DICE);
            String label = family + ", matcher";
            System.out.print(score.report(label));
            assertTrue(score.versions() >= 50, () -> family + " exercised: " + score.versions());
            Phase reached = family.startsWith("two") ? Phase.ASSIGNED : Phase.CONTAINED;
            assertTrue(family.endsWith("half") || score.byPhase.containsKey(reached), () -> score.report(label));
            assertEquals(score.versions(), score.exactVersions(), () -> score.report(label));
        }
    }

    /** The matcher keeps every identity a tree carries. */
    @Test
    void theMatcherKeepsCarriedIdentities() {
        Score score = score(cases(true, new Random(SEED)), correlatedBy(MATCHER));
        assertEquals(score.versions(), score.exactVersions(), () -> score.report("carried, matcher"));
    }

    /**
     * The same inputs, shuffled, get the same UUIDs: the history's and the incoming tree's vertex
     * indexes and child order are permuted, and every incoming vertex must come out holding
     * what it held before.
     */
    @Test
    void theMatcherIgnoresIndexAndChildOrder() {
        Random random = new Random(SEED + 1);
        int vertices = 0;
        for (Case c : cases(false, new Random(SEED))) {
            DiTreeEntity answer = MATCHER.correlate(c.history(), c.incoming());
            for (int s = 0; s < 3; s++) {
                List<DiTreeEntity> history = c.history().stream().map(tree -> shuffle(tree, random).tree()).toList();
                DiTreeShuffle.Shuffled incoming = shuffle(c.incoming(), random);
                DiTreeEntity again = MATCHER.correlate(history, incoming.tree());
                for (int j = 0; j < answer.vertexMap().size(); j++) {
                    assertEquals(uuidAt(answer, j), uuidAt(again, incoming.newIndex()[j]), c.edit()::name);
                    vertices++;
                }
            }
        }
        assertTrue(vertices > 0);
    }

    /**
     * The evidence for the container threshold: exact versions and recovery per phase across
     * thresholds, as exact versions and right container matches per family. A threshold too low
     * continues a group that kept only half its members ("moves that change half"); too high,
     * and one that kept most is born again ("moves that change most").
     */
    @Test
    void containerThresholdSweep() {
        Map<String, List<Case>> families = new java.util.LinkedHashMap<>();
        families.put("chains", cases(false, new Random(SEED)));
        families.put("set type changes", setTypeChanges(new Random(SEED)));
        families.put("moves that change most", movesThatChange(new Random(SEED), true));
        families.put("moves that change half", movesThatChange(new Random(SEED), false));
        families.put("two groups change", twoGroupsChange(new Random(SEED)));
        StringBuilder table = new StringBuilder(String.format("VertexIdentityOracleIT, container threshold sweep%n  %-6s",
                "Dice >"));
        families.keySet().forEach(family -> table.append(String.format(" %25s", family)));
        table.append('\n');
        for (double dice : new double[]{0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 0.95}) {
            table.append(String.format("  %6.2f", dice));
            families.values().forEach(cases -> {
                Score score = scorePhased(cases, dice);
                int[] contained = score.byPhase.getOrDefault(Phase.CONTAINED, new int[2]);
                table.append(String.format(" %,6d/%,6d c%,5d/%,5d", score.exactVersions(), score.versions(),
                        contained[1], contained[0]));
            });
            table.append('\n');
        }
        System.out.print(table);
    }
}
