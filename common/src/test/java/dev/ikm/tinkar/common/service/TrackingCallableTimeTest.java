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
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The remaining-time estimate of a {@link TrackingCallable}: windowed over the last ten
 * seconds of progress, behind a gate that keeps it quiet until there is something honest to say.
 * Time is driven by a clock the test advances, so the samples are exact.
 */
class TrackingCallableTimeTest {

    /** A task that never runs; progress is reported to it by the test. */
    private static final class Reported extends TrackingCallable<Void> {
        long nanos;

        Reported() {
            nanoClock = () -> nanos;
        }

        @Override
        protected Void compute() {
            return null;
        }

        /** Advances the clock by whole seconds and reports the work done. */
        void at(int seconds, double done, double max) {
            nanos = seconds * 1_000_000_000L;
            updateProgress(done, max);
        }
    }

    @Test
    void nothingToSayBeforeAnyProgress() {
        Reported task = new Reported();
        assertEquals(Optional.empty(), task.timeRemaining());
        assertEquals(Duration.ofDays(365), task.estimateTimeRemaining(), "the older method still says unknown");
        assertEquals("", task.estimateTimeRemainingString());
    }

    @Test
    void nothingToSayUntilAFewSecondsAndAFewPercent() {
        Reported task = new Reported();
        task.at(0, 0, 1000);
        task.at(1, 10, 1000);
        assertEquals(Optional.empty(), task.timeRemaining(), "one percent, one second");
        task.at(2, 15, 1000);
        assertEquals(Optional.empty(), task.timeRemaining(), "under three seconds");
        task.at(4, 40, 1000);
        assertTrue(task.timeRemaining().isPresent(), "four percent, four seconds");
    }

    @Test
    void aSteadyPaceIsExtrapolated() {
        Reported task = new Reported();
        for (int second = 0; second <= 10; second++) {
            task.at(second, second * 50, 1000);  // five percent a second: half done at ten seconds
        }
        assertEquals(Duration.ofSeconds(10), task.timeRemaining().orElseThrow());
    }

    @Test
    void theRateIsTheRecentOneNotTheLifetimeOne() {
        Reported task = new Reported();
        // Five seconds at eight percent a second, then ten seconds at one percent a second.
        for (int second = 0; second <= 5; second++) {
            task.at(second, second * 80, 1000);
        }
        for (int second = 6; second <= 15; second++) {
            task.at(second, 400 + (second - 5) * 10, 1000);
        }
        // Half done at fifteen seconds: a lifetime rate would say fifteen seconds remain; the
        // pace of the last ten seconds says fifty.
        assertEquals(Duration.ofSeconds(50), task.timeRemaining().orElseThrow());
    }

    @Test
    void aNewMeasureOfTheWorkStartsOver() {
        Reported task = new Reported();
        for (int second = 0; second <= 10; second++) {
            task.at(second, second * 50, 1000);
        }
        task.at(11, 100, 5000);  // the work is now measured in another unit
        assertEquals(Optional.empty(), task.timeRemaining(), "one sample of the new measure is not a rate");
    }

    @Test
    void aTaskWhoseProgressIsNotTimeSaysNothing() {
        Reported task = new Reported();
        task.setRemainingTimeEstimable(false);
        for (int second = 0; second <= 10; second++) {
            task.at(second, second * 50, 1000);
        }
        assertEquals(Optional.empty(), task.timeRemaining());
        assertFalse(task.timeText().contains("remaining"));
        assertTrue(task.timeText().endsWith(" elapsed"));
    }

    @Test
    void theTextSaysElapsedAndAboutHowLongRemains() {
        Reported task = new Reported();
        for (int second = 0; second <= 10; second++) {
            task.at(second, second * 50, 1000);
        }
        String text = task.timeText();
        assertTrue(text.endsWith(" elapsed, about 10 seconds remaining"), text);
        assertEquals("About 10 seconds remaining.", task.estimateTimeRemainingString());
    }

    @Test
    void durationsAreApproximatedCoarsely() {
        assertEquals("a few seconds", DurationUtil.approximate(Duration.ofSeconds(7)));
        assertEquals("30 seconds", DurationUtil.approximate(Duration.ofSeconds(37)));
        assertEquals("1 minute", DurationUtil.approximate(Duration.ofSeconds(80)));
        assertEquals("4 minutes", DurationUtil.approximate(Duration.ofSeconds(250)));
        assertEquals("1 h 25 min", DurationUtil.approximate(Duration.ofMinutes(84)));
        assertEquals("2 h", DurationUtil.approximate(Duration.ofMinutes(121)));
        assertEquals("more than a day", DurationUtil.approximate(Duration.ofHours(30)));
    }
}
