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

import arksic.musikr.tag.Date;
import arksic.musikr.tag.Disc;
import arksic.musikr.tag.Name;
import arksic.musikr.tag.ReplayGainAdjustment;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/** A song. */
public interface Song extends Music {
    @Override
    Name.Known name();

    /** Track number, null if absent from metadata. */
    Integer track();

    /** Disc, null if absent from metadata. */
    Disc disc();

    /** Release date, null if absent from metadata. */
    Date date();

    URI uri();

    /** Path of the audio file. */
    Path path();

    Format format();

    /** Size of the audio file in bytes. */
    long size();

    long durationMs();

    int bitrateKbps();

    int sampleRateHz();

    ReplayGainAdjustment replayGainAdjustment();

    long modifiedMs();

    /** Time the audio file was added, ms since the unix epoch. */
    long addedMs();

    /** Cover for this song, null if none. */
    Cover cover();

    /** Parent album. If the metadata names none, the parent directory is used. */
    Album album();

    /** Parent artists. Unlike Album, artists are prioritized for this field. */
    List<Artist> artists();

    List<Genre> genres();
}
