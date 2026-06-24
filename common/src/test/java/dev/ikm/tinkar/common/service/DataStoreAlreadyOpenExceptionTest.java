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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the non-retryable startup-failure contract that the
 * {@link ServiceLifecycleManager} relies on to choose a terminal
 * {@code FAILED} state over a retry reset, and that the desktop relies on to
 * present a graceful "already open" message.
 */
class DataStoreAlreadyOpenExceptionTest {

    @Test
    void carriesPathAndIsNonRetryable() {
        DataStoreAlreadyOpenException ex =
                new DataStoreAlreadyOpenException("/data/foo", new RuntimeException("lock"));

        assertEquals("/data/foo", ex.dataStorePath());
        assertTrue(ex instanceof NonRetryableStartupFailure,
                "DataStoreAlreadyOpenException must be a NonRetryableStartupFailure");
        assertTrue(ex.getMessage().contains("/data/foo"));
        assertSame(RuntimeException.class, ex.getCause().getClass());
    }

    @Test
    void detectedThroughWrappedCauseChain() {
        // Mirrors the real propagation: SearchProvider throws it, ProviderController
        // wraps once, ServiceLifecycleManager wraps again.
        DataStoreAlreadyOpenException root = new DataStoreAlreadyOpenException("/data/foo", null);
        RuntimeException providerWrap = new RuntimeException("Failed to start SearchProvider", root);
        RuntimeException managerWrap = new RuntimeException(
                "Service startup failed: SearchProvider$Controller", providerWrap);

        assertTrue(NonRetryableStartupFailure.isPresentIn(managerWrap));
        assertTrue(NonRetryableStartupFailure.findIn(managerWrap).isPresent());
        assertSame(root, DataStoreAlreadyOpenException.findIn(managerWrap).orElseThrow());
    }

    @Test
    void selfDetectionWithoutWrapping() {
        DataStoreAlreadyOpenException ex = new DataStoreAlreadyOpenException("/data/foo", null);

        assertTrue(NonRetryableStartupFailure.isPresentIn(ex));
        assertSame(ex, DataStoreAlreadyOpenException.findIn(ex).orElseThrow());
    }

    @Test
    void ordinaryFailureIsRetryable() {
        RuntimeException ordinary = new RuntimeException("disk hiccup", new IOException("io"));

        assertFalse(NonRetryableStartupFailure.isPresentIn(ordinary));
        assertTrue(DataStoreAlreadyOpenException.findIn(ordinary).isEmpty());
        assertTrue(NonRetryableStartupFailure.findIn(ordinary).isEmpty());
    }

    @Test
    void nullSafe() {
        assertFalse(NonRetryableStartupFailure.isPresentIn(null));
        Optional<NonRetryableStartupFailure> marker = NonRetryableStartupFailure.findIn(null);
        Optional<DataStoreAlreadyOpenException> typed = DataStoreAlreadyOpenException.findIn(null);

        assertTrue(marker.isEmpty());
        assertTrue(typed.isEmpty());
    }
}
