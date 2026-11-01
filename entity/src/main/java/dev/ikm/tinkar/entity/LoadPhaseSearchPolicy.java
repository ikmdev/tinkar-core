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
package dev.ikm.tinkar.entity;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Decides per-merge whether the search index should be updated live during a
 * loadPhase, or whether the load should defer search work and trigger a full
 * recreate at endLoadPhase. Holds counter state for the duration of a
 * loadPhase.
 *
 * <h2>Why this exists (and why it's temporary)</h2>
 *
 * <p>A full {@code recreateIndex()} at endLoadPhase walks every entity in the
 * store regardless of change-set size — pure waste for small change-sets
 * against a large store. A 10K-entity change-set against a 10M store re-walks
 * the 10M instead of just touching the 10K.
 *
 * <p>This class is a <b>pragmatic intermediate step</b>. It lets change-sets
 * index live (skipping the post-load recreate entirely) while live indexing
 * costs less than the recreate would, and falls back to the full recreate once
 * a change-set crosses that point. The recreate's cost grows with the store, so
 * the threshold is a fraction of the store ({@value #STORE_FRACTION} of the
 * nids it has issued when the load phase begins), never fewer than
 * {@value #MINIMUM_LIVE_INDEX_THRESHOLD} indexable items. The
 * {@value #THRESHOLD_PROPERTY} system property sets a fixed threshold
 * instead.
 *
 * <p>Behavior matrix:
 * <ul>
 *   <li>Change-set ≤ threshold: live-index every merge; no recreate at end.
 *       The documents are written as the merges happen and become visible
 *       to searches in one refresh when the load phase ends.</li>
 *   <li>Change-set &gt; threshold: live-index the first {@code threshold}
 *       merges, then skip the rest; full recreate at end. Worst case is
 *       about twice the cheaper of the two (the live-indexed prefix cost what
 *       the recreate costs, and is indexed again by it).</li>
 *   <li>Initial bulk load: overflows the minimum quickly, falls back to the
 *       full recreate.</li>
 * </ul>
 *
 * <h2>The proper solution</h2>
 *
 * <p>The real solution is touched-nid tracking during loadPhase plus a
 * per-nid catch-up walk at endLoadPhase, with threshold-driven fallback to
 * full recreate when the touched set crosses a fraction of the store. That
 * gives O(change-set-size) post-load work for any change-set, against the
 * O(store-size) cost we still pay above the threshold here.
 *
 * <p>Remove this class once the touched-nid design lands.
 *
 * <h2>Thread-safety</h2>
 *
 * <p>{@link #shouldIndexLive()} is the per-merge hot path and is called from
 * many concurrent threads during parallel imports. The counter is an
 * {@link AtomicLong}; the threshold and the overflow flag are
 * {@code volatile}. No locks.
 */
public final class LoadPhaseSearchPolicy {

    /** System property for a fixed live-index threshold, in place of the store-scaled one. */
    public static final String THRESHOLD_PROPERTY = "ike.search.loadphase.live.threshold";

    /**
     * The share of the store's nids a change-set may live-index before the recreate is cheaper.
     * Measured on Graphlet (2026-10-31) by loading change-sets of 6 K to 773 K indexable semantics
     * into a SNOMED CT store of 8.4 M nids and a SOLOR-GUDID store of 76 M: live indexing cost
     * 54 to 199 µs a semantic, the recreate 5 s and 33 to 59 s, so the two broke even at 0.24 % to
     * 1.1 % of the store. This is the geometric middle of that range: at worst about three times
     * the cheaper of the two, for a change-set just past it.
     */
    public static final double STORE_FRACTION = 0.005;

    /** The fewest indexable items a change-set may live-index, however small the store. */
    public static final long MINIMUM_LIVE_INDEX_THRESHOLD = 25_000;

    private static final Long FIXED_THRESHOLD = Long.getLong(THRESHOLD_PROPERTY);

    private final AtomicLong indexedCount = new AtomicLong();
    private volatile long threshold = MINIMUM_LIVE_INDEX_THRESHOLD;
    private volatile boolean overflowed = false;
    private volatile boolean recreated = false;

    /**
     * The live-index threshold for a store of this many nids: the fixed one the system property
     * sets, else {@link #STORE_FRACTION} of the store, never below the minimum.
     *
     * @param storeNids the nids the store has issued; negative for a store that cannot say
     * @return the most indexable items a change-set may live-index
     */
    public static long thresholdFor(long storeNids) {
        if (FIXED_THRESHOLD != null) {
            return FIXED_THRESHOLD;
        }
        return Math.max(MINIMUM_LIVE_INDEX_THRESHOLD, (long) (STORE_FRACTION * storeNids));
    }

    /**
     * Decides whether this merge should be live-indexed. Side-effect:
     * increments the merge counter and flips the overflow flag the first
     * time the threshold is crossed.
     *
     * <p>Once {@code overflowed} is true, every subsequent call returns
     * {@code false} (cheap path: a single volatile read).
     *
     * @return {@code true} if the caller should index this merge live;
     *         {@code false} once the threshold has been crossed
     */
    public boolean shouldIndexLive() {
        if (overflowed) {
            return false;
        }
        long n = indexedCount.incrementAndGet();
        if (n > threshold) {
            overflowed = true;
            return false;
        }
        return true;
    }

    /**
     * @return {@code true} if the threshold was crossed during this loadPhase
     *         and a full recreate is needed at endLoadPhase to catch up the
     *         entities that were merged after live indexing was abandoned.
     *         {@code false} if every merge during this loadPhase was indexed
     *         live (no post-load search work needed).
     */
    public boolean recreateRequired() {
        return overflowed;
    }

    /** Records that a full recreate ran after this load phase, so the index is current again. */
    public void markRecreated() {
        recreated = true;
    }

    /**
     * Whether the search index is current after this load phase: the change set was indexed
     * live, or it overflowed and a full recreate ran. A caller that would otherwise recreate
     * after a load checks this first, so an import that already rebuilt the index is not
     * followed by a second rebuild of the same entities (IKE-Network/ike-issues#1274).
     */
    public boolean searchIndexSettled() {
        return !overflowed || recreated;
    }

    /**
     * @return the number of merges that were live-indexed (counts up to
     *         and including the merge that crossed the threshold).
     */
    public long liveIndexedCount() {
        return indexedCount.get();
    }

    /** @return this load phase's threshold (for diagnostic logging). */
    public long threshold() {
        return threshold;
    }

    /**
     * Reset for the next loadPhase, with the threshold for the store it loads into. Called by
     * {@link EntityService#beginLoadPhase()}.
     *
     * @param storeNids the nids the store has issued as the load phase begins; negative for a
     *                  store that cannot say, which gets the minimum
     */
    public void reset(long storeNids) {
        threshold = thresholdFor(storeNids);
        indexedCount.set(0);
        overflowed = false;
        recreated = false;
    }
}
