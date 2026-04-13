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


/**
 * A publish-subscribe dispatcher that delivers items to registered subscribers.
 * Subscribers are held via weak references so they can be garbage-collected
 * when no longer strongly reachable.
 *
 * @param <T> the type of item dispatched to subscribers
 */
public interface Broadcaster<T> {

    /**
     * Dispatches the given item to all currently registered subscribers.
     *
     * @param item the item to broadcast
     */
    void dispatch(T item);

    /**
     * Registers a subscriber using a weak reference, allowing it to be
     * garbage-collected when no strong references remain.
     *
     * @param subscriber the subscriber to register
     */
    void addSubscriberWithWeakReference(Subscriber<T> subscriber);

    /**
     * Removes a previously registered subscriber so it no longer receives dispatched items.
     *
     * @param subscriber the subscriber to remove
     */
    void removeSubscriber(Subscriber<T> subscriber);

}
