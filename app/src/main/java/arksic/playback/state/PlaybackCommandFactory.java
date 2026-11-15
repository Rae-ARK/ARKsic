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

import arksic.list.ListSettings;
import arksic.list.Sort;
import arksic.musikr.MusicRepository;
import arksic.musikr.model.Album;
import arksic.musikr.model.Artist;
import arksic.musikr.model.Genre;
import arksic.musikr.model.Library;
import arksic.musikr.model.MusicParent;
import arksic.musikr.model.Playlist;
import arksic.musikr.model.Song;
import arksic.playback.PlaybackSettings;
import java.util.Collection;
import java.util.List;

/** Builds {@link PlaybackCommand}s from UI intents. Port of PlaybackCommandFactoryImpl. */
public final class PlaybackCommandFactory {
    private final PlaybackStateManager manager;
    private final PlaybackSettings playbackSettings;
    private final ListSettings listSettings;
    private final MusicRepository repository;

    public PlaybackCommandFactory(
            PlaybackStateManager manager, PlaybackSettings playbackSettings, ListSettings listSettings,
            MusicRepository repository) {
        this.manager = manager;
        this.playbackSettings = playbackSettings;
        this.listSettings = listSettings;
        this.repository = repository;
    }

    public PlaybackCommand song(Song song, ShuffleMode shuffle) {
        return make(song, null, List.of(song), shuffle);
    }

    public PlaybackCommand songFromAll(Song song, ShuffleMode shuffle) {
        return fromAll(song, shuffle);
    }

    public PlaybackCommand songFromAlbum(Song song, ShuffleMode shuffle) {
        return fromParent(song, song.album(), listSettings.albumSongSort, shuffle);
    }

    /** From an artist; if the artist is unspecified, only unambiguous when the song has one. */
    public PlaybackCommand songFromArtist(Song song, Artist artist, ShuffleMode shuffle) {
        if (artist != null) return fromParent(song, artist, listSettings.artistSongSort, shuffle);
        if (song.artists().size() == 1) return fromParent(song, song.artists().get(0), listSettings.artistSongSort, shuffle);
        return null;
    }

    public PlaybackCommand songFromGenre(Song song, Genre genre, ShuffleMode shuffle) {
        if (genre != null) return fromParent(song, genre, listSettings.genreSongSort, shuffle);
        if (song.genres().size() == 1) return fromParent(song, song.genres().get(0), listSettings.genreSongSort, shuffle);
        return null;
    }

    public PlaybackCommand songFromPlaylist(Song song, Playlist playlist, ShuffleMode shuffle) {
        return make(song, playlist, playlist.songs(), shuffle, null);
    }

    public PlaybackCommand all(ShuffleMode shuffle) {
        return fromAll(null, shuffle);
    }

    public PlaybackCommand songs(List<Song> songs, ShuffleMode shuffle) {
        return make(null, null, songs, shuffle);
    }

    public PlaybackCommand album(Album album, ShuffleMode shuffle) {
        return fromParent(null, album, listSettings.albumSongSort, shuffle);
    }

    public PlaybackCommand artist(Artist artist, ShuffleMode shuffle) {
        return fromParent(null, artist, listSettings.artistSongSort, shuffle);
    }

    public PlaybackCommand genre(Genre genre, ShuffleMode shuffle) {
        return fromParent(null, genre, listSettings.genreSongSort, shuffle);
    }

    public PlaybackCommand playlist(Playlist playlist, ShuffleMode shuffle) {
        return make(null, playlist, playlist.songs(), shuffle, null);
    }

    private PlaybackCommand fromAll(Song song, ShuffleMode shuffle) {
        Library library = repository.library();
        if (library == null) return null;
        return make(song, null, library.songs(), shuffle, listSettings.songSort);
    }

    private PlaybackCommand fromParent(Song song, MusicParent parent, Sort sort, ShuffleMode shuffle) {
        return make(song, parent, parent.songs(), shuffle, sort);
    }

    private PlaybackCommand make(Song song, MusicParent parent, Collection<Song> queue, ShuffleMode shuffle) {
        return make(song, parent, queue, shuffle, null);
    }

    private PlaybackCommand make(
            Song song, MusicParent parent, Collection<Song> queue, ShuffleMode shuffle, Sort sort) {
        if (queue.isEmpty() || (song != null && !queue.contains(song))) return null;
        List<Song> ordered = sort != null ? sort.songs(queue) : List.copyOf(queue);
        return new PlaybackCommand.Impl(song, parent, ordered, isShuffled(shuffle));
    }

    private boolean isShuffled(ShuffleMode shuffle) {
        return switch (shuffle) {
            case ON -> true;
            case OFF -> false;
            case IMPLICIT -> playbackSettings.keepShuffle && manager.isShuffled();
        };
    }
}
