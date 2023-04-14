package dev.ikm.tinkar.entity;

import com.github.benmanes.caffeine.cache.Cache;
import dev.ikm.tinkar.common.util.broadcast.Subscriber;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Remove objects from a cache based on
 */
public class CacheInvalidationSubscriber implements Subscriber<Integer> {
    CopyOnWriteArrayList<Cache<Integer, ? extends Object>> cachesToManage = new CopyOnWriteArrayList<>();


    public void addCaches(Cache<Integer, ? extends Object>... caches) {
        for (Cache<Integer, ?> cache : caches) {
            cachesToManage.add(cache);
        }
    }



    @Override
    public void onNext(Integer nid) {
        // Do nothing with item, but request another...
        for (Cache<Integer, ?> cache : cachesToManage) {
            cache.invalidate(nid);
        }
    }
}
