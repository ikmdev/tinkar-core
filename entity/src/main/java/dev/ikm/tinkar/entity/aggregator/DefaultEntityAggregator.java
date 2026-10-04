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
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.StampEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class DefaultEntityAggregator extends EntityAggregator {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultEntityAggregator.class);

    @Override
    public EntityCountSummary aggregate(IntConsumer nidConsumer) {
        initCounts();
        // Aggregate all Stamps
        PrimitiveData.get().forEachStampNid(stampNid -> {
            nidConsumer.accept(stampNid);
            stampsAggregatedCount.incrementAndGet();
        });

        // Aggregate all Concepts
        PrimitiveData.get().forEachConceptNid(conceptNid -> {
            nidConsumer.accept(conceptNid);
            conceptsAggregatedCount.incrementAndGet();
        });

        // Aggregate all Semantics
        PrimitiveData.get().forEachSemanticNid(semanticNid -> {
            nidConsumer.accept(semanticNid);
            semanticsAggregatedCount.incrementAndGet();
        });

        // Aggregate all Patterns
        PrimitiveData.get().forEachPatternNid(patternNid -> {
            nidConsumer.accept(patternNid);
            patternsAggregatedCount.incrementAndGet();
        });

        return summarize();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Native override: an entity is counted only when it resolves, so counts reflect
     * entities actually delivered to {@code entityConsumer} rather than nids visited; and
     * it is counted by what it is, not by which enumeration delivered it, so the counts
     * describe what was delivered even where a provider enumerates an entity under the
     * wrong kind (a Rocks KB files a stamp made under another stamp pattern among the
     * semantics).
     */
    @Override
    public EntityCountSummary aggregateEntities(Consumer<Entity<?>> entityConsumer) {
        initCounts();
        AtomicLong orphanCount = new AtomicLong();

        PrimitiveData.get().forEachStampNid(nid -> dispatch(nid, entityConsumer, orphanCount));
        PrimitiveData.get().forEachConceptNid(nid -> dispatch(nid, entityConsumer, orphanCount));
        PrimitiveData.get().forEachSemanticNid(nid -> dispatch(nid, entityConsumer, orphanCount));
        PrimitiveData.get().forEachPatternNid(nid -> dispatch(nid, entityConsumer, orphanCount));

        long orphans = orphanCount.get();
        if (orphans > 0) {
            LOG.info("Skipped {} orphan nid(s) during aggregation (allocated, no entity bytes)", orphans);
        }
        return summarize();
    }

    private void dispatch(int nid,
                          Consumer<Entity<?>> entityConsumer,
                          AtomicLong orphanCount) {
        Entity<?> entity = EntityService.get().getEntityFast(nid);
        if (entity == null) {
            orphanCount.incrementAndGet();
            return;
        }
        entityConsumer.accept(entity);
        if (entity instanceof StampEntity<?>) {
            stampsAggregatedCount.incrementAndGet();
        } else if (entity instanceof ConceptEntity<?>) {
            conceptsAggregatedCount.incrementAndGet();
        } else if (entity instanceof SemanticEntity<?>) {
            semanticsAggregatedCount.incrementAndGet();
        } else if (entity instanceof PatternEntity<?>) {
            patternsAggregatedCount.incrementAndGet();
        } else {
            LOG.warn("Aggregated an entity of no known kind, nid={}: {}", nid, entity.getClass().getName());
        }
    }
}
