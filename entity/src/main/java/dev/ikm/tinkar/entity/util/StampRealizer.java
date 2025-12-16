package dev.ikm.tinkar.entity.util;

import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.common.id.IntIds;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampEntityVersion;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.impl.factory.primitive.LongLists;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class StampRealizer extends EntityProcessor<StampEntity<StampEntityVersion>, StampEntityVersion> {
    ConcurrentHashMap<Integer, StampEntity> stamps = new ConcurrentHashMap<>();
    ConcurrentSkipListSet<Long> times = new ConcurrentSkipListSet<>();
    ConcurrentSkipListSet<Integer> authors = new ConcurrentSkipListSet<>();
    ConcurrentSkipListSet<Integer> modules = new ConcurrentSkipListSet<>();
    ConcurrentSkipListSet<Integer> paths = new ConcurrentSkipListSet<>();
    ConcurrentSkipListSet<Integer> stampNids = new ConcurrentSkipListSet<>();

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

    public IntIdSet stampNids() {
        return IntIds.set.of(stampNids.stream().mapToInt(wrappedPath -> (int) wrappedPath).toArray());
    }

    public ImmutableLongList timesInUse() {
        return LongLists.immutable.of(times.stream().mapToLong(wrappedTime -> wrappedTime.longValue()).toArray());
    }
}