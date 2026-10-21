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

import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.entity.PatternEntityVersion;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampEntityVersion;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@link DataKind} of components (IKE-Network/ike-issues#1313). A component's kind is its
 * <em>birth kind</em>: the kind of the module its first version was written under. A pattern's
 * kind bounds its elements: every element of an ephemeral or instance pattern is of that kind,
 * while a knowledge pattern may hold elements of any kind, such as the names of an instance
 * pattern, each of the kind of its own first version.
 */
public final class EntityBoundaries {

    private static final ConcurrentHashMap<Long, DataKind> BIRTH = new ConcurrentHashMap<>();
    private static final int CACHE_LIMIT = 500_000;

    private EntityBoundaries() {
    }

    /** Forgets every cached birth kind. */
    public static void clear() {
        BIRTH.clear();
    }

    /**
     * The kind of the module a STAMP names.
     *
     * @param stampNid the STAMP's nid
     * @return the module's resolution, knowledge when the STAMP cannot be read
     */
    public static ModuleKinds.Resolution stampKind(long stampNid) {
        return ModuleKinds.resolve(moduleOf(stampNid));
    }

    /**
     * The module a STAMP names: its latest version's module.
     *
     * @param stampNid the STAMP's nid
     * @return the module's nid, {@code 0} when the STAMP cannot be read
     */
    public static long moduleOf(long stampNid) {
        Optional<? extends Entity<? extends EntityVersion>> stamp = read(stampNid);
        if (stamp.isPresent() && stamp.get() instanceof StampEntity<?> s) {
            StampEntityVersion last = null;
            for (EntityVersion version : s.versions()) {
                if (version instanceof StampEntityVersion sv && (last == null || sv.time() >= last.time())) {
                    last = sv;
                }
            }
            return last == null ? 0 : last.moduleNid();
        }
        return 0;
    }

    /**
     * A component's birth kind: the kind of the module of its first version in time. A component
     * every version of which is uncommitted is born in the transaction being committed.
     *
     * @param nid the component's nid
     * @return its kind, knowledge when it cannot be read
     */
    public static DataKind birthKind(long nid) {
        DataKind cached = BIRTH.get(nid);
        if (cached != null) {
            return cached;
        }
        Optional<? extends Entity<? extends EntityVersion>> held = read(nid);
        if (held.isEmpty() || held.get() instanceof StampEntity<?>) {
            return DataKind.KNOWLEDGE;
        }
        EntityVersion first = null;
        for (EntityVersion version : held.get().versions()) {
            if (version.canceled()) {
                continue;
            }
            if (first == null || version.time() < first.time()) {
                first = version;
            }
        }
        if (first == null) {
            return DataKind.KNOWLEDGE;
        }
        DataKind kind = stampKind(first.stampNid()).kind();
        if (first.time() != Long.MAX_VALUE) {          // a committed birth never changes
            if (BIRTH.size() > CACHE_LIMIT) {
                BIRTH.clear();
            }
            BIRTH.put(nid, kind);
        }
        return kind;
    }

    /**
     * The kind a reference to a component leads into: the component's birth kind.
     *
     * @param nid the referenced component's nid
     * @return its kind
     */
    public static DataKind kindOf(long nid) {
        java.util.OptionalLong keyedUnder = patternFromNid(nid);
        if (keyedUnder.isPresent()) {
            // The layout carries the pattern in the nid: its kind, cached by the pattern's nid,
            // answers without reading the element (IKE-Network/ike-issues#1313).
            DataKind patternKind = birthKind(keyedUnder.getAsLong());
            return patternKind != DataKind.KNOWLEDGE ? patternKind : birthKind(nid);
        }
        Optional<? extends Entity<? extends EntityVersion>> held = read(nid);
        if (held.isPresent() && held.get() instanceof SemanticEntity<?> semantic) {
            DataKind patternKind = birthKind(semantic.patternNid());
            if (patternKind != DataKind.KNOWLEDGE) {
                return patternKind;                      // an instance pattern's elements are all of its kind
            }
        }
        return birthKind(nid);
    }

    /**
     * The nid of the pattern a component is keyed under, from its nid alone where the layout
     * carries the pattern: the 6-bit, 8-bit and 64-bit layouts. Empty in a sequential store, whose
     * nids carry no pattern, and for a pattern itself, keyed under the pattern-of-patterns.
     *
     * @param nid the component's nid
     * @return its pattern's nid, if the nid says
     */
    static java.util.OptionalLong patternFromNid(long nid) {
        dev.ikm.tinkar.common.id.impl.NidLayout layout = dev.ikm.tinkar.common.id.impl.NidLayout.active();
        if (layout == dev.ikm.tinkar.common.id.impl.NidLayout.SEQUENTIAL) {
            return java.util.OptionalLong.empty();
        }
        int patternOfPatterns = layout.patternPatternSequence();
        int sequence;
        try {
            sequence = layout.decodePatternSequence(nid);
            if (sequence <= 0 || sequence == patternOfPatterns) {
                return java.util.OptionalLong.empty();
            }
            return java.util.OptionalLong.of(layout.encode(patternOfPatterns, sequence));
        } catch (RuntimeException notANidOfTheLayout) {
            return java.util.OptionalLong.empty();
        }
    }

    /** The latest version of a pattern, uncommitted included, or {@code null}. */
    static PatternEntityVersion latestPattern(long patternNid) {
        Optional<? extends Entity<? extends EntityVersion>> held = read(patternNid);
        if (held.isEmpty() || !(held.get() instanceof PatternEntity<?> pattern)) {
            return null;
        }
        PatternEntityVersion latest = null;
        for (EntityVersion version : pattern.versions()) {
            if (version instanceof PatternEntityVersion pv && !pv.canceled() && (latest == null || pv.time() >= latest.time())) {
                latest = pv;
            }
        }
        return latest;
    }

    static Optional<? extends Entity<? extends EntityVersion>> read(long nid) {
        try {
            return EntityHandle.get(nid).entity();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
