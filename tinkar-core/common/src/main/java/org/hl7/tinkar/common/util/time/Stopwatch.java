package org.hl7.tinkar.common.util.time;

import java.time.Duration;
import java.time.Instant;

public class Stopwatch {
    private Instant startTime;
    private Instant endTime;

    public Stopwatch() {
        this.startTime = Instant.now();
    }

    public void end() {
        this.endTime = Instant.now();
    }

    public void reset() {
        this.startTime = Instant.now();
    }

    public void stop() {
        this.endTime = Instant.now();
    }

    public Duration duration() {
        Instant endForDuration = endTime;
        if (endForDuration == null) {
            endForDuration = Instant.now();
        }
        return Duration.between(startTime, endForDuration);
    }

    public String durationString () {
        return DurationUtil.format(duration());
    }
    public Duration averageDurationForElement (int count) {
        Instant endForDuration = endTime;
        if (endForDuration == null) {
            endForDuration = Instant.now();
        }
        Duration entireDuration = Duration.between(this.startTime, endForDuration);
        Duration average = entireDuration.dividedBy(count);

        return average;
    }

    public String averageDurationForElementString (int count) {
        return DurationUtil.format(averageDurationForElement (count));
    }
}
