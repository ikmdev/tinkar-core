package org.hl7.tinkar.entity;

import com.github.benmanes.caffeine.cache.Cache;
import org.hl7.tinkar.common.alert.AlertObject;
import org.hl7.tinkar.common.alert.AlertStreams;
import org.hl7.tinkar.common.util.broadcast.Subscriber;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;

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
