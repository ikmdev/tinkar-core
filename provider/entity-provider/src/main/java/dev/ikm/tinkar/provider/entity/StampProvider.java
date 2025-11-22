
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
package dev.ikm.tinkar.provider.entity;

import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.common.id.IntIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.broadcast.Subscriber;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.entity.util.EntityProcessor;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.impl.factory.primitive.LongLists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * TODO elegant shutdown of entityStream and others
 * <p>
 * TODO startup to wait for processing complete.
 * <p>
 * Encountered this error:
 * <p>
 * https://github.com/h2database/h2database/issues/2590
 * <p>
 * at dev.ikm.tinkar.provider.mvstore.MVStoreProvider.forEachParallel(MVStoreProvider.java:144)
 * at dev.ikm.tinkar.provider.entity.StampProvider.<init>(StampProvider.java:38)
 * at java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)
 * at java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:78)
 * at java.base/jdk.internal.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)
 */

public class StampProvider extends EntityProcessor<StampEntity<StampEntityVersion>, StampEntityVersion>
        implements StampService, Subscriber<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(StampProvider.class);

    //TODO: Revisit these field type choices...
    final ConcurrentHashMap<Integer, StampEntity> stamps = new ConcurrentHashMap<>();
    final ConcurrentSkipListSet<Long> times = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<Integer> authors = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<Integer> modules = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<Integer> paths = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<Integer> stampNids = new ConcurrentSkipListSet<>();
    
    private volatile boolean initialized = false;
    private volatile boolean subscribed = false;

    public StampProvider() {
        // Empty constructor - ServiceLoader requirement
    }

    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    // Only subscribe once per instance
                    if (!subscribed) {
                        try {
                            EntityService.get().addSubscriberWithWeakReference(this);
                            subscribed = true;
                        } catch (IllegalStateException e) {
                            // Already subscribed - this is fine
                            LOG.debug("Subscriber already registered: {}", this, e);
                            subscribed = true;
                        }
                    }
                    EntityService.get().forEachStampEntity(this);
                    initialized = true;
                }
            }
        }
    }

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
        ensureInitialized();
        return IntIds.set.of(stampNids.stream().mapToInt(wrappedPath -> (int) wrappedPath).toArray());
    }

    public ImmutableLongList timesInUse() {
        ensureInitialized();
        return LongLists.immutable.of(times.stream().mapToLong(wrappedTime -> wrappedTime.longValue()).toArray());
    }

    @Override
    public IntIdSet getAuthorNidsInUse() {
        ensureInitialized();
        return IntIds.set.of(authors, nid -> nid);
    }

    @Override
    public IntIdSet getModuleNidsInUse() {
        ensureInitialized();
        return IntIds.set.of(modules, nid -> nid);
    }

    @Override
    public IntIdSet getPathNidsInUse() {
        ensureInitialized();
        return IntIds.set.of(paths, nid -> nid);
    }

    @Override
    public ImmutableLongList getTimesInUse() {
        ensureInitialized();
        return LongLists.immutable.ofAll(times);
    }

    @Override
    public void onNext(Integer nid) {
        ensureInitialized();
        if (nid == Integer.MIN_VALUE) {
            return;
        }
        EntityHandle.get(nid).ifStamp(this::process);
    }
}