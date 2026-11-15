/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.musikr.graph;

import arksic.musikr.interpret.PreMusic.PreAlbum;
import arksic.musikr.interpret.PreMusic.PreArtist;
import arksic.musikr.interpret.PreMusic.PreArtistsFrom;
import arksic.musikr.interpret.PreMusic.PreGenre;
import arksic.musikr.interpret.PreMusic.PreSong;
import arksic.musikr.interpret.PrePlaylist;
import arksic.musikr.model.Music;
import arksic.musikr.playlist.SongPointer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The relationship graph between songs, albums, artists, genres and playlists. This is the heart
 * of musikr: songs go in as flat pre-songs, and clustering/"melding" resolves tag inconsistencies
 * (differently-cased names, partial MusicBrainz coverage, ...) into a coherent library.
 */
public final class MusicGraph {
    public final List<SongVertex> songVertex;
    public final List<AlbumVertex> albumVertex;
    public final List<ArtistVertex> artistVertex;
    public final List<GenreVertex> genreVertex;
    public final Set<PlaylistVertex> playlistVertex;

    private MusicGraph(
            List<SongVertex> songVertex,
            List<AlbumVertex> albumVertex,
            List<ArtistVertex> artistVertex,
            List<GenreVertex> genreVertex,
            Set<PlaylistVertex> playlistVertex) {
        this.songVertex = songVertex;
        this.albumVertex = albumVertex;
        this.artistVertex = artistVertex;
        this.genreVertex = genreVertex;
        this.playlistVertex = playlistVertex;
    }

    public static Builder builder() {
        return new Builder();
    }

    // --- vertices ---

    public interface Vertex {
        Object tag();

        void tag(Object tag);
    }

    public static final class SongVertex implements Vertex {
        public final PreSong preSong;
        public AlbumVertex albumVertex;
        public List<ArtistVertex> artistVertices;
        public List<GenreVertex> genreVertices;
        private Object tag;

        SongVertex(PreSong preSong, AlbumVertex album, List<ArtistVertex> artists, List<GenreVertex> genres) {
            this.preSong = preSong;
            this.albumVertex = album;
            this.artistVertices = artists;
            this.genreVertices = genres;
        }

        @Override public Object tag() { return tag; }
        @Override public void tag(Object tag) { this.tag = tag; }
        @Override public String toString() { return "SongVertex(" + preSong.rawName() + ")"; }
    }

    public static final class AlbumVertex implements Vertex {
        public final PreAlbum preAlbum;
        public List<ArtistVertex> artistVertices;
        public final Set<SongVertex> songVertices = new LinkedHashSet<>();
        private Object tag;

        AlbumVertex(PreAlbum preAlbum, List<ArtistVertex> artists) {
            this.preAlbum = preAlbum;
            this.artistVertices = artists;
        }

        @Override public Object tag() { return tag; }
        @Override public void tag(Object tag) { this.tag = tag; }
        @Override public String toString() { return "AlbumVertex(" + preAlbum.rawName() + ")"; }
    }

    public static final class ArtistVertex implements Vertex {
        public final PreArtist preArtist;
        public final Set<SongVertex> songVertices = new LinkedHashSet<>();
        public final Set<AlbumVertex> albumVertices = new LinkedHashSet<>();
        public final Set<GenreVertex> genreVertices = new LinkedHashSet<>();
        private Object tag;

        ArtistVertex(PreArtist preArtist) {
            this.preArtist = preArtist;
        }

        @Override public Object tag() { return tag; }
        @Override public void tag(Object tag) { this.tag = tag; }
        @Override public String toString() { return "ArtistVertex(" + preArtist.rawName() + ")"; }
    }

    public static final class GenreVertex implements Vertex {
        public final PreGenre preGenre;
        public final Set<SongVertex> songVertices = new LinkedHashSet<>();
        public final Set<ArtistVertex> artistVertices = new LinkedHashSet<>();
        private Object tag;

        GenreVertex(PreGenre preGenre) {
            this.preGenre = preGenre;
        }

        @Override public Object tag() { return tag; }
        @Override public void tag(Object tag) { this.tag = tag; }
        @Override public String toString() { return "GenreVertex(" + preGenre.rawName() + ")"; }
    }

    public static final class PlaylistVertex {
        public final PrePlaylist.Pre prePlaylist;
        public final SongVertex[] songVertices;
        public final Map<SongPointer, List<Integer>> pointerMap = new LinkedHashMap<>();

