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

import dev.ikm.tinkar.common.service.EntityCountSummary;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public abstract class EntityAggregator {
    private static final Logger LOG = LoggerFactory.getLogger(EntityAggregator.class);

    protected AtomicLong conceptsAggregatedCount = new AtomicLong(0);
    protected AtomicLong semanticsAggregatedCount = new AtomicLong(0);
    protected AtomicLong patternsAggregatedCount = new AtomicLong(0);
    protected AtomicLong stampsAggregatedCount = new AtomicLong(0);

    public abstract EntityCountSummary aggregate(IntConsumer nidConsumer);

    /**
     * Aggregates entities by resolving each nid produced by {@link #aggregate(IntConsumer)}
     * to its {@link Entity} and forwarding non-null results to {@code entityConsumer}.
     * Orphan nids — sequences allocated by the store but with no committed entity bytes
     * (typically a component that was canceled between sequence allocation and commit) —
     * are silently skipped and reported in aggregate at INFO level.
     *
     * <p>The default implementation delegates to {@link #aggregate(IntConsumer)} and
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
        EntityCountSummary summary = aggregate((int nid) -> {
            Entity<?> entity = EntityService.get().getEntityFast(nid);
            if (entity == null) {
                orphanCount.incrementAndGet();
                return;
            }
            entityConsumer.accept(entity);
        });
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
