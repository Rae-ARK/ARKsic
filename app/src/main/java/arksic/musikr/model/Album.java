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
import arksic.musikr.tag.ReleaseType;
import java.util.List;

/** A release group: albums, singles, EPs, compilations. */
public interface Album extends MusicParent {
    Date.Range dates();

    ReleaseType releaseType();

    CoverCollection covers();

    long durationMs();

    /** Earliest time a song in this album was added. */
    long addedMs();

    /** Parent artists. Unlike Song, album artists are prioritized for this field. */
    List<Artist> artists();
}
