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
package dev.ikm.tinkar.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * After a load phase the search index is settled: indexed live, or overflowed and recreated. The
 * threshold is a fraction of the store the load phase began on, never below the minimum.
 */
class LoadPhaseSearchPolicyTest {

    @Test
    void aChangeSetUnderTheThresholdIsSettledByLiveIndexing() {
        LoadPhaseSearchPolicy policy = new LoadPhaseSearchPolicy();
        policy.reset(0);
        for (int i = 0; i < 10; i++) {
            assertTrue(policy.shouldIndexLive());
        }
        assertFalse(policy.recreateRequired());
        assertTrue(policy.searchIndexSettled());
    }

    @Test
    void anOverflowedChangeSetIsSettledOnlyByARecreate() {
        LoadPhaseSearchPolicy policy = new LoadPhaseSearchPolicy();
        policy.reset(0);
        overflow(policy);
        assertTrue(policy.recreateRequired());
        assertFalse(policy.searchIndexSettled());
        policy.markRecreated();
        assertTrue(policy.searchIndexSettled());
    }

    @Test
    void resetForgetsTheRecreateWithTheOverflow() {
        LoadPhaseSearchPolicy policy = new LoadPhaseSearchPolicy();
        policy.reset(0);
        overflow(policy);
        policy.markRecreated();
        policy.reset(0);
        assertFalse(policy.recreateRequired());
        assertTrue(policy.searchIndexSettled());
        overflow(policy);
        assertFalse(policy.searchIndexSettled());
    }

    @Test
    void theThresholdScalesWithTheStoreAboveTheMinimum() {
        long large = 10 * (long) (LoadPhaseSearchPolicy.MINIMUM_LIVE_INDEX_THRESHOLD / LoadPhaseSearchPolicy.STORE_FRACTION);
        assertEquals(LoadPhaseSearchPolicy.MINIMUM_LIVE_INDEX_THRESHOLD, LoadPhaseSearchPolicy.thresholdFor(-1),
                "a store that cannot say its size gets the minimum");
        assertEquals(LoadPhaseSearchPolicy.MINIMUM_LIVE_INDEX_THRESHOLD, LoadPhaseSearchPolicy.thresholdFor(1_000));
        assertEquals((long) (LoadPhaseSearchPolicy.STORE_FRACTION * large), LoadPhaseSearchPolicy.thresholdFor(large));

        LoadPhaseSearchPolicy policy = new LoadPhaseSearchPolicy();
        policy.reset(large);
        assertEquals(LoadPhaseSearchPolicy.thresholdFor(large), policy.threshold());
        for (long i = 0; i <= LoadPhaseSearchPolicy.MINIMUM_LIVE_INDEX_THRESHOLD; i++) {
            policy.shouldIndexLive();
        }
        assertFalse(policy.recreateRequired(), "past the minimum, still within the store's share");
        policy.reset(0);
        assertEquals(LoadPhaseSearchPolicy.MINIMUM_LIVE_INDEX_THRESHOLD, policy.threshold(),
                "each load phase takes the threshold of the store it begins on");
    }

    private static void overflow(LoadPhaseSearchPolicy policy) {
        for (long i = 0; i <= policy.threshold(); i++) {
            policy.shouldIndexLive();
        }
    }
}
