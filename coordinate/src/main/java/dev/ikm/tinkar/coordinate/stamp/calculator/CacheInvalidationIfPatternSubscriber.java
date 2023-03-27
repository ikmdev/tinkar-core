package dev.ikm.tinkar.coordinate.stamp.calculator;

import com.github.benmanes.caffeine.cache.Cache;
import dev.ikm.tinkar.common.alert.AlertObject;
import dev.ikm.tinkar.common.alert.AlertStreams;
import dev.ikm.tinkar.common.util.broadcast.Subscriber;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.PatternEntity;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;

public class CacheInvalidationIfPatternSubscriber implements Subscriber<Integer> {
    CopyOnWriteArrayList<Cache<? extends Object, ? extends Object>> cachesToManage = new CopyOnWriteArrayList<>();


    public void addCaches(Cache<? extends Object, ? extends Object>... caches) {
        for (Cache<?, ?> cache : caches) {
            cachesToManage.add(cache);
        }
    }


    @Override
    public void onNext(Integer nid) {
        // Do nothing with item, but request another...
        Entity entity = Entity.provider().getEntityFast(nid);
        if (entity instanceof PatternEntity) {
            for (Cache<?, ?> cache : cachesToManage) {
                cache.invalidateAll();
            }
        }
    }

}
