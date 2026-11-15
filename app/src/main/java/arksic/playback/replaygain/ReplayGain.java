/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.playback.replaygain;

import arksic.musikr.model.Album;
import arksic.musikr.model.MusicParent;
import arksic.musikr.model.Song;
import arksic.musikr.tag.ReplayGainAdjustment;

/** ReplayGain gain resolution; the decision logic of ReplayGainAudioProcessor.kt. */
public final class ReplayGain {
    private ReplayGain() {}

    public enum Mode {
        OFF,
        /** Track gain, falling back to album gain. */
        TRACK,
        /** Album gain, falling back to track gain. */
        ALBUM,
        /** Album gain only when playing from that album; track gain otherwise. */
        DYNAMIC
    }

    /** Pre-amp (dB) applied when a gain tag was found ({@code with}) or not ({@code without}). */
    public record PreAmp(float with, float without) {}

    /**
     * Resolve the linear volume multiplier for a song.
     *
     * @param parent the parent currently being played from (used by DYNAMIC), may be null
     */
    public static float volumeFor(Song song, Mode mode, PreAmp preAmp, MusicParent parent) {
        if (song == null) return 1f;
        ReplayGainAdjustment gain = song.replayGainAdjustment();
        Float resolved = switch (mode) {
            case OFF -> null;
            case TRACK -> gain.track() != null ? gain.track() : gain.album();
            case ALBUM -> gain.album() != null ? gain.album() : gain.track();
            case DYNAMIC -> {
                boolean inAlbum = parent instanceof Album && song.album().equals(parent);
                yield gain.album() != null && inAlbum ? gain.album() : gain.track();
            }
        };
        float db = resolved != null ? resolved + preAmp.with() : preAmp.without();
        return (float) Math.pow(10.0, db / 20.0);
    }

    /** Apply a linear volume to 16-bit little-endian PCM, clamping to avoid wraparound popping. */
    public static void applyGain16le(byte[] buffer, int length, float volume) {
        if (volume == 1f) return;
        for (int i = 0; i + 1 < length; i += 2) {
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            int scaled = (int) (sample * volume);
            scaled = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, scaled));
            buffer[i] = (byte) scaled;
            buffer[i + 1] = (byte) (scaled >> 8);
        }
    }
}
