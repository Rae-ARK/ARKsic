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

import arksic.musikr.graph.MusicGraph;
import arksic.musikr.interpret.Naming;
import arksic.musikr.interpret.PrePlaylist;
import arksic.musikr.playlist.StoredPlaylists;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The concrete library; port of LibraryImpl.kt and LibraryFactory.kt. */
public final class LibraryImpl implements Library {
    private final Set<SongImpl> songs;
    private final Set<AlbumImpl> albums;
    private final Set<ArtistImpl> artists;
    private final Set<GenreImpl> genres;
    private final Set<PlaylistImpl> playlists;
    private final StoredPlaylists stored;
    private final Naming naming;

    private final Map<Music.UID, SongImpl> songUidMap = new LinkedHashMap<>();
    private final Map<Music.UID, SongImpl> v400 = new LinkedHashMap<>();
    private final Map<Music.UID, SongImpl> v401 = new LinkedHashMap<>();
    private final Map<Music.UID, AlbumImpl> albumUidMap = new LinkedHashMap<>();
    private final Map<Music.UID, ArtistImpl> artistUidMap = new LinkedHashMap<>();
    private final Map<Music.UID, GenreImpl> genreUidMap = new LinkedHashMap<>();
    private final Map<Music.UID, PlaylistImpl> playlistUidMap = new LinkedHashMap<>();

    private LibraryImpl(
            Set<SongImpl> songs, Set<AlbumImpl> albums, Set<ArtistImpl> artists, Set<GenreImpl> genres,
            Set<PlaylistImpl> playlists, StoredPlaylists stored, Naming naming) {
        this.songs = songs;
        this.albums = albums;
        this.artists = artists;
        this.genres = genres;
        this.playlists = playlists;
        this.stored = stored;
        this.naming = naming;
        for (SongImpl s : songs) {
            songUidMap.put(s.uid(), s);
            v400.put(s.v400Uid(), s);
            v401.put(s.v401Uid(), s);
        }
        for (AlbumImpl a : albums) albumUidMap.put(a.uid(), a);
        for (ArtistImpl a : artists) artistUidMap.put(a.uid(), a);
        for (GenreImpl g : genres) genreUidMap.put(g.uid(), g);
        for (PlaylistImpl p : playlists) playlistUidMap.put(p.uid(), p);
    }

    /** Resolve a vertex's tag (the model object built for it) with a sanity check. */
    static <T> T tag(MusicGraph.Vertex vertex, Class<T> type) {
        Object tag = vertex.tag();
        if (!type.isInstance(tag)) throw new IllegalStateException("Dead Vertex Detected: " + vertex);
        return type.cast(tag);
    }

    /** Build a library from a finished graph. Order matters: later objects resolve earlier ones. */
    public static Library create(MusicGraph graph, StoredPlaylists stored, Naming naming) {
        Set<SongImpl> songs = new LinkedHashSet<>();
        for (var v : graph.songVertex) {
            SongImpl s = new SongImpl(v);
            v.tag(s);
            songs.add(s);
        }
        Set<AlbumImpl> albums = new LinkedHashSet<>();
        for (var v : graph.albumVertex) {
            AlbumImpl a = new AlbumImpl(v);
            v.tag(a);
            albums.add(a);
        }
        Set<ArtistImpl> artists = new LinkedHashSet<>();
        for (var v : graph.artistVertex) {
            ArtistImpl a = new ArtistImpl(v);
            v.tag(a);
            artists.add(a);
        }
        Set<GenreImpl> genres = new LinkedHashSet<>();
        for (var v : graph.genreVertex) {
            GenreImpl g = new GenreImpl(v);
            v.tag(g);
            genres.add(g);
        }
        Set<PlaylistImpl> playlists = new LinkedHashSet<>();
        for (var v : graph.playlistVertex) {
            List<Song> ps = new ArrayList<>();
            for (var sv : v.songVertices) if (sv != null) ps.add(tag(sv, Song.class));
            playlists.add(new PlaylistImpl(v.prePlaylist, ps));
        }
        return new LibraryImpl(songs, albums, artists, genres, playlists, stored, naming);
    }

