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
package dev.ikm.tinkar.provider.mvstore;

import org.h2.mvstore.MVMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Crash-safe nid allocation over an {@link org.h2.mvstore.MVStore}.
 * <p>
 * The store runs with background auto-commit, so map entries that carry newly minted
 * nids can reach disk at any time, independently of an explicit {@code save()}. The
 * previous scheme persisted the next-nid counter only in {@code save()}; after any exit
 * without {@code close()} (killed test fork, crash) the reopened store resumed from a stale
 * counter and re-issued nids already bound to persisted UUIDs and components. A reused nid
 * makes the citation index point at an entity of a different kind, which surfaced as
 * {@code ConceptRecord cannot be cast to SemanticEntity} in {@code EntityProvider.textFast}.
 * <p>
 * This allocator reserves nids in blocks. The high-water mark of a block is written to the
 * map <em>before</em> any nid from that block is handed out. New UUID-to-nid entries go into
 * the same map afterwards, so any persisted version of that map that contains a nid also
 * contains a watermark that covers it; on reopen, allocation resumes at the watermark and
 * never re-issues a nid. A crash only wastes the unused remainder of the current block.
 * <p>
 * A store commit captures each map's root in turn rather than as one atomic snapshot, so a
 * nid-keyed map captured later in the same commit can hold a nid whose watermark the
 * identity map's captured root does not cover. The constructor therefore also resumes past
 * {@code lastKey()} of every nid-keyed map it is given.
 * <p>
 * Stores written before this scheme carry no {@link #FORMAT_KEY}; for those, the constructor
 * recovers the watermark by scanning the identity map and the component map once.
 */
final class NidAllocator {
    private static final Logger LOG = LoggerFactory.getLogger(NidAllocator.class);

    /** Legacy key holding the next nid; now holds the reserved high-water mark. */
    static final UUID WATERMARK_KEY = new UUID(Long.MAX_VALUE, Long.MIN_VALUE);
    /** Presence marks a store whose watermark follows the block-reservation invariant. */
    static final UUID FORMAT_KEY = new UUID(Long.MAX_VALUE, Long.MIN_VALUE + 1);
    static final int FORMAT_BLOCK_RESERVATION = 1;
    static final int DEFAULT_BLOCK_SIZE = 1024;

    private final MVMap<UUID, Integer> uuidToNidMap;
    private final int blockSize;
    private final AtomicInteger nextNid;
    /** Exclusive upper bound of nids whose reservation has been written to the map. */
    private volatile int reservedUpTo;

    /**
     * Opens the allocator over a store's identity map.
     *
     * @param uuidToNidMap the identity map; also holds the watermark and format marker
     * @param firstNid     the first nid an empty store issues
     * @param blockSize    how many nids each persisted reservation covers
     * @param nidKeyedMaps maps keyed by nid whose largest key allocation must resume past
     */
    NidAllocator(MVMap<UUID, Integer> uuidToNidMap, int firstNid, int blockSize,
                 List<MVMap<Integer, ?>> nidKeyedMaps) {
        if (blockSize < 1) {
            throw new IllegalArgumentException("blockSize must be positive: " + blockSize);
        }
        this.uuidToNidMap = uuidToNidMap;
        this.blockSize = blockSize;

        int start = Math.max(firstNid, uuidToNidMap.getOrDefault(WATERMARK_KEY, firstNid));

        // Cheap guard for every store: MVMap is sorted, so lastKey() is O(log n).
        for (MVMap<Integer, ?> nidKeyedMap : nidKeyedMaps) {
            Integer lastNid = nidKeyedMap.lastKey();
            if (lastNid != null && lastNid >= start) {
                start = lastNid + 1;
            }
        }

        if (!uuidToNidMap.containsKey(FORMAT_KEY)) {
            // Legacy store: the persisted counter may lag nids that auto-commit already
            // wrote. Scan the identity map once; nids can be minted before a component exists.
            int maxAssigned = Integer.MIN_VALUE;
            for (Map.Entry<UUID, Integer> entry : uuidToNidMap.entrySet()) {
                if (isReservedKey(entry.getKey())) {
                    continue;
                }
                maxAssigned = Math.max(maxAssigned, entry.getValue());
            }
            if (maxAssigned != Integer.MIN_VALUE && maxAssigned >= start) {
                LOG.warn("Recovered nid watermark: persisted counter was behind assigned nids "
                        + "(max assigned {}, resuming at {})", maxAssigned, maxAssigned + 1);
                start = maxAssigned + 1;
            }
        }

        this.nextNid = new AtomicInteger(start);
        this.reservedUpTo = start;
        // Persist the new invariant before handing out anything.
        reserveThrough(start);
        uuidToNidMap.put(FORMAT_KEY, FORMAT_BLOCK_RESERVATION);
    }

    static boolean isReservedKey(UUID uuid) {
        return WATERMARK_KEY.equals(uuid) || FORMAT_KEY.equals(uuid);
    }

    int newNid() {
        int nid = nextNid.getAndIncrement();
        if (nid >= reservedUpTo) {
            reserveThrough(nid);
        }
        return nid;
    }

    /** Peek at the next nid that would be issued. */
    int peekNextNid() {
        return nextNid.get();
    }

    /** Exclusive upper bound currently reserved in the map. */
    int reservedUpTo() {
        return reservedUpTo;
    }

    /**
     * Ensures the persisted watermark is strictly greater than {@code nid}. The map write
     * happens before the caller returns {@code nid}, which is what makes the scheme safe.
     */
    private synchronized void reserveThrough(int nid) {
        if (nid < reservedUpTo) {
            return;
        }
        long target = (long) nid + blockSize;
        int newWatermark = target > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) target;
        uuidToNidMap.put(WATERMARK_KEY, newWatermark);
        reservedUpTo = newWatermark;
    }
}
