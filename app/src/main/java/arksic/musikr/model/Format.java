/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.musikr.model;

import java.util.Locale;

/**
 * Audio file format. Auxio's Format covers many codecs; ARKsic's scope lock (see
 * docs/proposal) is MP3, FLAC and WAV, so only those are modelled, plus an Unknown fallback.
 */
public enum Format {
    MPEG3("audio/mpeg", "MP3"),
    FLAC("audio/flac", "FLAC"),
    WAV("audio/wav", "WAV"),
    UNKNOWN("application/octet-stream", "?");

    public final String mimeType;
    public final String label;

    Format(String mimeType, String label) {
        this.mimeType = mimeType;
        this.label = label;
    }

    public static Format fromFileName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".mp3")) return MPEG3;
        if (n.endsWith(".flac")) return FLAC;
        if (n.endsWith(".wav") || n.endsWith(".wave")) return WAV;
        return UNKNOWN;
    }
}
