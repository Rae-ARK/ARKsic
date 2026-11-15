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

import arksic.musikr.graph.MusicGraph.SongVertex;
import arksic.musikr.interpret.PreMusic.PreSong;
import arksic.musikr.tag.Date;
import arksic.musikr.tag.Disc;
import arksic.musikr.tag.Name;
import arksic.musikr.tag.ReplayGainAdjustment;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class SongImpl implements Song {
    private final SongVertex vertex;
    private final PreSong pre;
    private final int hash;

    SongImpl(SongVertex vertex) {
        this.vertex = vertex;
        this.pre = vertex.preSong;
        this.hash = 31 * pre.v363Uid().hashCode() + pre.hashCode();
    }

    Music.UID v400Uid() { return pre.v400Uid(); }
    Music.UID v401Uid() { return pre.v401Uid(); }

    @Override public Music.UID uid() { return pre.v363Uid(); }
    @Override public Name.Known name() { return pre.name(); }
    @Override public Integer track() { return pre.track(); }
    @Override public Disc disc() { return pre.disc(); }
    @Override public Date date() { return pre.date(); }
    @Override public URI uri() { return pre.uri(); }
    @Override public Path path() { return pre.path(); }
    @Override public Format format() { return pre.format(); }
    @Override public long size() { return pre.size(); }
    @Override public long durationMs() { return pre.durationMs(); }
    @Override public int bitrateKbps() { return pre.bitrateKbps(); }
    @Override public int sampleRateHz() { return pre.sampleRateHz(); }
    @Override public ReplayGainAdjustment replayGainAdjustment() { return pre.replayGainAdjustment(); }
    @Override public long modifiedMs() { return pre.modifiedMs(); }
    @Override public long addedMs() { return pre.addedMs(); }
    @Override public Cover cover() { return pre.cover(); }

    @Override
    public Album album() {
        return LibraryImpl.tag(vertex.albumVertex, Album.class);
    }

    @Override
    public List<Artist> artists() {
        List<Artist> out = new ArrayList<>();
        for (var v : vertex.artistVertices) out.add(LibraryImpl.tag(v, Artist.class));
        return out;
    }

    @Override
    public List<Genre> genres() {
        List<Genre> out = new ArrayList<>();
        for (var v : vertex.genreVertices) out.add(LibraryImpl.tag(v, Genre.class));
        return out;
    }

    @Override public int hashCode() { return hash; }

    @Override
    public boolean equals(Object o) {
        return o instanceof SongImpl s && uid().equals(s.uid()) && pre.equals(s.pre);
    }

    @Override public String toString() { return "Song(uid=" + uid() + ", name=" + name() + ")"; }
}
