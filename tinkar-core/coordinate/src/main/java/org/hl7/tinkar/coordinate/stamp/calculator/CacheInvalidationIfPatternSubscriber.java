package org.hl7.tinkar.coordinate.stamp.calculator;

import com.github.benmanes.caffeine.cache.Cache;
import org.hl7.tinkar.common.alert.AlertObject;
import org.hl7.tinkar.common.alert.AlertStreams;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.PatternEntity;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;

public class CacheInvalidationIfPatternSubscriber implements Flow.Subscriber<Integer> {
    Flow.Subscription subscription;
    CopyOnWriteArrayList<Cache<? extends Object, ? extends Object>> cachesToManage = new CopyOnWriteArrayList<>();


    public Flow.Subscription subscription() {
        return subscription;
    }

    public void addCaches(Cache<? extends Object, ? extends Object>... caches) {
        for (Cache<?, ?> cache : caches) {
            cachesToManage.add(cache);
        }
    }


    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        this.subscription.request(1);
    }

    @Override
    public void onNext(Integer nid) {
        // Do nothing with item, but request another...
        this.subscription.request(1);
        Entity entity = Entity.provider().getEntityFast(nid);
        if (entity instanceof PatternEntity) {
            for (Cache<?, ?> cache : cachesToManage) {
                cache.invalidateAll();
            }
        }
    }

    @Override
    public void onError(Throwable throwable) {
        AlertStreams.getRoot().dispatch(AlertObject.makeError(throwable));
    }

    @Override
    public void onComplete() {
        // Do nothing
    }
}