        PlaylistVertex(PrePlaylist.Pre prePlaylist) {
            this.prePlaylist = prePlaylist;
            this.songVertices = new SongVertex[prePlaylist.songPointers().size()];
            for (int i = 0; i < songVertices.length; i++) {
                pointerMap.computeIfAbsent(prePlaylist.songPointers().get(i), k -> new ArrayList<>()).add(i);
            }
        }
    }

    // --- builder ---

    public static final class Builder {
        private final Map<Music.UID, SongVertex> songVertices = new LinkedHashMap<>();
        private final Map<PreAlbum, AlbumVertex> albumVertices = new LinkedHashMap<>();
        private final Map<PreArtist, ArtistVertex> artistVertices = new LinkedHashMap<>();
        private final Map<PreGenre, GenreVertex> genreVertices = new LinkedHashMap<>();
        private final Set<PlaylistVertex> playlistVertices = new LinkedHashSet<>();

        // Kotlin's getOrPut (get, then put). computeIfAbsent would break on re-entrant use.
        private static <K, V> V getOrPut(Map<K, V> map, K key, Supplier<V> make) {
            V v = map.get(key);
            if (v == null) {
                v = make.get();
                map.put(key, v);
            }
            return v;
        }

        private static <T> T maxBySongs(Collection<T> c, java.util.function.ToIntFunction<T> size) {
            T best = null;
            int bestSize = Integer.MIN_VALUE;
            for (T t : c) {
                int s = size.applyAsInt(t);
                if (s > bestSize) {
                    best = t;
                    bestSize = s;
                }
            }
            return best;
        }

        public void add(PreSong preSong) {
            Music.UID uid = preSong.v363Uid();
            if (songVertices.containsKey(uid)) return;

            List<GenreVertex> songGenreVertices = new ArrayList<>();
            for (PreGenre g : preSong.preGenres()) {
                songGenreVertices.add(getOrPut(genreVertices, g, () -> new GenreVertex(g)));
            }

            List<ArtistVertex> songArtistVertices = new ArrayList<>();
            for (PreArtist a : preSong.preArtists()) {
                songArtistVertices.add(getOrPut(artistVertices, a, () -> new ArtistVertex(a)));
            }

            AlbumVertex albumVertex = getOrPut(albumVertices, preSong.preAlbum(), () -> {
                // Albums have their own parent artists that also need linking.
                List<ArtistVertex> albumArtistVertices = new ArrayList<>();
                for (PreArtist a : preSong.preAlbum().preArtists().preArtists()) {
                    albumArtistVertices.add(getOrPut(artistVertices, a, () -> new ArtistVertex(a)));
                }
                AlbumVertex v = new AlbumVertex(preSong.preAlbum(), new ArrayList<>(albumArtistVertices));
                for (ArtistVertex av : albumArtistVertices) av.albumVertices.add(v);
                return v;
            });

            SongVertex songVertex =
                    new SongVertex(preSong, albumVertex, new ArrayList<>(songArtistVertices), new ArrayList<>(songGenreVertices));
            albumVertex.songVertices.add(songVertex);

            for (ArtistVertex av : songArtistVertices) {
                av.songVertices.add(songVertex);
                for (GenreVertex gv : songGenreVertices) {
                    // Mutually link any new genres to the artist.
                    av.genreVertices.add(gv);
                    gv.artistVertices.add(av);
                }
            }
            for (GenreVertex gv : songGenreVertices) gv.songVertices.add(songVertex);

            songVertices.put(uid, songVertex);
        }

        public void add(PrePlaylist.Pre prePlaylist) {
            playlistVertices.add(new PlaylistVertex(prePlaylist));
        }

