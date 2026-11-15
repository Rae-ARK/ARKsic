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

import arksic.musikr.tag.Name;
import java.util.List;

public interface Playlist extends MusicParent {
    @Override
    Name.Known name();

    @Override
    List<Song> songs();

    long durationMs();

    CoverCollection covers();
}
