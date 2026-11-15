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

import arksic.musikr.tag.Name;
import arksic.musikr.tag.Name.Token;
import arksic.musikr.tag.Placeholder;
import java.text.CollationKey;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns raw tag strings into {@link Name}s. Port of Naming.kt.
 *
 * <p>Deviation from Auxio: the Android-only ICU "Any-Latin" transliteration step is omitted (it
 * relied on android.icu). Collation still handles non-Latin scripts reasonably.
 */
public abstract class Naming {
    public Name name(String raw, String sort, Placeholder placeholder) {
        return raw != null ? name(raw, sort) : new Name.Unknown(placeholder);
    }

    public abstract Name.Known name(String raw, String sort);

    public static Naming intelligent() {
        return IntelligentNaming.INSTANCE;
    }

    public static Naming simple() {
        return SimpleNaming.INSTANCE;
    }

    private static final Pattern PUNCT = Pattern.compile("[\\p{Punct}+]");
    private static final Pattern TOKEN = Pattern.compile("(\\d+)|(\\D+)");

    private static Collator newCollator() {
        Collator c = Collator.getInstance();
        c.setStrength(Collator.PRIMARY);
        return c;
    }

    // Collator is not safe for concurrent use across our scan threads; guard it.
    private static final Collator COLLATOR = newCollator();

    private static synchronized CollationKey key(String s) {
        return COLLATOR.getCollationKey(s);
    }

    private static final class SimpleNaming extends Naming {
        static final SimpleNaming INSTANCE = new SimpleNaming();

        @Override
        public Name.Known name(String raw, String sort) {
            return new SimpleKnownName(raw, sort);
        }
    }

    private static final class IntelligentNaming extends Naming {
        static final IntelligentNaming INSTANCE = new IntelligentNaming();

        @Override
        public Name.Known name(String raw, String sort) {
            return new IntelligentKnownName(raw, sort);
        }
    }

    /** Plain Name.Known implementation that is internationalization-safe. */
    private static final class SimpleKnownName extends Name.Known {
        private final String raw;
        private final String sort;
        private final List<Token> tokens;

        SimpleKnownName(String raw, String sort) {
            this.raw = raw;
            this.sort = sort;
            String name = sort != null ? sort : raw;
            // Punctuation usually isn't considered in sorting.
            String stripped = PUNCT.matcher(name).replaceAll("").strip();
            if (stripped.isEmpty()) stripped = name;
            this.tokens = List.of(new Token(key(stripped), Token.Type.LEXICOGRAPHIC));
        }

        @Override public String raw() { return raw; }
        @Override public String sort() { return sort; }
        @Override public List<Token> tokens() { return tokens; }

        @Override
        public int hashCode() { return Objects.hash(raw, sort); }

        @Override
        public boolean equals(Object o) {
            return o instanceof SimpleKnownName n && raw.equals(n.raw) && Objects.equals(sort, n.sort);
        }
    }

    /** Name.Known implementation adding numeric-aware sorting and article stripping. */
    private static final class IntelligentKnownName extends Name.Known {
        private final String raw;
        private final String sort;
        private final List<Token> tokens;

        IntelligentKnownName(String raw, String sort) {
            this.raw = raw;
            this.sort = sort;
            this.tokens = parseTokens(sort != null ? sort : raw);
        }

        private static List<Token> parseTokens(String name) {
            // Punctuation becomes spaces to create token boundaries ("15-9" vs "15-10").
            String stripped = PUNCT.matcher(name).replaceAll(" ");
            if (stripped.isBlank()) stripped = name;

            // Strip English articles from the start.
            if (stripped.length() > 4 && startsWithIgnoreCase(stripped, "the ")) {
                stripped = stripped.substring(4);
            } else if (stripped.length() > 3 && startsWithIgnoreCase(stripped, "an ")) {
                stripped = stripped.substring(3);
            } else if (stripped.length() > 2 && startsWithIgnoreCase(stripped, "a ")) {
                stripped = stripped.substring(2);
            }

            // Split into numeric and lexicographic runs so numbers compare by magnitude.
            List<Token> out = new ArrayList<>();
            Matcher m = TOKEN.matcher(stripped);
            while (m.find()) {
                String value = m.group();
                String token = value.strip();
                if (token.isEmpty()) token = value;
                if (Character.isDigit(token.charAt(0))) {
                    // Leading zeros break digit-string comparison; drop them.
                    int i = 0;
                    while (i < token.length() && Character.getNumericValue(token.charAt(i)) == 0) i++;
                    String digits = token.substring(i);
                    if (digits.isEmpty()) digits = token;
                    out.add(new Token(key(digits), Token.Type.NUMERIC));
                } else {
                    out.add(new Token(key(token), Token.Type.LEXICOGRAPHIC));
                }
            }
            return List.copyOf(out);
        }

        private static boolean startsWithIgnoreCase(String s, String prefix) {
            return s.regionMatches(true, 0, prefix, 0, prefix.length());
        }

        @Override public String raw() { return raw; }
        @Override public String sort() { return sort; }
        @Override public List<Token> tokens() { return tokens; }

        @Override
        public int hashCode() { return Objects.hash(raw, sort); }

        @Override
        public boolean equals(Object o) {
            return o instanceof IntelligentKnownName n && raw.equals(n.raw) && Objects.equals(sort, n.sort);
        }
    }
}
