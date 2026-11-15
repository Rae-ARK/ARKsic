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

/** How tags get interpreted: how names sort, and how multi-value tags split. */
public record Interpretation(Naming naming, Separators separators) {
    public static Interpretation defaults() {
        return new Interpretation(Naming.intelligent(), Separators.from("" + Separators.SEMICOLON));
    }
}
