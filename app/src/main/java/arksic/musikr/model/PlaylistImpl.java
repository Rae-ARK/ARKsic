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

import arksic.musikr.interpret.PrePlaylist;
import arksic.musikr.tag.Name;
import java.util.ArrayList;
import java.util.List;

final class PlaylistImpl implements Playlist {
    final PrePlaylist.Info info;
    private final List<Song> songs;
    private final long durationMs;
    private final CoverCollection covers;

    PlaylistImpl(PrePlaylist.Info info, List<Song> songs) {
        this.info = info;
        this.songs = List.copyOf(songs);
        long d = 0;
        List<Cover> cs = new ArrayList<>();
        for (Song s : songs) {
            d += s.durationMs();
            if (s.cover() != null) cs.add(s.cover());
        }
        this.durationMs = d;
        this.covers = CoverCollection.from(cs);
    }

    @Override public Music.UID uid() { return info.handle().uid(); }
    @Override public Name.Known name() { return info.name(); }
    @Override public List<Song> songs() { return songs; }
    @Override public long durationMs() { return durationMs; }
    @Override public CoverCollection covers() { return covers; }

    @Override
    public boolean equals(Object o) {
        return o instanceof PlaylistImpl p && uid().equals(p.uid()) && info.equals(p.info) && songs.equals(p.songs);
    }

    @Override public int hashCode() { return 31 * uid().hashCode() + songs.hashCode(); }

    @Override public String toString() { return "Playlist(uid=" + uid() + ", name=" + name() + ")"; }
}