    @Override public Collection<Song> songs() { return List.copyOf(songs); }
    @Override public Collection<Album> albums() { return List.copyOf(albums); }
    @Override public Collection<Artist> artists() { return List.copyOf(artists); }
    @Override public Collection<Genre> genres() { return List.copyOf(genres); }
    @Override public Collection<Playlist> playlists() { return List.copyOf(playlists); }
    @Override public boolean empty() { return songs.isEmpty(); }

    @Override
    public Song findSong(Music.UID uid) {
        SongImpl s = songUidMap.get(uid);
        if (s == null) s = v400.get(uid); // compat: see TagInterpreter for why
        if (s == null) s = v401.get(uid);
        return s;
    }

    @Override
    public Song findSongByPath(Path path) {
        for (SongImpl s : songs) if (s.path().equals(path)) return s;
        return null;
    }

    @Override public Album findAlbum(Music.UID uid) { return albumUidMap.get(uid); }
    @Override public Artist findArtist(Music.UID uid) { return artistUidMap.get(uid); }
    @Override public Genre findGenre(Music.UID uid) { return genreUidMap.get(uid); }
    @Override public Playlist findPlaylist(Music.UID uid) { return playlistUidMap.get(uid); }

    @Override
    public Playlist findPlaylistByName(String name) {
        for (PlaylistImpl p : playlists) if (p.name().raw().equals(name)) return p;
        return null;
    }

    private LibraryImpl withPlaylists(Set<PlaylistImpl> p) {
        return new LibraryImpl(songs, albums, artists, genres, p, stored, naming);
    }

    private PlaylistImpl require(Playlist playlist, String what) {
        PlaylistImpl impl = playlistUidMap.get(playlist.uid());
        if (impl == null) throw new IllegalArgumentException("Playlist to " + what + " is not in this library");
        return impl;
    }

    @Override
    public Library createPlaylist(String name, List<Song> songs) {
        var handle = stored.create(name, songs);
        var post = PrePlaylist.interpret(naming, name, handle);
        Set<PlaylistImpl> next = new LinkedHashSet<>(playlists);
        next.add(new PlaylistImpl(post, songs));
        return withPlaylists(next);
    }

    @Override
    public Library renamePlaylist(Playlist playlist, String name) {
        PlaylistImpl impl = require(playlist, "rename");
        impl.info.handle().rename(name);
        var post = PrePlaylist.interpret(naming, name, impl.info.handle());
        Set<PlaylistImpl> next = new LinkedHashSet<>(playlists);
        next.remove(impl);
        next.add(new PlaylistImpl(post, playlist.songs()));
        return withPlaylists(next);
    }

    @Override
    public Library addToPlaylist(Playlist playlist, List<Song> songs) {
        PlaylistImpl impl = require(playlist, "add to");
        impl.info.handle().add(songs);
        List<Song> all = new ArrayList<>(impl.songs());
        all.addAll(songs);
        Set<PlaylistImpl> next = new LinkedHashSet<>(playlists);
        next.remove(impl);
        next.add(new PlaylistImpl(impl.info, all));
        return withPlaylists(next);
    }

    @Override
    public Library rewritePlaylist(Playlist playlist, List<Song> songs) {
        PlaylistImpl impl = require(playlist, "rewrite");
        impl.info.handle().rewrite(songs);
        Set<PlaylistImpl> next = new LinkedHashSet<>(playlists);
        next.remove(impl);
        next.add(new PlaylistImpl(impl.info, songs));
        return withPlaylists(next);
    }

    @Override
    public Library deletePlaylist(Playlist playlist) {
        PlaylistImpl impl = require(playlist, "delete");
        impl.info.handle().delete();
        Set<PlaylistImpl> next = new LinkedHashSet<>(playlists);
        next.remove(impl);
        return withPlaylists(next);
    }
}
