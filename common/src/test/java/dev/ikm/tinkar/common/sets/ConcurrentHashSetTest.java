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
package dev.ikm.tinkar.common.sets;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A copy of a set others are changing holds members only: Transaction.activeTransactions()
 * copies its set while transactions open and close, and a null among the copies reached the
 * changes pane as a transaction.
 */
class ConcurrentHashSetTest {

    @Test
    void aSnapshotTakenWhileMembersLeaveHoldsNoNull() throws InterruptedException {
        ConcurrentHashSet<Integer> set = new ConcurrentHashSet<>();
        AtomicBoolean running = new AtomicBoolean(true);
        Thread churn = Thread.ofPlatform().start(() -> {
            int next = 0;
            while (running.get()) {
                set.add(next);
                set.remove(next - 8);
                next++;
            }
        });
        try {
            for (int i = 0; i < 200_000; i++) {
                Object[] snapshot = set.toArray();
                assertFalse(Arrays.asList(snapshot).contains(null), "a snapshot held a null");
            }
        } finally {
            running.set(false);
            churn.join();
        }
    }
}
