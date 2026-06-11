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

import java.util.UUID;

/**
 * Event published on {@link FrameworkTopics#COMMIT_TOPIC} when a transaction commits.
 * <p>
 * Unlike the bare-nid {@code Broadcaster<Integer>} refresh broadcast, this event carries the
 * finalized stamp nids and the changed component nids of the whole transaction, so subscribers
 * (e.g. a commit narrator) can react to a commit as a unit.
 */
public class CommitEvent extends Evt {

    public static final EvtType<CommitEvent> COMMITTED = new EvtType<>(Evt.ANY, "COMMITTED");

    private final UUID transactionUuid;
    private final String transactionName;
    private final long commitTime;
    private final int[] stampNids;
    private final int[] componentNids;
    private final int stampCount;

    /**
     * Constructs a commit event.
     *
     * @param source          the object on which the event initially occurred
     * @param eventType       the event type (typically {@link #COMMITTED})
     * @param transactionUuid the committed transaction's UUID
     * @param transactionName the transaction name (may be empty)
     * @param commitTime      the commit timestamp (epoch millis)
     * @param stampNids       the nids of the stamps finalized by this commit
     * @param componentNids   the nids of the components changed in this transaction
     * @param stampCount      the number of stamps finalized
     */
    public CommitEvent(Object source, EvtType eventType, UUID transactionUuid, String transactionName,
                       long commitTime, int[] stampNids, int[] componentNids, int stampCount) {
        super(source, eventType);
        this.transactionUuid = transactionUuid;
        this.transactionName = transactionName;
        this.commitTime = commitTime;
        this.stampNids = stampNids;
        this.componentNids = componentNids;
        this.stampCount = stampCount;
    }

    public UUID transactionUuid() {
        return transactionUuid;
    }

    public String transactionName() {
        return transactionName;
    }

    public long commitTime() {
        return commitTime;
    }

    public int[] stampNids() {
        return stampNids;
    }

    public int[] componentNids() {
        return componentNids;
    }

    public int stampCount() {
        return stampCount;
    }
}
