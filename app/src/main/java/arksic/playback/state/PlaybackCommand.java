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

import arksic.musikr.model.MusicParent;
import arksic.musikr.model.Song;
import java.util.List;

/** A request to begin new playback. Port of PlaybackCommand.kt (factory is in its own class). */
public interface PlaybackCommand {
    /** The song to start on; null means "start at the beginning (or a random song if shuffled)". */
    Song song();

    MusicParent parent();

    List<Song> queue();

    boolean shuffled();

    record Impl(Song song, MusicParent parent, List<Song> queue, boolean shuffled) implements PlaybackCommand {
        @Override
        public String toString() {
            return "PlaybackCommand(song=" + song + ", parent=" + parent + ", queue=" + queue.size()
                    + " songs, shuffled=" + shuffled + ")";
        }
    }
}
