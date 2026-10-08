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

import dev.ikm.tinkar.common.util.time.DurationUtil;
import dev.ikm.tinkar.common.util.time.Stopwatch;

import java.time.Duration;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.DoubleAdder;

public abstract class TrackingCallable<V> implements Callable<V> {
    private static final long DEFAULT_UI_UPDATE_TIMEOUT_SECONDS = 5;
    
    final boolean allowUserCancel;
    final boolean retainWhenComplete;
    Stopwatch stopwatch = new Stopwatch();
    TrackingListener listener;
    DoubleAdder workDone = new DoubleAdder();
    DoubleAdder maxWork = new DoubleAdder();
    double updateThreshold = 0.005;
    String title;
    String message;
    V value;
    boolean isCancelled = false;

    // ---- Time: elapsed, and a remaining-time estimate any surface can show ----
    /** How many progress samples the rate is taken over; a ring, the oldest overwritten. */
    private static final int SAMPLE_CAPACITY = 64;
    /** The rate is measured over the samples of the last ten seconds, so it follows a change of pace. */
    private static final long WINDOW_NANOS = 10_000_000_000L;
    /** No estimate before this much elapsed, or this fraction done: too little to go on. */
    private static final long MIN_ELAPSED_NANOS = 3_000_000_000L;
    private static final double MIN_FRACTION = 0.02;
    /** The estimate when nothing can be said, kept for callers of the older method. */
    private static final Duration UNKNOWN = Duration.ofDays(365);
    private final long[] sampleNanos = new long[SAMPLE_CAPACITY];
    private final double[] sampleWork = new double[SAMPLE_CAPACITY];
    private int sampleCount;
    private int sampleNext;
    private long firstSampleNanos;
    private volatile boolean remainingTimeEstimable = true;
    /** The clock the samples are stamped by; a test sets its own. */
    LongSupplier nanoClock = System::nanoTime;
    
    /**
     * Optional UI thread executor for blocking message updates.
     */
    private UiThreadExecutor uiThreadExecutor;

    public TrackingCallable() {
        this.allowUserCancel = true;
        this.retainWhenComplete = false;
    }

    public TrackingCallable(boolean allowUserCancel, boolean retainWhenComplete) {
        this.allowUserCancel = allowUserCancel;
        this.retainWhenComplete = retainWhenComplete;
    }

    public TrackingCallable(boolean allowUserCancel) {
        this.allowUserCancel = allowUserCancel;
        this.retainWhenComplete = false;
    }

    public boolean isCancelled() {
        return this.isCancelled;
    }

    public void cancel() {
        this.isCancelled = true;
    }

    public boolean allowUserCancel() {
        return allowUserCancel;
    }

    public boolean updateIntervalElapsed() {
        return stopwatch.updateIntervalElapsed();
    }

    public boolean retainWhenComplete() {
        return retainWhenComplete;
    }

    @Override
    public final V call() throws Exception {
        stopwatch.reset();
        clearSamples();
        try {
            V result = compute();
            stopwatch.stop();
            return result;
        } catch (Throwable th) {
            stopwatch.stop();
            if (th instanceof Exception ex) {
                throw ex;
            } else {
                throw new Exception(th);
            }
        }
    }

    protected abstract V compute() throws Exception;

