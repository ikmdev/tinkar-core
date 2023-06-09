package dev.ikm.tinkar.entity.util;

import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.common.id.IntIds;
import dev.ikm.tinkar.component.FieldDataType;
import dev.ikm.tinkar.entity.EntityRecordFactory;
import dev.ikm.tinkar.entity.StampEntity;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.impl.factory.primitive.LongLists;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

//TODO Add listener capability to primitive data store...
public class StampRealizer extends EntityProcessor {
    ConcurrentHashMap<Integer, StampEntity> stamps = new ConcurrentHashMap<>();
    ConcurrentSkipListSet<Long> times = new ConcurrentSkipListSet<>();
    ConcurrentSkipListSet<Integer> authors = new ConcurrentSkipListSet<>();
    ConcurrentSkipListSet<Integer> modules = new ConcurrentSkipListSet<>();
    ConcurrentSkipListSet<Integer> paths = new ConcurrentSkipListSet<>();
    ConcurrentSkipListSet<Integer> stampNids = new ConcurrentSkipListSet<>();

    @Override
    public void processBytesForType(FieldDataType componentType, byte[] bytes) {
        if (componentType == FieldDataType.STAMP) {
            StampEntity stampEntity = EntityRecordFactory.make(bytes);
            stamps.put(stampEntity.nid(), stampEntity);
            times.add(stampEntity.time());
            authors.add(stampEntity.authorNid());
            modules.add(stampEntity.moduleNid());
            paths.add(stampEntity.pathNid());
            stampNids.add(stampEntity.nid());
        }
    }

    public IntIdSet stampNids() {
        return IntIds.set.of(stampNids.stream().mapToInt(wrappedPath -> (int) wrappedPath).toArray());
    }

    public ImmutableLongList timesInUse() {
        return LongLists.immutable.of(times.stream().mapToLong(wrappedTime -> wrappedTime.longValue()).toArray());
    }
}
