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

import java.text.CollationKey;
import java.util.List;

/** The name of a music item, with Auxio's sorting heuristics baked into comparison. */
public sealed interface Name extends Comparable<Name> permits Name.Known, Name.Unknown {

    /** A name that could be obtained for the music item. */
    abstract non-sealed class Known implements Name {
        /** The raw name string obtained. */
        public abstract String raw();

        /** The raw sort name string obtained, may be null. */
        public abstract String sort();

        /** A tokenized version of the name that will be compared. */
        public abstract List<Token> tokens();

        @Override
        public abstract int hashCode();

        @Override
        public abstract boolean equals(Object other);

        @Override
        public final int compareTo(Name other) {
            if (other instanceof Known k) {
                List<Token> a = tokens();
                List<Token> b = k.tokens();
                int n = Math.min(a.size(), b.size());
                for (int i = 0; i < n; i++) {
                    int r = a.get(i).compareTo(b.get(i));
                    if (r != 0) return r;
                }
                return Integer.compare(a.size(), b.size());
            }
            return 1; // Unknown always sorts before known.
        }

        @Override
        public String toString() {
            return raw();
        }
    }

    /** Placeholder used when no Known name could be obtained. */
    record Unknown(Placeholder placeholder) implements Name {
        @Override
        public int compareTo(Name other) {
            return other instanceof Unknown ? 0 : -1;
        }
    }

    /** An individual part of a name string that can be compared intelligently. */
    final class Token implements Comparable<Token> {
        public enum Type {
            /** Compare as a digit string, like "65". */
            NUMERIC,
            /** Compare as a standard alphanumeric string, like "65daysofstatic". */
            LEXICOGRAPHIC
        }

        final CollationKey collationKey;
        final Type type;

        public Token(CollationKey collationKey, Type type) {
            this.collationKey = collationKey;
            this.type = type;
        }

        public String value() {
            return collationKey.getSourceString();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Token t && collationKey.equals(t.collationKey) && type == t.type;
        }

        @Override
        public int hashCode() {
            return 31 * collationKey.hashCode() + type.hashCode();
        }

        @Override
        public int compareTo(Token other) {
            // Numeric tokens always sort lower than lexicographic ones.
            int modeComp = type.compareTo(other.type);
            if (modeComp != 0) return modeComp;
            // Numeric strings are ordered by magnitude: shorter is smaller.
            if (type == Type.NUMERIC
                    && collationKey.getSourceString().length()
                            != other.collationKey.getSourceString().length()) {
                return collationKey.getSourceString().length()
                        - other.collationKey.getSourceString().length();
            }
            return collationKey.compareTo(other.collationKey);
        }
    }
}
