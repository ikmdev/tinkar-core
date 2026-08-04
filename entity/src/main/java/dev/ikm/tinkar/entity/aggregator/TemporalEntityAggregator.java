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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private long lastOrphanCount;

    public TemporalEntityAggregator(long fromEpochMillis, long toEpochMillis) {
        this.fromEpochMillis = fromEpochMillis;
        this.toEpochMillis = toEpochMillis;
    }

    @Override
    public EntityCountSummary aggregate(IntConsumer nidConsumer) {
        initCounts();
        // Filter Stamp Nids based on the supplied time span
        Set<Integer> filteredStampNids = new HashSet<>();
        PrimitiveData.get().forEachStampNid((stampNid) -> {
            EntityService.get().getStamp(stampNid).ifPresent((stampEntity) -> {
                if (fromEpochMillis <= stampEntity.time() && stampEntity.time() <= toEpochMillis) {
                    filteredStampNids.add(stampEntity.nid());
                }
            });
        });

        List<Integer> stampsToExport = new ArrayList<>();

        // Aggregate concepts with a filtered stamp. Resolution goes through
        // getEntityFast — the byte-backed lookup the downstream consumer uses — so a
        // nid is counted if and only if it can actually be delivered: an orphan nid
        // (allocated, no committed bytes; the entity cache may still answer for it)
        // is excluded from the count, the emission, and the stamp collection alike
        // (IKE-Network/ike-issues#933).
        lastOrphanCount = 0;
        PrimitiveData.get().forEachConceptNid((conceptNid) -> {
            Entity<?> conceptEntity = EntityService.get().getEntityFast(conceptNid);
            if (conceptEntity == null) {
                lastOrphanCount++;
                return;
            }
            Set<Integer> conceptStampNidList = conceptEntity.stampNids().mapToSet(i->i);
            // Write whole chronology if ANY of the stamps satisfy conditions
            if (!Collections.disjoint(filteredStampNids, conceptStampNidList)) {
                conceptsAggregatedCount.incrementAndGet();
                nidConsumer.accept(conceptNid);
                stampsToExport.addAll(conceptStampNidList);
            }
        });

        // Aggregate semantics with a filtered stamp
        PrimitiveData.get().forEachSemanticNid((semanticNid) -> {
            Entity<?> semanticEntity = EntityService.get().getEntityFast(semanticNid);
            if (semanticEntity == null) {
                lastOrphanCount++;
                return;
            }
            Set<Integer> semanticStampNidList = semanticEntity.stampNids().mapToSet(i->i);
            // Write whole chronology if ANY of the stamps satisfy conditions
            if (!Collections.disjoint(filteredStampNids, semanticStampNidList)) {
                semanticsAggregatedCount.incrementAndGet();
                nidConsumer.accept(semanticNid);
                stampsToExport.addAll(semanticStampNidList);
            }
        });

        // Aggregate patterns with a filtered stamp
        PrimitiveData.get().forEachPatternNid((patternNid) -> {
            Entity<?> patternEntity = EntityService.get().getEntityFast(patternNid);
            if (patternEntity == null) {
                lastOrphanCount++;
                return;
            }
            Set<Integer> patternStampNidList = patternEntity.stampNids().mapToSet(i->i);
            // Write whole chronology if ANY of the stamps satisfy conditions
            if (!Collections.disjoint(filteredStampNids, patternStampNidList)) {
                patternsAggregatedCount.incrementAndGet();
                nidConsumer.accept(patternNid);
                stampsToExport.addAll(patternStampNidList);
            }
        });

        // Deduplicate and export aggregated stamps — resolution-checked like every
        // other bucket, so the count only claims stamps that can be delivered.
        Set<Integer> deduplicatedStampsToExport = new HashSet<>(stampsToExport);
        List<Integer> deliverableStampNids = new ArrayList<>();
        for (int stampNid : deduplicatedStampsToExport) {
            if (EntityService.get().getEntityFast(stampNid) != null) {
                deliverableStampNids.add(stampNid);
            } else {
                lastOrphanCount++;
            }
        }
        stampsAggregatedCount.set(deliverableStampNids.size());
        deliverableStampNids.forEach(nidConsumer::accept);

        if (lastOrphanCount > 0) {
            LOG.warn("Temporal aggregation skipped {} orphan nid(s) (allocated, no entity"
                    + " bytes) — excluded from counts and emission alike", lastOrphanCount);
        }
        return summarize();
    }
}