        public MusicGraph build() {
            // Genres: cluster case-insensitively by raw name.
            for (List<GenreVertex> cluster : groupBy(genreVertices.values(), g -> lower(g.preGenre.rawName())).values()) {
                simplifyGenreCluster(cluster);
            }

            // Artists, first pass: cluster by name and process MBIDs where valid.
            for (List<ArtistVertex> cluster : groupBy(artistVertices.values(), a -> lower(a.preArtist.rawName())).values()) {
                simplifyArtistCluster(cluster);
            }
            // Second pass: meld vertices with identical UIDs to prevent broken artist objects.
            for (List<ArtistVertex> cluster : groupBy(artistVertices.values(), a -> a.preArtist.uid()).values()) {
                PreArtist canon = maxBySongs(cluster, v -> v.songVertices.size()).preArtist;
                boolean same = cluster.stream().allMatch(v -> v.preArtist.equals(canon));
                if (!same) {
                    // Invalid MBID setup: strip MBIDs from all artists in the cluster.
                    ArtistVertex simple = getOrPut(artistVertices, canon, () -> new ArtistVertex(canon));
                    for (ArtistVertex a : cluster) meldArtistVertices(a, simple);
                }
            }

            // Albums, first pass.
            for (List<AlbumVertex> cluster : groupBy(albumVertices.values(), a -> lower(a.preAlbum.rawName())).values()) {
                simplifyAlbumCluster(cluster);
            }
            // Second pass: identical UIDs.
            for (List<AlbumVertex> cluster : groupBy(albumVertices.values(), a -> a.preAlbum.uid()).values()) {
                PreAlbum canon = maxBySongs(cluster, v -> v.songVertices.size()).preAlbum;
                boolean same = cluster.stream().allMatch(v -> v.preAlbum.equals(canon));
                if (!same) {
                    AlbumVertex simple = getOrPut(albumVertices, canon, () -> new AlbumVertex(canon, new ArrayList<>()));
                    for (AlbumVertex a : cluster) meldAlbumVertices(a, simple);
                }
            }

            // Remove edges that wound up connecting to the same artist after simplification.
            for (AlbumVertex a : albumVertices.values()) a.artistVertices = distinct(a.artistVertices);

            for (Map.Entry<Music.UID, SongVertex> entry : songVertices.entrySet()) {
                SongVertex vertex = entry.getValue();
                vertex.artistVertices = distinct(vertex.artistVertices);
                vertex.genreVertices = distinct(vertex.genreVertices);

                for (PlaylistVertex pv : playlistVertices) {
                    for (Music.UID uid : List.of(entry.getKey(), vertex.preSong.v400Uid(), vertex.preSong.v401Uid())) {
                        List<Integer> indices = pv.pointerMap.get(new SongPointer(uid));
                        if (indices != null) for (int i : indices) pv.songVertices[i] = vertex;
                    }
                }
            }

            return new MusicGraph(
                    new ArrayList<>(songVertices.values()),
                    new ArrayList<>(albumVertices.values()),
                    new ArrayList<>(artistVertices.values()),
                    new ArrayList<>(genreVertices.values()),
                    playlistVertices);
        }

        private static String lower(String s) {
            return s == null ? null : s.toLowerCase();
        }

        private static <K, T> Map<K, List<T>> groupBy(Collection<T> items, java.util.function.Function<T, K> key) {
            Map<K, List<T>> out = new LinkedHashMap<>();
            for (T t : new ArrayList<>(items)) out.computeIfAbsent(key.apply(t), k -> new ArrayList<>()).add(t);
            return out;
        }

        private static <T> List<T> distinct(List<T> l) {
            return new ArrayList<>(new LinkedHashSet<>(l));
        }

        // --- genres ---

        private void simplifyGenreCluster(Collection<GenreVertex> cluster) {
            if (cluster.size() == 1) return;
            // All semantically equivalent: pick the most popular variation, meld the rest into it.
            Set<GenreVertex> set = new LinkedHashSet<>(cluster);
            GenreVertex dst = maxBySongs(set, v -> v.songVertices.size());
            set.remove(dst);
            for (GenreVertex src : set) meldGenreVertices(src, dst);
        }

        private void meldGenreVertices(GenreVertex src, GenreVertex dst) {
            if (src == dst) return;
            dst.songVertices.addAll(src.songVertices);
            dst.artistVertices.addAll(src.artistVertices);
            for (SongVertex s : src.songVertices) {
                int index = s.genreVertices.indexOf(src);
                if (index < 0) throw new IllegalStateException("Illegal state: directed edge between genre and song");
                s.genreVertices.set(index, dst);
            }
            for (ArtistVertex a : src.artistVertices) {
                a.genreVertices.remove(src);
                a.genreVertices.add(dst);
            }
            genreVertices.remove(src.preGenre);
        }

        // --- artists ---

        private void simplifyArtistCluster(List<ArtistVertex> cluster) {
            if (cluster.size() == 1) return;
            boolean fullMbid = cluster.stream().allMatch(v -> v.preArtist.musicBrainzId() != null);
            if (fullMbid) {
                for (List<ArtistVertex> c : groupBy(cluster, v -> v.preArtist.musicBrainzId()).values()) {
                    simplifyArtistClusterImpl(c);
                }
                return;
            }
            // No full MBID coverage: discard the MBIDs from the graph.
            List<ArtistVertex> stripped = new ArrayList<>();
            for (ArtistVertex v : cluster) {
                PreArtist noMbid = v.preArtist.withMusicBrainzId(null);
                ArtistVertex simple = getOrPut(artistVertices, noMbid, () -> new ArtistVertex(noMbid));
                meldArtistVertices(v, simple);
                stripped.add(simple);
            }
            simplifyArtistClusterImpl(stripped);
        }

