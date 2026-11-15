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

import java.util.Collection;
import java.util.List;

/** An artist: a combination of the artist and album-artist tags from the library. */
public interface Artist extends MusicParent {
    /** Albums directly credited via an "Album Artist" tag. */
    Collection<Album> explicitAlbums();

    /** Albums indirectly credited via an "Artist" tag. */
    Collection<Album> implicitAlbums();

    long durationMs();

    CoverCollection covers();

    List<Genre> genres();
}
