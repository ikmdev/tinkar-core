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

import dev.ikm.tinkar.common.service.PrimitiveData;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility methods and formatters for converting between epoch milliseconds,
 * {@link Instant}, {@link ZonedDateTime}, and human-readable date/time strings.
 * Sentinel values ({@link Long#MAX_VALUE}, {@link Long#MIN_VALUE}, and the
 * premundane constant) are mapped to descriptive labels.
 */
public class DateTimeUtil {
    /** Private constructor to prevent instantiation. */
    private DateTimeUtil() {}
    /** Approximate number of milliseconds in one year (365 days). */
    public static final long MS_IN_YEAR =   1000L * 60 * 60 * 24 * 365;
    /** Approximate number of milliseconds in one month (30 days). */
    public static final long MS_IN_MONTH =  1000L * 60 * 60 * 24 * 30;
    /** Number of milliseconds in one day. */
    public static final long MS_IN_DAY =    1000L * 60 * 60 * 24;
    /** Number of milliseconds in one hour. */
    public static final long MS_IN_HOUR =   1000L * 60 * 60;
    /** Number of milliseconds in one minute. */
    public static final long MS_IN_MINUTE = 1000L * 60;
    /** Number of milliseconds in one second. */
    public static final long MS_IN_SEC =    1000L;

    /** Date formatter: {@code MMM dd, yyyy}. */
    public static final DateTimeFormatter EASY_TO_READ_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    /** Time formatter: {@code h:mm a zzz}. */
    public static final DateTimeFormatter EASY_TO_READ_TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a zzz");
    /** Date-time formatter: {@code h:mm a zzz MMM dd, yyyy}. */
    public static final DateTimeFormatter EASY_TO_READ_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a zzz MMM dd, yyyy");
    /** Default date-time formatter: {@code yyyy-MM-dd HH:mm}. */
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** Date-time formatter with seconds: {@code yyyy-MM-dd HH:mm:ss}. */
    public static final DateTimeFormatter SEC_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** Date-time formatter to minute precision: {@code yyyy-MM-dd HH:mm}. */
    public static final DateTimeFormatter MIN_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** Short date-time formatter to minute precision: {@code yy-MM-dd HH:mm}. */
    public static final DateTimeFormatter SHORT_MIN_FORMATTER = DateTimeFormatter.ofPattern("yy-MM-dd HH:mm");
    /** Date-time formatter to hour precision: {@code yyyy-MM-dd HH:00}. */
    public static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
    /** Date-only formatter: {@code yyyy-MM-dd}. */
    public static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** Month-year formatter: {@code MMMM yyyy}. */
    public static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy");
    /** Year-only formatter: {@code yyyy}. */
    public static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy");
    /** ISO date-time formatter with zone information. */
    public static final DateTimeFormatter ZONE_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    /** Human-readable date-time formatter with time zone: {@code MMM dd, yyyy; hh:mm:ss a zzz}. */
    public static final DateTimeFormatter TEXT_FORMAT_WITH_ZONE = DateTimeFormatter.ofPattern("MMM dd, yyyy; hh:mm:ss a zzz");
    /** Simple time-only formatter: {@code HH:mm:ss}. */
    public static final DateTimeFormatter TIME_SIMPLE = DateTimeFormatter.ofPattern("HH:mm:ss");
    /** Compressed date-time formatter with zone: {@code yyyyMMdd'T'HHmmssz}. */
    public static final DateTimeFormatter COMPRESSED_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssz");
    /** Compressed date-only formatter: {@code yyyyMMdd}. */
    public static final DateTimeFormatter COMPRESSED_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Sentinel label for {@link Long#MAX_VALUE} epoch values. */
    public static final String LATEST = "Latest";
    /** Sentinel label for {@link Long#MIN_VALUE} epoch values. */
    public static final String CANCELED = "Canceled";
    /** Sentinel label for premundane epoch values. */
    public static final String PREMUNDANE = "Premundane";

    /**
     * Converts an epoch-millisecond value to an {@link Instant}, mapping
     * {@link Long#MAX_VALUE} to {@link Instant#MAX} and {@link Long#MIN_VALUE}
     * to {@link Instant#MIN}.
     *
     * @param epochMilliSecond the epoch millisecond value
     * @return the corresponding {@link Instant}
     */
    public static Instant epochMsToInstant(long epochMilliSecond) {
        if (epochMilliSecond == Long.MAX_VALUE) {
            return Instant.MAX;
        }
        if (epochMilliSecond == Long.MIN_VALUE) {
            return Instant.MIN;
        }
        return Instant.ofEpochMilli(epochMilliSecond);
    }

    /**
     * Converts an {@link Instant} to epoch milliseconds, mapping
     * {@link Instant#MIN} to {@link Long#MIN_VALUE} and {@link Instant#MAX}
     * to {@link Long#MAX_VALUE}.
     *
     * @param instant the instant to convert
     * @return the epoch millisecond value
     */
    public static long instantToEpochMs(Instant instant) {
        if (instant.equals(Instant.MIN)) {
            return Long.MIN_VALUE;
        }
        if (instant.equals(Instant.MAX)) {
            return Long.MAX_VALUE;
        }
        return instant.toEpochMilli();
    }

    /**
     * Converts an epoch-millisecond value to a UTC {@link ZonedDateTime}.
     *
     * @param epochMilliSecond the epoch millisecond value
     * @return the corresponding {@link ZonedDateTime} in UTC
     */
    public static ZonedDateTime epochToZonedDateTime(long epochMilliSecond) {
        return Instant.ofEpochMilli(epochMilliSecond).atZone(ZoneOffset.UTC);
    }
    /**
     * Converts an epoch-millisecond value to an {@link Instant}.
     *
     * @param epochMilliSecond the epoch millisecond value
     * @return the corresponding {@link Instant}
     */
    public static Instant epochToInstant(long epochMilliSecond) {
        return Instant.ofEpochMilli(epochMilliSecond);
    }

    /**
     * Formats an epoch-millisecond value using the default {@link #FORMATTER}.
     *
     * @param epochMilliSecond the epoch millisecond value
     * @return the formatted date-time string, or a sentinel label for special values
     */
    public static String format(long epochMilliSecond) {
        return format(epochMilliSecond, FORMATTER);
    }
    /**
     * Formats an epoch-millisecond value using the specified formatter.
     * Sentinel values are returned as descriptive labels.
     *
     * @param epochMilliSecond the epoch millisecond value
     * @param formatter the formatter to apply
     * @return the formatted date-time string, or a sentinel label for special values
     */
    public static String format(long epochMilliSecond, DateTimeFormatter formatter) {
        if (epochMilliSecond == Long.MAX_VALUE) {
            return LATEST;
        }
        if (epochMilliSecond == Long.MIN_VALUE) {
            return CANCELED;
        }
        if (epochMilliSecond == PrimitiveData.PREMUNDANE_TIME) {
            return PREMUNDANE;
        }
        ZonedDateTime positionTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMilliSecond), ZoneOffset.UTC);
        ZonedDateTime inLocalZone = positionTime.withZoneSameInstant(ZoneId.systemDefault());
        return inLocalZone.format(formatter);
    }
    /**
     * Returns the current time formatted as {@code HH:mm:ss}.
     *
     * @return the current time string
     */
    public static String timeNowSimple() {
        return TIME_SIMPLE.format(ZonedDateTime.now());
    }

    /**
     * Returns the current date-time formatted in ISO-8601.
     *
     * @return the current date-time in ISO format
     */
    public static String nowInISO() {
        return DateTimeFormatter.ISO_DATE_TIME.format(ZonedDateTime.now());
    }
    /**
     * Returns the current date-time in compressed format with zone ({@code yyyyMMdd'T'HHmmssz}).
     *
     * @return the compressed date-time string
     */
    public static String nowWithZoneCompact() {
        return COMPRESSED_DATE_TIME.format(ZonedDateTime.now());
    }
    /**
     * Returns the current date-time formatted with time zone information.
     *
     * @return the formatted date-time string with zone
     */
    public static String nowWithZone() {
        return textFormatWithZone(ZonedDateTime.now());
    }
    /**
     * Formats a {@link ZonedDateTime} using the {@link #TEXT_FORMAT_WITH_ZONE} pattern.
     *
     * @param zonedDateTime the zoned date-time to format
     * @return the formatted string
     */
    public static String textFormatWithZone(ZonedDateTime zonedDateTime) {
        return TEXT_FORMAT_WITH_ZONE.format(zonedDateTime);
    }

    /**
     * Parses a zoned date-time string into epoch milliseconds.
     *
     * @param dateTime a date-time string such as '2011-12-03T10:15:30',
     *                 '2011-12-03T10:15:30+01:00', or '2011-12-03T10:15:30+01:00[Europe/Paris]'
     * @return the epoch millisecond value
     */
    public static long parseWithZone(String dateTime) {
        if (dateTime.equalsIgnoreCase(LATEST)) {
            return Long.MAX_VALUE;
        }
        if (dateTime.equalsIgnoreCase(CANCELED)) {
            return Long.MIN_VALUE;
        }
        if (dateTime.equalsIgnoreCase(PREMUNDANE)) {
            return PrimitiveData.PREMUNDANE_TIME;
        }
        return ZonedDateTime.parse(dateTime, ZONE_FORMATTER).toInstant().toEpochMilli();
    }

    /**
     * Parses a compressed date-time string ({@code yyyyMMdd'T'HHmmssz}) into epoch milliseconds.
     *
     * @param dateTime the compressed date-time string
     * @return the epoch millisecond value
     */
    public static long compressedParse(String dateTime) {
        if (dateTime.equalsIgnoreCase(LATEST)) {
            return Long.MAX_VALUE;
        }
        if (dateTime.equalsIgnoreCase(CANCELED)) {
            return Long.MIN_VALUE;
        }
        if (dateTime.equalsIgnoreCase(PREMUNDANE)) {
            return PrimitiveData.PREMUNDANE_TIME;
        }
        return LocalDateTime.parse(dateTime, COMPRESSED_DATE_TIME).atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    /**
     * Parses a compressed date string ({@code yyyyMMdd}) into epoch milliseconds at
     * the start of the day in UTC.
     *
     * @param date the compressed date string
     * @return the epoch millisecond value
     */
    public static long compressedDateParse(String date) {
        if (date.equalsIgnoreCase(LATEST)) {
            return Long.MAX_VALUE;
        }
        if (date.equalsIgnoreCase(CANCELED)) {
            return Long.MIN_VALUE;
        }
        if (date.equalsIgnoreCase(PREMUNDANE)) {
            return PrimitiveData.PREMUNDANE_TIME;
        }
        return LocalDate.parse(date, COMPRESSED_DATE).atStartOfDay().atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    /**
     * Parses a date-time string in {@code yyyy-MM-dd HH:mm} format into epoch milliseconds.
     *
     * @param dateTime the date-time string
     * @return the epoch millisecond value
     */
    public static long parse(String dateTime) {
        if (dateTime.equalsIgnoreCase(LATEST)) {
            return Long.MAX_VALUE;
        }
        if (dateTime.equalsIgnoreCase(CANCELED)) {
            return Long.MIN_VALUE;
        }
        if (dateTime.equalsIgnoreCase(PREMUNDANE)) {
            return PrimitiveData.PREMUNDANE_TIME;
        }
        return LocalDateTime.parse(dateTime, FORMATTER).atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
    }
    /**
     * Formats an {@link Instant} using the specified formatter.
     * Sentinel instants are returned as descriptive labels.
     *
     * @param instant the instant to format
     * @param formatter the formatter to apply
     * @return the formatted date-time string, or a sentinel label for special values
     */
    public static String format(Instant instant, DateTimeFormatter formatter) {
        if (instant.equals(Instant.MAX)) {
            return LATEST;
        }
        if (instant.equals(Instant.MIN)) {
            return CANCELED;
        }
        if (instant.equals(PrimitiveData.PREMUNDANE_INSTANT)) {
            return PREMUNDANE;
        }
        return formatter.format(instant.atOffset(ZoneOffset.UTC));
    }
    /**
     * Formats an {@link Instant} using the default {@link #FORMATTER}.
     *
     * @param instant the instant to format
     * @return the formatted date-time string, or a sentinel label for special values
     */
    public static String format(Instant instant) {
        return format(instant, FORMATTER);
    }
    /**
     * Formats a {@link ZonedDateTime} using the ISO zone formatter.
     *
     * @param zonedDateTime the zoned date-time to format
     * @return the formatted ISO date-time string
     */
    public static String format(ZonedDateTime zonedDateTime) {
        return zonedDateTime.format(ZONE_FORMATTER);
    }
    /**
     * Formats an {@link Instant} at a precision determined by the given resolution
     * (in milliseconds). Smaller resolution values produce more precise output.
     *
     * @param instant the instant to format
     * @param resolution the display resolution in milliseconds
     * @return the formatted string at the appropriate precision
     */
    public static String format(Instant instant, Double resolution) {
        if (instant.equals(Instant.MAX)) {
            return LATEST;
        }
        if (instant.equals(Instant.MIN)) {
            return CANCELED;
        }
        if (instant.equals(PrimitiveData.PREMUNDANE_INSTANT)) {
            return PREMUNDANE;
        }
        if (resolution < MS_IN_SEC) {
            return ZONE_FORMATTER.format(instant);
        }
        if (resolution < MS_IN_MINUTE) {
            return SEC_FORMATTER.format(instant);
        }
        if (resolution < MS_IN_HOUR) {
            return MIN_FORMATTER.format(instant);
        }
        if (resolution < MS_IN_DAY) {
            return HOUR_FORMATTER.format(instant);
        }
        if (resolution < MS_IN_MONTH) {
            return DAY_FORMATTER.format(instant);
        }
        if (resolution < MS_IN_YEAR) {
            return MONTH_FORMATTER.format(instant);
        }
        return YEAR_FORMATTER.format(instant);
    }
    /**
     * Formats a {@link ZonedDateTime} at a precision determined by the given resolution
     * (in milliseconds). Smaller resolution values produce more precise output.
     *
     * @param zonedDateTime the zoned date-time to format
     * @param resolution the display resolution in milliseconds
     * @return the formatted string at the appropriate precision
     */
    public static String format(ZonedDateTime zonedDateTime, Double resolution) {
        if (resolution < MS_IN_SEC) {
            return zonedDateTime.format(ZONE_FORMATTER);
        }
        if (resolution < MS_IN_MINUTE) {
            return zonedDateTime.format(SEC_FORMATTER);
        }
        if (resolution < MS_IN_HOUR) {
            return zonedDateTime.format(MIN_FORMATTER);
        }
        if (resolution < MS_IN_DAY) {
            return zonedDateTime.format(HOUR_FORMATTER);
        }
        if (resolution < MS_IN_MONTH) {
            return zonedDateTime.format(DAY_FORMATTER);
        }
        if (resolution < MS_IN_YEAR) {
            return zonedDateTime.format(MONTH_FORMATTER);
        }
        return zonedDateTime.format(YEAR_FORMATTER);
    }

    /**
     * Converts a {@link LocalDateTime} to epoch milliseconds using the system default time zone.
     *
     * @param localDateTime the local date-time to convert
     * @return the epoch millisecond value
     */
    public static long toEpochMilliseconds(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
    /**
     * Returns the English ordinal suffix for a day-of-month value
     * (e.g., "st" for 1, "nd" for 2, "rd" for 3, "th" for 4-20).
     *
     * @param n the day of month (1-31)
     * @return the ordinal suffix
     * @throws IllegalArgumentException if {@code n} is not between 1 and 31
     */
    public static String getDayOfMonthSuffix(final int n) {
        if (n < 1 || n > 31) {
            throw new IllegalArgumentException("illegal day of month: " + n);
        }
        if (n >= 11 && n <= 13) {
            return "th";
        }
        switch (n % 10) {
            case 1:  return "st";
            case 2:  return "nd";
            case 3:  return "rd";
            default: return "th";
        }
    }
}
