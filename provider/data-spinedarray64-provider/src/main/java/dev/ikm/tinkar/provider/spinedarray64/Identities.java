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
package dev.ikm.tinkar.provider.spinedarray64;

import dev.ikm.tinkar.common.id.Nid;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.DiagnosticText;
import dev.ikm.tinkar.common.service.IdentityAdvisories;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.terms.EntityBinding;
import dev.ikm.tinkar.terms.PatternFacade;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntUnaryOperator;
import java.util.function.LongSupplier;

/**
 * The UUID to nid map of a 64-bit spined-array store, and the allocation of nids by pattern, as
 * the 64-bit Rocks store allocates them: every UUID of a component maps to its nid, and a
 * component gets its nid from its pattern's counter the first time any of its UUIDs is seen. A
 * pattern is an element of the pattern-of-patterns, sequence {@value #PATTERN_OF_PATTERNS}, and its
 * sequence is its element sequence there: the concept pattern is {@value #CONCEPT_PATTERN}, the
 * stamp pattern {@value #STAMP_PATTERN}, under the governed {@link EntityBinding} UUIDs.
 *
 * <p>The map lives in memory and is rebuilt from the records when a store opens. A nid minted and
 * not yet written has its UUIDs recorded at the mint, the only answer to {@link #publicIdOf} until
 * its record arrives (IKE-Network/ike-issues#1310), and saved with the store, since the rebuild
 * cannot find them.
 */
final class Identities {

    /** The pattern-of-patterns: the pattern whose elements are the patterns. */
    static final int PATTERN_OF_PATTERNS = Nid.PATTERN_OF_PATTERNS_SEQUENCE_64;
    /** The concept pattern: element 2 of the pattern-of-patterns. */
    static final int CONCEPT_PATTERN = Nid.CONCEPT_SEQUENCE_64;
    /** The stamp pattern: element 3 of the pattern-of-patterns. */
    static final int STAMP_PATTERN = Nid.STAMP_SEQUENCE_64;
    /** The first pattern sequence a semantic pattern can have; the three before it are fixed. */
    static final int FIRST_SEMANTIC_PATTERN = Nid.FIRST_SEMANTIC_SEQUENCE_64;

    static final long PATTERN_OF_PATTERNS_NID = Nid.compose64(PATTERN_OF_PATTERNS, PATTERN_OF_PATTERNS);
    static final long CONCEPT_PATTERN_NID = Nid.compose64(PATTERN_OF_PATTERNS, CONCEPT_PATTERN);
    static final long STAMP_PATTERN_NID = Nid.compose64(PATTERN_OF_PATTERNS, STAMP_PATTERN);

    private static final int STRIPES = 1024;

    private final ConcurrentHashMap<UUID, Long> uuidToNid = new ConcurrentHashMap<>();
    /** The UUIDs of nids minted here whose record has not been written. */
    private final ConcurrentHashMap<Long, UUID[]> unwritten = new ConcurrentHashMap<>();
    /** Issues the next element sequence of a pattern sequence. */
    private final IntUnaryOperator nextElement;
    private final ReentrantLock[] stripes = new ReentrantLock[STRIPES];

    Identities(IntUnaryOperator nextElement) {
        this.nextElement = nextElement;
        for (int i = 0; i < STRIPES; i++) {
            stripes[i] = new ReentrantLock();
        }
        bind(EntityBinding.Pattern.pattern(), PATTERN_OF_PATTERNS_NID);
        bind(EntityBinding.Concept.pattern(), CONCEPT_PATTERN_NID);
        bind(EntityBinding.Stamp.pattern(), STAMP_PATTERN_NID);
    }

    private void bind(PatternFacade binding, long nid) {
        // The proxy's UUIDs only: its nid asks the running provider.
        for (UUID uuid : binding.publicId().asUuidArray()) {
            uuidToNid.put(uuid, nid);
        }
    }

