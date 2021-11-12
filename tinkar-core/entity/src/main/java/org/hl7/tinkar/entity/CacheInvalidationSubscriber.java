package org.hl7.tinkar.entity;

import com.github.benmanes.caffeine.cache.Cache;
import org.hl7.tinkar.common.alert.AlertObject;
import org.hl7.tinkar.common.alert.AlertStreams;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;

/**
 * Remove objects from a cache based on
 */
public class CacheInvalidationSubscriber implements Flow.Subscriber<Integer> {
    Flow.Subscription subscription;
    CopyOnWriteArrayList<Cache<Integer, ? extends Object>> cachesToManage = new CopyOnWriteArrayList<>();

    public Flow.Subscription subscription() {
        return subscription;
    }

    public void addCaches(Cache<Integer, ? extends Object>... caches) {
        for (Cache<Integer, ?> cache : caches) {
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
        for (Cache<Integer, ?> cache : cachesToManage) {
            cache.invalidate(nid);
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
