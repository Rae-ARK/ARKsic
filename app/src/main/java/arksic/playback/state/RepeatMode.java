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

/** Repeat mode of the player. Port of RepeatMode.kt. */
public enum RepeatMode {
    /** Do not repeat; playback is paused when the queue wraps. */
    NONE,
    /** Repeat the whole queue. */
    ALL,
    /** Repeat the current song until skipped. */
    TRACK;

    public RepeatMode increment() {
        return switch (this) {
            case NONE -> ALL;
            case ALL -> TRACK;
            case TRACK -> NONE;
        };
    }
}
