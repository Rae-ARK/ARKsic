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

/** A change to the queue. */
public record QueueChange(Type type, UpdateInstructions instructions) {
    public enum Type {
        /** Only the queue mapping changed. */
        MAPPING,
        /** The mapping and the current index changed, but not the current song. */
        INDEX,
        /** The current song changed. */
        SONG
    }
}
