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

import arksic.musikr.model.Cover;
import arksic.musikr.model.Format;
import arksic.musikr.model.Music;
import arksic.musikr.tag.Date;
import arksic.musikr.tag.Disc;
import arksic.musikr.tag.Name;
import arksic.musikr.tag.ReleaseType;
import arksic.musikr.tag.ReplayGainAdjustment;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Pre-graph music items: what the interpreter produces and the graph clusters. */
public final class PreMusic {
    private PreMusic() {}

    public record PreSong(
            Music.UID v363Uid,
            Music.UID v400Uid,
            Music.UID v401Uid,
            UUID musicBrainzId,
            Name.Known name,
            String rawName,
            Integer track,
            Disc disc,
            Date date,
            URI uri,
            Path path,
            Format format,
            long size,
            long durationMs,
            int bitrateKbps,
            int sampleRateHz,
            ReplayGainAdjustment replayGainAdjustment,
            long modifiedMs,
            long addedMs,
            Cover cover,
            PreAlbum preAlbum,
            List<PreArtist> preArtists,
            List<PreGenre> preGenres) {}

    public sealed interface PreArtistsFrom {
        List<PreArtist> preArtists();

        record Individual(List<PreArtist> preArtists) implements PreArtistsFrom {}

        record Album(List<PreArtist> preArtists) implements PreArtistsFrom {}
    }

    public record PreAlbum(
            UUID musicBrainzId,
            Name name,
            String rawName,
            ReleaseType releaseType,
            PreArtistsFrom preArtists) {

        /** Prefer a MusicBrainz ID, else hash names only (more stable than hashing dates too). */
        public Music.UID uid() {
            if (musicBrainzId != null) {
                return Music.UID.musicBrainz(Music.UID.Item.ALBUM, musicBrainzId);
            }
            return Music.UID.auxio(
                    Music.UID.Item.ALBUM,
                    h -> {
                        h.update(rawName);
                        h.update(preArtists.preArtists().stream().map(PreArtist::rawName)
                                .filter(java.util.Objects::nonNull).toList());
                    });
        }

        public PreAlbum withMusicBrainzId(UUID id) {
            return new PreAlbum(id, name, rawName, releaseType, preArtists);
        }

        public PreAlbum withPreArtists(PreArtistsFrom from) {
            return new PreAlbum(musicBrainzId, name, rawName, releaseType, from);
        }
    }

    public record PreArtist(UUID musicBrainzId, Name name, String rawName) {
        public Music.UID uid() {
            if (musicBrainzId != null) {
                return Music.UID.musicBrainz(Music.UID.Item.ARTIST, musicBrainzId);
            }
            return Music.UID.auxio(Music.UID.Item.ARTIST, h -> h.update(rawName));
        }

        public PreArtist withMusicBrainzId(UUID id) {
            return new PreArtist(id, name, rawName);
        }
    }

    public record PreGenre(Name name, String rawName) {}
}
