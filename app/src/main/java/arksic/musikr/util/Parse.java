/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.musikr.util;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntPredicate;

/** Port of ParseUtil.kt and the small helpers from LangUtil.kt. */
public final class Parse {
    private Parse() {}

    /** Split by selector, honouring backslash-escaped separators. */
    public static List<String> splitEscaped(String s, IntPredicate selector) {
        List<String> split = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char a = s.charAt(i);
            Character b = i + 1 < s.length() ? s.charAt(i + 1) : null;
            if (selector.test(a)) {
                split.add(current.toString());
                current.setLength(0);
                i++;
                continue;
            }
            if (b != null && a == '\\' && selector.test(b)) {
                current.append(b);
                i += 2;
            } else {
                current.append(a);
                i++;
            }
        }
        if (current.length() > 0) split.add(current.toString());
        return split;
    }

    /** Trim; null if blank. */
    public static String correctWhitespace(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }

    public static List<String> correctWhitespace(List<String> strings) {
        List<String> out = new ArrayList<>();
        for (String s : strings) {
            String c = correctWhitespace(s);
            if (c != null) out.add(c);
        }
        return out;
    }

    public static Integer positiveOrNull(int v) {
        return v > 0 ? v : null;
    }

    public static Float nonZeroOrNull(float v) {
        return v != 0f ? v : null;
    }

    public static Integer inRangeOrNull(int v, int lo, int hiInclusive) {
        return v >= lo && v <= hiInclusive ? v : null;
    }

    public static UUID toUuidOrNull(String s) {
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