    /** Maps the UUIDs of a record found when the store opens. */
    void opened(long nid, Iterable<UUID> uuids) {
        for (UUID uuid : uuids) {
            uuidToNid.putIfAbsent(uuid, nid);
        }
    }

    /**
     * Maps every UUID no component holds to a nid: the UUIDs a written record names, so that a
     * component given one more UUID by a later source is found by it in this session, as it is
     * after the store reopens and rebuilds the map from the records.
     */
    void mapIfAbsent(long nid, UUID[] uuids) {
        for (UUID uuid : uuids) {
            uuidToNid.putIfAbsent(uuid, nid);
        }
    }

    /** Restores the UUIDs of a nid minted before and never written, read from the store's file. */
    void restoreUnwritten(long nid, UUID[] uuids) {
        unwritten.put(nid, uuids);
        for (UUID uuid : uuids) {
            uuidToNid.putIfAbsent(uuid, nid);
        }
    }

    /** The nids minted and never written, with their UUIDs. */
    Map<Long, UUID[]> unwritten() {
        return unwritten;
    }

    /** A record is written for the nid: its UUIDs are in the record now. */
    void written(long nid) {
        if (!unwritten.isEmpty()) {
            unwritten.remove(nid);
        }
    }

    /** Forgets an erased entity's UUIDs. The fixed bindings are never erased. */
    void forget(long nid, UUID[] uuids) {
        unwritten.remove(nid);
        for (UUID uuid : uuids) {
            if (nid != PATTERN_OF_PATTERNS_NID && nid != CONCEPT_PATTERN_NID && nid != STAMP_PATTERN_NID) {
                uuidToNid.remove(uuid, nid);
            }
        }
    }

    int size() {
        return uuidToNid.size();
    }

    boolean knows(UUID uuid) {
        return uuidToNid.containsKey(uuid);
    }

    Optional<Long> nid(UUID uuid) {
        return Optional.ofNullable(uuidToNid.get(uuid));
    }

    /**
     * The UUIDs minted for a nid with no record: what its mint recorded, else a scan of the map,
     * correctness over speed, for a nid whose record was erased. Empty when none is known.
     */
    Optional<PublicId> publicIdOf(long nid) {
        UUID[] minted = unwritten.get(nid);
        if (minted != null) {
            return Optional.of(PublicIds.of(minted));
        }
        List<UUID> uuids = new ArrayList<>();
        uuidToNid.forEach((uuid, mapped) -> {
            if (mapped == nid) {
                uuids.add(uuid);
            }
        });
        return uuids.isEmpty() ? Optional.empty() : Optional.of(PublicIds.of(uuids.toArray(new UUID[0])));
    }

    /**
     * The nid of an entity of a pattern, allocated if the entity is new; the pattern itself is
     * resolved first, and allocated under the pattern-of-patterns if it is new.
     */
    long nidFor(PublicId patternId, PublicId entityId) {
        long patternNid = keyFor(patternId, this::allocatePattern);
        if (Nid.patternSequence64(patternNid) != PATTERN_OF_PATTERNS) {
            throw new IllegalStateException(patternId + " is not a pattern of this store: it is "
                    + DiagnosticText.component(patternNid));
        }
        return keyFor(entityId, () -> allocateElementOf(patternNid));
    }

    /**
     * The nid of a component by its UUIDs: the one known for the least known UUID, else a new one
     * under the pattern in scope ({@link PrimitiveData#SCOPED_PATTERN_PUBLICID_FOR_NID}), else a
     * failure, since every nid of this store names its pattern.
     */
    long nidForUuids(UUID... uuids) {
        UUID[] ordered = uuids;
        if (uuids.length > 1) {
            ordered = uuids.clone();
            Arrays.sort(ordered);
            adviseIfSeveralComponents(ordered);
        }
        for (UUID uuid : ordered) {
            Long known = uuidToNid.get(uuid);
            if (known != null) {
                // Every UUID no component holds is mapped to the one found, as the sequential
                // store maps them: a later lookup by any of them finds the component.
                if (uuids.length > 1) {
                    mapIfAbsent(known, uuids);
                }
                return known;
            }
        }
        if (PrimitiveData.SCOPED_PATTERN_PUBLICID_FOR_NID.isBound()) {
            return nidFor(PrimitiveData.SCOPED_PATTERN_PUBLICID_FOR_NID.get(), PublicIds.of(uuids));
        }
        throw new IllegalStateException("No entity key found for UUIDs: " + Arrays.toString(uuids)
                + ", and no pattern in scope to allocate one under");
    }

