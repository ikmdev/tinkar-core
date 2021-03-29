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

    public String elapsedTime () {
        Instant endForDuration = endTime;
        if (endForDuration == null) {
            endForDuration = Instant.now();
        }
        return DurationUtil.format(Duration.between(startTime, endForDuration));
    }
    public String averageElapsedTimeForElement (int count) {
        Instant endForDuration = endTime;
        if (endForDuration == null) {
            endForDuration = Instant.now();
        }
        Duration entireDuration = Duration.between(this.startTime, endForDuration);
        Duration average = entireDuration.dividedBy(count);

        return DurationUtil.format(average);
    }
}
