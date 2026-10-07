package dev.ikm.tinkar.entity.aggregator;

import java.util.function.LongConsumer;

import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.common.service.EntityCountSummary;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.SemanticEntity;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

import java.util.function.IntConsumer;

public class InferredEntityAggregatorFilter extends EntityAggregatorFilter {

    public InferredEntityAggregatorFilter(EntityAggregator entityAggregator) {
        super(entityAggregator);
    }

    @Override
    public EntityCountSummary aggregate(LongConsumer nidConsumer) {
        initCounts();
        final ImmutableList<Long> inferredNidList = Lists.immutable.of(
                KernelTerm.INFERRED_NAVIGATION_PATTERN.nid(),
                KernelTerm.EL_PLUS_PLUS_INFERRED_AXIOMS_PATTERN.nid());

        LongConsumer inferredFilterConsumer = (nid) -> {
            Entity<? extends EntityVersion> entity = EntityHandle.get(nid).orNull();
            // Filter out inferred Semantics
            if (entity instanceof SemanticEntity semanticEntity
                && inferredNidList.contains(semanticEntity.patternNid())) {
                semanticsFilteredCount.incrementAndGet();
            } else {
                nidConsumer.accept(nid);
            }
        };

        EntityCountSummary unfilteredEntityCounts = entityAggregator.aggregate(inferredFilterConsumer);
        adjustCounts(unfilteredEntityCounts);
        return summarize();
    }

}
