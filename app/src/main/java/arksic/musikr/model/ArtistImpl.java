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

import arksic.musikr.graph.MusicGraph.ArtistVertex;
import arksic.musikr.interpret.PreMusic.PreArtist;
import arksic.musikr.tag.Name;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ArtistImpl implements Artist {
    private final ArtistVertex vertex;
    private final PreArtist pre;
    private final Music.UID uid;
    private final Set<Song> songs = new LinkedHashSet<>();
    private final Set<Album> explicitAlbums = new LinkedHashSet<>();
    private final Set<Album> implicitAlbums = new LinkedHashSet<>();
    private final long durationMs;
    private final CoverCollection covers;

    ArtistImpl(ArtistVertex vertex) {
        this.vertex = vertex;
        this.pre = vertex.preArtist;
        this.uid = pre.uid();
        for (var s : vertex.songVertices) songs.add(LibraryImpl.tag(s, Song.class));
        for (var a : vertex.albumVertices) explicitAlbums.add(LibraryImpl.tag(a, Album.class));
        for (Song s : songs) implicitAlbums.add(s.album());
        implicitAlbums.removeAll(explicitAlbums);

        long d = 0;
        List<Cover> songCovers = new ArrayList<>();
        for (Song s : songs) {
            d += s.durationMs();
            if (s.cover() != null) songCovers.add(s.cover());
        }
        if (songCovers.isEmpty()) {
            for (Album a : explicitAlbums) songCovers.addAll(a.covers().covers());
        }
        this.durationMs = d;
        this.covers = CoverCollection.from(songCovers);
    }

    @Override public Music.UID uid() { return uid; }
    @Override public Name name() { return pre.name(); }
    @Override public Collection<Song> songs() { return songs; }
    @Override public Collection<Album> explicitAlbums() { return explicitAlbums; }
    @Override public Collection<Album> implicitAlbums() { return implicitAlbums; }
    @Override public long durationMs() { return durationMs; }
    @Override public CoverCollection covers() { return covers; }

    @Override
    public List<Genre> genres() {
        List<Genre> out = new ArrayList<>();
        for (var v : vertex.genreVertices) out.add(LibraryImpl.tag(v, Genre.class));
        return out;
    }

    @Override public int hashCode() { return 31 * uid.hashCode() + songs.size(); }

    @Override
    public boolean equals(Object o) {
        return o instanceof ArtistImpl a && uid.equals(a.uid) && pre.equals(a.pre) && songs.equals(a.songs);
    }

    @Override public String toString() { return "Artist(uid=" + uid + ", name=" + name() + ")"; }
}
