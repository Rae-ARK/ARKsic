package arksic.musikr.metadata;

import static org.junit.jupiter.api.Assertions.*;

import arksic.musikr.interpret.ParsedTags;
import arksic.musikr.interpret.RawSong;
import arksic.musikr.tag.Date;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Reads real (if tiny) WAV, MP3 and FLAC files through jaudiotagger, not mocks. */
class TagReaderTest {
    private Path dir;

    @BeforeEach
    void setUp() throws IOException {
        dir = Files.createTempDirectory("arksic-tagreader");
    }

    @AfterEach
    void tearDown() throws IOException {
        AudioFixtures.deleteRecursively(dir);
    }

    private enum Kind { WAV, MP3, FLAC }

    private Path make(Kind kind, String name) throws IOException {
        Path f = dir.resolve(name + "." + kind.name().toLowerCase());
        return switch (kind) {
            case WAV -> AudioFixtures.wav(f, 1.0);
            case MP3 -> AudioFixtures.mp3(f, 40);
            case FLAC -> AudioFixtures.flac(f, AudioFixtures.SAMPLE_RATE);
        };
    }

    // --- position parsing (pure) ---

    @Test
    void positionParsing() {
        assertEquals(3, TagReader.position("3"));
        assertEquals(3, TagReader.position("3/12"));
        assertEquals(7, TagReader.position(" 7 / 9 "));
        assertNull(TagReader.position("0"));
        assertNull(TagReader.position("0/0"));
        assertEquals(0, TagReader.position("0/12")); // track 0 is only believable with a total
        assertNull(TagReader.position("abc"));
        assertNull(TagReader.position("/12"));
        assertNull(TagReader.position((String) null));
        assertNull(TagReader.position(""));
    }

    @Test
    void positionCombinesSeparateTotal() {
        assertEquals(3, TagReader.position("3", "12"));
        assertEquals(0, TagReader.position("0", "12"));
        assertNull(TagReader.position("0", null));
        assertEquals(3, TagReader.position("3", null));
        assertEquals(3, TagReader.position("3/12", "99")); // already slashed: total ignored
        assertNull(TagReader.position(null, "12"));
    }

    // --- core tags in every format ---

