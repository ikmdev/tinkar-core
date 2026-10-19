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
package dev.ikm.tinkar.entity.graph;

import dev.ikm.tinkar.common.id.LongIdList;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.IdentityRule;
import dev.ikm.tinkar.terms.ComponentWithNid;
import dev.ikm.tinkar.terms.ConceptFacade;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import org.eclipse.collections.impl.factory.primitive.LongObjectMaps;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongPredicate;

/**
 * Carries vertex identity from the stored versions of a definition to an incoming version
 * (arch-vertex-identity-correlation; IKE-Network/ike-issues#1310): each incoming vertex gets the
 * UUID of the earlier vertex it continues, or is a newborn.
 *
 * <p>The phases, each deterministic:
 * <ol>
 *   <li>{@link Phase#CARRIED} -- UUIDs the incoming tree already holds from history win.
 *   <li>{@link Phase#DERIVED} -- beneath a matched parent, an operand whose derived UUID
 *       ({@link VertexDerivation}) is that of a leftover earlier child with its key is matched
 *       by lookup: the common case, when history was itself derived. It runs within keyed
 *       recovery, after equal content, so a content change that reorders equal-key siblings
 *       cannot trade their identities.
 *   <li>{@link Phase#ANCHORED} -- subtrees with equal canonical hashes
 *       ({@link DiTreeIsomorphism#vertexHashes}) match anywhere in the tree, largest first, when
 *       the hash is unique among the unmatched vertices of both trees: an unchanged subtree that
 *       moved. A sole successor is identified by its position, not its content, so it anchors
 *       only with its parent: when one set's operands move to another, the sets keep their
 *       ANDs rather than trading them.
 *   <li>{@link Phase#KEYED} -- top down from the root, a matched pair's unmatched children
 *       match by the identity rule (arch-vertex-identity-rule): a sole successor by position,
 *       an operand by equal content and then by key, one on each side. Anchors and keyed
 *       recovery then alternate until neither matches more: a moved subtree whose twin stayed
 *       put is unique once the twin is matched.
 *   <li>{@link Phase#ASSIGNED} -- operands with equal keys, several on a side, pair by the
 *       overlap of their matched descendants (Hungarian assignment).
 *   <li>{@link Phase#RESURRECTED} -- an operand still unmatched beneath a matched parent takes
 *       back the identity of a retired vertex last seen under that parent with the same content.
 *   <li>{@link Phase#CONTAINED} -- an unmatched vertex matches an unmatched earlier vertex of the
 *       same key when its matched descendants mostly lie in that vertex's subtree (Dice
 *       coefficient above {@link #CONTAINER_DICE}): a subtree that moved and changed. The rule
 *       phases run again beneath each such match.
 * </ol>
 *
 * <p>A newborn's UUID is derived (arch-vertex-identity-birth): the root's from the seed the
 * caller gives (the semantic's public id and field), any other's from its parent's UUID, its key
 * and its ordinal among equal-key siblings. A derived UUID an inherited vertex holds is never
 * taken: the newborn re-derives with its ordinal bumped. A derived UUID that retired under the
 * same parent with the same meaning comes back ({@link Phase#RESURRECTED}): resurrection in place
 * is free. Without a seed, a newborn root keeps the UUID it arrived with when history never held
 * it.
 *
 * <p>The design places containers before keyed recovery. Here they run after it, because the
 * rule decides first: a vertex whose parent was matched and whose key is unchanged keeps its
 * UUID, and a container match made before it can take that UUID for a subtree that left (a role
 * moved into a new set would take the source set's AND, whose remaining operands are fewer than
 * what moved). Containers then match only what the rule leaves open.
 *
 * <p>Nothing depends on vertex indexes, child order or hash-map iteration: every choice is made
 * in an order of canonical hash, then of path signature (the hashes from the root down, unique
 * per vertex in a tree with no two isomorphic siblings, as a normal form never has) on the
 * incoming side, and then of previous UUID. Between isomorphic siblings, which a normal form
 * never holds, child order decides.
 *
 * <p>The identity ledger is the semantic's stored versions: every UUID they hold, the retired
 * ones as last seen. Versions are immutable, so settled identities stay frozen.
 */
public final class DiTreeCorrelation {

    /**
     * The Dice coefficient a container match must exceed: twice the matched descendants an
     * incoming vertex and an earlier one have in common, over all their descendants. Above it,
     * most of what lies beneath the vertex continues what lay beneath the earlier one.
     *
     * <p>Set from the vertex-identity oracle (VertexIdentityOracleIT.containerThresholdSweep,
     * IKE-Network/ike-issues#1310). A role group that moved and had one of its n members
     * changed scores (n - 1)/n at its AND: a group of three or more that kept most of its
     * members is recovered for any threshold below 2/3, and a group of two that kept half is
     * born again for any threshold of 1/2 or more. The threshold is the lower end of that
     * window, which leaves the widest margin for larger groups that changed more. A set whose
     * meaning changed scores 1 at its AND and is recovered at every threshold measured.
     */
    public static final double CONTAINER_DICE = 0.5;

