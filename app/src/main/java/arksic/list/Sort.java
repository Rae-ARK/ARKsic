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

import arksic.musikr.model.Album;
import arksic.musikr.model.Artist;
import arksic.musikr.model.Genre;
import arksic.musikr.model.Playlist;
import arksic.musikr.model.Song;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * A sort mode plus direction. Port of Auxio's Sort.kt, including its trick of applying several
 * <em>stable</em> sorts in sequence (least-significant key first) to get multi-key ordering.
 *
 * <p>Null handling matches Kotlin: {@code sortBy} puts nulls first, {@code sortByDescending}
 * puts them last.
 */
public record Sort(Mode mode, Direction direction) {
    public enum Direction {
        ASCENDING,
        DESCENDING
    }

    public enum Mode {
        BY_NAME(0xA10C),
        BY_ARTIST(0xA10D),
        BY_ALBUM(0xA10E),
        BY_DATE(0xA10F),
        BY_DURATION(0xA114),
        BY_COUNT(0xA115),
        BY_DISC(0xA116),
        BY_TRACK(0xA117),
        BY_DATE_ADDED(0xA118);

        public final int intCode;

        Mode(int intCode) {
            this.intCode = intCode;
        }

        public static Mode fromIntCode(int code) {
            for (Mode m : values()) if (m.intCode == code) return m;
            return null;
        }

        /** Human label for UI. */
        public String label() {
            return switch (this) {
                case BY_NAME -> "Name";
                case BY_ARTIST -> "Artist";
                case BY_ALBUM -> "Album";
                case BY_DATE -> "Date";
                case BY_DURATION -> "Duration";
                case BY_COUNT -> "Song count";
                case BY_DISC -> "Disc";
                case BY_TRACK -> "Track";
                case BY_DATE_ADDED -> "Date added";
            };
        }
    }

    // ---- defaults, as in ListSettings.kt ----
    public static Sort byNameAscending() { return new Sort(Mode.BY_NAME, Direction.ASCENDING); }
    public static Sort albumSongDefault() { return new Sort(Mode.BY_DISC, Direction.ASCENDING); }
    public static Sort artistSongDefault() { return new Sort(Mode.BY_DATE, Direction.DESCENDING); }
    public static Sort genreSongDefault() { return byNameAscending(); }

    public int intCode() {
        // Formatted as AMMMM: low bit is ascending flag, the rest is the mode's int code.
        return (mode.intCode << 1) | (direction == Direction.ASCENDING ? 1 : 0);
    }

    public static Sort fromIntCode(int intCode) {
        Direction d = (intCode & 1) == 1 ? Direction.ASCENDING : Direction.DESCENDING;
        Mode m = Mode.fromIntCode(intCode >> 1);
        return m == null ? null : new Sort(m, d);
    }

    // ---- kotlin sortBy / sortByDescending equivalents (stable) ----

    private static <T, K extends Comparable<? super K>> void sortBy(List<T> l, Function<T, K> key) {
        l.sort(Comparator.comparing(key, Comparator.nullsFirst(Comparator.<K>naturalOrder())));
    }

    private static <T, K extends Comparable<? super K>> void sortByDesc(List<T> l, Function<T, K> key) {
        l.sort(Comparator.comparing(key, Comparator.nullsFirst(Comparator.<K>naturalOrder())).reversed());
    }

    private <T, K extends Comparable<? super K>> void dir(List<T> l, Function<T, K> key) {
        if (direction == Direction.ASCENDING) sortBy(l, key);
        else sortByDesc(l, key);
    }

    // ---- public sorting API ----

