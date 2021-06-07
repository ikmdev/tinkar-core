package org.hl7.tinkar.provider.entity;

import com.google.auto.service.AutoService;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.impl.factory.primitive.LongLists;
import org.hl7.tinkar.common.id.IntIdSet;
import org.hl7.tinkar.common.id.IntIds;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.entity.util.EntityProcessor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Flow;

/**
 * TODO elegant shutdown of entityStream and others
 *
 * TODO startup to wait for processing complete.
 */

@AutoService({StampService.class})
public class StampProvider extends EntityProcessor implements StampService, Flow.Subscriber<Entity<? extends EntityVersion>> {
    protected static final System.Logger LOG = System.getLogger(StampProvider.class.getName());

    final ConcurrentHashMap<Integer, StampEntity> stamps = new ConcurrentHashMap<>();
    final ConcurrentSkipListSet<Long> times = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<Integer> authors = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<Integer> modules = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<Integer> paths = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<Integer> stampNids = new ConcurrentSkipListSet<>();

    public StampProvider() {
        EntityService.get().subscribe(this);
        PrimitiveData.get().forEachParallel(this);
    }

    @Override
    public void processBytesForType(FieldDataType componentType, byte[] bytes) {
        if (componentType == FieldDataType.STAMP) {
            StampEntity stampEntity = EntityFactory.make(bytes);
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
    public void onSubscribe(Flow.Subscription s) {
        LOG.log(System.Logger.Level.INFO, "Subscribed to Entity Stream");
    }

    @Override
    public void onNext(Entity<? extends EntityVersion> entity) {
        if (entity instanceof StampEntity stampEntity) {
            stamps.put(stampEntity.nid(), stampEntity);
            times.add(stampEntity.time());
            authors.add(stampEntity.authorNid());
            modules.add(stampEntity.moduleNid());
            paths.add(stampEntity.pathNid());
            stampNids.add(stampEntity.nid());
        }
    }

    @Override
    public void onError(Throwable t) {
        LOG.log(System.Logger.Level.ERROR, t);
    }

    @Override
    public void onComplete() {
        LOG.log(System.Logger.Level.INFO, "Entity Stream complete");
    }
}
