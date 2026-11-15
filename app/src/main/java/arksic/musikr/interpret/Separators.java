/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.musikr.interpret;

import arksic.musikr.util.Parse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** User-specified parsing of multi-value tags. Port of Separators.kt. */
public interface Separators {
    char COMMA = ',';
    char SEMICOLON = ';';
    char SLASH = '/';
    char PLUS = '+';
    char AND = '&';

    /**
     * Split a value that's a single string into several using the configured separators. If the
     * input is already multi-valued, nothing is done.
     */
    List<String> split(List<String> strings);

    static Separators from(String chars) {
        if (chars == null || chars.isEmpty()) return strings -> strings;
        Set<Integer> set = new HashSet<>();
        chars.chars().forEach(set::add);
        return strings -> {
            if (strings.size() != 1) return strings;
            return Parse.correctWhitespace(Parse.splitEscaped(strings.get(0), set::contains));
        };
    }
}
