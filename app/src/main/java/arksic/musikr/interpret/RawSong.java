/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.musikr.interpret;

import arksic.musikr.model.Cover;
import java.nio.file.Path;

/** A song as extracted from disk: file facts, audio properties, and parsed tags. */
public record RawSong(
        Path file,
        long size,
        long modifiedMs,
        long addedMs,
        int bitrateKbps,
        int sampleRateHz,
        ParsedTags tags,
        Cover cover) {}
