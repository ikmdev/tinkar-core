package org.hl7.tinkar.common.service;

import org.hl7.tinkar.common.util.time.DurationUtil;
import org.hl7.tinkar.common.util.time.Stopwatch;

import java.time.Duration;
import java.util.concurrent.Callable;

public abstract class TrackingCallable<V> implements Callable<V> {
    Stopwatch stopwatch = new Stopwatch();
    TrackingListener listener;
    double workDone;
    double maxWork;
    String title;
    String message;
    V value;

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
            this.listener.updateProgress(this.workDone,  this.maxWork);
        } else {
            throw new IllegalStateException("Listener already set");
        }
    }

    public Duration estimateTimeRemaining() {
        return duration().multipliedBy((long) (maxWork/workDone));
    }

    public String estimateTimeRemainingString() {
        return DurationUtil.format(estimateTimeRemaining());
    }

    public Duration duration() {
        return stopwatch.duration();
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

    protected void updateValue(V result) {
        if (listener != null) {
            listener.updateValue(result);
        }
    }

    protected void updateMessage(String message) {
        this.message = message;
        if (listener != null) {
            listener.updateMessage(message);
        }
    }

    protected void updateTitle(String title) {
        this.title = title;
        if (listener != null) {
            listener.updateTitle(title);
        }
    }

    protected void updateProgress(long workDone, long maxWork) {
        updateProgress((double)workDone, (double)maxWork);
    }

    protected void updateProgress(double workDone, double maxWork) {
        this.workDone = workDone;
        this.maxWork = maxWork;
        if (listener != null) {
            listener.updateProgress(workDone, maxWork);
        }
    }
}
