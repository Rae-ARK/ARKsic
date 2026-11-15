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

/**
 * The thing that actually owns the player and queue. The manager mirrors its state and forwards
 * commands to it. Port of PlaybackStateHolder.kt (the ExoPlayer implementation is replaced by
 * {@code QueuePlaybackStateHolder}).
 */
public interface PlaybackStateHolder {
    Progression progression();

    RepeatMode repeatMode();

    MusicParent parent();

    RawQueue resolveQueue();

    void newPlayback(PlaybackCommand command);

    void playing(boolean playing);

    void seekTo(long positionMs);

    void repeatMode(RepeatMode repeatMode);

    void next();

    void prev();

    void goTo(int index);

    void playNext(List<Song> songs, StateAck.PlayNext ack);

    void addToQueue(List<Song> songs, StateAck.AddToQueue ack);

    void move(int from, int to, StateAck.Move ack);

    void remove(int at, StateAck.Remove ack);

    void shuffled(boolean shuffled);

    void applySavedState(MusicParent parent, RawQueue rawQueue, long positionMs, RepeatMode repeatMode, StateAck.NewPlayback ack);

    void endSession();

    void reset(StateAck.NewPlayback ack);
}
