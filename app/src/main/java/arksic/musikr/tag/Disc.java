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

/** A disc identifier for a song. Equality deliberately ignores the subtitle. */
public final class Disc implements Comparable<Disc> {
    public final int number;
    public final String name;

    public Disc(int number, String name) {
        this.number = number;
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Disc d && number == d.number;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(number);
    }

    @Override
    public int compareTo(Disc other) {
        return Integer.compare(number, other.number);
    }
}
