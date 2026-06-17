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
package dev.ikm.tinkar.common.util.broadcast;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Process-wide, events-free fan-out of commit notifications.
 * <p>
 * The entity layer publishes a {@link CommitNotification} here when a transaction commits;
 * higher layers (e.g. the {@code events} module) re-publish it as a typed event. This seam
 * lives in the {@code common} module — which the entity layer already requires — so the
 * entity layer can broadcast commits without depending on the (entity-dependent)
 * {@code events} module, avoiding a module dependency cycle.
 * <p>
 * A listener must never let an exception propagate; {@link #publish(CommitNotification)}
 * isolates listener failures so a misbehaving listener can never break a commit.
 */
public final class CommitBroadcaster {

    /**
     * A committed transaction, carried to listeners without any reference to the
     * {@code entity}/{@code events} types.
     *
     * @param transactionUuid the committed transaction's UUID
     * @param transactionName the transaction name (may be empty)
     * @param commitTime      the commit timestamp (epoch millis)
     * @param stampNids       the nids of the stamps finalized by this commit
     * @param componentNids   the nids of the components changed in this transaction
     * @param stampCount      the number of stamps finalized
     */
    public record CommitNotification(UUID transactionUuid, String transactionName, long commitTime,
                                     int[] stampNids, int[] componentNids, int stampCount) {}

    private static final CopyOnWriteArrayList<Consumer<CommitNotification>> LISTENERS =
            new CopyOnWriteArrayList<>();

    private CommitBroadcaster() {}

    /**
     * Registers a listener (idempotent). Listeners are invoked on the committing thread.
     *
     * @param listener the listener to add
     */
    public static void subscribe(Consumer<CommitNotification> listener) {
        LISTENERS.addIfAbsent(listener);
    }

    /**
     * Removes a previously-registered listener.
     *
     * @param listener the listener to remove
     */
    public static void unsubscribe(Consumer<CommitNotification> listener) {
        LISTENERS.remove(listener);
    }

    /**
     * Fans a commit notification out to all listeners. Listener exceptions are swallowed so
     * a listener can never break the commit that triggered this call.
     *
     * @param notification the commit notification to publish
     */
    public static void publish(CommitNotification notification) {
        for (Consumer<CommitNotification> listener : LISTENERS) {
            try {
                listener.accept(notification);
            } catch (Throwable ignored) {
                // A listener must never break a commit.
            }
        }
    }
}
