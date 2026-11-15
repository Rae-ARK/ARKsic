/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.musikr.metadata;

import arksic.musikr.interpret.ParsedTags;
import arksic.musikr.interpret.RawSong;
import arksic.musikr.model.Cover;
import arksic.musikr.tag.Date;
import arksic.musikr.util.Parse;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.TagTextField;
import org.jaudiotagger.tag.id3.AbstractID3v2Frame;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX;
import org.jaudiotagger.tag.wav.WavTag;

/**
 * Reads a file into a {@link RawSong}. This replaces Auxio's TagLib-over-JNI extractor and its
 * TagParser/TagFields: jaudiotagger already normalises ID3/Vorbis/etc. behind {@link FieldKey},
 * so the per-container key tables in TagFields.kt collapse into this one class. The fallback
 * rules (composer-for-artist, compilation => "Various Artists") are carried over from TagParser.
 */
public final class TagReader {
    private static final String[] COVER_NAMES = {
        "cover.jpg", "cover.png", "folder.jpg", "folder.png", "front.jpg", "front.png", "albumart.jpg"
    };
    // Strips non-numeric noise like " dB" from ReplayGain values. Derived from vanilla music.
    private static final Pattern RG_FILTER = Pattern.compile("(?:[^\\d.-]+)");

    // jaudiotagger is very chatty via java.util.logging. LogManager only holds loggers weakly, so
    // this reference must stay: without it the OFF level can vanish after a garbage collection.
    private static final Logger JAUDIOTAGGER_LOG = Logger.getLogger("org.jaudiotagger");

    static {
        JAUDIOTAGGER_LOG.setLevel(Level.OFF);
    }

    private TagReader() {}

    /** Read one file. Throws if the file cannot be parsed as audio at all. */
    public static RawSong read(Path file) throws Exception {
        File f = file.toFile();
        AudioFile audio = AudioFileIO.read(f);
        AudioHeader header = audio.getAudioHeader();
        Tag tag = audio.getTag();

        ParsedTags.Builder b = new ParsedTags.Builder();
        b.durationMs = Math.round(header.getPreciseTrackLength() * 1000.0);

        if (tag != null) fill(b, tag);

        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        long modified = attrs.lastModifiedTime().toMillis();
        long added = Math.min(modified, attrs.creationTime().toMillis());

        Cover cover = null;
        if (tag != null && !tag.getArtworkList().isEmpty()) {
            cover = new Cover(file, true);
        } else {
            Path dir = file.getParent();
            if (dir != null) {
                for (String name : COVER_NAMES) {
                    Path c = dir.resolve(name);
                    if (Files.isRegularFile(c)) {
                        cover = new Cover(c, false);
                        break;
                    }
                }
            }
        }

        return new RawSong(
                file, attrs.size(), modified, added,
                (int) Math.min(Integer.MAX_VALUE, header.getBitRateAsNumber()),
                header.getSampleRateAsNumber(),
                b.build(), cover);
    }

    private static void fill(ParsedTags.Builder b, Tag tag) {
        boolean compilation = "1".equals(first(tag, FieldKey.IS_COMPILATION));

        b.musicBrainzId = first(tag, FieldKey.MUSICBRAINZ_RELEASE_TRACK_ID);
        if (b.musicBrainzId == null) b.musicBrainzId = first(tag, FieldKey.MUSICBRAINZ_TRACK_ID);
        b.name = first(tag, FieldKey.TITLE);
        b.sortName = first(tag, FieldKey.TITLE_SORT);
        b.track = position(first(tag, FieldKey.TRACK), first(tag, FieldKey.TRACK_TOTAL));
        b.disc = position(first(tag, FieldKey.DISC_NO), first(tag, FieldKey.DISC_TOTAL));
        b.subtitle = first(tag, FieldKey.DISC_SUBTITLE);
        b.date = date(tag);

        b.albumMusicBrainzId = first(tag, FieldKey.MUSICBRAINZ_RELEASEID);
        b.albumName = first(tag, FieldKey.ALBUM);
        b.albumSortName = first(tag, FieldKey.ALBUM_SORT);
        List<String> types = all(tag, FieldKey.MUSICBRAINZ_RELEASE_TYPE);
        b.releaseTypes = !types.isEmpty() ? types : compilation ? List.of("compilation") : List.of();

        List<String> artists = firstNonEmpty(tag, FieldKey.ARTISTS, FieldKey.ARTIST);
        b.artistMusicBrainzIds = all(tag, FieldKey.MUSICBRAINZ_ARTISTID);
        b.artistNames = artists;
        b.artistSortNames = firstNonEmpty(tag, FieldKey.ARTISTS_SORT, FieldKey.ARTIST_SORT);
        if (artists.isEmpty()) {
            // No first-class composer type: it's just a fallback for artist. Override all three
            // at once so MBIDs/sort tags don't get hybridised between artist and composer.
            b.artistMusicBrainzIds = List.of();
            b.artistNames = all(tag, FieldKey.COMPOSER);
            b.artistSortNames = all(tag, FieldKey.COMPOSER_SORT);
        }

        b.albumArtistMusicBrainzIds = all(tag, FieldKey.MUSICBRAINZ_RELEASEARTISTID);
        List<String> albumArtists = firstNonEmpty(tag, FieldKey.ALBUM_ARTISTS, FieldKey.ALBUM_ARTIST);
        // Compilation heavily implies "various artists" when nothing else is given.
        b.albumArtistNames = !albumArtists.isEmpty() ? albumArtists
                : compilation ? List.of("Various Artists") : List.of();
        b.albumArtistSortNames = firstNonEmpty(tag, FieldKey.ALBUM_ARTISTS_SORT, FieldKey.ALBUM_ARTIST_SORT);

        b.genreNames = all(tag, FieldKey.GENRE);

        b.replayGainTrack = replayGain(tag, "REPLAYGAIN_TRACK_GAIN");
        b.replayGainAlbum = replayGain(tag, "REPLAYGAIN_ALBUM_GAIN");
    }

