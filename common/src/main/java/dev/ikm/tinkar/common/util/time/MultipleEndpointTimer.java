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
package dev.ikm.tinkar.common.util.time;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * Supports timing on concurrent threads with statistics for multiple endpoints.
 *
 * @param <T> an enum type whose constants identify the individual endpoints
 */
public class MultipleEndpointTimer<T extends Enum<T>> {
    final Class<T> endPointsEnumClass;
    final SumInfo[] sumInfoArray;
    final SumInfo globalInfo = new SumInfo();

    final T[] enums;

    /**
     * Creates a timer that collects statistics for each constant in the given enum class.
     *
     * @param endPointsEnumClass the enum class whose constants define the endpoints
     */
    public MultipleEndpointTimer(Class<T> endPointsEnumClass) {
        this.endPointsEnumClass = endPointsEnumClass;
        enums = endPointsEnumClass.getEnumConstants();
        sumInfoArray = new SumInfo[enums.length];
        for (T enumValue: enums) {
            sumInfoArray[enumValue.ordinal()] = new SumInfo();
        }
    }

    /**
     * Starts and returns a new {@link Stopwatch} that begins timing immediately.
     *
     * @return a new running stopwatch
     */
    public Stopwatch startNew() {
        return new Stopwatch();
    }
    /**
     * Returns a brief progress string showing global (all-endpoint) statistics.
     *
     * @return the progress summary
     */
    public String progress() {
        StringBuilder sb = new StringBuilder();
        sb.append("Processed ");
        appendSumInfo(sb, globalInfo);
        sb.append("\n");
        return sb.toString();
    }

    /**
     * Returns a detailed summary string with per-endpoint and overall statistics
     * (count, min, mean, and max durations).
     *
     * @return the full summary
     */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        for (T enumValue: enums) {
            sb.append(enumValue.toString()).append(": ");
            appendSumInfo(sb, sumInfoArray[enumValue.ordinal()]);
            sb.append("\n");
        }

        sb.append("Overall: ");
        appendSumInfo(sb, globalInfo);
        sb.append("\n");
        return sb.toString();
    }

    private void appendSumInfo(StringBuilder sb, SumInfo sumInfoForEndpoint) {
        Duration minDuration = Duration.ofNanos(sumInfoForEndpoint.min.get());
        Duration maxDuration = Duration.ofNanos(sumInfoForEndpoint.max.get());
        long count = sumInfoForEndpoint.count.sum();
        Duration averageDuration = Duration.ofNanos((long)(sumInfoForEndpoint.sum.sum() / count));
        sb.append(String.format("count: %,d", count));
        sb.append(" min: ").append(DurationUtil.format(minDuration));
        sb.append(" mean: ").append(DurationUtil.format(averageDuration));
        sb.append(" max: ").append(DurationUtil.format(maxDuration));
    }

    private record SumInfo(LongAdder count, DoubleAdder sum, AtomicLong min, AtomicLong max) {
        public SumInfo() {
            this(new LongAdder(), new DoubleAdder(), new AtomicLong(Long.MAX_VALUE), new AtomicLong(Long.MIN_VALUE));
        }
        public void accept(long nanoseconds) {
            count.increment();
            sum.add(nanoseconds);
            long currentMax = max.get();
            while (nanoseconds > currentMax) {
                max.compareAndSet(currentMax, nanoseconds);
                currentMax = max.get();
            }
            long currentMin = min.get();
            while (nanoseconds < currentMin) {
                min.compareAndSet(currentMin, nanoseconds);
                currentMin = min.get();
            }
        }
    }

    /**
     * A simple stopwatch that records the elapsed time between creation and
     * a call to one of the {@code end} methods.
     */
    public class Stopwatch {
        private final Instant start = Instant.now();
        private Instant end;

        /**
         * Stops the stopwatch and records the elapsed duration against the specified
         * endpoint as well as the global statistics.
         *
         * @param endPoint the endpoint to attribute this timing to
         * @return the elapsed duration
         */
        public Duration end(T endPoint) {
            this.end = Instant.now();
            SumInfo sumInfoForEndpoint = sumInfoArray[endPoint.ordinal()];
            Duration duration = Duration.between(start, end);
            long nanoseconds = duration.toNanos();
            sumInfoForEndpoint.accept(nanoseconds);
            globalInfo.accept(nanoseconds);
            return duration;
        }
        /**
         * Stops the stopwatch and records the elapsed duration against the global
         * statistics only (no specific endpoint).
         *
         * @return the elapsed duration
         */
        public Duration end() {
            this.end = Instant.now();
             Duration duration = Duration.between(start, end);
            globalInfo.accept(duration.toNanos());
            return duration;
        }
    }


}
