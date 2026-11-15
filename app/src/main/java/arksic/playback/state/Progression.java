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

/**
 * A snapshot of playback progression that can extrapolate the current position without being
 * polled. Port of Progression in PlaybackStateHolder.kt (SystemClock -> System.nanoTime).
 */
public final class Progression {
    private final boolean isPlaying;
    private final boolean isAdvancing;
    private final long initPositionMs;
    private final long creationTimeMs;

    private Progression(boolean isPlaying, boolean isAdvancing, long initPositionMs, long creationTimeMs) {
        this.isPlaying = isPlaying;
        this.isAdvancing = isAdvancing;
        this.initPositionMs = initPositionMs;
        this.creationTimeMs = creationTimeMs;
    }

    private static long now() {
        return System.nanoTime() / 1_000_000L;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public long calculateElapsedPositionMs() {
        // Not advancing (buffering, transient pause): position is frozen.
        return isAdvancing ? initPositionMs + (now() - creationTimeMs) : initPositionMs;
    }

    public static Progression from(boolean isPlaying, boolean isAdvancing, long positionMs) {
        // Sanity check: can't advance while paused.
        return new Progression(isPlaying, isPlaying && isAdvancing, positionMs, now());
    }

    public static Progression nil() {
        return new Progression(false, false, 0, now());
    }

    // Equality ignores creation time so functionally identical states compare equal.
    @Override
    public boolean equals(Object o) {
        return o instanceof Progression p
                && isPlaying == p.isPlaying
                && isAdvancing == p.isAdvancing
                && initPositionMs == p.initPositionMs;
    }

    @Override
    public int hashCode() {
        int r = Boolean.hashCode(isPlaying);
        r = 31 * r + Boolean.hashCode(isAdvancing);
        return 31 * r + Long.hashCode(initPositionMs);
    }
}
