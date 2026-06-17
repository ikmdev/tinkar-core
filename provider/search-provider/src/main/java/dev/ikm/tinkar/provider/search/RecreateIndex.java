
package dev.ikm.tinkar.provider.search;

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceLifecycleManager;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityRecordFactory;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.LongAdder;

/**
 * The {@code RecreateIndex} class is responsible for rebuilding a Lucene index.
 * This process includes iterating over all entities, indexing them using a provided
 * {@link Indexer}, and updating progress tracking mechanisms.
 * <p>The class extends {@code TrackingCallable<Void>}, providing mechanisms for tracking
 * and reporting progress, as well as handling cancellation and lifecycle events
 * of the index recreation task.
 * <p>Constructor:
 * <p> - Initializes the class with a specified {@link Indexer}.
 * <p> - Sets relevant properties for tracking task status and progress.
 * <p> - Logs the start of the index recreation process.
 * <p>Methods:
 * <p> - {@link #compute()}:
 * <p>   Executes the index rebuilding task, including:
 * <p>   - Initializing load phases through {@link EntityService}.
 * <p>   - Counting and indexing entities in a parallelized manner.
 * <p>   - Committing the changes to the index.
 * <p>   - Building the "Type Ahead" search suggester.
 * <p>   - Logging and updating task completion status.
 */
public class RecreateIndex extends TrackingCallable<Void> {
    private static final Logger LOG = LoggerFactory.getLogger(RecreateIndex.class);
    private final Indexer indexer;
    private final RecreateReason reason;

    /**
     * Construct a recreate task with a reason that drives the user-visible
     * title and initial message. Use this constructor from
     * {@link SearchProvider} so the progress dialog tells the right story
     * (post-upgrade rebuild vs. first-time build vs. user-requested, etc.).
     *
     * @param indexer the {@link Indexer} owning the writer to rebuild against
     * @param reason  why this recreate run was triggered; never {@code null}
     */
    public RecreateIndex(Indexer indexer, RecreateReason reason) {
        super(false, true);
        this.indexer = indexer;
        this.reason = reason;
        this.updateTitle(reason.title());
        this.updateMessage(reason.description());
        LOG.info("Recreate Lucene Index started — reason: {}", reason.name());
    }

    /**
     * Back-compat constructor — defaults the reason to
     * {@link RecreateReason#USER_REQUESTED}. Prefer the two-arg form so the
     * dialog can explain why the rebuild is running.
     *
     * @param indexer the {@link Indexer} owning the writer to rebuild against
     */
    public RecreateIndex(Indexer indexer) {
        this(indexer, RecreateReason.USER_REQUESTED);
    }

    /**
     * Check if this task should be cancelled due to:
     * <ul>
     *   <li>Explicit task cancellation ({@code cancel()} called)</li>
     *   <li>Thread interruption (executor shutdown)</li>
     *   <li>Lifecycle manager shutdown (application exit)</li>
     * </ul>
     *
     * @return true if the task should stop, false otherwise
     */
    private boolean shouldStop() {
        // Check explicit cancellation
        if (isCancelled()) {
            return true;
        }

        // Check thread interruption (executor shutdown)
        if (Thread.currentThread().isInterrupted()) {
            return true;
        }

        // Check lifecycle manager state (application shutdown)
        ServiceLifecycleManager.State state = ServiceLifecycleManager.get().getState();
        if (state == ServiceLifecycleManager.State.SHUTTING_DOWN ||
                state == ServiceLifecycleManager.State.SHUTDOWN) {
            return true;
        }

        return false;
    }

