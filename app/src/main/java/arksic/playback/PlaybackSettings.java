/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.playback;

import arksic.playback.replaygain.ReplayGain;

/** Playback-related user settings. Defaults match Auxio's PlaybackSettings.kt. */
public final class PlaybackSettings {
    public volatile ReplayGain.Mode replayGainMode = ReplayGain.Mode.DYNAMIC;
    public volatile ReplayGain.PreAmp replayGainPreAmp = new ReplayGain.PreAmp(0f, 0f);
    /** Keep shuffle on when starting new playback with IMPLICIT shuffle mode. */
    public volatile boolean keepShuffle = true;
    /** Previous-button rewinds the song first if it has played for a while. */
    public volatile boolean rewindWithPrev = true;
    /** Pause at the end of each song when repeating a single track. */
    public volatile boolean pauseOnRepeat = false;
    /** If paused, stay paused when changing songs. */
    public volatile boolean rememberPause = false;
}