    /** How a vertex got its UUID. */
    public enum Phase {
        /** The incoming tree held it, from history. */
        CARRIED,
        /** Beneath a matched parent, by its derived UUID. */
        DERIVED,
        /** Part of an unchanged subtree whose hash is unique on both sides. */
        ANCHORED,
        /** Beneath a matched parent, by position, by content or by key. */
        KEYED,
        /** Beneath a matched parent, one of several with an equal key, by descendant overlap. */
        ASSIGNED,
        /** A retired identity, last seen under the same parent with the same content. */
        RESURRECTED,
        /** By the share of its matched descendants. */
        CONTAINED,
        /** None: a UUID history never held. */
        NEWBORN
    }

    /**
     * The correlated tree.
     *
     * @param tree   the incoming tree, each vertex holding the UUID it continues or a newborn's
     * @param phases by vertex index, the phase that gave the vertex its UUID
     */
    public record Correlation(DiTreeEntity tree, ImmutableList<Phase> phases) {
    }

    private DiTreeCorrelation() {
    }

    /**
     * Correlates an incoming tree with the stored versions of its definition.
     *
     * @param history  the stored versions, oldest first, each holding its settled vertex UUIDs
     * @param incoming the new version, whose UUIDs carry identity only where history holds them
     * @return the incoming tree, same indexes, edges, meanings and properties, each vertex
     *         holding a UUID from history (the vertex it continues, live or retired) or one
     *         history never held, no UUID twice
     */
    public static Correlation correlate(List<DiTreeEntity> history, DiTreeEntity incoming) {
        return correlate(history, incoming, CONTAINER_DICE);
    }

    /**
     * Correlates with another container threshold, for measuring it.
     *
     * @param history       as {@link #correlate(List, DiTreeEntity)}
     * @param incoming      as {@link #correlate(List, DiTreeEntity)}
     * @param containerDice the Dice coefficient a container match must exceed
     * @return as {@link #correlate(List, DiTreeEntity)}
     */
    public static Correlation correlate(List<DiTreeEntity> history, DiTreeEntity incoming, double containerDice) {
        return new Matcher(history, incoming, containerDice, null, VertexIdentityTable.LOGICAL, true).run();
    }

    /**
     * Correlates as the write path does, a newborn root taking the UUID derived for the field.
     *
     * @param history  as {@link #correlate(List, DiTreeEntity)}
     * @param incoming as {@link #correlate(List, DiTreeEntity)}
     * @param rootSeed the root's derived UUID ({@link VertexDerivation#root})
     * @return as {@link #correlate(List, DiTreeEntity)}
     */
    public static Correlation correlate(List<DiTreeEntity> history, DiTreeEntity incoming, UUID rootSeed) {
        return correlate(history, incoming, rootSeed, VertexIdentityTable.LOGICAL);
    }

    /**
     * Correlates a tree over any vocabulary, by the identity table that covers it: its rules
     * decide how each vertex's successors are told apart, and its ordered meanings make a
     * vertex's children a sequence, hashed, paired and derived by position.
     *
     * @param history  as {@link #correlate(List, DiTreeEntity)}
     * @param incoming as {@link #correlate(List, DiTreeEntity)}
     * @param rootSeed the root's derived UUID ({@link VertexDerivation#root}), or null
     * @param table    the identity table covering every vertex of the trees
     * @return as {@link #correlate(List, DiTreeEntity)}
     */
    public static Correlation correlate(List<DiTreeEntity> history, DiTreeEntity incoming, UUID rootSeed,
                                        VertexIdentityTable table) {
        return correlate(history, incoming, rootSeed, table, true);
    }

    /**
     * Correlates, deciding whether the incoming tree's own UUIDs carry identity.
     *
     * @param history  as {@link #correlate(List, DiTreeEntity)}
     * @param incoming as {@link #correlate(List, DiTreeEntity)}
     * @param rootSeed as {@link #correlate(List, DiTreeEntity, UUID, VertexIdentityTable)}
     * @param table    as {@link #correlate(List, DiTreeEntity, UUID, VertexIdentityTable)}
     * @param carried  whether {@link Phase#CARRIED} runs; false for a tree whose UUIDs were minted
     *                 by no rule this one recognizes, such as a change set whose identities were
     *                 not derived, so a UUID history happens to share is not taken as continuity
     * @return as {@link #correlate(List, DiTreeEntity)}
     */
    public static Correlation correlate(List<DiTreeEntity> history, DiTreeEntity incoming, UUID rootSeed,
                                        VertexIdentityTable table, boolean carried) {
        return new Matcher(history, incoming, CONTAINER_DICE, rootSeed, table, carried).run();
    }

