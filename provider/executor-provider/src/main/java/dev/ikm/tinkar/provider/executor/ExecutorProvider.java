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
package dev.ikm.tinkar.provider.executor;

import dev.ikm.tinkar.common.alert.UncaughtExceptionAlertStreamer;
import dev.ikm.tinkar.common.service.ExecutorService;
import dev.ikm.tinkar.common.util.thread.NamedThreadFactory;
import dev.ikm.tinkar.common.util.thread.ThreadPoolExecutorFixed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generally available thread pools for doing background processing in an ISAAC application.
 * <p>
 * The {@link #forkJoinThreadPool()} that this provides is identical to the @{link {@link ForkJoinPool#commonPool()}
 * with the exception that it will bottom out at 6 processing threads, rather than 1, to help prevent
 * deadlock situations in common ISAAC usage patterns.  This has an unbounded queue depth, and LIFO behavior.
 * <p>
 * The {@link #blockingThreadPool()} that this provides is a standard thread pool with (up to) the same number of threads
 * as there are cores present on the computer - with a minimum of 6 threads.  This executor has no queue - internally
 * it uses a {@link SynchronousQueue} - so if no thread is available to accept the task being queued, it will block
 * submission of the task until a thread is available to accept the job.
 * <p>
 * The {@link #threadPool()} that this provides is a standard thread pool with (up to) the same number of threads
 * as there are cores present on the computer - with a minimum of 6 threads.  This executor has an unbounded queue
 * depth, and FIFO behavior.
 * <p>
 * The {@link #ioThreadPool()} that this provides is a standard thread pool with 6 threads.  This executor has an unbounded queue
 * depth, and FIFO behavior.  This executor is good for jobs that tend to block on disk IO, where you don't want many running in parallel.
 *
 * @author <a href="mailto:daniel.armbrust.list@gmail.com">Dan Armbrust</a>
 */

public class ExecutorProvider implements ExecutorService {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutorProvider.class);

    private static final UncaughtExceptionAlertStreamer exceptionAlertStreamer = new UncaughtExceptionAlertStreamer();

    private final AtomicBoolean started = new AtomicBoolean(false);

    /**
     * The fork join executor.
     */
    private ForkJoinPool forkJoinExecutor;

    /**
     * The blocking thread pool executor.
     */
    private ThreadPoolExecutor blockingThreadPoolExecutor;

    /**
     * The thread pool executor.
     */
    private ThreadPoolExecutor threadPoolExecutor;

    /**
     * The io thread pool executor.
     */
    private TinkarThreadPoolExecutor ioThreadPoolExecutor;

    /**
     * The scheduled executor.
     */
    private TinkarScheduledExecutor scheduledExecutor;

    /**
     * Start me.
     */
    protected void start() {
        if (!started.compareAndSet(false, true)) {
            return; // Already started
        }

        LOG.info("Starting the WorkExecutors thread pools");

        // The java default ForkJoinPool.commmonPool starts with only 1 thread, on 1 and 2 core systems, which can get us deadlocked pretty easily.
        final int procCount = Runtime.getRuntime()
                .availableProcessors();
        final int parallelism = ((procCount - 1) < 6 ? 6
                : procCount - 1);  // set between 6 and 1 less than proc count (not less than 6)

        this.forkJoinExecutor = new ForkJoinPool(parallelism);

        final int corePoolSize = 2;
        final int maximumPoolSize = parallelism;
        final int keepAliveTime = 60;
        final TimeUnit timeUnit = TimeUnit.SECONDS;

        // The blocking executor
        this.blockingThreadPoolExecutor = new ThreadPoolExecutorFixed(corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                timeUnit,
                new SynchronousQueue<>(),
                new NamedThreadFactory("Tinkar-B-work-thread", true));
        this.blockingThreadPoolExecutor.setRejectedExecutionHandler((runnable, executor) -> {
            try {
                executor.getQueue()
                        .offer(runnable, Long.MAX_VALUE, TimeUnit.HOURS);
            } catch (final Exception e) {
                throw new RejectedExecutionException("Interrupted while waiting to enqueue");
            }
        });

        // The non-blocking executor - set core threads equal to max - otherwise, it will never increase the thread count
        // with an unbounded queue.
        this.threadPoolExecutor = new ThreadPoolExecutorFixed(maximumPoolSize,
                maximumPoolSize,
                keepAliveTime,
                timeUnit,
                new LinkedBlockingQueue<>(),
                new NamedThreadFactory("Tinkar-Q-work-thread", true));
        this.threadPoolExecutor.allowCoreThreadTimeOut(true);

        // The IO non-blocking executor - set core threads equal to max - otherwise, it will never increase the thread count
        // with an unbounded queue.
        this.ioThreadPoolExecutor = new TinkarThreadPoolExecutor(6,
                6,
                keepAliveTime,
                timeUnit,
                new LinkedBlockingQueue<>(),
                new NamedThreadFactory("Tinkar-IO-work-thread", true));
        this.ioThreadPoolExecutor.allowCoreThreadTimeOut(true);

        // Execute this once, early on, in a background thread - as randomUUID uses secure random - and the initial
        // init of secure random can block on many systems that don't have enough entropy occuring.  The DB load process
        // should provide enough entropy to get it initialized, so it doesn't pause things later when someone requests a random UUID.
        threadPool().execute(() -> UUID.randomUUID());
        this.scheduledExecutor = new TinkarScheduledExecutor(1,
                new NamedThreadFactory("Tinkar-Scheduled-Thread", true));
        LOG.info("WorkExecutors thread pools ready");

    }

    /**
     * Stop me.
     */
    protected void stop() {
        if (!started.get()) {
            return; // Not started
        }

        LOG.info("Stopping WorkExecutors thread pools. ");

        try {
            if (this.forkJoinExecutor != null) {
                this.forkJoinExecutor.shutdown();
                if (this.forkJoinExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    LOG.info("forkJoinExecutor terminated successfully");
                }
            }

            if (this.blockingThreadPoolExecutor != null) {
                this.blockingThreadPoolExecutor.shutdown();
                if (this.blockingThreadPoolExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    LOG.info("blockingThreadPoolExecutor terminated successfully");
                }
            }

            if (this.threadPoolExecutor != null) {
                this.threadPoolExecutor.shutdown();
                if (this.threadPoolExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    LOG.info("threadPoolExecutor terminated successfully");
                }
            }

            if (this.ioThreadPoolExecutor != null) {
                this.ioThreadPoolExecutor.shutdown();
                if (this.ioThreadPoolExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    LOG.info("ioThreadPoolExecutor terminated successfully");
                }
            }

            if (this.scheduledExecutor != null) {
                this.scheduledExecutor.shutdown();
                if (this.scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    LOG.info("scheduledExecutor terminated successfully");
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            started.set(false);
        }
        LOG.info("Stopped WorkExecutors thread pools");
    }

    //~--- get methods ---------------------------------------------------------

    /**
     * Gets the fork join pool executor.
     *
     * @return the Tinkar common {@link ForkJoinPool} instance - (behavior described in the class docs)
     * This is backed by an unbounded queue - it won't block / reject submissions because of being full.
     */
    @Override
    public ForkJoinPool forkJoinThreadPool() {
        return this.forkJoinExecutor;
    }

    /**
     * Gets the potentially blocking executor.
     *
     * @return The Tinkar common {@link ThreadPoolExecutor} - (behavior described in the class docs).
     * This is a synchronous queue - if no thread is available to take a job, it will block until a thread
     * is available to accept the job.
     */
    public ThreadPoolExecutor blockingThreadPool() {
        return this.blockingThreadPoolExecutor;
    }

    /**
     * Gets the executor.
     *
     * @return The Tinkar common {@link ThreadPoolExecutor} - (behavior described in the class docs).
     * This is backed by an unbounded queue - it won't block / reject submissions because of being full.
     * This executor has processing threads linked to the number of CPUs available.  It is good for compute
     * intensive jobs.
     */
    @Override
    public ThreadPoolExecutor threadPool() {
        return this.threadPoolExecutor;
    }

    /**
     * Gets the IO executor.
     *
     * @return The Tinkar common IO {@link ThreadPoolExecutor} - (behavior described in the class docs).
     * This is backed by an unbounded queue - it won't block / reject submissions because of being full.
     * This executor differs from {@link #threadPool()} by having a much smaller number of threads - good for
     * jobs that tend to block on IO.
     */
    @Override
    public ThreadPoolExecutor ioThreadPool() {
        return this.ioThreadPoolExecutor;
    }

    /**
     * Gets the scheduled thread pool executor.
     *
     * @return the Tinkar common {@link ScheduledThreadPoolExecutor} instance - (behavior described in the class docs)
     * This pool only has a single thread - submitted jobs should be fast executing.
     */
    @Override
    public ScheduledExecutorService scheduled() {
        return this.scheduledExecutor;
    }
}