    public List<Song> songs(Collection<Song> songs) {
        List<Song> l = new ArrayList<>(songs);
        switch (mode) {
            case BY_NAME -> dir(l, Song::name);
            case BY_ALBUM -> {
                sortBy(l, Song::name);
                sortBy(l, Song::track);
                sortBy(l, Song::disc);
                dir(l, s -> s.album().name());
            }
            case BY_ARTIST -> {
                sortBy(l, Song::name);
                sortBy(l, Song::track);
                sortBy(l, Song::disc);
                sortBy(l, s -> s.album().name());
                sortByDesc(l, s -> s.album().dates());
                dir(l, s -> s.artists().isEmpty() ? null : s.artists().get(0).name());
            }
            case BY_DATE -> {
                sortBy(l, Song::name);
                sortBy(l, Song::track);
                sortBy(l, Song::disc);
                sortByDesc(l, s -> s.album().name());
                dir(l, s -> s.album().dates());
            }
            case BY_DURATION -> {
                sortBy(l, Song::name);
                dir(l, Song::durationMs);
            }
            case BY_DISC -> {
                sortBy(l, Song::name);
                sortBy(l, Song::track);
                dir(l, Song::disc);
            }
            case BY_TRACK -> {
                sortBy(l, Song::name);
                dir(l, Song::track);
                sortBy(l, Song::disc);
            }
            case BY_DATE_ADDED -> {
                sortBy(l, Song::name);
                dir(l, Song::addedMs);
            }
            case BY_COUNT -> throw new UnsupportedOperationException("Sorting songs is not supported for this mode");
        }
        return l;
    }

    public List<Album> albums(Collection<Album> albums) {
        List<Album> l = new ArrayList<>(albums);
        switch (mode) {
            case BY_NAME -> dir(l, Album::name);
            case BY_ARTIST -> {
                sortBy(l, Album::name);
                sortByDesc(l, Album::dates);
                dir(l, a -> a.artists().isEmpty() ? null : a.artists().get(0).name());
            }
            case BY_DATE -> {
                sortBy(l, Album::name);
                dir(l, Album::dates);
            }
            case BY_DURATION -> {
                sortBy(l, Album::name);
                dir(l, Album::durationMs);
            }
            case BY_COUNT -> {
                sortBy(l, Album::name);
                dir(l, a -> a.songs().size());
            }
            case BY_DATE_ADDED -> {
                sortBy(l, Album::name);
                dir(l, Album::addedMs);
            }
            default -> throw new UnsupportedOperationException("Sorting albums is not supported for this mode");
        }
        return l;
    }

    public List<Artist> artists(Collection<Artist> artists) {
        List<Artist> l = new ArrayList<>(artists);
        switch (mode) {
            case BY_NAME -> dir(l, Artist::name);
            case BY_DURATION -> {
                sortBy(l, Artist::name);
                dir(l, Artist::durationMs);
            }
            case BY_COUNT -> {
                sortBy(l, Artist::name);
                dir(l, a -> a.songs().size());
            }
            default -> throw new UnsupportedOperationException("Sorting artists is not supported for this mode");
        }
        return l;
    }

    public List<Genre> genres(Collection<Genre> genres) {
        List<Genre> l = new ArrayList<>(genres);
        switch (mode) {
            case BY_NAME -> dir(l, Genre::name);
            case BY_DURATION -> {
                sortBy(l, Genre::name);
                dir(l, Genre::durationMs);
            }
            case BY_COUNT -> {
                sortBy(l, Genre::name);
                dir(l, g -> g.songs().size());
            }
            default -> throw new UnsupportedOperationException("Sorting genres is not supported for this mode");
        }
        return l;
    }

    public List<Playlist> playlists(Collection<Playlist> playlists) {
        List<Playlist> l = new ArrayList<>(playlists);
        switch (mode) {
            case BY_NAME -> dir(l, Playlist::name);
            case BY_DURATION -> {
                sortBy(l, Playlist::name);
                dir(l, Playlist::durationMs);
            }
            case BY_COUNT -> {
                sortBy(l, Playlist::name);
                dir(l, p -> p.songs().size());
            }
            default -> throw new UnsupportedOperationException("Sorting playlists is not supported for this mode");
        }
        return l;
    }
}
