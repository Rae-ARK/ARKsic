package arksic.musikr;

import static arksic.musikr.TestLibrary.song;
import static org.junit.jupiter.api.Assertions.*;

import arksic.musikr.interpret.Interpretation;
import arksic.musikr.interpret.Naming;
import arksic.musikr.interpret.Separators;
import arksic.musikr.model.*;
import arksic.musikr.playlist.StoredPlaylists;
import arksic.musikr.tag.Name;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LibraryGraphTest {
    private static Artist artist(Library l, String raw) {
        return l.artists().stream()
                .filter(a -> a.name() instanceof Name.Known k && k.raw().equals(raw))
                .findFirst().orElseThrow();
    }

    @Test
    void groupsSongsIntoAlbumsAndArtists() {
        Library l = TestLibrary.of(
                song().title("One").album("LP").artists("A").track(1),
                song().title("Two").album("LP").artists("A").track(2),
                song().title("Three").album("Other").artists("B"));
        assertEquals(3, l.songs().size());
        assertEquals(2, l.albums().size());
        assertEquals(2, l.artists().size());
        assertEquals(2, artist(l, "A").songs().size());
    }

    @Test
    void artistNamesDifferingOnlyByCaseAreMelded() {
        Library l = TestLibrary.of(
                song().title("1").album("X").artists("Björk"),
                song().title("2").album("X").artists("björk"),
                song().title("3").album("X").artists("Björk"));
        assertEquals(1, l.artists().size());
        // The more popular spelling wins.
        assertEquals("Björk", ((Name.Known) l.artists().iterator().next().name()).raw());
        for (Song s : l.songs()) assertEquals(1, s.artists().size());
    }

    @Test
    void albumNamesDifferingOnlyByCaseAreMelded() {
        Library l = TestLibrary.of(
                song().title("1").album("Abbey Road").artists("A"),
                song().title("2").album("abbey road").artists("A"));
        assertEquals(1, l.albums().size());
        assertEquals(2, l.albums().iterator().next().songs().size());
    }

    @Test
    void genreNamesDifferingOnlyByCaseAreMelded() {
        Library l = TestLibrary.of(
                song().title("1").genres("Rock").artists("A"),
                song().title("2").genres("rock").artists("B"),
                song().title("3").genres("ROCK").artists("C"));
        assertEquals(1, l.genres().size());
        assertEquals(3, l.genres().iterator().next().songs().size());
    }

    @Test
    void multiArtistTagsSplitOnSeparators() {
        Library l = TestLibrary.of(song().title("Duet").album("D").artists("A; B"));
        Song s = l.songs().iterator().next();
        assertEquals(2, s.artists().size());
        assertEquals(2, l.artists().size());
    }

    @Test
    void escapedSeparatorsAreKept() {
        Library l = TestLibrary.of(song().title("T").album("D").artists("AC\\;DC"));
        Song s = l.songs().iterator().next();
        assertEquals(1, s.artists().size());
        assertEquals("AC;DC", ((Name.Known) s.artists().get(0).name()).raw());
    }

    @Test
    void noSeparatorsMeansNoSplitting() {
        Interpretation interp = new Interpretation(Naming.intelligent(), Separators.from(""));
        Library l = TestLibrary.of(interp, StoredPlaylists.inMemory(), song().title("T").album("D").artists("A; B"));
        assertEquals(1, l.songs().iterator().next().artists().size());
    }

    private static Song songNamed(Library l, String name) {
        return l.songs().stream().filter(s -> s.name().raw().equals(name)).findFirst().orElseThrow();
    }

    private static List<String> artistNames(List<Artist> artists) {
        return artists.stream().map(a -> ((Name.Known) a.name()).raw()).toList();
    }

    @Test
    void albumArtistsTakePriorityForAlbumsButNotForSongs() {
        Library l = TestLibrary.of(
                song().title("1").album("Comp").artists("Guest1").albumArtists("Host"),
                song().title("2").album("Comp").artists("Guest2").albumArtists("Host"));
        // The album belongs to its album artist...
        Album album = l.albums().iterator().next();
        assertEquals(List.of("Host"), artistNames(album.artists()));
        // ...but each song keeps its own performing artist.
        assertEquals(List.of("Guest1"), artistNames(songNamed(l, "1").artists()));
        assertEquals(List.of("Guest2"), artistNames(songNamed(l, "2").artists()));
        // Host has the album explicitly; guests reach it implicitly.
        assertEquals(1, artist(l, "Host").explicitAlbums().size());
        assertEquals(0, artist(l, "Host").implicitAlbums().size());
        assertEquals(0, artist(l, "Guest1").explicitAlbums().size());
        assertEquals(1, artist(l, "Guest1").implicitAlbums().size());
        assertEquals(1, artist(l, "Guest2").implicitAlbums().size());
    }

    @Test
    void missingMetadataFallsBackToUnknownAndDirectory() {
        Library l = TestLibrary.of(song().file("mystery.mp3").dir("Music/Some Folder"));
        Song s = l.songs().iterator().next();
        assertEquals("mystery", s.name().raw());
        assertTrue(s.album().name() instanceof Name.Known k && k.raw().equals("Some Folder"));
        assertTrue(s.artists().get(0).name() instanceof Name.Unknown);
        assertTrue(s.genres().get(0).name() instanceof Name.Unknown);
    }

    @Test
    void albumAggregates() {
        Library l = TestLibrary.of(
                song().title("1").album("LP").artists("A").durationMs(1000).date("2001"),
                song().title("2").album("LP").artists("A").durationMs(2000).date("1999-05"));
        Album a = l.albums().iterator().next();
        assertEquals(3000, a.durationMs());
        assertEquals("1999-05", a.dates().min.toString());
        assertEquals("2001", a.dates().max.toString());
    }

    @Test
    void partialMusicBrainzCoverageIsDiscarded() {
        // One file has an album MBID, the other doesn't: they must still be the same album.
        String mbid = UUID.randomUUID().toString();
        Library l = TestLibrary.of(
                song().title("1").album("LP").artists("A").albumMbid(mbid),
                song().title("2").album("LP").artists("A"));
        assertEquals(1, l.albums().size());
    }

    @Test
    void fullMusicBrainzCoverageKeepsDistinctReleasesApart() {
        Library l = TestLibrary.of(
                song().title("1").album("LP").artists("A").albumMbid(UUID.randomUUID().toString()),
                song().title("2").album("LP").artists("A").albumMbid(UUID.randomUUID().toString()));
        assertEquals(2, l.albums().size());
    }

    @Test
    void sameAlbumNameDifferentAlbumArtistsStaySeparate() {
        Library l = TestLibrary.of(
                song().title("1").album("Greatest Hits").artists("A").albumArtists("A"),
                song().title("2").album("Greatest Hits").artists("B").albumArtists("B"));
        assertEquals(2, l.albums().size());
    }

    @Test
    void duplicateSongsAreDeduplicated() {
        Library l = TestLibrary.of(
                song().title("Same").album("LP").artists("A").track(1).file("a.mp3"),
                song().title("Same").album("LP").artists("A").track(1).file("b.mp3"));
        assertEquals(1, l.songs().size());
    }

    @Test
    void lookupByUidAndPath() {
        Library l = TestLibrary.of(song().title("X").album("LP").artists("A"));
        Song s = l.songs().iterator().next();
        assertSame(s, l.findSong(s.uid()));
        assertSame(s, l.findSongByPath(s.path()));
        assertSame(s.album(), l.findAlbum(s.album().uid()));
        assertNull(l.findSong(Music.UID.auxio(Music.UID.Item.SONG)));
    }

    @Test
    void emptyLibrary() {
        assertTrue(TestLibrary.of().empty());
    }

    @Test
    void playlistLifecycleAndPersistence() throws Exception {
        java.nio.file.Path file = java.nio.file.Files.createTempFile("pl", ".txt");
        try {
            StoredPlaylists store = new StoredPlaylists(file);
            Library l = TestLibrary.of(Interpretation.defaults(), store,
                    song().title("a").album("L").artists("A"), song().title("b").album("L").artists("A"));
            List<Song> songs = List.copyOf(l.songs());

            l = l.createPlaylist("Mix\tTab", songs.subList(0, 1));
            Playlist p = l.findPlaylistByName("Mix\tTab");
            assertNotNull(p);
            assertEquals(1, p.songs().size());

            l = l.addToPlaylist(p, songs.subList(1, 2));
            p = l.findPlaylistByName("Mix\tTab");
            assertEquals(2, p.songs().size());

            l = l.renamePlaylist(p, "Renamed");
            assertNull(l.findPlaylistByName("Mix\tTab"));
            p = l.findPlaylistByName("Renamed");
            assertEquals(2, p.songs().size());

            // Reload from disk: the playlist and its song pointers survive.
            Library reloaded = TestLibrary.of(Interpretation.defaults(), new StoredPlaylists(file),
                    song().title("a").album("L").artists("A"), song().title("b").album("L").artists("A"));
            Playlist rp = reloaded.findPlaylistByName("Renamed");
            assertNotNull(rp);
            assertEquals(2, rp.songs().size());

            l = l.deletePlaylist(p);
            assertTrue(l.playlists().isEmpty());
            assertTrue(new StoredPlaylists(file).read().isEmpty());
        } finally {
            java.nio.file.Files.deleteIfExists(file);
        }
    }

    @Test
    void playlistMutationOfForeignPlaylistFails() {
        Library l1 = TestLibrary.of(song().title("a").album("L").artists("A"));
        Library l2 = TestLibrary.of(song().title("a").album("L").artists("A"));
        Library withPl = l1.createPlaylist("P", List.copyOf(l1.songs()));
        Playlist p = withPl.findPlaylistByName("P");
        assertThrows(IllegalArgumentException.class, () -> l2.deletePlaylist(p));
    }
}
