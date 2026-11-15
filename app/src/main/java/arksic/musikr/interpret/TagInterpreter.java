/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.musikr.interpret;

import arksic.musikr.interpret.PreMusic.*;
import arksic.musikr.model.Format;
import arksic.musikr.model.Music;
import arksic.musikr.tag.Disc;
import arksic.musikr.tag.Name;
import arksic.musikr.tag.Placeholder;
import arksic.musikr.tag.ReleaseType;
import arksic.musikr.tag.ReplayGainAdjustment;
import arksic.musikr.util.Parse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Interprets raw tags into pre-graph music items. Port of TagInterpreter.kt. */
public final class TagInterpreter {
    private final Interpretation interpretation;

    public TagInterpreter(Interpretation interpretation) {
        this.interpretation = interpretation;
    }

    public PreSong interpret(RawSong song) {
        ParsedTags t = song.tags();
        List<PreArtist> individual =
                makePreArtists(t.artistMusicBrainzIds(), t.artistNames(), t.artistSortNames());
        List<PreArtist> albumArtists =
                makePreArtists(
                        t.albumArtistMusicBrainzIds(), t.albumArtistNames(), t.albumArtistSortNames());
        PreAlbum preAlbum = makePreAlbum(t, song.file(), individual, albumArtists);

        List<PreArtist> rawArtists = !individual.isEmpty() ? individual
                : !albumArtists.isEmpty() ? albumArtists : List.of(unknownPreArtist());
        List<PreGenre> genres = makePreGenres(t);
        if (genres.isEmpty()) genres = List.of(unknownPreGenre());

        String fileName = song.file().getFileName().toString();
        String songNameOrFile = t.name() != null ? t.name() : fileName;
        String songNameOrFileNoExt = t.name() != null ? t.name() : beforeFirstDot(fileName);
        String songNameOrFileNoExtCorrect = t.name() != null ? t.name() : beforeLastDot(fileName);
        Path parent = song.file().getParent();
        String dirName = parent != null && parent.getFileName() != null ? parent.getFileName().toString() : "";
        String albumNameOrDir = t.albumName() != null ? t.albumName() : dirName;

        UUID mbid = Parse.toUuidOrNull(t.musicBrainzId());

        // Three generations of UID spec are computed for compat with Auxio's saved state/playlists.
        Music.UID v363 = mbid != null ? Music.UID.musicBrainz(Music.UID.Item.SONG, mbid)
                : Music.UID.auxio(Music.UID.Item.SONG, h -> {
                    h.update(songNameOrFileNoExtCorrect);
                    h.update(albumNameOrDir);
                    h.update(t.date());
                    h.update(t.track());
                    h.update(t.disc());
                    h.update(t.artistNames());
                    h.update(t.albumArtistNames());
                });

        Music.UID v400 = mbid != null ? Music.UID.musicBrainz(Music.UID.Item.SONG, mbid)
                : Music.UID.auxio(Music.UID.Item.SONG, h -> {
                    h.update(songNameOrFile);
                    h.update(t.albumName());
                    h.update(t.date());
                    h.update(t.track());
                    h.update(t.disc());
                    List<String> artistNames = interpretation.separators().split(t.artistNames());
                    h.update(artistNames.isEmpty() ? nullList() : artistNames);
                    List<String> albumArtistNames = interpretation.separators().split(t.albumArtistNames());
                    List<String> use = !albumArtistNames.isEmpty() ? albumArtistNames
                            : !artistNames.isEmpty() ? artistNames : nullList();
                    h.update(use);
                });

        Music.UID v401 = mbid != null ? Music.UID.musicBrainz(Music.UID.Item.SONG, mbid)
                : Music.UID.auxio(Music.UID.Item.SONG, h -> {
                    h.update(songNameOrFileNoExt);
                    h.update(albumNameOrDir);
                    h.update(t.date());
                    h.update(t.track());
                    h.update(t.disc());
                    h.update(t.artistNames());
                    h.update(t.albumArtistNames());
                });

        return new PreSong(
                v363, v400, v401, mbid,
                interpretation.naming().name(songNameOrFileNoExtCorrect, t.sortName()),
                songNameOrFileNoExtCorrect,
                t.track(),
                t.disc() != null ? new Disc(t.disc(), t.subtitle()) : null,
                t.date(),
                song.file().toUri(),
                song.file(),
                Format.fromFileName(fileName),
                song.size(),
                t.durationMs(),
                song.bitrateKbps(),
                song.sampleRateHz(),
                new ReplayGainAdjustment(t.replayGainTrackAdjustment(), t.replayGainAlbumAdjustment()),
                song.modifiedMs(),
                song.addedMs(),
                song.cover(),
                preAlbum,
                rawArtists,
                genres);
    }

    /** A list holding a single null, which hashes as a single zero byte (Kotlin's listOf(null)). */
    private static List<String> nullList() {
        List<String> l = new ArrayList<>(1);
        l.add(null);
        return l;
    }

    private static String beforeFirstDot(String s) {
        int i = s.indexOf('.');
        return i < 0 ? s : s.substring(0, i);
    }

    private static String beforeLastDot(String s) {
        int i = s.lastIndexOf('.');
        return i < 0 ? s : s.substring(0, i);
    }

    private PreAlbum makePreAlbum(
            ParsedTags tags, Path file, List<PreArtist> individual, List<PreArtist> albumArtists) {
        Path parent = file.getParent();
        String dir = parent != null && parent.getFileName() != null ? parent.getFileName().toString() : "";
        String name = tags.albumName() != null ? tags.albumName() : dir;
        ReleaseType type = ReleaseType.parse(interpretation.separators().split(tags.releaseTypes()));
        if (type == null) type = new ReleaseType.Album(null);
        PreArtistsFrom from = !albumArtists.isEmpty()
                ? new PreArtistsFrom.Album(albumArtists)
                : new PreArtistsFrom.Individual(individual.isEmpty() ? List.of(unknownPreArtist()) : individual);
        return new PreAlbum(
                Parse.toUuidOrNull(tags.albumMusicBrainzId()),
                interpretation.naming().name(name, tags.albumSortName(), Placeholder.ALBUM),
                name,
                type,
                from);
    }

    private List<PreArtist> makePreArtists(List<String> rawIds, List<String> rawNames, List<String> rawSortNames) {
        List<String> ids = interpretation.separators().split(rawIds);
        List<String> names = interpretation.separators().split(rawNames);
        List<String> sorts = interpretation.separators().split(rawSortNames);
        List<PreArtist> out = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            out.add(makePreArtist(i < ids.size() ? ids.get(i) : null, names.get(i), i < sorts.size() ? sorts.get(i) : null));
        }
        return out;
    }

    private PreArtist makePreArtist(String mbid, String rawName, String sortName) {
        Name name = interpretation.naming().name(rawName, sortName, Placeholder.ARTIST);
        return new PreArtist(Parse.toUuidOrNull(mbid), name, rawName);
    }

    private static PreArtist unknownPreArtist() {
        return new PreArtist(null, new Name.Unknown(Placeholder.ARTIST), null);
    }

    private List<PreGenre> makePreGenres(ParsedTags tags) {
        List<String> names = Id3Genres.parseNames(tags.genreNames());
        if (names == null) names = interpretation.separators().split(tags.genreNames());
        List<PreGenre> out = new ArrayList<>();
        for (String n : names) {
            out.add(new PreGenre(interpretation.naming().name(n, null, Placeholder.GENRE), n));
        }
        return out;
    }

    private static PreGenre unknownPreGenre() {
        return new PreGenre(new Name.Unknown(Placeholder.GENRE), null);
    }
}
