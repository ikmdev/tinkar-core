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

    public String estimateTimeRemainingString() {
        return "About " + DurationUtil.format(estimateTimeRemaining()) + " remaining.";
    }

    public Duration estimateTimeRemaining() {
        if (maxWork.sum() == 0) {
            return Duration.ofDays(365);
        }
        double percentDone = workDone.sum() / maxWork.sum();
        if (percentDone < 0.00001) {
            return Duration.ofDays(365);
        }
        //(TimeTaken / linesProcessed) * linesLeft = timeLeft
        double secondsDuration = duration().getSeconds();
        double secondsRemaining = secondsDuration / workDone.sum() * (maxWork.sum() - workDone.sum());
        return Duration.ofSeconds((long) secondsRemaining);
    }

    public Duration duration() {
        return stopwatch.duration();
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
     * <p>
     * Requires a UiThreadExecutor to be set via setUiThreadExecutor().
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
