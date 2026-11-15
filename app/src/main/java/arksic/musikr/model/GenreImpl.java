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

import arksic.musikr.graph.MusicGraph.GenreVertex;
import arksic.musikr.interpret.PreMusic.PreGenre;
import arksic.musikr.tag.Name;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class GenreImpl implements Genre {
    private final PreGenre pre;
    private final Music.UID uid;
    private final Set<Song> songs = new LinkedHashSet<>();
    private final Set<Artist> artists = new LinkedHashSet<>();
    private final long durationMs;
    private final CoverCollection covers;

    GenreImpl(GenreVertex vertex) {
        this.pre = vertex.preGenre;
        this.uid = Music.UID.auxio(Music.UID.Item.GENRE, h -> h.update(pre.rawName()));
        for (var s : vertex.songVertices) songs.add(LibraryImpl.tag(s, Song.class));
        for (var a : vertex.artistVertices) artists.add(LibraryImpl.tag(a, Artist.class));
        long d = 0;
        List<Cover> cs = new ArrayList<>();
        for (Song s : songs) {
            d += s.durationMs();
            if (s.cover() != null) cs.add(s.cover());
        }
        this.durationMs = d;
        this.covers = CoverCollection.from(cs);
    }

    @Override public Music.UID uid() { return uid; }
    @Override public Name name() { return pre.name(); }
    @Override public Collection<Song> songs() { return songs; }
    @Override public Collection<Artist> artists() { return artists; }
    @Override public long durationMs() { return durationMs; }
    @Override public CoverCollection covers() { return covers; }

    @Override public int hashCode() { return 31 * uid.hashCode() + songs.size(); }

    @Override
    public boolean equals(Object o) {
        return o instanceof GenreImpl g && uid.equals(g.uid) && pre.equals(g.pre) && songs.equals(g.songs);
    }

    @Override public String toString() { return "Genre(uid=" + uid + ", name=" + name() + ")"; }
}
