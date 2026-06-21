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

import java.util.Optional;

/**
 * Marker interface for startup failures that cannot be resolved by retrying.
 * <p>The {@link ServiceLifecycleManager} normally resets itself to
 * {@link ServiceLifecycleManager.State#DISCOVERED} after a service fails to
 * start, on the assumption that the failure is transient and a later attempt
 * may succeed. Some failures, however, are terminal — for example, a data
 * store that is already open in another process ({@link DataStoreAlreadyOpenException}).
 * Retrying such a failure only produces noise (background waiters polling for
 * services that will never appear) and hides the real cause from the user.
 * <p>An exception that implements this interface tells the lifecycle manager to
 * record a terminal {@link ServiceLifecycleManager.State#FAILED} state instead
 * of resetting to {@code DISCOVERED}, so callers can detect the abort and exit
 * gracefully rather than spin.
 * <p>This is a compiler-visible contract: detection is by type, not by parsing
 * exception messages.
 *
 * @see DataStoreAlreadyOpenException
 * @see ServiceLifecycleManager
 */
public interface NonRetryableStartupFailure {

    /**
     * Returns {@code true} if the given throwable, or any throwable in its
     * cause chain, is a {@code NonRetryableStartupFailure}.
     * <p>Service startup failures are typically wrapped several times as they
     * propagate (provider controller → lifecycle manager → caller), so callers
     * must inspect the whole chain rather than just the outermost exception.
     *
     * @param throwable the throwable to inspect; may be {@code null}
     * @return {@code true} if a non-retryable failure is present in the chain
     */
    static boolean isPresentIn(Throwable throwable) {
        return findIn(throwable).isPresent();
    }

    /**
     * Finds the first {@code NonRetryableStartupFailure} in the cause chain of
     * the given throwable.
     *
     * @param throwable the throwable to inspect; may be {@code null}
     * @return the first non-retryable failure found, walking from {@code throwable}
     *         through its causes; empty if none is present
     */
    static Optional<NonRetryableStartupFailure> findIn(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof NonRetryableStartupFailure failure) {
                return Optional.of(failure);
            }
        }
        return Optional.empty();
    }
}
