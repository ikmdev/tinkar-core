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

import java.util.List;
import java.util.UUID;

/**
 * Runs the reasoner somewhere other than this JVM.
 *
 * <p>Provided by a data provider whose store is remote. The local
 * {@code ReasonerService} SPI cannot serve that case: it is a stateful pipeline
 * (extract, load, compute, write) over a local entity store, and a client backed by a remote
 * store has no such store to run it against.
 *
 * <p>Discovered the same way as {@link RemoteConceptSearchService}:
 * <pre>{@code
 * ServiceLifecycleManager.get().getRunningService(RemoteReasonerService.class)
 * }</pre>
 * Absent when the datastore is local, which is the signal to run the reasoner in-process
 * instead.
 *
 * <p>Concepts are returned as public IDs rather than nids: nids are assigned per store, so a
 * remote store's are meaningless here. The caller resolves them against its own store.
 */
public interface RemoteReasonerService {

    /** Notified as each phase of the remote pipeline completes. */
    @FunctionalInterface
    interface PhaseListener {
        /**
         * @param step       1-based phase that just completed
         * @param totalSteps phases in the pipeline
         * @param message    what the phase did
         */
        void onPhase(int step, int totalSteps, String message);
    }

    /**
     * Outcome of a remote classification.
     *
     * <p>{@code classifiedConceptCount} is a count rather than a list because the set is large
     * — hundreds of thousands of concepts on a real dataset — and consumers only display its
     * size. The remaining sets are the ones a results view actually enumerates.
     *
     * @param classifiedConceptCount        how many concepts were classified
     * @param conceptsWithInferredChanges   concepts whose inferred axioms changed
     * @param conceptsWithNavigationChanges concepts whose navigation changed
     * @param orphans                       concepts left without a parent
     * @param equivalentSets                equivalence classes, each a list of concepts
     * @param commitTime                    when the remote store committed the inferred
     *                                      results; advance the view to this to see them
     * @param stampCoordinateText           remote coordinate, pre-rendered for display
     * @param logicCoordinateText           remote coordinate, pre-rendered for display
     * @param editCoordinateText            remote coordinate, pre-rendered for display
     * @param durationMs                    how long the remote pipeline took
     */
    record RemoteReasonerOutcome(
            int classifiedConceptCount,
            List<List<UUID>> conceptsWithInferredChanges,
            List<List<UUID>> conceptsWithNavigationChanges,
            List<List<UUID>> orphans,
            List<List<List<UUID>>> equivalentSets,
            long commitTime,
            String stampCoordinateText,
            String logicCoordinateText,
            String editCoordinateText,
            long durationMs) {
    }

    /**
     * Runs the full reasoner pipeline on the remote store and returns once it has finished.
     *
     * <p>Blocking, and slow — call it off the UI thread. Progress arrives through
     * {@code listener} as each phase completes.
     *
     * @param listener notified per phase; may be {@code null}
     * @return the classification outcome
     * @throws IllegalStateException if the remote service is unreachable or reported a failure
     */
    RemoteReasonerOutcome runFullReasoner(PhaseListener listener);
}
