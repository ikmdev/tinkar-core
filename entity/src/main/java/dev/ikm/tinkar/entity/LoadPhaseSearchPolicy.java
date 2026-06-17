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
 * <p>Today's import path skips per-merge live indexing entirely during
 * loadPhase, then runs a full {@code recreateIndex()} at endLoadPhase. The
 * full recreate walks every entity in the store regardless of change-set
 * size — pure waste for small change-sets against a large store. A 10K-entity
 * change-set against a 10M store re-walks the 10M instead of just touching
 * the 10K.
 *
 * <p>This class is a <b>pragmatic intermediate step</b>. It lets small
 * change-sets index live (skipping the post-load recreate entirely) and
 * falls back to today's full-recreate behavior once a change-set crosses a
 * fixed threshold ({@value #DEFAULT_LIVE_INDEX_THRESHOLD} indexable items by
 * default). Configured via the {@value #THRESHOLD_PROPERTY} system property.
 *
 * <p>Behavior matrix:
 * <ul>
 *   <li>Change-set ≤ threshold: live-index every merge; no recreate at end.
 *       Index stays continuously current.</li>
 *   <li>Change-set &gt; threshold: live-index the first {@code threshold}
 *       merges, then skip the rest; full recreate at end. Worst case is
 *       marginally slower than today (the live-indexed prefix adds NRT-refresh
 *       overhead before fallback fires) but covers every change-set
 *       conservatively.</li>
 *   <li>Initial bulk load: overflows the threshold quickly, falls back to
 *       full recreate. Identical to today.</li>
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
 * {@link AtomicLong}; the overflow flag is {@code volatile}. No locks.
 */
public final class LoadPhaseSearchPolicy {

    /** System property override for the live-index threshold. */
    public static final String THRESHOLD_PROPERTY = "ike.search.loadphase.live.threshold";

    /** Default threshold: above this many indexable items in a single
     *  loadPhase, give up on live indexing and fall back to full recreate. */
    public static final int DEFAULT_LIVE_INDEX_THRESHOLD = 25_000;

    private static final int LIVE_INDEX_THRESHOLD =
            Integer.getInteger(THRESHOLD_PROPERTY, DEFAULT_LIVE_INDEX_THRESHOLD);

    private final AtomicLong indexedCount = new AtomicLong();
    private volatile boolean overflowed = false;

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
        if (n > LIVE_INDEX_THRESHOLD) {
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

    /**
     * @return the number of merges that were live-indexed (counts up to
     *         and including the merge that crossed the threshold).
     */
    public long liveIndexedCount() {
        return indexedCount.get();
    }

    /** @return the configured threshold (for diagnostic logging). */
    public static int threshold() {
        return LIVE_INDEX_THRESHOLD;
    }

    /**
     * Reset for the next loadPhase. Called by
     * {@link EntityService#beginLoadPhase()}.
     */
    public void reset() {
        indexedCount.set(0);
        overflowed = false;
    }
}
