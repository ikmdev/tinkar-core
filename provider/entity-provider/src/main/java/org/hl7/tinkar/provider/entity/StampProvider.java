package org.hl7.tinkar.provider.entity;

import com.google.auto.service.AutoService;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.impl.factory.primitive.LongLists;
import org.hl7.tinkar.common.id.IntIdSet;
import org.hl7.tinkar.common.id.IntIds;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.util.broadcast.Subscriber;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.entity.util.EntityProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Flow;

/**
 * TODO elegant shutdown of entityStream and others
 * <p>
 * TODO startup to wait for processing complete.
 * <p>
 * Encountered this error:
 * <p>
 * https://github.com/h2database/h2database/issues/2590
 * <p>
 * at org.hl7.tinkar.provider.mvstore.MVStoreProvider.forEachParallel(MVStoreProvider.java:144)
 * at org.hl7.tinkar.provider.entity.StampProvider.<init>(StampProvider.java:38)
 * at java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)
 * at java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:78)
 * at java.base/jdk.internal.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)
 */

@AutoService({StampService.class})
public class StampProvider extends EntityProcessor implements StampService, Subscriber<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(StampProvider.class);

    final ConcurrentHashMap<Integer, StampEntity> stamps = new ConcurrentHashMap<>();
    final ConcurrentSkipListSet<Long> times = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<Integer> authors = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<Integer> modules = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<Integer> paths = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<Integer> stampNids = new ConcurrentSkipListSet<>();

    public StampProvider() {
        EntityService.get().addSubscriberWithWeakReference(this);
        PrimitiveData.get().forEachParallel(this);
    }

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

    @Override
    public IntIdSet getAuthorNidsInUse() {
        return IntIds.set.of(authors, nid -> nid);
    }

    @Override
    public IntIdSet getModuleNidsInUse() {
        return IntIds.set.of(modules, nid -> nid);
    }

    @Override
    public IntIdSet getPathNidsInUse() {
        return IntIds.set.of(paths, nid -> nid);
    }

    @Override
    public ImmutableLongList getTimesInUse() {
        return LongLists.immutable.ofAll(times);
    }



    @Override
    public void onNext(Integer nid) {
        Entity entity = Entity.provider().getEntityFast(nid);
        if (entity instanceof StampEntity stampEntity) {
            stamps.put(stampEntity.nid(), stampEntity);
            times.add(stampEntity.time());
            authors.add(stampEntity.authorNid());
            modules.add(stampEntity.moduleNid());
            paths.add(stampEntity.pathNid());
            stampNids.add(stampEntity.nid());
        }
    }
}
