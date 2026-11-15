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

/** Acknowledgements a state holder sends the manager after it changes something. */
public sealed interface StateAck {
    record IndexMoved() implements StateAck {}

    record PlayNext(int at, int size) implements StateAck {}

    record AddToQueue(int at, int size) implements StateAck {}

    record Move(int from, int to) implements StateAck {}

    record Remove(int index) implements StateAck {}

    record QueueReordered() implements StateAck {}

    record NewPlayback() implements StateAck {}

    record ProgressionChanged() implements StateAck {}

    record RepeatModeChanged() implements StateAck {}

    record SessionEnded() implements StateAck {}
}