    // ----- the matcher -----

    /** Where a vertex stands among its siblings: what kind of position a move may keep. */
    private enum Position {
        ROOT, SOLE, OPERAND
    }

    /** What tells a vertex apart from its siblings: its meaning and, as an operand, its identifying values. */
    private record Key(long meaning, List<Object> values) {
    }

    /** A retired identity as last seen. */
    private record Retired(UUID uuid, Tree tree, int index, UUID parent, int version) {
    }

    /** A tree with what the matcher reads from it, computed once. */
    private static final class Tree {
        final DiTreeEntity tree;
        final int size;
        final int root;
        final long[] hashes;
        final int[] parent;
        final int[] subtreeSize;
        final int[] height;
        final int[] depth;
        final int[] pre;
        final int[] post;
        final long[] signature;
        final UUID[] uuid;
        final VertexIdentityTable table;

        Tree(DiTreeEntity tree, VertexIdentityTable table) {
            this.tree = tree;
            this.table = table;
            this.size = tree.vertexMap().size();
            this.root = tree.root().vertexIndex();
            this.hashes = DiTreeIsomorphism.vertexHashes(tree, table::ordered);
            this.parent = new int[size];
            this.subtreeSize = new int[size];
            this.height = new int[size];
            this.depth = new int[size];
            this.pre = new int[size];
            this.post = new int[size];
            this.signature = new long[size];
            this.uuid = new UUID[size];
            Arrays.fill(parent, -1);
            for (int i = 0; i < size; i++) {
                uuid[i] = EntityVertexUuid.of(tree.vertex(i));
            }
            signature[root] = DiTreeIsomorphism.mix(hashes[root]);
            walk(root, new int[1]);
        }

        private void walk(int index, int[] clock) {
            pre[index] = clock[0]++;
            subtreeSize[index] = 1;
            for (int child : children(index).toArray()) {
                parent[child] = index;
                depth[child] = depth[index] + 1;
                signature[child] = DiTreeIsomorphism.mix(signature[index] ^ hashes[child]);
                walk(child, clock);
                subtreeSize[index] += subtreeSize[child];
                height[index] = Math.max(height[index], height[child] + 1);
            }
            post[index] = clock[0]++;
        }

        ImmutableIntList children(int index) {
            return tree.successors(index);
        }

        EntityVertex vertex(int index) {
            return tree.vertex(index);
        }

        long meaning(int index) {
            return tree.vertex(index).getMeaningNid();
        }

        /** Whether {@code index} lies in the subtree rooted at {@code ancestor}, itself included. */
        boolean within(int index, int ancestor) {
            return pre[ancestor] <= pre[index] && post[index] <= post[ancestor];
        }

        /** Whether a vertex's children are operands: so the rule says, or the shape is not the rule's. */
        boolean operands(int index) {
            IdentityRule.Successors successors = table.rule(meaning(index)).successors();
            return successors == IdentityRule.Successors.OPERANDS
                    || successors != IdentityRule.Successors.ORDERED && children(index).size() > 1;
        }

        /** Whether a vertex's children are a sequence, told apart by position. */
        boolean sequence(int index) {
            return table.ordered(meaning(index));
        }

        Position position(int index) {
            if (parent[index] < 0) {
                return Position.ROOT;
            }
            return operands(parent[index]) ? Position.OPERAND : Position.SOLE;
        }

        void subtree(int index, List<Integer> into) {
            into.add(index);
            for (int child : children(index).toArray()) {
                subtree(child, into);
            }
        }
    }

    private static final class Matcher {
        final Tree in;
        final Tree prev;
        final double containerDice;
        final int[] inToPrev;
        final int[] prevToIn;
        final UUID[] assigned;
        final Phase[] phase;
        final boolean[] recovered;
        final Set<UUID> used = new HashSet<>();
        final Set<UUID> historyUuids = new HashSet<>();
        final Map<UUID, Integer> uuidCountIn = new HashMap<>();
        final Map<UUID, Retired> retired = new HashMap<>();
        final Map<UUID, List<Retired>> retiredByParent = new HashMap<>();
        final Map<UUID, Integer> prevByUuid = new HashMap<>();
        final Deque<Integer> work = new ArrayDeque<>();
        final UUID rootSeed;
        final String[] derivationKey;
        final int[] ordinal;
        long[] publicHashes;

        final VertexIdentityTable table;
        final LongPredicate ordered;
        final boolean carriedPhase;

