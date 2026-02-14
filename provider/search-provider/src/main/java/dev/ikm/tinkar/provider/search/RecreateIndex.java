
package dev.ikm.tinkar.provider.search;

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceLifecycleManager;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityRecordFactory;
import dev.ikm.tinkar.entity.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * The {@code RecreateIndex} class is responsible for rebuilding a Lucene index.
 * This process includes iterating over all entities, indexing them using a provided
 * {@link Indexer}, and updating progress tracking mechanisms.
 * <p>
 * The class extends {@code TrackingCallable<Void>}, providing mechanisms for tracking
 * and reporting progress, as well as handling cancellation and lifecycle events
 * of the index recreation task.
 * <p>
 * Constructor:
 * <p> - Initializes the class with a specified {@link Indexer}.
 * <p> - Sets relevant properties for tracking task status and progress.
 * <p> - Logs the start of the index recreation process.
 * <p>
 * Methods:
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

    // Batch size for commits - tune based on available memory
    private static final int COMMIT_BATCH_SIZE =
            Integer.getInteger("lucene.index.commit.batch.size", 50_000);

    public RecreateIndex(Indexer indexer) {
        super(false, true);
        this.indexer = indexer;
        this.updateTitle("Recreate Lucene Index");
        LOG.info("Recreate Lucene Index started (batch size: {})", COMMIT_BATCH_SIZE);
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
        updateTitle("Indexing Semantics");
        updateMessage("Initializing...");
        updateProgress(-1,1);

        EntityService.get().beginLoadPhase();
        this.indexer.setBulkMode(true);
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

            // Use atomic counter for batching across parallel threads
            AtomicInteger batchCounter = new AtomicInteger(0);

            PrimitiveData.get().forEachParallel((bytes, nid) -> {
                // Check for cancellation periodically
                if (shouldStop()) {
                    return;
                }

                // Only process non-null entities
                if (bytes != null && bytes.length > 0) {
                    Entity<?> entity = EntityRecordFactory.make(bytes);
                    if (entity != null) {
                        this.indexer.index(entity);
                        indexedEntities.increment();

                        // Commit in batches
                        int count = batchCounter.incrementAndGet();
                        if (count % COMMIT_BATCH_SIZE == 0) {
                            if (shouldStop()) {
                                return;
                            }

                            try {
                                synchronized (this.indexer) {
                                    this.indexer.commit();
                                    LOG.debug("Committed batch at {} entities", count);
                                }
                            } catch (IOException e) {
                                LOG.error("Error committing batch at count {}", count, e);
                            }
                        }
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
                String reason = getStopReason();
                LOG.info("Lucene index recreation cancelled before final commit: {}", reason);
                LOG.info("Processed {} of {} entities before cancellation",
                        indexedEntities.longValue(), totalCount);
                return null;
            }

            // Final commit
            this.indexer.commit();
            LOG.info("Final commit completed - indexed {} entities",
                    String.format("%,d", indexedEntities.longValue()));

        } finally {
            this.indexer.setBulkMode(false);
            EntityService.get().endLoadPhase();
        }

        if (shouldStop()) {
            String reason = getStopReason();
            LOG.info("Lucene index recreation stopped: {}", reason);
            this.updateTitle("Lucene Index Cancelled");
            this.updateMessage("Index creation was cancelled: " + reason);
        } else {
            LOG.info("Recreate Lucene Index completed in {}", this.durationString());
            this.updateTitle("Recreate Lucene Index Completed");
            this.updateMessage("Index time: " + this.durationString());
        }
        updateProgress(1,1);
        return null;
    }
}