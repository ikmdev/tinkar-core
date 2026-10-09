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

import java.util.concurrent.TimeUnit;

/**
 *
 * 
 */
public class DurationUtil {
    /**
     * Seconds per minute.
     */
    static final int SECONDS_PER_MINUTE = 60;

    /**
     * Minutes per hour.
     */
    static final int MINUTES_PER_HOUR = 60;

    /**
     * Seconds per hour.
     */
    static final int SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR;

    /**
     * A duration in words coarse enough not to twitch as an estimate is refined: "a few seconds",
     * "30 seconds" (to ten seconds), "4 minutes" (to the minute), "1 h 25 min" (to five minutes),
     * "more than a day". For a remaining-time estimate; the caller adds "about".
     *
     * @param d the duration
     * @return the duration in words
     */
    public static String approximate(java.time.Duration d) {
        long seconds = Math.max(0, d.getSeconds());
        if (seconds < 10) {
            return "a few seconds";
        }
        if (seconds < 60) {
            return (seconds / 10) * 10 + " seconds";
        }
        if (seconds < 3600) {
            long minutes = Math.max(1, Math.round(seconds / 60.0));
            return minutes == 1 ? "1 minute" : minutes + " minutes";
        }
        if (seconds < 86400) {
            long fiveMinutes = Math.round(seconds / 300.0) * 5;
            long hours = fiveMinutes / 60;
            long minutes = fiveMinutes % 60;
            return minutes == 0 ? hours + " h" : hours + " h " + minutes + " min";
        }
        return "more than a day";
    }

    /**
     * A duration as a stopwatch reads it, for the corner of a progress row: "22s", "12m 5s",
     * "1h 3m". Seconds are whole and are dropped past an hour; a clock reading ("12:05") was
     * taken for a time of day at a glance (IKE-Network/ike-issues#1271).
     *
     * @param d the duration
     * @return the stopwatch reading
     */
    public static String stopwatch(java.time.Duration d) {
        long seconds = Math.max(0, d.getSeconds());
        long hours = seconds / SECONDS_PER_HOUR;
        long minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE;
        long secs = seconds % SECONDS_PER_MINUTE;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + secs + "s";
        }
        return secs + "s";
    }

    /**
     * {@link #approximate} in the fewest characters, for a label that must not grow: "<10 s",
     * "30 s", "6 min", "1 h 25 min", ">1 day". The rounding is the hedge; no tilde or "about"
     * says it again.
     *
     * @param d the duration
     * @return the duration, short
     */
    public static String approximateShort(java.time.Duration d) {
        long seconds = Math.max(0, d.getSeconds());
        if (seconds < 10) {
            return "<10 s";
        }
        if (seconds < 60) {
            return (seconds / 10) * 10 + " s";
        }
        if (seconds < 3600) {
            return Math.max(1, Math.round(seconds / 60.0)) + " min";
        }
        if (seconds < 86400) {
            long fiveMinutes = Math.round(seconds / 300.0) * 5;
            long hours = fiveMinutes / 60;
            long minutes = fiveMinutes % 60;
            return minutes == 0 ? hours + " h" : hours + " h " + minutes + " min";
        }
        return ">1 day";
    }

    public static String format(java.time.Duration d) {
        final StringBuilder builder = new StringBuilder();
        final long          seconds = d.getSeconds();

        if (seconds > 0) {
            final long hours   = seconds / SECONDS_PER_HOUR;
            final int  minutes = (int) ((seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE);
            final int  secs    = (int) (seconds % SECONDS_PER_MINUTE);

            if (hours != 0) {
                builder.append(hours)
                        .append(" h ");
            }

            if (minutes != 0) {
                builder.append(minutes)
                        .append(" m ");
            }

            builder.append(secs)
                    .append(" s");
            return builder.toString();
        }

        final int  nanos = d.getNano();
        final long milis = TimeUnit.MILLISECONDS.convert(nanos, TimeUnit.NANOSECONDS);

        if (milis > 0) {
            return builder.append(milis)
                    .append(" ms")
                    .toString();
        }

        final long micro = TimeUnit.MICROSECONDS.convert(nanos, TimeUnit.NANOSECONDS);

        if (micro > 0) {
            return builder.append(micro)
                    .append(" μs")
                    .toString();
        }

        return builder.append(nanos)
                .append(" ns")
                .toString();
    }

    public static String msTo8601(long milliseconds) {
        StringBuilder builder = new StringBuilder();
        builder.append("P");

        boolean addT = true;

        if (milliseconds >= DateTimeUtil.MS_IN_YEAR) { // years
            builder.append(milliseconds/DateTimeUtil.MS_IN_YEAR);
            builder.append("Y");
            milliseconds = milliseconds % DateTimeUtil.MS_IN_YEAR;
        }
        if (milliseconds >= DateTimeUtil.MS_IN_MONTH) { // months
            builder.append(milliseconds/DateTimeUtil.MS_IN_MONTH);
            builder.append("M");
            milliseconds = milliseconds % DateTimeUtil.MS_IN_MONTH;
        }
        if (milliseconds >= DateTimeUtil.MS_IN_DAY) { // days
            builder.append(milliseconds/DateTimeUtil.MS_IN_DAY);
            builder.append("D");
            milliseconds = milliseconds % DateTimeUtil.MS_IN_DAY;
        }
        if (milliseconds >= DateTimeUtil.MS_IN_HOUR) { // hours
            addT = false;
            builder.append("T");
            builder.append(milliseconds/DateTimeUtil.MS_IN_HOUR);
            builder.append("H");
            milliseconds = milliseconds % DateTimeUtil.MS_IN_HOUR;
        }
        if (milliseconds >= DateTimeUtil.MS_IN_MINUTE) { // minutes
            if (addT) {
                builder.append("T");
                addT = false;
            }
            builder.append(milliseconds/DateTimeUtil.MS_IN_MINUTE);
            builder.append("M");
            milliseconds = milliseconds % DateTimeUtil.MS_IN_MINUTE;
        }
        if (milliseconds >= DateTimeUtil.MS_IN_SEC) { // seconds
            long microseconds = milliseconds % DateTimeUtil.MS_IN_SEC;
            if (addT) {
                builder.append("T");
                addT = false;
            }
            builder.append(milliseconds/DateTimeUtil.MS_IN_SEC);
            if (microseconds >= 100) {
                builder.append(".").append(microseconds);
            } else if (microseconds >= 10) {
                builder.append(".0").append(microseconds);
            } else if (microseconds > 0) {
                builder.append("0.00").append(microseconds);
            }
            builder.append("S");
        } else if (milliseconds > 0) {
            if (addT) {
                builder.append("T");
                addT = false;
            }
            if (milliseconds >= 100) {
                builder.append("0.").append(milliseconds);
            } else if (milliseconds >= 10) {
                builder.append("0.0").append(milliseconds);
            } else {
                builder.append("0.00").append(milliseconds);
            }

            builder.append("S");

        }

        return builder.toString();
    }
}