        Matcher(List<DiTreeEntity> history, DiTreeEntity incoming, double containerDice, UUID rootSeed,
                VertexIdentityTable table, boolean carriedPhase) {
            this.table = table;
            this.carriedPhase = carriedPhase;
            this.ordered = table::ordered;
            this.in = new Tree(incoming, table);
            this.rootSeed = rootSeed;
            this.derivationKey = new String[in.size];
            this.ordinal = new int[in.size];
            Arrays.fill(ordinal, -1);
            this.prev = history.isEmpty() ? null : new Tree(history.getLast(), table);
            this.containerDice = containerDice;
            this.inToPrev = new int[in.size];
            this.prevToIn = new int[prev == null ? 0 : prev.size];
            Arrays.fill(inToPrev, -1);
            Arrays.fill(prevToIn, -1);
            this.assigned = new UUID[in.size];
            this.phase = new Phase[in.size];
            this.recovered = new boolean[in.size];
            for (UUID uuid : in.uuid) {
                uuidCountIn.merge(uuid, 1, Integer::sum);
            }
            ledger(history);
        }

        /** Every UUID history holds, and the retired ones as last seen, by their last parent. */
        private void ledger(List<DiTreeEntity> history) {
            Map<UUID, Retired> lastSeen = new HashMap<>();
            for (int v = 0; v < history.size(); v++) {
                Tree tree = v == history.size() - 1 ? prev : new Tree(history.get(v), table);
                for (int i = 0; i < tree.size; i++) {
                    UUID parent = tree.parent[i] < 0 ? null : tree.uuid[tree.parent[i]];
                    lastSeen.put(tree.uuid[i], new Retired(tree.uuid[i], tree, i, parent, v));
                }
            }
            historyUuids.addAll(lastSeen.keySet());
            if (prev != null) {
                for (int p = 0; p < prev.size; p++) {
                    prevByUuid.put(prev.uuid[p], p);
                }
            }
            lastSeen.forEach((uuid, seen) -> {
                if (!prevByUuid.containsKey(uuid)) {
                    retired.put(uuid, seen);
                    if (seen.parent() != null) {
                        retiredByParent.computeIfAbsent(seen.parent(), parent -> new ArrayList<>()).add(seen);
                    }
                }
            });
        }

        Correlation run() {
            if (carriedPhase) {
                carried();
            }
            if (prev != null) {
                anchors();
                if (!isAssigned(in.root) && prevToIn[prev.root] < 0 && in.meaning(in.root) == prev.meaning(prev.root)) {
                    match(in.root, prev.root, Phase.KEYED);
                }
                // What keyed recovery matches no longer counts against uniqueness: a subtree whose
                // twin stayed put is then the only one of its kind left to anchor, and what it
                // anchors is overlap a pair already recovered can now assign by.
                for (int before = -1; before != used.size(); ) {
                    before = used.size();
                    recoverAll();
                    anchors();
                }
                containers();
            }
            newborns();
            return new Correlation(build(), Lists.immutable.of(phase));
        }

        // ----- bookkeeping -----

        boolean isAssigned(int i) {
            return assigned[i] != null;
        }

        void match(int i, int p, Phase how) {
            inToPrev[i] = p;
            prevToIn[p] = i;
            assign(i, prev.uuid[p], how);
            work.add(i);
        }

        void assign(int i, UUID uuid, Phase how) {
            assigned[i] = uuid;
            phase[i] = how;
            used.add(uuid);
        }

        /** Matches two isomorphic subtrees vertex by vertex, skipping any vertex already taken. */
        void matchSubtree(int[] images, Phase how) {
            for (int i = 0; i < images.length; i++) {
                int p = images[i];
                if (p >= 0 && !isAssigned(i) && prevToIn[p] < 0) {
                    match(i, p, how);
                }
            }
        }

        boolean freeIn(int i) {
            List<Integer> subtree = new ArrayList<>();
            in.subtree(i, subtree);
            return subtree.stream().noneMatch(this::isAssigned);
        }

        boolean freePrev(int p) {
            List<Integer> subtree = new ArrayList<>();
            prev.subtree(p, subtree);
            return subtree.stream().allMatch(x -> prevToIn[x] < 0);
        }

        /** Incoming indexes in an order of the comparator, then canonical hash, then path signature. */
        List<Integer> inOrder(Comparator<Integer> first) {
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < in.size; i++) {
                order.add(i);
            }
            order.sort(first.thenComparingLong(x -> in.hashes[x]).thenComparingLong(x -> in.signature[x]));
            return order;
        }

        Comparator<Integer> byHashAndSignature() {
            return Comparator.<Integer>comparingLong(x -> in.hashes[x]).thenComparingLong(x -> in.signature[x]);
        }

        Comparator<Integer> byHashAndUuid() {
            return Comparator.<Integer>comparingLong(x -> prev.hashes[x]).thenComparing(x -> prev.uuid[x]);
        }

