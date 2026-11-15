/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.musikr.tag;

import arksic.musikr.util.Parse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An ISO-8601/RFC 3339 date with variable precision. Only encodes the timestamp spec and its
 * conversion to a human-readable date; use the {@code from} factories to create one.
 */
public final class Date implements Comparable<Date> {
    private final List<Integer> tokens;

    public final int year;
    public final Integer month;
    public final Integer day;
    public final Integer hour;
    public final Integer minute;
    public final Integer second;

    private Date(List<Integer> tokens) {
        this.tokens = Collections.unmodifiableList(tokens);
        this.year = tokens.get(0);
        this.month = at(tokens, 1);
        this.day = at(tokens, 2);
        this.hour = at(tokens, 3);
        this.minute = at(tokens, 4);
        this.second = at(tokens, 5);
    }

    private static Integer at(List<Integer> l, int i) {
        return i < l.size() ? l.get(i) : null;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Date d && compareTo(d) == 0;
    }

    @Override
    public int hashCode() {
        return tokens.hashCode();
    }

    @Override
    public int compareTo(Date other) {
        int n = Math.max(tokens.size(), other.tokens.size());
        for (int i = 0; i < n; i++) {
            Integer ai = at(tokens, i);
            Integer bi = at(other.tokens, i);
            if (ai != null && bi != null) {
                int r = ai.compareTo(bi);
                if (r != 0) return r;
            } else if (ai == null && bi != null) {
                return -1;
            } else if (ai == null) {
                return 0;
            } else {
                return 1;
            }
        }
        return 0;
    }

    /** ISO-8601 string, dropping precision that doesn't exist. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(fixed(year, 4));
        if (month == null) return sb.toString();
        sb.append('-').append(fixed(month, 2));
        if (day == null) return sb.toString();
        sb.append('-').append(fixed(day, 2));
        if (hour == null) return sb.toString();
        sb.append('T').append(fixed(hour, 2));
        if (minute == null) return sb.append('Z').toString();
        sb.append(':').append(fixed(minute, 2));
        if (second == null) return sb.append('Z').toString();
        sb.append(':').append(fixed(second, 2));
        return sb.append('Z').toString();
    }

    private static String fixed(int v, int len) {
        String s = Integer.toString(v);
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < len; i++) sb.append('0');
        sb.append(s);
        return sb.substring(0, len);
    }

    /** A range of dates, used where an item's date is derived from several sub-items. */
    public static final class Range implements Comparable<Range> {
        public final Date min;
        public final Date max;

        public Range(Date min, Date max) {
            if (min.compareTo(max) > 0) throw new IllegalStateException("Min date must be <= max date");
            this.min = min;
            this.max = max;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Range r && min.equals(r.min) && max.equals(r.max);
        }

        @Override
        public int hashCode() {
            return 31 * max.hashCode() + min.hashCode();
        }

        @Override
        public int compareTo(Range other) {
            return min.compareTo(other.min);
        }
    }

    // Derived from mutagen, same as Auxio.
    private static final Pattern ISO8601 =
            Pattern.compile(
                    "^(\\d{4})([-.](\\d{2})([-.](\\d{2})([T ](\\d{2})([:.](\\d{2})([:.](\\d{2})(Z)?)?)?)?)?)?$");

    public static Date from(int year) {
        if (year >= 10000000 && year <= 100000000) {
            // Actually a packed YYYYMMDD value.
            String s = Integer.toString(year);
            return from(
                    Integer.parseInt(s.substring(0, 4)),
                    Integer.parseInt(s.substring(4, 6)),
                    Integer.parseInt(s.substring(6, 8)));
        }
        return fromTokens(List.of(year));
    }

    public static Date from(int year, int month, int day) {
        return fromTokens(List.of(year, month, day));
    }

    public static Date from(int year, int month, int day, int hour, int minute) {
        return fromTokens(List.of(year, month, day, hour, minute));
    }

    public static Date from(String timestamp) {
        if (timestamp == null) return null;
        Matcher m = ISO8601.matcher(timestamp);
        if (!m.matches()) {
            try {
                return from(Integer.parseInt(timestamp));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        List<Integer> tokens = new ArrayList<>();
        for (int g = 1; g <= 11; g += 2) {
            String s = m.group(g);
            if (s != null) tokens.add(Integer.parseInt(s));
        }
        return fromTokens(tokens);
    }

    private static Date fromTokens(List<Integer> tokens) {
        List<Integer> validated = new ArrayList<>();
        transformTokens(tokens, validated);
        return validated.isEmpty() ? null : new Date(validated);
    }

    /** Validate tokens, stopping at the first invalid one. */
    private static void transformTokens(List<Integer> src, List<Integer> dst) {
        int[][] ranges = {{1, Integer.MAX_VALUE}, {1, 12}, {1, 31}, {0, 23}, {0, 59}, {0, 59}};
        for (int i = 0; i < ranges.length; i++) {
            if (i >= src.size()) return;
            int v = src.get(i);
            if (v < ranges[i][0] || v > ranges[i][1]) return;
            dst.add(v);
        }
    }
}
