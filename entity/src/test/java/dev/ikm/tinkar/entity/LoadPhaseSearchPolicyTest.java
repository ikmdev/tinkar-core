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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** After a load phase the search index is settled: indexed live, or overflowed and recreated. */
class LoadPhaseSearchPolicyTest {

    @Test
    void aChangeSetUnderTheThresholdIsSettledByLiveIndexing() {
        LoadPhaseSearchPolicy policy = new LoadPhaseSearchPolicy();
        for (int i = 0; i < 10; i++) {
            assertTrue(policy.shouldIndexLive());
        }
        assertFalse(policy.recreateRequired());
        assertTrue(policy.searchIndexSettled());
    }

    @Test
    void anOverflowedChangeSetIsSettledOnlyByARecreate() {
        LoadPhaseSearchPolicy policy = new LoadPhaseSearchPolicy();
        for (int i = 0; i <= LoadPhaseSearchPolicy.threshold(); i++) {
            policy.shouldIndexLive();
        }
        assertTrue(policy.recreateRequired());
        assertFalse(policy.searchIndexSettled());
        policy.markRecreated();
        assertTrue(policy.searchIndexSettled());
    }

    @Test
    void resetForgetsTheRecreateWithTheOverflow() {
        LoadPhaseSearchPolicy policy = new LoadPhaseSearchPolicy();
        for (int i = 0; i <= LoadPhaseSearchPolicy.threshold(); i++) {
            policy.shouldIndexLive();
        }
        policy.markRecreated();
        policy.reset();
        assertFalse(policy.recreateRequired());
        assertTrue(policy.searchIndexSettled());
        for (int i = 0; i <= LoadPhaseSearchPolicy.threshold(); i++) {
            policy.shouldIndexLive();
        }
        assertFalse(policy.searchIndexSettled());
    }
}