    // --- field helpers (jaudiotagger throws for keys a container doesn't support) ---

    private static String first(Tag tag, FieldKey key) {
        try {
            return Parse.correctWhitespace(tag.getFirst(key));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<String> all(Tag tag, FieldKey key) {
        try {
            List<String> out = new ArrayList<>();
            for (String s : tag.getAll(key)) {
                String c = Parse.correctWhitespace(s);
                if (c != null) out.add(c);
            }
            return out;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static List<String> firstNonEmpty(Tag tag, FieldKey... keys) {
        for (FieldKey k : keys) {
            List<String> v = all(tag, k);
            if (!v.isEmpty()) return v;
        }
        return List.of();
    }

    /**
     * Combine a position with its separate total field (ID3 via jaudiotagger splits "3/12" into a
     * number and a total; Vorbis may hand back "3/12" whole). Auxio treats a position of 0 as valid
     * only when a positive total accompanies it, so the total has to reach {@link #position(String)}.
     */
    static Integer position(String number, String total) {
        if (number != null && total != null && !number.contains("/")) return position(number + "/" + total);
        return position(number);
    }

    /** "3/12" or "3" => 3; zero only counts if a positive total was given. */
    static Integer position(String s) {
        if (s == null) return null;
        String[] parts = s.split("/", 2);
        Integer pos = toInt(parts[0]);
        Integer total = parts.length > 1 ? toInt(parts[1]) : null;
        return pos != null && (pos > 0 || (total != null && total > 0)) ? pos : null;
    }

    private static Integer toInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Date date(Tag tag) {
        // Auxio's hierarchy: original date (solves "released in X, remastered in Y"), then date.
        for (FieldKey k : new FieldKey[] {FieldKey.ORIGINAL_YEAR, FieldKey.YEAR}) {
            String v = first(tag, k);
            if (v != null) {
                Date d = Date.from(v);
                if (d != null) return d;
            }
        }
        return null;
    }

    static Float replayGain(Tag tag, String description) {
        String raw = custom(tag, description);
        if (raw == null) return null;
        try {
            float v = Float.parseFloat(RG_FILTER.matcher(raw).replaceAll(""));
            return v != 0f ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A user-defined text field: TXXX:desc for ID3, a plain named field for Vorbis/etc. */
    private static String custom(Tag tag, String description) {
        try {
            if (tag instanceof WavTag w) tag = w.getID3Tag() != null ? w.getID3Tag() : tag;
            if (tag instanceof AbstractID3v2Tag id3) {
                Object frames = id3.getFrame("TXXX");
                List<?> list = frames instanceof List<?> l ? l : frames == null ? List.of() : List.of(frames);
                for (Object o : list) {
                    if (o instanceof AbstractID3v2Frame frame && frame.getBody() instanceof FrameBodyTXXX body
                            && description.equalsIgnoreCase(body.getDescription())) {
                        return Parse.correctWhitespace(body.getUserFriendlyValue());
                    }
                }
                return null;
            }
            for (TagField field : tag.getFields(description)) {
                if (field instanceof TagTextField t) return Parse.correctWhitespace(t.getContent());
            }
        } catch (RuntimeException ignored) {
            // unsupported on this container
        }
        return null;
    }
}
