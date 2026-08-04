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
package dev.ikm.tinkar.integration.builder;

import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.FieldDefinitionForEntity;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.entity.PatternEntityVersion;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.integration.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.factory.primitive.IntLists;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The starter-set sectioning spike (IKE-Network/ike-issues#873): walks the ENTIRE
 * unreasoned starter set and reports the taxonomy-bucket layout a sectioned ledger would
 * take — every child-of-root subtree with its component and semantic counts, oversized
 * buckets broken down one level further — plus full-set tallies of every
 * ledger-expressibility dimension the ten-component spike (#868) could only sample.
 * Exploratory by design: the report is the deliverable.
 */
class StarterSetSectionSpikeIT {

    private static final Logger LOG = LoggerFactory.getLogger(StarterSetSectionSpikeIT.class);

    /** Buckets larger than this get a depth-2 breakdown in the report. */
    private static final int BUCKET_SPLIT_THRESHOLD = 60;

    private static final Map<Integer, MutableIntList> PARENTS = new HashMap<>();
    private static final Map<Integer, MutableIntList> CHILDREN = new HashMap<>();
    private static final Map<String, Integer> FULL_SET_TALLIES = new TreeMap<>();
    private static final Map<String, Integer> SEMANTICS_BY_PATTERN = new TreeMap<>();

    @BeforeAll
    static void loadUnreasonedStarterSet() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA);
    }

    @AfterAll
    static void stop() {
        TestHelper.stopDatabase();
    }

    @Test
    @DisplayName("Full starter set categorized: taxonomy buckets sized, expressibility tallies at scale")
    void categorizeFullSet() {
        int parentsFieldIndex = statedNavigationParentsIndex();
        buildTaxonomy(parentsFieldIndex);

        List<Integer> concepts = new ArrayList<>();
        PrimitiveData.get().forEachConceptNid(concepts::add);
        List<Integer> patterns = new ArrayList<>();
        PrimitiveData.get().forEachPatternNid(patterns::add);

        List<Integer> roots = concepts.stream()
                .filter(nid -> !PARENTS.containsKey(nid) && CHILDREN.containsKey(nid))
                .toList();

        StringBuilder report = new StringBuilder("\n═══ Sectioning spike report (#873) ═══\n");
        report.append(String.format("Concepts: %d   Patterns: %d   Roots (navigable, parentless): %s%n",
                concepts.size(), patterns.size(),
                roots.stream().map(PrimitiveData::text).toList()));

        Map<Integer, Integer> conceptToBucket = new HashMap<>();
        Set<Integer> multiBucket = new HashSet<>();
        for (Integer root : roots) {
            for (int depthOne : CHILDREN.getOrDefault(root, IntLists.mutable.empty()).toArray()) {
                assignSubtree(depthOne, depthOne, conceptToBucket, multiBucket);
            }
        }

        Map<String, List<Integer>> buckets = new TreeMap<>();
        for (Map.Entry<Integer, Integer> entry : conceptToBucket.entrySet()) {
            buckets.computeIfAbsent(PrimitiveData.text(entry.getValue()), key -> new ArrayList<>())
                    .add(entry.getKey());
        }
        List<Integer> unanchored = concepts.stream()
                .filter(nid -> !conceptToBucket.containsKey(nid))
                .filter(nid -> !roots.contains(nid))
                .toList();

        report.append(String.format("Bucketed: %d   Multi-bucket (dual-parented across buckets): %d   Unanchored: %d%n",
                conceptToBucket.size(), multiBucket.size(), unanchored.size()));
        if (!unanchored.isEmpty()) {
            report.append("Unanchored concepts (no stated-navigation path from a root):\n");
            unanchored.stream().limit(30).forEach(nid ->
                    report.append("    ").append(PrimitiveData.text(nid)).append('\n'));
        }

        report.append("\n── Taxonomy buckets (children of root; the section candidates) ──\n");
        report.append(String.format("  %-58s %8s %10s%n", "bucket", "concepts", "verbs~"));
        for (Map.Entry<String, List<Integer>> bucket : buckets.entrySet()) {
            int verbEstimate = bucket.getValue().stream().mapToInt(this::verbEstimate).sum();
            report.append(String.format("  %-58s %8d %10d%n",
                    bucket.getKey(), bucket.getValue().size(), verbEstimate));
            if (bucket.getValue().size() > BUCKET_SPLIT_THRESHOLD) {
                Integer bucketNid = conceptToBucket.get(bucket.getValue().getFirst());
                for (Integer member : bucket.getValue()) {
                    if (PrimitiveData.text(member).equals(bucket.getKey())) {
                        bucketNid = member;
                        break;
                    }
                }
                breakdown(bucketNid, 1, report);
            }
        }

        report.append("\n── Pattern components (their own section) ──\n");
        for (Integer patternNid : patterns) {
            PatternEntity<?> pattern = EntityHandle.get(patternNid).expectPattern();
            report.append(String.format("  %-70s versions=%d%n",
                    PrimitiveData.text(patternNid), pattern.versions().size()));
        }

        tallyFullSet(concepts, patterns);
        report.append("\n── Semantics by pattern (full set) ──\n");
        SEMANTICS_BY_PATTERN.forEach((pattern, count) ->
                report.append(String.format("  %-58s %6d%n", pattern, count)));
        report.append("\n── Expressibility tallies at full-set scale (#868 dimensions) ──\n");
        FULL_SET_TALLIES.forEach((dimension, count) ->
                report.append(String.format("  %-58s %6d%n", dimension, count)));

        LOG.info(report.toString());

        assertFalse(buckets.isEmpty(), "the starter set must yield taxonomy buckets");
        assertTrue(unanchored.size() < concepts.size() / 4,
                "most concepts must be navigable from a root for taxonomy sectioning to hold");
    }

    /**
     * Resolves which stated-navigation field carries the parents, from the pattern
     * itself. Navigation runs root-to-leaf: on a concept's navigation semantic the
     * "destination" field holds its children, so the parents are the "origin" field.
     */
    private static int statedNavigationParentsIndex() {
        PatternEntity<?> navigation =
                EntityHandle.get(TinkarTerm.STATED_NAVIGATION_PATTERN.nid()).expectPattern();
        PatternEntityVersion latest = navigation.versions().getLast();
        for (int index = 0; index < latest.fieldDefinitions().size(); index++) {
            FieldDefinitionForEntity field = latest.fieldDefinitions().get(index);
            String meaning = PrimitiveData.text(field.meaningNid()).toLowerCase();
            if (meaning.contains("origin")) {
                return index;
            }
        }
        throw new IllegalStateException("No origin field on the stated navigation pattern");
    }

    private static void buildTaxonomy(int parentsFieldIndex) {
        List<Integer> semanticNids = new ArrayList<>();
        PrimitiveData.get().forEachSemanticNid(semanticNids::add);
        for (Integer semanticNid : semanticNids) {
            SemanticEntity<?> semantic = EntityHandle.get(semanticNid).expectSemantic();
            if (semantic.patternNid() != TinkarTerm.STATED_NAVIGATION_PATTERN.nid()) {
                continue;
            }
            int child = semantic.referencedComponentNid();
            Object field = semantic.versions().getLast().fieldValues().get(parentsFieldIndex);
            if (field instanceof IntIdSet parents) {
                parents.forEach(parent -> {
                    PARENTS.computeIfAbsent(child, key -> IntLists.mutable.empty()).add(parent);
                    CHILDREN.computeIfAbsent(parent, key -> IntLists.mutable.empty()).add(child);
                });
            }
        }
    }

    private static void assignSubtree(int start, int bucket, Map<Integer, Integer> conceptToBucket,
                                      Set<Integer> multiBucket) {
        Deque<Integer> pending = new ArrayDeque<>();
        pending.push(start);
        while (!pending.isEmpty()) {
            int nid = pending.pop();
            Integer assigned = conceptToBucket.putIfAbsent(nid, bucket);
            if (assigned != null) {
                if (assigned != bucket) {
                    multiBucket.add(nid);
                }
                continue;
            }
            for (int child : CHILDREN.getOrDefault(nid, IntLists.mutable.empty()).toArray()) {
                pending.push(child);
            }
        }
    }

    /** Recursive subtree sizing: oversized nodes break down one level further, to depth 4. */
    private void breakdown(int nodeNid, int depth, StringBuilder report) {
        Map<String, Integer> childSizes = new TreeMap<>();
        Map<String, Integer> childNids = new TreeMap<>();
        for (int child : CHILDREN.getOrDefault(nodeNid, IntLists.mutable.empty()).toArray()) {
            childSizes.put(PrimitiveData.text(child), subtreeSize(child));
            childNids.put(PrimitiveData.text(child), child);
        }
        String indent = "      ".repeat(depth);
        childSizes.forEach((name, size) -> {
            report.append(String.format("%s↳ %-" + Math.max(20, 52 - indent.length()) + "s %8d%n",
                    indent, name, size));
            if (size > BUCKET_SPLIT_THRESHOLD && depth < 4) {
                breakdown(childNids.get(name), depth + 1, report);
            }
        });
    }

    private int subtreeSize(int start) {
        Set<Integer> subtree = new HashSet<>();
        Deque<Integer> pending = new ArrayDeque<>();
        pending.push(start);
        while (!pending.isEmpty()) {
            int nid = pending.pop();
            if (subtree.add(nid)) {
                for (int child : CHILDREN.getOrDefault(nid, IntLists.mutable.empty()).toArray()) {
                    pending.push(child);
                }
            }
        }
        return subtree.size();
    }

    /** Rough verb count: one birth + one per semantic version, navigation excluded. */
    private int verbEstimate(int conceptNid) {
        int[] verbs = {1};
        EntityService.get().forEachSemanticForComponent(conceptNid, semantic -> {
            if (semantic.patternNid() != TinkarTerm.STATED_NAVIGATION_PATTERN.nid()
                    && semantic.patternNid() != TinkarTerm.INFERRED_NAVIGATION_PATTERN.nid()
                    && semantic.patternNid() != TinkarTerm.EL_PLUS_PLUS_INFERRED_AXIOMS_PATTERN.nid()) {
                verbs[0] += semantic.versions().size();
            }
        });
        return verbs[0];
    }

    private static void tallyFullSet(List<Integer> concepts, List<Integer> patterns) {
        List<Integer> components = new ArrayList<>();
        components.addAll(concepts);
        components.addAll(patterns);
        Set<Integer> stampNids = new HashSet<>();
        for (Integer componentNid : components) {
            Entity<?> entity = EntityHandle.get(componentNid).expectEntity();
            if (entity.publicId().uuidCount() > 1) {
                tally("multi-UUID public ids on components");
            }
            if (entity.versions().size() > 1) {
                tally("multi-version component chronologies");
            }
            for (EntityVersion version : entity.versions()) {
                stampNids.add(version.stampNid());
            }
            EntityService.get().forEachSemanticForComponent(componentNid, semantic ->
                    classifySemantic(semantic, stampNids, patterns.contains(componentNid)));
        }
        for (Integer stampNid : stampNids) {
            StampEntity<?> stamp = Entity.getStamp(stampNid);
            if (!tupleDerived(stamp)) {
                tally("declared-identity stamps required");
            }
            if (stamp.publicId().uuidCount() > 1) {
                tally("multi-UUID stamp identities");
            }
            if (stamp.state() != State.ACTIVE && stamp.state() != State.INACTIVE) {
                tally("stamp state beyond active/inactive: " + stamp.state());
            }
        }
        FULL_SET_TALLIES.putIfAbsent("stamps in use (total)", stampNids.size());
    }

    private static void classifySemantic(SemanticEntity<SemanticEntityVersion> semantic,
                                         Set<Integer> stampNids, boolean onPattern) {
        SEMANTICS_BY_PATTERN.merge(PrimitiveData.text(semantic.patternNid()), 1, Integer::sum);
        if (semantic.publicId().uuidCount() > 1) {
            tally("multi-UUID semantic identities");
        }
        if (semantic.versions().size() > 1) {
            tally("multi-version semantics");
        }
        for (SemanticEntityVersion version : semantic.versions()) {
            stampNids.add(version.stampNid());
            if (version.stamp().state() == State.INACTIVE) {
                tally("inactive semantic versions");
            }
        }
        int patternNid = semantic.patternNid();
        if (patternNid == TinkarTerm.DESCRIPTION_PATTERN.nid()) {
            SemanticEntityVersion latest = semantic.versions().getLast();
            if (nidOf(latest.fieldValues().get(0)) != TinkarTerm.ENGLISH_LANGUAGE.nid()) {
                tally("non-English descriptions");
            }
            if (nidOf(latest.fieldValues().get(2)) != TinkarTerm.DESCRIPTION_NOT_CASE_SENSITIVE.nid()) {
                tally("non-default case significance");
            }
        } else if (patternNid == TinkarTerm.GB_DIALECT_PATTERN.nid()) {
            tally("GB dialect semantics");
        } else if (patternNid == TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid() && onPattern) {
            tally("stated-axiom semantics on PATTERN components");
        }
        if (onPattern && patternNid == TinkarTerm.STATED_NAVIGATION_PATTERN.nid()) {
            tally("navigation semantics on PATTERN components");
        }
    }

    private static boolean tupleDerived(StampEntity<?> stamp) {
        String canonical = stamp.state().publicId().asUuidArray()[0]
                + "|" + stamp.time()
                + "|" + firstUuid(stamp.authorNid())
                + "|" + firstUuid(stamp.moduleNid())
                + "|" + firstUuid(stamp.pathNid());
        UUID tuple = UuidT5Generator.get(UuidT5Generator.STAMP_NAMESPACE, canonical);
        return tuple.equals(stamp.publicId().asUuidArray()[0]);
    }

    private static int nidOf(Object fieldValue) {
        if (fieldValue instanceof dev.ikm.tinkar.terms.EntityFacade facade) {
            return facade.nid();
        }
        return 0;
    }

    private static UUID firstUuid(int nid) {
        return PrimitiveData.publicId(nid).asUuidArray()[0];
    }

    private static void tally(String dimension) {
        FULL_SET_TALLIES.merge(dimension, 1, Integer::sum);
    }
}