    /**
     * Returns a human-readable description of why the task was stopped.
     * Call this AFTER shouldStop() returns true.
     */
    private String getStopReason() {
        if (isCancelled()) {
            return "User cancellation";
        }
        if (Thread.currentThread().isInterrupted()) {
            return "Thread interruption";
        }
        ServiceLifecycleManager.State state = ServiceLifecycleManager.get().getState();
        if (state == ServiceLifecycleManager.State.SHUTTING_DOWN ||
                state == ServiceLifecycleManager.State.SHUTDOWN) {
            return "Application shutdown";
        }
        return "Unknown reason";
    }
    @Override
    protected Void compute() throws Exception {
        // Title was set in the constructor from the reason; keep it so the
        // dialog displays the right context throughout the run.
        updateMessage(reason.description());
        updateProgress(-1,1);

        EntityService.get().beginLoadPhase();
        try {
            LongAdder totalEntities = new LongAdder();
            LongAdder processedEntities = new LongAdder();
            LongAdder indexedEntities = new LongAdder(); // NEW: count actual entities

            // Check for cancellation before counting
            if (shouldStop()) {
                LOG.info("Lucene index recreation cancelled during initialization: {}", getStopReason());
                return null;
            }

            // Count ACTUAL entities (non-null)
            PrimitiveData.get().forEachParallel((bytes, nid) -> {
                if (bytes != null && bytes.length > 0) {
                    totalEntities.increment();
                }
            });

            long totalCount = totalEntities.longValue();
            LOG.info("Total entities to index: {}", String.format("%,d", totalCount));
            updateMessage("Generating Lucene Indexes...");
            updateProgress(0, totalCount + 1);

            // Wipe the existing index before rebuilding. Without this, every call
            // to recreateIndex() appends a fresh full copy of every entity on top
            // of whatever was already in the index — Indexer.index() does not
            // delete prior versions (see Indexer#deleteDocumentIfExists, deliberately
            // disabled for the append-only chronology design). Repeated invocations
            // — notably from LoadEntitiesFromProtobufFile.commitSearchIndexIfAvailable()
            // after every import / sync / pull — therefore produced geometric
            // duplicate-document growth. The recreate path is now honest: the index
            // is genuinely emptied here before the parallel walk repopulates it.
            //
            // The startup callers in SearchProvider (codec-mismatch, missing index,
            // empty index) only invoke recreateIndex() against an already-empty
            // index, so the deleteAll() is a no-op for them.
            LOG.info("Wiping existing Lucene index before recreation");
            synchronized (this.indexer) {
                Indexer.indexWriter().deleteAll();
                this.indexer.commit();
            }

            // Track docs added so the end-of-run log carries useful diagnostics.
            // No periodic commits during the walk — Lucene's RAM buffer auto-flushes
            // to disk segments as it fills (default 256 MB; see
            // Indexer.RAM_BUFFER_SIZE_MB), and TieredMergePolicy compacts segments
            // in the background. A single commit at the end persists everything.
            // Forcing periodic commits during the walk caused a "commit storm"
            // where the modulo gate tripped on stretches of non-text-bearing
            // semantics that had nothing to flush — hundreds of empty commits
            // in tight succession.
            LongAdder docsAdded = new LongAdder();

            PrimitiveData.get().forEachParallel((bytes, nid) -> {
                // Check for cancellation periodically
                if (shouldStop()) {
                    return;
                }

                // Only process non-null entities. Lucene indexing applies to
                // semantics only — concept/pattern/stamp content reaches the index
                // through their description semantics.
                if (bytes != null && bytes.length > 0) {
                    Entity<?> entity = EntityRecordFactory.make(bytes);
                    if (entity instanceof SemanticEntity<?> semantic) {
                        // indexFresh skips the per-call delete-by-NID. The
                        // writer was deleteAll()'d at the start of this run
                        // (see above), so the delete would resolve to a
                        // no-match query against an initially-empty writer
                        // and accumulate tens of millions of buffered
                        // PointRangeQuery entries that turn flush-time work
                        // O(n²). Live writes through SearchProvider.index
                        // still go through index(), preserving idempotent
                        // delete-then-add for evolving entities.
                        int added = this.indexer.indexFresh(semantic);
                        docsAdded.add(added);
                        indexedEntities.increment();
                    }
                }

                // Increment processed for ACTUAL entities only
                long processed = indexedEntities.longValue();
                if (updateIntervalElapsed() && totalCount > 0) {
                    updateProgress(processed, totalCount);
                    updateMessage(String.format("Indexed %,d / %,d entities (%d%%)",
                            processed,
                            totalCount,
                            (int)(100.0 * processed / totalCount)));
                }
            });

            // Check before final commit
            if (shouldStop()) {
                String stopReason = getStopReason();
                LOG.info("Lucene index recreation cancelled before final commit: {}", stopReason);
                LOG.info("Processed {} of {} entities ({} docs added) before cancellation",
                        indexedEntities.longValue(), totalCount, docsAdded.longValue());
                return null;
            }

            // Final commit — guard with hasUncommittedChanges() so a recreate
            // that produced zero indexable content (extreme edge case: no
            // text-bearing semantics in the entity store) doesn't pay for an
            // empty commit.
            if (Indexer.indexWriter().hasUncommittedChanges()) {
                this.indexer.commit();
                LOG.info("Final commit completed — indexed {} entities ({} docs)",
                        String.format("%,d", indexedEntities.longValue()),
                        String.format("%,d", docsAdded.longValue()));
            } else {
                LOG.info("Nothing to commit at end — {} entities walked produced no indexable docs",
                        String.format("%,d", indexedEntities.longValue()));
            }

        } finally {
            EntityService.get().endLoadPhase();
        }

        if (shouldStop()) {
            String stopReason = getStopReason();
            LOG.info("Lucene index recreation stopped: {}", stopReason);
            this.updateTitle(reason.title() + " — Cancelled");
            this.updateMessage("Index creation was cancelled: " + stopReason);
        } else {
            LOG.info("Recreate Lucene Index completed in {}", this.durationString());
            this.updateTitle(reason.title() + " — Completed");
            this.updateMessage("Index time: " + this.durationString());
        }
        updateProgress(1,1);
        return null;
    }
}