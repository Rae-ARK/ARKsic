/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.audio;

import java.nio.file.Path;

/**
 * Plays one file at a time with millisecond position and seeking. The queue logic (what plays
 * next) lives above this in {@code QueuePlaybackStateHolder}; the engine only knows "this file".
 *
 * <p>Callbacks arrive on the engine's decode thread.
 */
public interface AudioEngine {
    interface Listener {
        /** The loaded file played to its end. */
        void onEnded();

        void onError(Exception error);
    }

    void setListener(Listener listener);

    /** Load {@code path}, start at {@code startMs}, and either play or sit paused. */
    void load(Path path, long startMs, boolean play);

    /** Play/pause the loaded file. */
    void setPlaying(boolean playing);

    /** Whether the engine is currently set to play (and has something loaded and unfinished). */
    boolean isPlaying();

    void seekTo(long positionMs);

    long positionMs();

    /** Multiplier applied to the PCM (user volume * ReplayGain). */
    void setGain(float linear);

    /** Release the loaded file. */
    void stop();
}
