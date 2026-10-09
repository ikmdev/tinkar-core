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

import dev.ikm.tinkar.common.id.PublicId;

import java.io.File;
import java.util.List;

/**
 * Imports and exports changesets on a remote datastore, for a client whose own store is only a
 * local view of it — Komet connected over gRPC.
 *
 * <p>Such a client cannot import into its local store: the import would be lost when it closes,
 * and nobody else would see it. Nor can it export from it: the local store holds only what the
 * client happened to load. Both have to happen where the data lives.
 *
 * <p>Found the way {@link RemoteReasonerService} is, through
 * {@code ServiceLifecycleManager.get().getRunningService(RemoteChangesetService.class)}, so the
 * client does not depend on the implementation.
 */
public interface RemoteChangesetService {

    /** Notified as a remote job advances. */
    @FunctionalInterface
    interface ProgressListener {
        /**
         * @param done    work done so far; negative while the total is not known
         * @param total   total work; 0 or 1 while it is not known
         * @param message what the job is doing, or why it is waiting
         */
        void onProgress(long done, long total, String message);
    }

    /**
     * Uploads {@code changeset} and imports it on the remote datastore, returning once the import
     * has finished.
     *
     * <p>A remote import cannot be cancelled — stopped part way it would leave some entities
     * written. Cancelling {@code tracker} only stops waiting for it; the import carries on.
     *
     * @param tracker cancelling it stops waiting; may be null
     * @return the counts of what was imported
     * @throws java.util.concurrent.CancellationException if {@code tracker} was cancelled first
     * @throws IllegalStateException if the remote import failed
     */
    EntityCountSummary importChangeset(File changeset, ProgressListener listener, TrackingCallable<?> tracker);

    /**
     * Exports the entities changed between two times — a "change set" — from the remote datastore
     * into {@code target}.
     *
     * @param tracker cancelling it cancels the remote export, which leaves no file; may be null
     * @return the counts of what was exported
     * @throws java.util.concurrent.CancellationException if the export was cancelled
     * @throws IllegalStateException if the remote export failed
     */
    EntityCountSummary exportChangeSet(File target, long fromEpochMillis, long toEpochMillis,
                                       ProgressListener listener, TrackingCallable<?> tracker);

    /**
     * Exports the members of {@code membershipTags} from the remote datastore into {@code target}.
     *
     * @see #exportChangeSet
     */
    EntityCountSummary exportMembership(File target, List<PublicId> membershipTags,
                                        ProgressListener listener, TrackingCallable<?> tracker);
}