        private void simplifyArtistClusterImpl(Collection<ArtistVertex> cluster) {
            if (cluster.size() == 1) return;
            Set<ArtistVertex> set = new LinkedHashSet<>(cluster);
            ArtistVertex dst = maxBySongs(set, v -> v.songVertices.size());
            set.remove(dst);
            for (ArtistVertex src : set) meldArtistVertices(src, dst);
        }

        private void meldArtistVertices(ArtistVertex src, ArtistVertex dst) {
            if (src == dst) return;
            dst.songVertices.addAll(src.songVertices);
            dst.albumVertices.addAll(src.albumVertices);
            dst.genreVertices.addAll(src.genreVertices);
            // Duplicate artist vertices can exist per song/album; replace every occurrence.
            for (SongVertex s : src.songVertices) {
                for (int i = 0; i < s.artistVertices.size(); i++) {
                    if (s.artistVertices.get(i) == src) s.artistVertices.set(i, dst);
                }
            }
            for (AlbumVertex a : src.albumVertices) {
                for (int i = 0; i < a.artistVertices.size(); i++) {
                    if (a.artistVertices.get(i) == src) a.artistVertices.set(i, dst);
                }
            }
            for (GenreVertex g : src.genreVertices) {
                g.artistVertices.remove(src);
                g.artistVertices.add(dst);
            }
            artistVertices.remove(src.preArtist);
        }

        // --- albums ---

        private void simplifyAlbumCluster(List<AlbumVertex> cluster) {
            if (cluster.size() == 1) return;
            boolean fullMbid = cluster.stream().allMatch(v -> v.preAlbum.musicBrainzId() != null);
            if (fullMbid) {
                for (List<AlbumVertex> c : groupBy(cluster, v -> v.preAlbum.musicBrainzId()).values()) {
                    simplifyAlbumClusterImpl(c);
                }
                return;
            }
            // No full MBID coverage: discard MBIDs.
            List<AlbumVertex> strippedMbid = new ArrayList<>();
            for (AlbumVertex v : cluster) {
                PreAlbum noMbid = v.preAlbum.withMusicBrainzId(null);
                AlbumVertex simple = getOrPut(albumVertices, noMbid, () -> new AlbumVertex(noMbid, new ArrayList<>(v.artistVertices)));
                meldAlbumVertices(v, simple);
                strippedMbid.add(simple);
            }
            boolean fullAlbumArtist = strippedMbid.stream().allMatch(v -> v.preAlbum.preArtists() instanceof PreArtistsFrom.Album);
            if (fullAlbumArtist) {
                // All albums have album artists: cluster around artists, not just the name.
                for (List<AlbumVertex> c : groupBy(strippedMbid, v -> v.preAlbum.preArtists().preArtists()).values()) {
                    simplifyAlbumClusterImpl(c);
                }
                return;
            }
            List<AlbumVertex> strippedAlbumArtist = new ArrayList<>();
            for (AlbumVertex v : strippedMbid) {
                PreAlbum noAa = v.preAlbum.withPreArtists(new PreArtistsFrom.Individual(v.preAlbum.preArtists().preArtists()));
                AlbumVertex simple = getOrPut(albumVertices, noAa, () -> new AlbumVertex(noAa, new ArrayList<>(v.artistVertices)));
                meldAlbumVertices(v, simple);
                strippedAlbumArtist.add(simple);
            }
            simplifyAlbumClusterImpl(strippedAlbumArtist);
        }

        private void simplifyAlbumClusterImpl(Collection<AlbumVertex> cluster) {
            if (cluster.size() == 1) return;
            Set<AlbumVertex> set = new LinkedHashSet<>(cluster);
            AlbumVertex dst = maxBySongs(set, v -> v.songVertices.size());
            set.remove(dst);
            for (AlbumVertex src : set) meldAlbumVertices(src, dst);
        }

        private void meldAlbumVertices(AlbumVertex src, AlbumVertex dst) {
            if (src == dst) return;
            dst.songVertices.addAll(src.songVertices);
            dst.artistVertices.addAll(src.artistVertices);
            for (SongVertex s : src.songVertices) s.albumVertex = dst;
            for (ArtistVertex a : src.artistVertices) {
                a.albumVertices.remove(src);
                a.albumVertices.add(dst);
            }
            albumVertices.remove(src.preAlbum);
        }
    }
}
