/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.playback.state;

/** How a list changed, so a view can animate/patch instead of redrawing. */
public sealed interface UpdateInstructions {
    record Add(int at, int size) implements UpdateInstructions {}

    record Remove(int at, int size) implements UpdateInstructions {}

    record Move(int from, int to) implements UpdateInstructions {}
}
