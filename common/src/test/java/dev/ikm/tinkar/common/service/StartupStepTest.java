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
package dev.ikm.tinkar.common.service;

import dev.ikm.tinkar.common.service.ServiceLifecycleManager.StartupStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A startup step counts what a progress indicator shows: steps complete, steps remaining. */
class StartupStepTest {

    @Test
    void countsTheStepsAroundThisOne() {
        StartupStep sixthOfEleven = new StartupStep("DataLoadController", ServiceLifecyclePhase.DATA_LOAD, 5, 11, 4, 7);
        assertEquals(5, sixthOfEleven.completed());
        assertEquals(5, sixthOfEleven.remaining());

        StartupStep first = new StartupStep("MigrationRelaunchLifecycle", ServiceLifecyclePhase.INFRASTRUCTURE, 0, 11, 0, 7);
        assertEquals(0, first.completed());
        assertEquals(10, first.remaining());

        StartupStep last = new StartupStep("ComplexClauseBootstrap", ServiceLifecyclePhase.APPLICATION_SERVICES, 10, 11, 6, 7);
        assertEquals(10, last.completed());
        assertEquals(0, last.remaining());
    }

    @Test
    void labelsThePhaseInWords() {
        assertEquals("Data storage", step(ServiceLifecyclePhase.DATA_STORAGE).phaseLabel());
        assertEquals("Infrastructure", step(ServiceLifecyclePhase.INFRASTRUCTURE).phaseLabel());
        assertEquals("Application services", step(ServiceLifecyclePhase.APPLICATION_SERVICES).phaseLabel());
    }

    private static StartupStep step(ServiceLifecyclePhase phase) {
        return new StartupStep("service", phase, 0, 1, 0, 1);
    }
}
