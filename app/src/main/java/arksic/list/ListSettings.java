/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.list;

/** Sort preferences for each list. Defaults match ListSettings.kt. */
public final class ListSettings {
    public volatile Sort songSort = Sort.byNameAscending();
    public volatile Sort albumSort = Sort.byNameAscending();
    public volatile Sort artistSort = Sort.byNameAscending();
    public volatile Sort genreSort = Sort.byNameAscending();
    public volatile Sort playlistSort = Sort.byNameAscending();
    public volatile Sort albumSongSort = Sort.albumSongDefault();
    public volatile Sort artistSongSort = Sort.artistSongDefault();
    public volatile Sort genreSongSort = Sort.genreSongDefault();
}