        // ----- phase 1: carried identity -----

        void carried() {
            for (int i = 0; i < in.size; i++) {
                UUID uuid = in.uuid[i];
                if (uuidCountIn.get(uuid) != 1) {
                    continue;
                }
                Integer p = prevByUuid.get(uuid);
                if (p != null) {
                    if (in.meaning(i) == prev.meaning(p)) {
                        inToPrev[i] = p;
                        prevToIn[p] = i;
                        assign(i, uuid, Phase.CARRIED);
                    }
                } else {
                    Retired seen = retired.get(uuid);
                    if (seen != null && seen.tree().meaning(seen.index()) == in.meaning(i)) {
                        assign(i, uuid, Phase.CARRIED);
                    }
                }
            }
        }

        // ----- phase 3: global exact anchors -----

        void anchors() {
            Map<Long, List<Integer>> inByHash = new HashMap<>();
            Map<Long, List<Integer>> prevByHash = new HashMap<>();
            for (int i = 0; i < in.size; i++) {
                inByHash.computeIfAbsent(in.hashes[i], h -> new ArrayList<>()).add(i);
            }
            for (int p = 0; p < prev.size; p++) {
                prevByHash.computeIfAbsent(prev.hashes[p], h -> new ArrayList<>()).add(p);
            }
            Set<Long> seen = new HashSet<>();
            for (int i : inOrder(Comparator.comparingInt((Integer x) -> -in.subtreeSize[x]))) {
                long hash = in.hashes[i];
                if (!seen.add(hash) || !prevByHash.containsKey(hash)) {
                    continue;
                }
                List<Integer> inFree = inByHash.get(hash).stream().filter(this::freeIn).toList();
                List<Integer> prevFree = prevByHash.get(hash).stream().filter(this::freePrev).toList();
                if (inFree.size() != 1 || prevFree.size() != 1) {
                    continue;
                }
                int a = inFree.getFirst();
                int p = prevFree.getFirst();
                if (in.position(a) == Position.SOLE || in.position(a) != prev.position(p)) {
                    continue;
                }
                int[] images = DiTreeIsomorphism.correspondence(in.tree, in.hashes, a, prev.tree, prev.hashes, p, ordered);
                if (images != null) {
                    matchSubtree(images, Phase.ANCHORED);
                }
            }
        }

        // ----- phases 5 and 6: keyed recovery and assignment, with resurrection -----

        /** Recovers beneath every matched pair, top down, until nothing more matches. */
        void recoverAll() {
            for (int before = -1; before != used.size(); ) {
                before = used.size();
                Arrays.fill(recovered, false);
                for (int i : inOrder(Comparator.comparingInt((Integer x) -> in.depth[x]))) {
                    if (inToPrev[i] >= 0) {
                        work.add(i);
                    }
                }
                recover();
            }
        }

        void recover() {
            while (!work.isEmpty()) {
                int i = work.poll();
                if (!recovered[i] && inToPrev[i] >= 0) {
                    recovered[i] = true;
                    recoverBeneath(i, inToPrev[i]);
                }
            }
        }

        void recoverBeneath(int i, int p) {
            List<Integer> leftIn = leftIn(i);
            List<Integer> leftPrev = leftPrev(p);
            if (leftIn.isEmpty()) {
                return;
            }
            if (in.sequence(i) || prev.sequence(p)) {
                // a sequence: each child continues the one at its position, as a sole successor does
                ImmutableIntList childrenIn = in.children(i);
                ImmutableIntList childrenPrev = prev.children(p);
                for (int k = 0; k < Math.min(childrenIn.size(), childrenPrev.size()); k++) {
                    int c = childrenIn.get(k);
                    int q = childrenPrev.get(k);
                    if (!isAssigned(c) && prevToIn[q] < 0 && in.meaning(c) == prev.meaning(q)) {
                        match(c, q, Phase.KEYED);
                    }
                }
                resurrect(i);
                return;
            }
            boolean operands = in.operands(i) || prev.operands(p);
            if (!operands) {
                if (leftIn.size() == 1 && leftPrev.size() == 1 && in.meaning(leftIn.getFirst()) == prev.meaning(leftPrev.getFirst())) {
                    match(leftIn.getFirst(), leftPrev.getFirst(), Phase.KEYED);
                }
            } else {
                // equal content
                for (int c : leftIn) {
                    for (int q : leftPrev) {
                        if (prevToIn[q] < 0 && prev.hashes[q] == in.hashes[c]) {
                            int[] images = DiTreeIsomorphism.correspondence(in.tree, in.hashes, c, prev.tree, prev.hashes, q, ordered);
                            if (images != null) {
                                matchSubtree(images, Phase.KEYED);
                                break;
                            }
                        }
                    }
                }
                // derived
                for (int c : leftIn(i)) {
                    Integer q = prevByUuid.get(derived(c, assigned[i], 0));
                    if (q != null && prev.parent[q] == p && prevToIn[q] < 0 && prev.meaning(q) == in.meaning(c)
                            && key(prev.vertex(q), true, table).equals(key(in.vertex(c), true, table))) {
                        match(c, q, Phase.DERIVED);
                    }
                }
                // equal key
                Map<Key, List<Integer>> keysIn = new LinkedHashMap<>();
                for (int c : leftIn(i)) {
                    keysIn.computeIfAbsent(key(in.vertex(c), true, table), k -> new ArrayList<>()).add(c);
                }
                Map<Key, List<Integer>> keysPrev = new HashMap<>();
                for (int q : leftPrev(p)) {
                    keysPrev.computeIfAbsent(key(prev.vertex(q), true, table), k -> new ArrayList<>()).add(q);
                }
                keysIn.forEach((key, cs) -> {
                    List<Integer> qs = keysPrev.get(key);
                    if (qs == null) {
                        return;
                    }
                    if (cs.size() == 1 && qs.size() == 1) {
                        match(cs.getFirst(), qs.getFirst(), Phase.KEYED);
                    } else {
                        assign(cs, qs);
                    }
                });
            }
            resurrect(i);
        }

