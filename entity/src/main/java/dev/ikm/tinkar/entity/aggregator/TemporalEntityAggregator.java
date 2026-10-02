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
package dev.ikm.tinkar.entity.aggregator;

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.EntityCountSummary;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;

public class TemporalEntityAggregator extends EntityAggregator {
    private static final Logger LOG = LoggerFactory.getLogger(TemporalEntityAggregator.class);

    private final long fromEpochMillis;
    private final long toEpochMillis;

    /** Orphan nids seen by the last {@link #aggregate(IntConsumer)} run — sequences
     * allocated by the store but with no committed entity bytes (typically canceled
     * between allocation and commit). Counted for the log line; never counted in the
     * summary and never emitted, so a manifest written from the summary always matches
     * the records actually delivered (IKE-Network/ike-issues#933). */
    private final AtomicLong lastOrphanCount = new AtomicLong();

    public TemporalEntityAggregator(long fromEpochMillis, long toEpochMillis) {
        this.fromEpochMillis = fromEpochMillis;
        this.toEpochMillis = toEpochMillis;
    }

    @Override
    public EntityCountSummary aggregate(IntConsumer nidConsumer) {
        initCounts();
        // Concurrent collections throughout: the forEach*Nid scans run in parallel, and a plain
        // HashSet or ArrayList filled from several threads can drop entries — here, stamps the
        // export would then silently leave out.
        // Filter Stamp Nids based on the supplied time span
        Set<Integer> filteredStampNids = ConcurrentHashMap.newKeySet();
        PrimitiveData.get().forEachStampNid((stampNid) -> {
            if (isCancelled()) {
                return;
            }
            EntityService.get().getStamp(stampNid).ifPresent((stampEntity) -> {
                if (fromEpochMillis <= stampEntity.time() && stampEntity.time() <= toEpochMillis) {
                    filteredStampNids.add(stampEntity.nid());
                }
            });
        });
        throwIfCancelled();

        Set<Integer> stampsToExport = ConcurrentHashMap.newKeySet();

        // Every concept, semantic and pattern is read to see whether any of its stamps falls in
        // the range — there is no index from a stamp to the components that use it. Read past the
        // entity cache (readUncached): a scan touching every entity once would otherwise evict
        // the cache's working set and, from many threads, stall on its eviction lock.
        //
        // A nid is counted if and only if it can actually be delivered: an orphan nid (allocated,
        // no committed bytes) is excluded from the count, the emission, and the stamp collection
        // alike (IKE-Network/ike-issues#933).
        lastOrphanCount.set(0);
        PrimitiveData.get().forEachConceptNid(nid -> aggregateIfInRange(nid, filteredStampNids, stampsToExport,
                conceptsAggregatedCount, nidConsumer));
        throwIfCancelled();

        PrimitiveData.get().forEachSemanticNid(nid -> aggregateIfInRange(nid, filteredStampNids, stampsToExport,
                semanticsAggregatedCount, nidConsumer));
        throwIfCancelled();

        PrimitiveData.get().forEachPatternNid(nid -> aggregateIfInRange(nid, filteredStampNids, stampsToExport,
                patternsAggregatedCount, nidConsumer));
        throwIfCancelled();

        // Export the aggregated stamps — resolution-checked like every other bucket, so the count
        // only claims stamps that can be delivered.
        List<Integer> deliverableStampNids = new ArrayList<>();
        for (int stampNid : stampsToExport) {
            if (readUncached(stampNid) != null) {
                deliverableStampNids.add(stampNid);
            } else {
                lastOrphanCount.incrementAndGet();
            }
        }
        stampsAggregatedCount.set(deliverableStampNids.size());
        deliverableStampNids.forEach(nidConsumer::accept);

        if (lastOrphanCount.get() > 0) {
            LOG.warn("Temporal aggregation skipped {} orphan nid(s) (allocated, no entity"
                    + " bytes) — excluded from counts and emission alike", lastOrphanCount.get());
        }
        return summarize();
    }

    /** Emits {@code nid} — its whole chronology — if any of its stamps falls in the range. */
    private void aggregateIfInRange(int nid, Set<Integer> filteredStampNids, Set<Integer> stampsToExport,
                                    AtomicLong aggregatedCount, IntConsumer nidConsumer) {
        if (isCancelled()) {
            return;
        }
        Entity<?> entity = readUncached(nid);
        if (entity == null) {
            lastOrphanCount.incrementAndGet();
            return;
        }
        Set<Integer> stampNids = entity.stampNids().mapToSet(i -> i);
        if (!Collections.disjoint(filteredStampNids, stampNids)) {
            aggregatedCount.incrementAndGet();
            nidConsumer.accept(nid);
            stampsToExport.addAll(stampNids);
        }
    }
}
