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

import arksic.musikr.tag.Date;
import java.util.List;

/** Tags after parsing out of a file, before interpretation. Port of ParsedTags.kt. */
public record ParsedTags(
        long durationMs,
        Float replayGainTrackAdjustment,
        Float replayGainAlbumAdjustment,
        String musicBrainzId,
        String name,
        String sortName,
        Integer track,
        Integer disc,
        String subtitle,
        Date date,
        String albumMusicBrainzId,
        String albumName,
        String albumSortName,
        List<String> releaseTypes,
        List<String> artistMusicBrainzIds,
        List<String> artistNames,
        List<String> artistSortNames,
        List<String> albumArtistMusicBrainzIds,
        List<String> albumArtistNames,
        List<String> albumArtistSortNames,
        List<String> genreNames) {

    /** Mutable builder so the tag reader doesn't need a 21-arg constructor call. */
    public static final class Builder {
        public long durationMs;
        public Float replayGainTrack;
        public Float replayGainAlbum;
        public String musicBrainzId;
        public String name;
        public String sortName;
        public Integer track;
        public Integer disc;
        public String subtitle;
        public Date date;
        public String albumMusicBrainzId;
        public String albumName;
        public String albumSortName;
        public List<String> releaseTypes = List.of();
        public List<String> artistMusicBrainzIds = List.of();
        public List<String> artistNames = List.of();
        public List<String> artistSortNames = List.of();
        public List<String> albumArtistMusicBrainzIds = List.of();
        public List<String> albumArtistNames = List.of();
        public List<String> albumArtistSortNames = List.of();
        public List<String> genreNames = List.of();

        public ParsedTags build() {
            return new ParsedTags(
                    durationMs, replayGainTrack, replayGainAlbum, musicBrainzId, name, sortName,
                    track, disc, subtitle, date, albumMusicBrainzId, albumName, albumSortName,
                    releaseTypes, artistMusicBrainzIds, artistNames, artistSortNames,
                    albumArtistMusicBrainzIds, albumArtistNames, albumArtistSortNames, genreNames);
        }
    }
}
