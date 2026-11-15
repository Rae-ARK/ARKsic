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

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/** An immutable snapshot of the music library. */
public interface Library {
    Collection<Song> songs();

    Collection<Album> albums();

    Collection<Artist> artists();

    Collection<Genre> genres();

    Collection<Playlist> playlists();

    /** Whether this library is empty (no songs, hence no other music item). */
    boolean empty();

    Song findSong(Music.UID uid);

    Song findSongByPath(Path path);

    Album findAlbum(Music.UID uid);

    Artist findArtist(Music.UID uid);

    Genre findGenre(Music.UID uid);

    Playlist findPlaylist(Music.UID uid);

    Playlist findPlaylistByName(String name);

    // Mutation returns a new Library, matching Auxio's MutableLibrary (minus coroutines).

    Library createPlaylist(String name, List<Song> songs);

    Library renamePlaylist(Playlist playlist, String name);

    Library addToPlaylist(Playlist playlist, List<Song> songs);

    Library rewritePlaylist(Playlist playlist, List<Song> songs);

    Library deletePlaylist(Playlist playlist);
}
