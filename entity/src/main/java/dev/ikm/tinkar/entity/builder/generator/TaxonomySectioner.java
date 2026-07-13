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

import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.factory.primitive.IntLists;
import org.eclipse.collections.api.list.primitive.MutableIntList;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Buckets a loaded store's concepts into taxonomy sections — the ledger generator's
 * (IKE-Network/ike-issues#869) answer to organizing a large starter set into reviewable,
 * IDE-completion-friendly source files (IKE-Network/ike-issues#873): sections slice the
 * taxonomy, never a component, so a concept's whole story stays in one section.
 * <p>
 * This class does the <em>structural</em> half of sectioning — parent/child from stated
 * navigation, and size-threshold bucket splitting. The <em>curatorial</em> half — merging
 * related subtrees under one human-chosen name (dialect with language, the scattered
 * coordinate-properties subtrees together) — is a caller concern: {@link Section#name()}
 * is the bucket's own root concept name, and a caller free to fold sections together by
 * name before emitting one source file per final grouping.
 */
public final class TaxonomySectioner {

    private final Map<Integer, MutableIntList> children = new HashMap<>();
    private final Map<Integer, MutableIntList> parents = new HashMap<>();

    private TaxonomySectioner() {
    }

    /**
     * Builds the parent/child taxonomy graph from every stated-navigation semantic's
     * latest active version, per the given calculator's coordinate — the platform's own
     * "what's current" answer, not list-position "latest".
     *
     * @param calculator the coordinate to resolve latest-active versions with
     * @return the built sectioner, ready to bucket any root's descendants
     */
    public static TaxonomySectioner fromStatedNavigation(StampCalculator calculator) {
        TaxonomySectioner sectioner = new TaxonomySectioner();
        calculator.forEachSemanticVersionOfPattern(TinkarTerm.STATED_NAVIGATION_PATTERN,
                (semanticVersion, patternVersion) -> {
                    int child = semanticVersion.referencedComponentNid();
                    int parentsFieldIndex = originFieldIndex(patternVersion);
                    Object field = semanticVersion.fieldValues().get(parentsFieldIndex);
                    if (field instanceof IntIdSet parentNids) {
                        parentNids.forEach(parentNid -> sectioner.addEdge(parentNid, child));
                    }
                });
        return sectioner;
    }

    private void addEdge(int parentNid, int childNid) {
        children.computeIfAbsent(parentNid, key -> IntLists.mutable.empty()).add(childNid);
        parents.computeIfAbsent(childNid, key -> IntLists.mutable.empty()).add(parentNid);
    }

    /**
     * Resolves which stated-navigation field carries a concept's parents. Navigation
     * runs root-to-leaf — the "destination" field holds children — so parents are the
     * "origin" field; resolved from the pattern's own field definitions rather than a
     * hardcoded index, so it tracks the pattern regardless of field-order changes.
     */
    private static int originFieldIndex(dev.ikm.tinkar.entity.PatternEntityVersion patternVersion) {
        for (int index = 0; index < patternVersion.fieldDefinitions().size(); index++) {
            String meaning = dev.ikm.tinkar.common.service.PrimitiveData
                    .text(patternVersion.fieldDefinitions().get(index).meaningNid()).toLowerCase();
            if (meaning.contains("origin")) {
                return index;
            }
        }
        throw new IllegalStateException("No origin field on the stated navigation pattern");
    }

    /** The direct children of a concept in the built taxonomy graph, empty if none. */
    public List<Integer> childrenOf(int nid) {
        int[] childNids = children.getOrDefault(nid, IntLists.mutable.empty()).toArray();
        return Arrays.stream(childNids).boxed().toList();
    }

    /** Every concept nid reachable from (and including) the given root, depth-first. */
    public List<Integer> subtreeOf(int rootNid) {
        List<Integer> subtree = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        Deque<Integer> pending = new ArrayDeque<>();
        pending.push(rootNid);
        while (!pending.isEmpty()) {
            int nid = pending.pop();
            if (visited.add(nid)) {
                subtree.add(nid);
                for (int child : children.getOrDefault(nid, IntLists.mutable.empty()).toArray()) {
                    pending.push(child);
                }
            }
        }
        return subtree;
    }

    /**
     * Buckets every descendant of {@code taxonomyRootNid} into one section per direct
     * child, splitting any section whose subtree exceeds {@code splitThreshold} into one
     * section per grandchild (recursively, up to {@code maxDepth} levels) instead. A
     * concept reachable from more than one section root — a dual-parented classification
     * concept — is a member of every section whose root reaches it (sections are not
     * disjoint by design; the ledger declares such a concept once, in whichever generated
     * file the caller resolves as primary).
     *
     * <b>Dedup is the caller's job.</b> This method does not deduplicate a
     * dual-parented concept across the sections it returns — by design, per above.
     * A caller that emits every section's members verbatim without first resolving
     * each dual-parented concept to exactly one section will, at ledger-compose time,
     * hit {@code ComponentLedger}'s one-version-per-stamp guard (every declaration in
     * a generated ledger shares one stamp, so a duplicate declaration collides on it)
     * — a real, if indirect, safety net, but the resulting
     * {@code IllegalArgumentException} says nothing about sections or this contract.
     * Callers should dedupe explicitly (first-section-wins is the simplest policy)
     * rather than relying on that exception as documentation.
     *
     * @param taxonomyRootNid the concept whose children seed the top-level sections
     * @param splitThreshold  a section subtree larger than this splits one level deeper
     * @param maxDepth        the deepest split level below the root (1 = only direct
     *                        children, oversized ones flattened into their own single
     *                        section rather than split further)
     * @return one {@link Section} per bucket, in stable (sorted by root's nid) order
     */
    public List<Section> sectionsUnder(int taxonomyRootNid, int splitThreshold, int maxDepth) {
        List<Section> sections = new ArrayList<>();
        for (int child : childrenOf(taxonomyRootNid)) {
            splitInto(child, splitThreshold, maxDepth, 1, sections, new HashSet<>());
        }
        // childrenOf/splitInto walk in HashMap iteration order (ultimately driven by
        // forEachSemanticVersionOfPattern's iteration order), not nid order — sorted
        // here so the documented "stable (sorted by root's nid)" contract actually
        // holds regardless of store iteration order, which is what makes regenerating
        // the ledger produce a reviewable, stable diff.
        sections.sort(Comparator.comparingInt(Section::rootNid));
        return sections;
    }

    /**
     * Convenience for callers that need FULL store coverage, not just the
     * taxonomy-reachable subset: {@link #sectionsUnder(int, int, int)}, first-section-wins
     * deduplicated across sections (this class's own documented "dedup is the caller's
     * job" contract, applied once here so every caller doesn't reimplement it), plus a
     * residual catch-all covering every concept and pattern NOT reachable via stated
     * navigation — meta-schema concepts the taxonomy scan found unanchored, and the
     * entirely separate pattern taxonomy, which {@link #sectionsUnder} never walks.
     * Residual members are batched by {@code residualBatchSize}: a single
     * {@code compose()} method over every residual member can approach the same 64KB
     * bytecode-per-method limit that drives {@code splitThreshold}/{@code maxDepth}
     * above.
     *
     * @param taxonomyRootNid  forwarded to {@link #sectionsUnder}
     * @param splitThreshold   forwarded to {@link #sectionsUnder}
     * @param maxDepth         forwarded to {@link #sectionsUnder}
     * @param residualBatchSize the maximum members per residual-catch-all section
     * @return every section needed for full-store coverage, deduplicated
     */
    public List<Section> sectionsCoveringFullStore(int taxonomyRootNid, int splitThreshold, int maxDepth,
                                                    int residualBatchSize) {
        List<Section> sections = sectionsUnder(taxonomyRootNid, splitThreshold, maxDepth);

        Set<Integer> assigned = new HashSet<>();
        List<Section> exclusiveSections = new ArrayList<>();
        for (Section section : sections) {
            List<Integer> exclusiveMembers = section.members().stream().filter(assigned::add).toList();
            exclusiveSections.add(new Section(section.rootNid(), exclusiveMembers));
        }

        List<Integer> residualMembers = new ArrayList<>();
        EntityService.get().forEachConceptEntity(concept -> {
            if (assigned.add(concept.nid())) {
                residualMembers.add(concept.nid());
            }
        });
        EntityService.get().forEachPatternEntity(pattern -> {
            if (assigned.add(pattern.nid())) {
                residualMembers.add(pattern.nid());
            }
        });
        for (int start = 0; start < residualMembers.size(); start += residualBatchSize) {
            List<Integer> batch = residualMembers.subList(start,
                    Math.min(start + residualBatchSize, residualMembers.size()));
            exclusiveSections.add(new Section(taxonomyRootNid, List.copyOf(batch)));
        }
        return exclusiveSections;
    }

    private void splitInto(int nid, int splitThreshold, int maxDepth, int depth, List<Section> sections,
                           Set<Integer> ancestorPath) {
        if (!ancestorPath.add(nid)) {
            // A genuine cycle — this node is its own ancestor in the current
            // splitting path. A dual-parented (but acyclic) node reached via a
            // DIFFERENT top-level branch is unaffected: ancestorPath is scoped to
            // one root-to-here path, backtracked below, not shared globally — that
            // sharing would silently break the documented dual-parent contract
            // (a concept reachable from more than one section root is a member of
            // every section whose root reaches it).
            return;
        }
        try {
            List<Integer> subtree = subtreeOf(nid);
            List<Integer> grandchildren = childrenOf(nid);
            if (subtree.size() <= splitThreshold || depth >= maxDepth || grandchildren.isEmpty()) {
                sections.add(new Section(nid, List.copyOf(subtree)));
                return;
            }
            // This node's descendants are being split out below — but the node itself
            // still needs a home, or it silently vanishes from every generated section.
            sections.add(new Section(nid, List.of(nid)));
            for (int grandchild : grandchildren) {
                splitInto(grandchild, splitThreshold, maxDepth, depth + 1, sections, ancestorPath);
            }
        } finally {
            ancestorPath.remove(nid);
        }
    }

    /**
     * One taxonomy section: the subtree root that seeded it, and every concept nid in
     * its subtree (the root included). {@link #name()} resolves the root's display text
     * lazily — call sites that already loaded a store can call it directly.
     *
     * @param rootNid the concept whose subtree this section covers
     * @param members every concept nid in the subtree, root included
     */
    public record Section(int rootNid, List<Integer> members) {
        public String name() {
            return dev.ikm.tinkar.common.service.PrimitiveData.text(rootNid);
        }
    }
}