    private long allocatePattern() {
        int sequence = nextElement.applyAsInt(PATTERN_OF_PATTERNS);
        return Nid.compose64(PATTERN_OF_PATTERNS, sequence);
    }

    private long allocateElementOf(long patternNid) {
        if (patternNid == PATTERN_OF_PATTERNS_NID) {
            return allocatePattern();
        }
        int patternSequence = Nid.elementSequence64(patternNid);
        return Nid.compose64(patternSequence, nextElement.applyAsInt(patternSequence));
    }

    /**
     * The nid of a public id, allocated under the stripes of its UUIDs if none of them has one,
     * and mapped from every UUID of the id. Ids that share a UUID serialize on its stripe, so each
     * id gets exactly one nid (IKE-Network/ike-issues#1140).
     */
    private long keyFor(PublicId id, LongSupplier allocator) {
        UUID[] uuids = id.asUuidArray();
        if (uuids.length == 1) {
            Long known = uuidToNid.get(uuids[0]);
            if (known != null) {
                return known;
            }
        } else {
            Long existing = existing(uuids);
            if (existing != null && allMapped(uuids)) {
                return existing;
            }
        }
        int[] held = stripesFor(uuids);
        for (int stripe : held) {
            stripes[stripe].lock();
        }
        try {
            adviseIfSeveralComponents(uuids);
            Long known = existing(uuids);
            long nid;
            if (known == null) {
                nid = allocator.getAsLong();
                unwritten.put(nid, uuids.clone());
            } else {
                nid = known;
            }
            for (UUID uuid : uuids) {
                uuidToNid.putIfAbsent(uuid, nid);
            }
            return nid;
        } finally {
            for (int i = held.length - 1; i >= 0; i--) {
                stripes[held[i]].unlock();
            }
        }
    }

    private Long existing(UUID[] uuids) {
        for (UUID uuid : uuids) {
            Long nid = uuidToNid.get(uuid);
            if (nid != null) {
                return nid;
            }
        }
        return null;
    }

    private boolean allMapped(UUID[] uuids) {
        for (UUID uuid : uuids) {
            if (!uuidToNid.containsKey(uuid)) {
                return false;
            }
        }
        return true;
    }

    private void adviseIfSeveralComponents(UUID[] uuids) {
        if (uuids.length < 2) {
            return;
        }
        TreeSet<Long> nids = new TreeSet<>();
        for (UUID uuid : uuids) {
            Long nid = uuidToNid.get(uuid);
            if (nid != null) {
                nids.add(nid);
            }
        }
        if (nids.size() > 1) {
            IdentityAdvisories.componentsShareUuids(List.of(uuids), nids);
        }
    }

    /** The distinct stripes of the UUIDs, ascending, so no set of threads can wait in a cycle. */
    private static int[] stripesFor(UUID[] uuids) {
        int[] held = new int[uuids.length];
        for (int i = 0; i < uuids.length; i++) {
            int hash = uuids[i].hashCode();
            hash ^= (hash >>> 16);
            held[i] = hash & (STRIPES - 1);
        }
        Arrays.sort(held);
        int distinct = 0;
        for (int i = 0; i < held.length; i++) {
            if (i == 0 || held[i] != held[i - 1]) {
                held[distinct++] = held[i];
            }
        }
        return distinct == held.length ? held : Arrays.copyOf(held, distinct);
    }
}
