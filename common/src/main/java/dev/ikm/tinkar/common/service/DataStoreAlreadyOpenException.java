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
 * Thrown when a data store cannot be opened because it is already open in
 * another process (or another window of this process).
 * <p>The canonical trigger is the search provider failing to obtain the Lucene
 * write lock ({@code LockObtainFailedException: Lock held by another program}),
 * which is the only component that takes an OS-level exclusive lock on the data
 * store. This condition is <b>terminal</b>: no amount of retrying will succeed
 * while the other holder is alive. The exception therefore implements
 * {@link NonRetryableStartupFailure} so the {@link ServiceLifecycleManager}
 * records a terminal failure instead of resetting for a retry, and the
 * application can present a clear message and exit gracefully.
 *
 * @see NonRetryableStartupFailure
 * @see ServiceLifecycleManager
 */
public class DataStoreAlreadyOpenException extends RuntimeException
        implements NonRetryableStartupFailure {

    private final String dataStorePath;

    /**
     * Constructs a {@code DataStoreAlreadyOpenException} for the given data
     * store location.
     *
     * @param dataStorePath the file-system path of the data store that could
     *                      not be opened because it is already in use
     * @param cause the underlying failure (typically a Lucene
     *              {@code LockObtainFailedException}); may be {@code null}
     */
    public DataStoreAlreadyOpenException(String dataStorePath, Throwable cause) {
        super("Data store is already open in another process: " + dataStorePath, cause);
        this.dataStorePath = dataStorePath;
    }

    /**
     * Returns the file-system path of the data store that is already open.
     *
     * @return the data store path; never {@code null}
     */
    public String dataStorePath() {
        return dataStorePath;
    }

    /**
     * Finds the first {@code DataStoreAlreadyOpenException} in the cause chain
     * of the given throwable.
     * <p>Startup failures are wrapped repeatedly as they propagate, so callers
     * that want to react specifically to "data store already open" must inspect
     * the whole chain.
     *
     * @param throwable the throwable to inspect; may be {@code null}
     * @return the first matching exception in the chain, or empty if none
     */
    public static Optional<DataStoreAlreadyOpenException> findIn(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof DataStoreAlreadyOpenException alreadyOpen) {
                return Optional.of(alreadyOpen);
            }
        }
        return Optional.empty();
    }
}