        /** The incoming vertex's unassigned children, by hash and path signature. */
        List<Integer> leftIn(int i) {
            List<Integer> left = new ArrayList<>();
            for (int c : in.children(i).toArray()) {
                if (!isAssigned(c)) {
                    left.add(c);
                }
            }
            left.sort(byHashAndSignature());
            return left;
        }

        /** The previous vertex's unmatched children, by hash and UUID. */
        List<Integer> leftPrev(int p) {
            List<Integer> left = new ArrayList<>();
            for (int q : prev.children(p).toArray()) {
                if (prevToIn[q] < 0) {
                    left.add(q);
                }
            }
            left.sort(byHashAndUuid());
            return left;
        }

        /** Pairs equal-key operands by the overlap of their matched descendants; no overlap, no pair. */
        void assign(List<Integer> cs, List<Integer> qs) {
            int[][] overlap = new int[cs.size()][qs.size()];
            boolean any = false;
            for (int r = 0; r < cs.size(); r++) {
                List<Integer> subtree = new ArrayList<>();
                in.subtree(cs.get(r), subtree);
                for (int d : subtree.subList(1, subtree.size())) {
                    int image = inToPrev[d];
                    if (image >= 0) {
                        for (int k = 0; k < qs.size(); k++) {
                            if (image != qs.get(k) && prev.within(image, qs.get(k))) {
                                overlap[r][k]++;
                                any = true;
                            }
                        }
                    }
                }
            }
            if (!any) {
                return;
            }
            int[] pairs = Hungarian.maximize(overlap);
            for (int r = 0; r < cs.size(); r++) {
                int k = pairs[r];
                if (k >= 0 && overlap[r][k] > 0) {
                    match(cs.get(r), qs.get(k), Phase.ASSIGNED);
                }
            }
        }

        /**
         * Gives each child still unassigned beneath a matched vertex the identity of a retired
         * vertex last seen under it with the same content, its subtree with it; the most
         * recently seen, then the least UUID, when several qualify.
         */
        void resurrect(int i) {
            List<Retired> candidates = retiredByParent.get(assigned[i]);
            if (candidates == null) {
                return;
            }
            for (int c : leftIn(i)) {
                Retired best = null;
                int[] bestImages = null;
                for (Retired seen : candidates) {
                    if (used.contains(seen.uuid()) || seen.tree().hashes[seen.index()] != in.hashes[c]) {
                        continue;
                    }
                    int[] images = DiTreeIsomorphism.correspondence(in.tree, in.hashes, c,
                            seen.tree().tree, seen.tree().hashes, seen.index(), ordered);
                    if (images != null && (best == null || seen.version() > best.version()
                            || seen.version() == best.version() && seen.uuid().compareTo(best.uuid()) < 0)) {
                        best = seen;
                        bestImages = images;
                    }
                }
                if (best != null) {
                    for (int x = 0; x < bestImages.length; x++) {
                        if (bestImages[x] >= 0 && !isAssigned(x)) {
                            UUID uuid = best.tree().uuid[bestImages[x]];
                            if (!used.contains(uuid) && !prevByUuid.containsKey(uuid)) {
                                assign(x, uuid, Phase.RESURRECTED);
                            }
                        }
                    }
                }
            }
        }

        // ----- phase 4: bottom-up containers -----

