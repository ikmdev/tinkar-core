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

import java.util.function.LongConsumer;

import dev.ikm.tinkar.common.service.EntityCountSummary;
import dev.ikm.tinkar.common.service.internal.EntityStore;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityRecordFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public abstract class EntityAggregator {
    private static final Logger LOG = LoggerFactory.getLogger(EntityAggregator.class);

    protected AtomicLong conceptsAggregatedCount = new AtomicLong(0);
    protected AtomicLong semanticsAggregatedCount = new AtomicLong(0);
    protected AtomicLong patternsAggregatedCount = new AtomicLong(0);
    protected AtomicLong stampsAggregatedCount = new AtomicLong(0);

    /** Checked during a scan; once true, the scan skips what is left and the aggregation throws. */
    private volatile BooleanSupplier cancelled = () -> false;

    public abstract EntityCountSummary aggregate(LongConsumer nidConsumer);

    /**
     * Makes the aggregation stop early once {@code cancelled} returns true, throwing
     * {@link CancellationException}. Scans run in parallel and cannot be broken out of, so each
     * remaining nid is skipped instead — it is reading them that takes the time.
     */
    public void setCancellationCheck(BooleanSupplier cancelled) {
        this.cancelled = cancelled == null ? () -> false : cancelled;
    }

    protected boolean isCancelled() {
        return cancelled.getAsBoolean();
    }

    /** @throws CancellationException if the aggregation has been cancelled */
    protected void throwIfCancelled() {
        if (isCancelled()) {
            throw new CancellationException("Aggregation cancelled");
        }
    }

    /**
     * Whether {@link #totalCount()} is cheap enough to call before aggregating, to size a progress
     * bar. It runs the whole aggregation once over, so for an aggregator that has to read every
     * entity to decide — a time range, say — it doubles the cost of an export.
     */
    public boolean totalCountIsCheap() {
        return false;
    }

    /**
     * Reads an entity straight from the store, bypassing the shared entity cache.
     *
     * <p>For scans that touch every entity once: sending millions of one-off reads through the
     * cache evicts the working set everyone else relies on, and with many threads it stalls on
     * the cache's eviction lock.
     *
     * @return the entity, or null if the nid has no entity bytes
     */
    protected static Entity<?> readUncached(long nid) {
        byte[] bytes = EntityStore.current().getBytes(nid);
        return bytes == null ? null : EntityRecordFactory.make(bytes);
    }

    /**
     * Aggregates entities by resolving each nid produced by {@link #aggregate(LongConsumer)}
     * to its {@link Entity} and forwarding non-null results to {@code entityConsumer}.
     * Orphan nids — sequences allocated by the store but with no committed entity bytes
     * (typically a component that was canceled between sequence allocation and commit) —
     * are silently skipped and reported in aggregate at INFO level.
     *
     * <p>The default implementation delegates to {@link #aggregate(LongConsumer)} and
     * therefore inherits whatever per-bucket counting that method performs. With the
     * {@link DefaultEntityAggregator} that means per-bucket counts will overstate by
     * the number of orphans, since they are incremented per nid visited rather than
     * per entity successfully resolved. Subclasses that need accurate per-bucket counts
     * in the presence of orphans should override this method (see
     * {@link DefaultEntityAggregator#aggregateEntities(Consumer)}).
     *
     * @param entityConsumer receives each non-null aggregated entity
     * @return summary of aggregated counts
     */
    public EntityCountSummary aggregateEntities(Consumer<Entity<?>> entityConsumer) {
        AtomicLong orphanCount = new AtomicLong();
        EntityCountSummary summary = aggregate((long nid) -> {
            if (isCancelled()) {
                return;
            }
            Entity<?> entity = readUncached(nid);
            if (entity == null) {
                orphanCount.incrementAndGet();
                return;
            }
            entityConsumer.accept(entity);
        });
        throwIfCancelled();
        long orphans = orphanCount.get();
        if (orphans > 0) {
            LOG.info("Skipped {} orphan nid(s) during aggregation (allocated, no entity bytes)", orphans);
        }
        return summary;
    }

    public long totalCount() {
        EntityCountSummary countSummary = this.aggregate((nid) -> {});
        return countSummary.getTotalCount();
    }

    public EntityCountSummary summarize() {
        return new EntityCountSummary(
            conceptsAggregatedCount.get(),
            semanticsAggregatedCount.get(),
            patternsAggregatedCount.get(),
            stampsAggregatedCount.get()
        );
    }

    protected void initCounts() {
        conceptsAggregatedCount.set(0);
        semanticsAggregatedCount.set(0);
        patternsAggregatedCount.set(0);
        stampsAggregatedCount.set(0);
    }
}
