/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