        void containers() {
            for (int i : inOrder(Comparator.comparingInt((Integer x) -> in.height[x]))) {
                if (isAssigned(i) || in.children(i).isEmpty()) {
                    continue;
                }
                List<Integer> subtree = new ArrayList<>();
                in.subtree(i, subtree);
                Map<Integer, Integer> common = new HashMap<>();
                for (int d : subtree.subList(1, subtree.size())) {
                    int image = inToPrev[d];
                    for (int a = image < 0 ? -1 : prev.parent[image]; a >= 0; a = prev.parent[a]) {
                        if (prevToIn[a] < 0) {
                            common.merge(a, 1, Integer::sum);
                        }
                    }
                }
                Key key = key(in.vertex(i), true, table);
                Position position = in.position(i);
                int best = -1;
                double bestDice = 0;
                List<Integer> candidates = new ArrayList<>(common.keySet());
                candidates.sort(byHashAndUuid());
                for (int a : candidates) {
                    if (!key.equals(key(prev.vertex(a), true, table)) || prev.position(a) != position) {
                        continue;
                    }
                    double dice = 2.0 * common.get(a) / (in.subtreeSize[i] - 1 + prev.subtreeSize[a] - 1);
                    if (best < 0 || dice > bestDice
                            || dice == bestDice && prev.hashes[a] == in.hashes[i] && prev.hashes[best] != in.hashes[i]) {
                        best = a;
                        bestDice = dice;
                    }
                }
                if (best >= 0 && bestDice > containerDice) {
                    match(i, best, Phase.CONTAINED);
                    recoverAll();
                }
            }
        }

        // ----- newborns -----

        /**
         * Every vertex still unassigned is a newborn, top down, so each parent's UUID is settled
         * before its children derive from it. A child derives its UUID, bumping its ordinal past
         * any UUID an inherited vertex holds, and takes back a derived UUID that retired under
         * the same parent with the same meaning. A root derives from the seed, or without one
         * keeps the UUID it arrived with when history never held it.
         */
        void newborns() {
            for (int i : inOrder(Comparator.comparingInt((Integer x) -> in.depth[x]))) {
                if (isAssigned(i)) {
                    continue;
                }
                if (in.parent[i] >= 0) {
                    UUID parent = assigned[in.parent[i]];
                    for (int bump = 0; ; bump++) {
                        UUID uuid = derived(i, parent, bump);
                        if (used.contains(uuid)) {
                            continue;
                        }
                        Retired seen = retired.get(uuid);
                        if (seen != null && parent.equals(seen.parent()) && seen.tree().meaning(seen.index()) == in.meaning(i)) {
                            assign(i, uuid, Phase.RESURRECTED);
                            break;
                        }
                        if (!historyUuids.contains(uuid)) {
                            assign(i, uuid, Phase.NEWBORN);
                            break;
                        }
                    }
                } else {
                    UUID uuid = rootSeed != null ? rootSeed : in.uuid[i];
                    long salt = 0;
                    while (historyUuids.contains(uuid) || used.contains(uuid) || rootSeed == null && uuidCountIn.get(uuid) != 1) {
                        uuid = VertexDerivation.child(rootSeed != null ? rootSeed : in.uuid[i], "root", (int) salt++);
                    }
                    assign(i, uuid, Phase.NEWBORN);
                }
            }
        }

        /**
         * A vertex's derived UUID beneath a parent UUID, its ordinal bumped as asked: the ordinal
         * ranks the parent's children with an equal key by their public canonical hash.
         */
        UUID derived(int i, UUID parent, int bump) {
            if (ordinal[i] < 0) {
                if (publicHashes == null) {
                    publicHashes = VertexDerivation.publicHashes(in.tree, ordered);
                }
                int p = in.parent[i];
                if (in.sequence(p)) {
                    // a sequence: the key is the meaning, the ordinal the position
                    ImmutableIntList sequence = in.children(p);
                    for (int k = 0; k < sequence.size(); k++) {
                        derivationKey[sequence.get(k)] = VertexDerivation.key(in.vertex(sequence.get(k)), false, table);
                        ordinal[sequence.get(k)] = k;
                    }
                    return VertexDerivation.child(parent, derivationKey[i], ordinal[i] + bump);
                }
                boolean operand = in.operands(p);
                List<Integer> siblings = new ArrayList<>();
                for (int s : in.children(p).toArray()) {
                    if (derivationKey[s] == null) {
                        derivationKey[s] = VertexDerivation.key(in.vertex(s), operand, table);
                    }
                    siblings.add(s);
                }
                Map<String, List<Integer>> byKey = new HashMap<>();
                for (int s : siblings) {
                    byKey.computeIfAbsent(derivationKey[s], k -> new ArrayList<>()).add(s);
                }
                for (List<Integer> equal : byKey.values()) {
                    // between identical siblings, which a normal form never holds, index decides
                    equal.sort(Comparator.<Integer>comparingLong(x -> publicHashes[x]).thenComparingInt(x -> x));
                    for (int rank = 0; rank < equal.size(); rank++) {
                        ordinal[equal.get(rank)] = rank;
                    }
                }
            }
            return VertexDerivation.child(parent, derivationKey[i], ordinal[i] + bump);
        }

