package dev.ikm.tinkar.entity.util;

import dev.ikm.tinkar.common.id.LongIdSet;
import dev.ikm.tinkar.common.id.LongIds;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampEntityVersion;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.impl.factory.primitive.LongLists;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class StampRealizer extends EntityProcessor<StampEntity<StampEntityVersion>, StampEntityVersion> {
    ConcurrentHashMap<Long, StampEntity> stamps = new ConcurrentHashMap<>();
    ConcurrentSkipListSet<Long> times = new ConcurrentSkipListSet<>();
    ConcurrentSkipListSet<Long> authors = new ConcurrentSkipListSet<>();
    ConcurrentSkipListSet<Long> modules = new ConcurrentSkipListSet<>();
    ConcurrentSkipListSet<Long> paths = new ConcurrentSkipListSet<>();
    ConcurrentSkipListSet<Long> stampNids = new ConcurrentSkipListSet<>();

    /**
     * Single method to implement - handles both byte-based and entity-based processing.
     */
    @Override
    protected void process(StampEntity<StampEntityVersion> stampEntity) {
        stamps.put(stampEntity.nid(), stampEntity);
        times.add(stampEntity.time());
        authors.add(stampEntity.authorNid());
        modules.add(stampEntity.moduleNid());
        paths.add(stampEntity.pathNid());
        stampNids.add(stampEntity.nid());
    }

    public LongIdSet stampNids() {
        return LongIds.set.of(stampNids.stream().mapToLong(stampNid -> stampNid).toArray());
    }

    public ImmutableLongList timesInUse() {
        return LongLists.immutable.of(times.stream().mapToLong(wrappedTime -> wrappedTime.longValue()).toArray());
    }
}