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
package dev.ikm.tinkar.events;

import dev.ikm.tinkar.common.service.LifecyclePhase;
import dev.ikm.tinkar.common.service.ServiceLifecycle;
import dev.ikm.tinkar.common.service.ServiceLifecyclePhase;
import dev.ikm.tinkar.common.util.broadcast.CommitBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Always-on bridge that re-publishes the entity layer's events-free
 * {@link CommitBroadcaster.CommitNotification}s as typed {@link CommitEvent}s on
 * {@link FrameworkTopics#COMMIT_TOPIC}.
 * <p>
 * This lives in the {@code events} module (which already depends on {@code entity}) rather than
 * in the entity layer, so the typed event/topic can stay here without inverting the module
 * dependency edge into a cycle. It is discovered and started by the {@link ServiceLifecycle}
 * machinery, so the typed {@code COMMIT_TOPIC} is available application-wide regardless of any
 * individual subscriber. It is placed after data load so the initial dataset import is not
 * re-published as per-commit events.
 */
@LifecyclePhase(value = ServiceLifecyclePhase.CORE_SERVICES, subPriority = 10)
public class CommitEventBridge implements ServiceLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(CommitEventBridge.class);
    private static final Object SOURCE = "CommitEventBridge";

    private final Consumer<CommitBroadcaster.CommitNotification> listener = this::republish;

    @Override
    public void startup() {
        CommitBroadcaster.subscribe(listener);
        LOG.info("CommitEventBridge installed: CommitBroadcaster -> EvtBus {}", FrameworkTopics.COMMIT_TOPIC);
    }

    @Override
    public void shutdown() {
        CommitBroadcaster.unsubscribe(listener);
    }

    private void republish(CommitBroadcaster.CommitNotification n) {
        try {
            EvtBusFactory.getDefaultEvtBus().publish(FrameworkTopics.COMMIT_TOPIC,
                    new CommitEvent(SOURCE, CommitEvent.COMMITTED, n.transactionUuid(),
                            n.transactionName(), n.commitTime(), n.stampNids(),
                            n.componentNids(), n.stampCount()));
        } catch (Throwable t) {
            LOG.error("Failed to re-publish commit notification", t);
        }
    }
}