        DiTreeEntity build() {
            EntityVertex[] vertices = new EntityVertex[in.size];
            for (int i = 0; i < in.size; i++) {
                EntityVertex original = in.vertex(i);
                EntityVertex vertex = EntityVertex.make(assigned[i], original.getMeaningNid());
                MutableLongObjectMap<Object> properties = LongObjectMaps.mutable.empty();
                original.properties().forEachKeyValue(properties::put);
                vertex.setProperties(properties);
                vertex.setVertexIndex(i);
                vertices[i] = vertex;
            }
            return new DiTreeEntity(vertices[in.root], Lists.immutable.of(vertices),
                    in.tree.successorMap(), in.tree.predecessorMap());
        }
    }

    // ----- keys -----

    /**
     * A vertex's key (arch-vertex-identity-table): its meaning and, as an operand, the values of
     * its identifying properties, a component by nid and a sequence of components in order.
     */
    private static Key key(EntityVertex vertex, boolean operand, VertexIdentityTable table) {
        if (!operand) {
            return new Key(vertex.getMeaningNid(), List.of());
        }
        List<Object> values = new ArrayList<>();
        for (ConceptFacade property : table.rule(vertex.getMeaningNid()).identifying()) {
            values.add(identifyingValue(vertex.properties().get(property.nid())));
        }
        return new Key(vertex.getMeaningNid(), values);
    }

    private static Object identifyingValue(Object value) {
        return switch (value) {
            case null -> "";
            case ComponentWithNid component -> component.nid();
            case PublicId publicId -> PrimitiveData.get().hasPublicId(publicId) ? (Object) PrimitiveData.nid(publicId) : publicId;
            case LongIdList list -> Arrays.stream(list.toArray()).boxed().toList();
            default -> value;
        };
    }

    // ----- assignment -----

    /** The Hungarian method (Kuhn–Munkres, with potentials) over a small rectangular matrix. */
    static final class Hungarian {

        private Hungarian() {
        }

        /**
         * The assignment of rows to columns that maximizes the total weight.
         *
         * @param weight the weights, rows by columns
         * @return by row, its column, or -1 for a row left without one
         */
        static int[] maximize(int[][] weight) {
            int rows = weight.length;
            int columns = rows == 0 ? 0 : weight[0].length;
            int n = Math.max(rows, columns);
            long max = 0;
            for (int[] row : weight) {
                for (int w : row) {
                    max = Math.max(max, w);
                }
            }
            long[][] cost = new long[n + 1][n + 1];
            for (int r = 1; r <= n; r++) {
                for (int c = 1; c <= n; c++) {
                    cost[r][c] = max - (r <= rows && c <= columns ? weight[r - 1][c - 1] : 0);
                }
            }
            long[] u = new long[n + 1];
            long[] v = new long[n + 1];
            int[] rowOf = new int[n + 1];
            int[] way = new int[n + 1];
            for (int r = 1; r <= n; r++) {
                rowOf[0] = r;
                int column = 0;
                long[] least = new long[n + 1];
                boolean[] done = new boolean[n + 1];
                Arrays.fill(least, Long.MAX_VALUE);
                do {
                    done[column] = true;
                    int row = rowOf[column];
                    long delta = Long.MAX_VALUE;
                    int next = 0;
                    for (int c = 1; c <= n; c++) {
                        if (!done[c]) {
                            long reduced = cost[row][c] - u[row] - v[c];
                            if (reduced < least[c]) {
                                least[c] = reduced;
                                way[c] = column;
                            }
                            if (least[c] < delta) {
                                delta = least[c];
                                next = c;
                            }
                        }
                    }
                    for (int c = 0; c <= n; c++) {
                        if (done[c]) {
                            u[rowOf[c]] += delta;
                            v[c] -= delta;
                        } else {
                            least[c] -= delta;
                        }
                    }
                    column = next;
                } while (rowOf[column] != 0);
                do {
                    int previous = way[column];
                    rowOf[column] = rowOf[previous];
                    column = previous;
                } while (column != 0);
            }
            int[] columnOf = new int[rows];
            Arrays.fill(columnOf, -1);
            for (int c = 1; c <= n; c++) {
                if (rowOf[c] >= 1 && rowOf[c] <= rows && c <= columns) {
                    columnOf[rowOf[c] - 1] = c - 1;
                }
            }
            return columnOf;
        }
    }
}
