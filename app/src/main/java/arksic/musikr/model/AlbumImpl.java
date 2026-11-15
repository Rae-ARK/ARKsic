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

import arksic.musikr.graph.MusicGraph.AlbumVertex;
import arksic.musikr.interpret.PreMusic.PreAlbum;
import arksic.musikr.tag.Date;
import arksic.musikr.tag.Name;
import arksic.musikr.tag.ReleaseType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class AlbumImpl implements Album {
    private final AlbumVertex vertex;
    private final PreAlbum pre;
    private final Music.UID uid;
    private final Set<Song> songs = new LinkedHashSet<>();
    private final long durationMs;
    private final long addedMs;
    private final CoverCollection covers;
    private final Date.Range dates;

    AlbumImpl(AlbumVertex vertex) {
        this.vertex = vertex;
        this.pre = vertex.preAlbum;
        this.uid = pre.uid();
        for (var s : vertex.songVertices) songs.add(LibraryImpl.tag(s, Song.class));
        long d = 0;
        long added = Long.MAX_VALUE;
        Date min = null;
        Date max = null;
        List<Cover> covers = new ArrayList<>();
        for (Song s : songs) {
            d += s.durationMs();
            added = Math.min(added, s.addedMs());
            if (s.cover() != null) covers.add(s.cover());
            Date date = s.date();
            if (date != null) {
                if (min == null || date.compareTo(min) < 0) min = date;
                if (max == null || date.compareTo(max) > 0) max = date;
            }
        }
        this.durationMs = d;
        this.addedMs = added;
        this.covers = CoverCollection.from(covers);
        this.dates = min != null ? new Date.Range(min, max) : null;
    }

    @Override public Music.UID uid() { return uid; }
    @Override public Name name() { return pre.name(); }
    @Override public ReleaseType releaseType() { return pre.releaseType(); }
    @Override public Date.Range dates() { return dates; }
    @Override public long durationMs() { return durationMs; }
    @Override public long addedMs() { return addedMs; }
    @Override public CoverCollection covers() { return covers; }
    @Override public Collection<Song> songs() { return songs; }

    @Override
    public List<Artist> artists() {
        List<Artist> out = new ArrayList<>();
        for (var v : vertex.artistVertices) out.add(LibraryImpl.tag(v, Artist.class));
        return out;
    }

    @Override public int hashCode() { return 31 * uid.hashCode() + songs.size(); }

    @Override
    public boolean equals(Object o) {
        return o instanceof AlbumImpl a && uid.equals(a.uid) && pre.equals(a.pre) && songs.equals(a.songs);
    }

    @Override public String toString() { return "Album(uid=" + uid + ", name=" + name() + ")"; }
}
