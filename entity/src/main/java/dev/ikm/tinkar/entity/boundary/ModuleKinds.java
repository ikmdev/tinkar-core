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
package dev.ikm.tinkar.entity.boundary;

import dev.ikm.tinkar.common.id.LongIdCollection;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.FieldDefinitionForEntity;
import dev.ikm.tinkar.entity.PatternEntityVersion;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpression;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import dev.ikm.tinkar.terms.PatternFacade;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a module's {@link DataKind} through the taxonomy, with inheritance: a module that is a
 * kind of {@code KernelConcept.EPHEMERAL_MODULE} at any depth is ephemeral, one that is a kind of
 * {@code KernelConcept.INSTANCE_MODULE} is instance, and every other module is knowledge
 * (IKE-Network/ike-issues#1313).
 *
 * <p>A module's parents are read from its stated axioms, every concept axiom of its latest version,
 * and from its stated and inferred navigation, the field whose meaning is
 * {@code KernelConcept.RELATIONSHIP_ORIGIN}. The axioms come first because a module minted at run
 * time has no navigation until a classifier runs. Latest means the latest version in time,
 * uncommitted ones included, since a module may be written in the transaction being committed.
 *
 * <p>Resolutions are cached; a module's kind is fixed once data is written under it, which the
 * commit validator enforces, so a cached kind does not go stale for any module that matters.
 */
public final class ModuleKinds {

    /**
     * A module's kind, and whether it reaches both parents, which the two parents' disjointness
     * forbids.
     *
     * @param kind        the kind: ephemeral or instance when it reaches that parent, else knowledge
     * @param conflicting whether it reaches both parents
     */
    public record Resolution(DataKind kind, boolean conflicting) { }

    private static final Resolution KNOWLEDGE = new Resolution(DataKind.KNOWLEDGE, false);
    private static final ConcurrentHashMap<Long, Resolution> CACHE = new ConcurrentHashMap<>();
    private static final int MAX_DEPTH = 64;

    private ModuleKinds() {
    }

    /**
     * @return whether the store holds either parent; until one does, every module is knowledge
     */
    public static boolean parentsPresent() {
        return PrimitiveData.get().hasPublicId(KernelConcept.EPHEMERAL_MODULE.publicId())
                || PrimitiveData.get().hasPublicId(KernelConcept.INSTANCE_MODULE.publicId());
    }

    /**
     * Resolves a module's kind.
     *
     * @param moduleNid the module concept's nid
     * @return its kind, and whether it reaches both parents
     */
    public static Resolution resolve(long moduleNid) {
        if (moduleNid == 0 || !parentsPresent()) {
            return KNOWLEDGE;                            // not cached: a store may gain the parents later
        }
        Resolution cached = CACHE.get(moduleNid);
        if (cached != null) {
            return cached;
        }
        Resolution resolved = walk(moduleNid);
        CACHE.put(moduleNid, resolved);
        return resolved;
    }

    /** Forgets every resolution, for a store that was reopened or a test that rewrites modules. */
    public static void clear() {
        CACHE.clear();
    }

    private static Resolution walk(long moduleNid) {
        long ephemeral = present(KernelConcept.EPHEMERAL_MODULE.publicId()) ? KernelConcept.EPHEMERAL_MODULE.nid() : Long.MIN_VALUE;
        long instance = present(KernelConcept.INSTANCE_MODULE.publicId()) ? KernelConcept.INSTANCE_MODULE.nid() : Long.MIN_VALUE;
        if (ephemeral == Long.MIN_VALUE && instance == Long.MIN_VALUE) {
            return KNOWLEDGE;
        }
        Set<DataKind> reached = EnumSet.noneOf(DataKind.class);
        MutableLongSet seen = new LongHashSet();
        Deque<long[]> work = new ArrayDeque<>();
        work.push(new long[] {moduleNid, 0});
        while (!work.isEmpty()) {
            long[] at = work.pop();
            long nid = at[0];
            if (!seen.add(nid) || at[1] > MAX_DEPTH) {
                continue;
            }
            if (nid == ephemeral) {
                reached.add(DataKind.EPHEMERAL);
                continue;
            }
            if (nid == instance) {
                reached.add(DataKind.INSTANCE);
                continue;
            }
            parents(nid).forEach(parent -> work.push(new long[] {parent, at[1] + 1}));
        }
        if (reached.size() > 1) {
            return new Resolution(DataKind.INSTANCE, true);
        }
        return reached.isEmpty() ? KNOWLEDGE : new Resolution(reached.iterator().next(), false);
    }

    private static boolean present(dev.ikm.tinkar.common.id.PublicId id) {
        return PrimitiveData.get().hasPublicId(id);
    }

    /** A concept's parents: the concept axioms of its stated axioms, and its navigation origins. */
    static MutableLongSet parents(long conceptNid) {
        MutableLongSet parents = new LongHashSet();
        EntityService.get().semanticsForComponentOfPattern(conceptNid, KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid())
                .forEach(semantic -> {
                    SemanticEntityVersion latest = latest(semantic);
                    if (latest != null && !latest.fieldValues().isEmpty() && latest.fieldValues().get(0) instanceof DiTreeEntity tree) {
                        for (LogicalAxiom.Atom.ConceptAxiom axiom : new LogicalExpression(tree).nodesOfType(LogicalAxiom.Atom.ConceptAxiom.class)) {
                            parents.add(axiom.concept().nid());
                        }
                    }
                });
        navigationParents(conceptNid, KernelPattern.STATED_NAVIGATION_PATTERN, parents);
        navigationParents(conceptNid, KernelPattern.INFERRED_NAVIGATION_PATTERN, parents);
        parents.remove(conceptNid);
        return parents;
    }

    private static void navigationParents(long conceptNid, PatternFacade pattern, MutableLongSet into) {
        if (!present(pattern.publicId())) {
            return;
        }
        int origin = originField(pattern.nid());
        if (origin < 0) {
            return;
        }
        EntityService.get().semanticsForComponentOfPattern(conceptNid, pattern.nid()).forEach(semantic -> {
            SemanticEntityVersion latest = latest(semantic);
            if (latest != null && latest.fieldValues().size() > origin && latest.fieldValues().get(origin) instanceof LongIdCollection ids) {
                into.addAll(ids.toArray());
            }
        });
    }

    private static int originField(long patternNid) {
        PatternEntityVersion version = EntityBoundaries.latestPattern(patternNid);
        if (version == null) {
            return -1;
        }
        long origin = KernelConcept.RELATIONSHIP_ORIGIN.nid();
        int index = 0;
        for (FieldDefinitionForEntity definition : version.fieldDefinitions()) {
            if (definition.meaningNid() == origin) {
                return index;
            }
            index++;
        }
        return -1;
    }

    /** The latest version in time, uncommitted included, cancelled excluded. */
    static SemanticEntityVersion latest(SemanticEntity<? extends SemanticEntityVersion> semantic) {
        SemanticEntityVersion latest = null;
        for (SemanticEntityVersion version : semantic.versions()) {
            if (version.canceled()) {
                continue;
            }
            if (latest == null || version.time() >= latest.time()) {
                latest = version;
            }
        }
        return latest;
    }
}