    private void tagCore(Path f) throws Exception {
        AudioFixtures.tag(f, t -> {
            try {
                t.setField(FieldKey.TITLE, "Song Title");
                t.setField(FieldKey.ARTIST, "The Artist");
                t.setField(FieldKey.ALBUM, "The Album");
                t.setField(FieldKey.ALBUM_ARTIST, "The Album Artist");
                t.setField(FieldKey.GENRE, "Shoegaze");
                t.setField(FieldKey.TRACK, "3");
                t.setField(FieldKey.TRACK_TOTAL, "12");
                t.setField(FieldKey.DISC_NO, "2");
                t.setField(FieldKey.DISC_TOTAL, "3");
                t.setField(FieldKey.YEAR, "2019-03-07");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private void assertCore(ParsedTags p) {
        assertEquals("Song Title", p.name());
        assertEquals(List.of("The Artist"), p.artistNames());
        assertEquals("The Album", p.albumName());
        assertEquals(List.of("The Album Artist"), p.albumArtistNames());
        assertEquals(List.of("Shoegaze"), p.genreNames());
        assertEquals(3, p.track());
        assertEquals(2, p.disc());
        assertEquals(Date.from("2019-03-07"), p.date());
    }

    @Test
    void coreTagsRoundTripInWav() throws Exception {
        Path f = make(Kind.WAV, "core");
        tagCore(f);
        RawSong raw = TagReader.read(f);
        assertCore(raw.tags());
        assertEquals(1000, raw.tags().durationMs());
        assertEquals(44100, raw.sampleRateHz());
        assertEquals(Files.size(f), raw.size());
    }

    @Test
    void coreTagsRoundTripInMp3() throws Exception {
        Path f = make(Kind.MP3, "core");
        tagCore(f);
        RawSong raw = TagReader.read(f);
        assertCore(raw.tags());
        assertEquals(AudioFixtures.mp3DurationMs(40), raw.tags().durationMs(), 60);
        assertEquals(44100, raw.sampleRateHz());
        assertEquals(128, raw.bitrateKbps());
    }

    @Test
    void coreTagsRoundTripInFlac() throws Exception {
        Path f = make(Kind.FLAC, "core");
        tagCore(f);
        RawSong raw = TagReader.read(f);
        assertCore(raw.tags());
        assertEquals(1000, raw.tags().durationMs());
        assertEquals(44100, raw.sampleRateHz());
    }

    @Test
    void sortNamesAndMultipleArtistsInFlac() throws Exception {
        Path f = make(Kind.FLAC, "multi");
        AudioFixtures.tag(f, t -> {
            try {
                t.setField(FieldKey.TITLE, "Zebra");
                t.setField(FieldKey.TITLE_SORT, "Aardvark");
                t.setField(FieldKey.ARTIST, "First");
                t.addField(FieldKey.ARTIST, "Second");
                t.setField(FieldKey.ALBUM, "Album");
                t.setField(FieldKey.ALBUM_SORT, "Sorted Album");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        ParsedTags p = TagReader.read(f).tags();
        assertEquals("Aardvark", p.sortName());
        assertEquals("Sorted Album", p.albumSortName());
        assertEquals(List.of("First", "Second"), p.artistNames());
    }

    @Test
    void musicBrainzIdsAreRead() throws Exception {
        Path f = make(Kind.FLAC, "mbid");
        String track = "11111111-1111-1111-1111-111111111111";
        String release = "22222222-2222-2222-2222-222222222222";
        String artist = "33333333-3333-3333-3333-333333333333";
        AudioFixtures.tag(f, t -> {
            try {
                t.setField(FieldKey.ARTIST, "Named Artist"); // MBIDs without a name are discarded, by design
                t.setField(FieldKey.MUSICBRAINZ_TRACK_ID, track);
                t.setField(FieldKey.MUSICBRAINZ_RELEASEID, release);
                t.setField(FieldKey.MUSICBRAINZ_ARTISTID, artist);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        ParsedTags p = TagReader.read(f).tags();
        assertEquals(track, p.musicBrainzId());
        assertEquals(release, p.albumMusicBrainzId());
        assertEquals(List.of(artist), p.artistMusicBrainzIds());
    }

    @Test
    void artistMusicBrainzIdsWithoutAnArtistNameAreDiscarded() throws Exception {
        Path f = make(Kind.FLAC, "mbid-orphan");
        AudioFixtures.tag(f, t -> {
            try {
                t.setField(FieldKey.MUSICBRAINZ_ARTISTID, "33333333-3333-3333-3333-333333333333");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertEquals(List.of(), TagReader.read(f).tags().artistMusicBrainzIds());
    }

    // --- ReplayGain ---

    private void tagReplayGain(Kind kind, Path f, String track, String album) throws Exception {
        AudioFixtures.tag(f, t -> {
            try {
                if (kind == Kind.FLAC) {
                    if (track != null) AudioFixtures.vorbis(t, "REPLAYGAIN_TRACK_GAIN", track);
                    if (album != null) AudioFixtures.vorbis(t, "REPLAYGAIN_ALBUM_GAIN", album);
                } else if (track != null && album != null) {
                    AudioFixtures.txxx(t, "REPLAYGAIN_TRACK_GAIN", track, "REPLAYGAIN_ALBUM_GAIN", album);
                } else if (track != null) {
                    AudioFixtures.txxx(t, "REPLAYGAIN_TRACK_GAIN", track);
                } else if (album != null) {
                    AudioFixtures.txxx(t, "REPLAYGAIN_ALBUM_GAIN", album);
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Test
    void replayGainIsParsedInEveryFormat() throws Exception {
        for (Kind kind : Kind.values()) {
            Path f = make(kind, "rg");
            tagReplayGain(kind, f, "-6.50 dB", "+3.20 dB");
            ParsedTags p = TagReader.read(f).tags();
            assertEquals(-6.5f, p.replayGainTrackAdjustment(), 0.001f, kind + " track gain");
            assertEquals(3.2f, p.replayGainAlbumAdjustment(), 0.001f, kind + " album gain");
        }
    }

    @Test
    void replayGainIsFoundAmongUnrelatedTxxxFrames() throws Exception {
        Path f = make(Kind.MP3, "rg-many");
        AudioFixtures.tag(f, t -> AudioFixtures.txxx(t,
                "SOMETHING ELSE", "ignore me",
                "REPLAYGAIN_TRACK_GAIN", "-2.25 dB",
                "MusicBrainz Album Type", "album"));
        ParsedTags p = TagReader.read(f).tags();
        assertEquals(-2.25f, p.replayGainTrackAdjustment(), 0.001f);
        assertNull(p.replayGainAlbumAdjustment());
    }

    @Test
    void zeroOrGarbageReplayGainIsIgnored() throws Exception {
        for (Kind kind : Kind.values()) {
            Path f = make(kind, "rg0");
            tagReplayGain(kind, f, "0.00 dB", "loud");
            ParsedTags p = TagReader.read(f).tags();
            assertNull(p.replayGainTrackAdjustment(), kind + " zero gain means 'none'");
            assertNull(p.replayGainAlbumAdjustment(), kind + " unparseable gain");
        }
    }

    @Test
    void missingReplayGainIsNull() throws Exception {
        Path f = make(Kind.MP3, "norg");
        tagCore(f);
        ParsedTags p = TagReader.read(f).tags();
        assertNull(p.replayGainTrackAdjustment());
        assertNull(p.replayGainAlbumAdjustment());
    }

    // --- fallbacks carried over from Auxio's TagParser ---

    @Test
    void compilationFlagImpliesVariousArtistsAndReleaseType() throws Exception {
        Path f = make(Kind.FLAC, "comp");
        AudioFixtures.tag(f, t -> {
            try {
                t.setField(FieldKey.TITLE, "T");
                t.setField(FieldKey.ARTIST, "Someone");
                t.setField(FieldKey.IS_COMPILATION, "1");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        ParsedTags p = TagReader.read(f).tags();
        assertEquals(List.of("Various Artists"), p.albumArtistNames());
        assertEquals(List.of("compilation"), p.releaseTypes());
        assertEquals(List.of("Someone"), p.artistNames());
    }

    @Test
    void explicitAlbumArtistBeatsCompilationFlag() throws Exception {
        Path f = make(Kind.FLAC, "comp2");
        AudioFixtures.tag(f, t -> {
            try {
                t.setField(FieldKey.ALBUM_ARTIST, "Curator");
                t.setField(FieldKey.IS_COMPILATION, "1");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertEquals(List.of("Curator"), TagReader.read(f).tags().albumArtistNames());
    }

    @Test
    void composerStandsInForMissingArtist() throws Exception {
        Path f = make(Kind.FLAC, "composer");
        AudioFixtures.tag(f, t -> {
            try {
                t.setField(FieldKey.TITLE, "Piece");
                t.setField(FieldKey.COMPOSER, "Bach");
                t.setField(FieldKey.MUSICBRAINZ_ARTISTID, "33333333-3333-3333-3333-333333333333");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        ParsedTags p = TagReader.read(f).tags();
        assertEquals(List.of("Bach"), p.artistNames());
        // The artist MBID belonged to the artist tag, not the composer: it must not leak over.
        assertEquals(List.of(), p.artistMusicBrainzIds());
    }

    @Test
    void whitespaceIsTrimmedAndBlankIsNull() throws Exception {
        Path f = make(Kind.FLAC, "ws");
        AudioFixtures.tag(f, t -> {
            try {
                t.setField(FieldKey.TITLE, "  Padded  ");
                t.setField(FieldKey.ALBUM, "   ");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        ParsedTags p = TagReader.read(f).tags();
        assertEquals("Padded", p.name());
        assertNull(p.albumName());
    }

    @Test
    void unicodeSurvivesInEveryFormat() throws Exception {
        String title = "日本語 – Ünïcødé ✓";
        for (Kind kind : Kind.values()) {
            Path f = make(kind, "uni");
            AudioFixtures.tag(f, t -> {
                try {
                    t.setField(FieldKey.TITLE, title);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            assertEquals(title, TagReader.read(f).tags().name(), kind.toString());
        }
    }

    @Test
    void untaggedFileStillHasAudioFacts() throws Exception {
        for (Kind kind : Kind.values()) {
            Path f = make(kind, "bare");
            RawSong raw = TagReader.read(f);
            assertNull(raw.tags().name(), kind.toString());
            assertNull(raw.tags().albumName());
            assertTrue(raw.tags().artistNames().isEmpty());
            assertTrue(raw.tags().genreNames().isEmpty());
            assertTrue(raw.tags().durationMs() > 900, kind + " duration " + raw.tags().durationMs());
            assertEquals(f, raw.file());
        }
    }

    @Test
    void garbageThrows() throws Exception {
        Path f = dir.resolve("garbage.mp3");
        Files.write(f, "this is not audio at all, just some text repeated. ".repeat(40).getBytes());
        assertThrows(Exception.class, () -> TagReader.read(f));
    }

    // --- covers ---

    private void embedArt(Path f) throws Exception {
        AudioFixtures.tag(f, t -> setArt(t));
    }

    private static void setArt(Tag t) {
        try {
            Artwork art = ArtworkFactory.getNew();
            art.setBinaryData(AudioFixtures.png());
            art.setMimeType("image/png");
            art.setPictureType(3);
            t.setField(art);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void embeddedArtworkIsDetected() throws Exception {
        for (Kind kind : new Kind[] {Kind.MP3, Kind.FLAC}) {
            Path f = make(kind, "art");
            embedArt(f);
            RawSong raw = TagReader.read(f);
            assertNotNull(raw.cover(), kind.toString());
            assertTrue(raw.cover().embedded(), kind.toString());
            assertEquals(f, raw.cover().source());
        }
    }

    @Test
    void folderImageIsUsedWhenNothingIsEmbedded() throws Exception {
        Path f = make(Kind.WAV, "song");
        Path cover = dir.resolve("cover.png");
        Files.write(cover, AudioFixtures.png());
        RawSong raw = TagReader.read(f);
        assertNotNull(raw.cover());
        assertFalse(raw.cover().embedded());
        assertEquals(cover, raw.cover().source());
    }

    @Test
    void embeddedArtworkBeatsFolderImage() throws Exception {
        Path f = make(Kind.FLAC, "both");
        embedArt(f);
        Files.write(dir.resolve("cover.jpg"), new byte[] {1, 2, 3});
        assertTrue(TagReader.read(f).cover().embedded());
    }

    @Test
    void noCoverIsNull() throws Exception {
        assertNull(TagReader.read(make(Kind.WAV, "nocover")).cover());
    }
}