    public void addListener(TrackingListener listener) {
        if (this.listener == null) {
            this.listener = listener;
            this.listener.updateValue(this.value);
            this.listener.updateMessage(this.message);
            this.listener.updateTitle(this.title);
            this.listener.updateProgress(this.workDone.sum(), this.maxWork.sum());
        } else {
            throw new IllegalStateException("Listener already set");
        }
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    /**
     * "About 4 minutes remaining." when the task can say ({@link #timeRemaining()}), else empty.
     */
    public String estimateTimeRemainingString() {
        return timeRemaining().map(remaining -> "About " + DurationUtil.approximate(remaining) + " remaining.").orElse("");
    }

    /**
     * The remaining time as the older callers took it: the estimate when there is one, otherwise
     * a year, which stood for "unknown". New callers use {@link #timeRemaining()}.
     */
    public Duration estimateTimeRemaining() {
        return timeRemaining().orElse(UNKNOWN);
    }

    /**
     * The time this task has run, from {@link #call()} to now, or to its end once it ended.
     */
    public Duration elapsed() {
        return stopwatch.duration();
    }

    public Duration duration() {
        return stopwatch.duration();
    }

    /**
     * Whether a remaining time may be estimated from this task's progress. On by default; a task
     * whose progress is not a measure of time, such as one counting unequal steps, turns it off,
     * and then shows elapsed time alone rather than a confident wrong number.
     */
    public void setRemainingTimeEstimable(boolean estimable) {
        this.remainingTimeEstimable = estimable;
    }

    public boolean remainingTimeEstimable() {
        return remainingTimeEstimable;
    }

    /**
     * The time remaining, when the task can say: it is determinate, estimation is on, a few
     * seconds have passed and a few percent are done, and its rate over the last ten seconds of
     * progress is positive. The rate is taken over that window rather than the task's whole life,
     * so a task that changes pace, an import whose second pass costs more per byte than its
     * first, say, is estimated at the pace it is going, not the pace it started at. Empty
     * otherwise: a surface then shows elapsed time alone.
     *
     * @return the remaining time, or empty when there is nothing honest to say
     */
    public Optional<Duration> timeRemaining() {
        if (!remainingTimeEstimable) {
            return Optional.empty();
        }
        double max = maxWork.sum();
        double done = workDone.sum();
        if (max <= 0 || done <= 0 || done >= max || done / max < MIN_FRACTION) {
            return Optional.empty();
        }
        long now = nanoClock.getAsLong();
        long latestNanos;
        double latestWork;
        long oldestNanos;
        double oldestWork;
        synchronized (sampleNanos) {
            if (sampleCount < 2 || now - firstSampleNanos < MIN_ELAPSED_NANOS) {
                return Optional.empty();
            }
            int latest = Math.floorMod(sampleNext - 1, SAMPLE_CAPACITY);
            latestNanos = sampleNanos[latest];
            latestWork = sampleWork[latest];
            // The oldest sample within the window; at least one sample back, whatever its age.
            int oldest = latest;
            for (int back = 1; back < sampleCount; back++) {
                int candidate = Math.floorMod(latest - back, SAMPLE_CAPACITY);
                if (back > 1 && latestNanos - sampleNanos[candidate] > WINDOW_NANOS) {
                    break;
                }
                oldest = candidate;
            }
            oldestNanos = sampleNanos[oldest];
            oldestWork = sampleWork[oldest];
        }
        double seconds = (latestNanos - oldestNanos) / 1e9;
        double workPerSecond = seconds > 0 ? (latestWork - oldestWork) / seconds : 0;
        if (workPerSecond <= 0) {
            return Optional.empty();
        }
        long remaining = Math.round((max - latestWork) / workPerSecond);
        return Optional.of(Duration.ofSeconds(Math.max(0, remaining)));
    }

    /**
     * The time in words for a progress row: "12 s elapsed", with ", about 4 minutes remaining"
     * when the task can say.
     */
    public String timeText() {
        String text = DurationUtil.format(elapsed()) + " elapsed";
        return timeRemaining().map(remaining -> text + ", about " + DurationUtil.approximate(remaining) + " remaining").orElse(text);
    }

    private void recordSample(double done) {
        long now = nanoClock.getAsLong();
        synchronized (sampleNanos) {
            if (sampleCount == 0) {
                firstSampleNanos = now;
            }
            sampleNanos[sampleNext] = now;
            sampleWork[sampleNext] = done;
            sampleNext = (sampleNext + 1) % SAMPLE_CAPACITY;
            if (sampleCount < SAMPLE_CAPACITY) {
                sampleCount++;
            }
        }
    }

    private void clearSamples() {
        synchronized (sampleNanos) {
            sampleCount = 0;
            sampleNext = 0;
            firstSampleNanos = 0;
        }
    }

    public void completedUnitOfWork() {
        workDone.add(1);
        if (listener != null && workDone.sum() % 128 == 0) {
            listener.updateProgress(workDone.sum(), maxWork.sum());
        }
    }

    public String durationString() {
        return stopwatch.durationString();
    }

    public Duration averageDurationForElement(int count) {
        return stopwatch.averageDurationForElement(count);
    }

    public String averageDurationForElementString(int count) {
        return stopwatch.averageDurationForElementString(count);
    }

    public void updateValue(V result) {
        if (listener != null) {
            listener.updateValue(result);
        }
    }

    /**
     * Sets the UI thread executor for this task.
     * This allows the task to perform blocking UI updates without depending on a specific UI framework.
     * 
     * @param executor the UI thread executor
     */
    public void setUiThreadExecutor(UiThreadExecutor executor) {
        this.uiThreadExecutor = executor;
    }

    public void updateMessage(String message) {
        if (message != null && this.message == null) {
            if (listener != null) {
                listener.updateMessage(message);
            }
        } else if (listener != null && !this.message.equals(message)) {
            listener.updateMessage(message);
        }
        this.message = message;
    }

    /**
     * Updates the message and blocks until the UI thread has processed the update.
     * This is useful for ensuring final status messages are displayed before a task completes,
     * especially for very fast tasks that might complete in microseconds.
     * <p>     * Requires a UiThreadExecutor to be set via setUiThreadExecutor().
     * If no executor is set, falls back to regular updateMessage().
     * 
     * @param message the message to set
     * @throws InterruptedException if interrupted while waiting for the UI update
     */
    public void updateMessageAndBlock(String message) throws InterruptedException {
        updateMessageAndBlock(message, DEFAULT_UI_UPDATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Updates the message and blocks until the UI thread has processed the update.
     * 
     * @param message the message to set
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @throws InterruptedException if interrupted while waiting for the UI update
     */
    public void updateMessageAndBlock(String message, long timeout, TimeUnit unit) throws InterruptedException {
        if (uiThreadExecutor == null || listener == null) {
            // No UI executor set, just update directly
            updateMessage(message);
            return;
        }

        // Use the UI thread executor to run and wait
        CountDownLatch latch = new CountDownLatch(1);
        uiThreadExecutor.executeAndSignal(() -> updateMessage(message), latch);

        if (!latch.await(timeout, unit)) {
            // Timeout occurred - update internal state anyway
            this.message = message;
        }
    }

    public void updateTitle(String title) {
        this.title = title;
        if (listener != null) {
            listener.updateTitle(title);
        }
    }

    public void addToTotalWork(long amountToAdd) {
        this.maxWork.add(amountToAdd);
        updateProgress(workDone.sum(), this.maxWork.sum());
    }

    public void updateProgress(double workDone, double maxWork) {
        boolean update = false;

        if (this.maxWork.sum() != maxWork) {
            // A new measure of the work: the samples of the old one say nothing about it.
            clearSamples();
            this.maxWork.reset();
            this.maxWork.add(maxWork);
            this.workDone.reset();
            this.workDone.add(workDone);
            update = true;
        } else {
            double difference = workDone - this.workDone.sum();
            double percentDifference = difference / maxWork;
            if (percentDifference > updateThreshold) {
                update = true;
                this.workDone.reset();
                this.workDone.add(workDone);
            }
        }

        if (update && maxWork > 0) {
            recordSample(workDone);
        }
        if (listener != null && update) {
            listener.updateProgress(workDone, maxWork);
        }
    }

    public void updateProgress(long workDone, long maxWork) {
        updateProgress((double) workDone, (double) maxWork);
    }

    /**
     * Interface for executing runnables on a UI thread.
     * Implementations should execute the runnable on the appropriate UI thread
     * and signal the latch when complete.
     */
    @FunctionalInterface
    public interface UiThreadExecutor {
        /**
         * Executes the given runnable on the UI thread and counts down the latch when complete.
         * 
         * @param runnable the code to execute
         * @param completionSignal the latch to count down after execution
         */
        void executeAndSignal(Runnable runnable, CountDownLatch completionSignal);
    }
}
