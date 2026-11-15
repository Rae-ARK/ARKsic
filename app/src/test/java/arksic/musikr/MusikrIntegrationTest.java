package arksic.musikr;

import static org.junit.jupiter.api.Assertions.*;

import arksic.musikr.interpret.Interpretation;
import arksic.musikr.metadata.AudioFixtures;
import arksic.musikr.model.Library;
import arksic.musikr.model.Music;
import arksic.musikr.model.Playlist;
import arksic.musikr.model.Song;
import arksic.musikr.playlist.StoredPlaylists;
import arksic.musikr.tag.Name;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jaudiotagger.tag.FieldKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** End to end: a real folder tree on disk through Musikr.run. */
class MusikrIntegrationTest {
    private Path root;

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createTempDirectory("arksic-musikr");
    }

    @AfterEach
    void tearDown() throws IOException {
        AudioFixtures.deleteRecursively(root);
    }

    private static void fields(Path f, String title, String artist, String album, int track) throws Exception {
        AudioFixtures.tag(f, t -> {
            try {
                t.setField(FieldKey.TITLE, title);
                t.setField(FieldKey.ARTIST, artist);
                t.setField(FieldKey.ALBUM, album);
                t.setField(FieldKey.TRACK, Integer.toString(track));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /** A/X: two FLACs; B/Y: one MP3; misc: an untagged WAV, a text file; bad: a corrupt MP3. */
    private void buildTree() throws Exception {
        Path ax = Files.createDirectories(root.resolve("Artist A/Album X"));
        Path by = Files.createDirectories(root.resolve("Artist B/Album Y"));
        Path misc = Files.createDirectories(root.resolve("misc"));
        Path bad = Files.createDirectories(root.resolve("bad"));
        Path one = AudioFixtures.flac(ax.resolve("01.flac"), 44100);
        Path two = AudioFixtures.flac(ax.resolve("02.flac"), 88200);
        Path three = AudioFixtures.mp3(by.resolve("01.mp3"), 40);
        AudioFixtures.wav(misc.resolve("loose.wav"), 0.5);
        Files.writeString(misc.resolve("notes.txt"), "not audio");
        Files.writeString(root.resolve("README"), "no extension");
        Files.write(bad.resolve("corrupt.mp3"), "definitely not mp3 data. ".repeat(60).getBytes());
        fields(one, "First", "Artist A", "Album X", 1);
        fields(two, "Second", "Artist A", "Album X", 2);
        fields(three, "Third", "Artist B", "Album Y", 1);
    }

    private Musikr.Result scan(StoredPlaylists store, AtomicInteger progress) throws Exception {
        return Musikr.run(List.of(root), Interpretation.defaults(), store,
                progress == null ? null : progress::set);
    }

    private static List<String> names(Iterable<? extends Music> items) {
        List<String> out = new ArrayList<>();
        for (Music m : items) out.add(m.name() instanceof Name.Known k ? k.raw() : "<unknown>");
        out.sort(null);
        return out;
    }

    @Test
    void scansNestedFoldersAndGroupsEverything() throws Exception {
        buildTree();
        Library lib = scan(StoredPlaylists.inMemory(), null).library();
        assertEquals(List.of("First", "Second", "Third", "loose"), names(lib.songs()));
        // The untagged WAV falls back to its folder name for the album and to Unknown for artist.
        assertEquals(List.of("Album X", "Album Y", "misc"), names(lib.albums()));
        assertEquals(List.of("<unknown>", "Artist A", "Artist B"), names(lib.artists()));
    }

    @Test
    void ignoresNonAudioFilesAndReportsUnreadableOnes() throws Exception {
        buildTree();
        Musikr.Result r = scan(StoredPlaylists.inMemory(), null);
        assertEquals(1, r.failed().size());
        assertEquals("corrupt.mp3", r.failed().get(0).getFileName().toString());
        assertEquals(4, r.library().songs().size());
    }

    @Test
    void progressReachesTheNumberOfCandidateFiles() throws Exception {
        buildTree();
        AtomicInteger progress = new AtomicInteger();
        scan(StoredPlaylists.inMemory(), progress);
        assertEquals(5, progress.get()); // 4 readable + the corrupt one; .txt and README never count
    }

    @Test
    void progressIsReportedInOrderEvenWithManyWorkers() throws Exception {
        Path dir = Files.createDirectories(root.resolve("many"));
        for (int i = 0; i < 60; i++) AudioFixtures.wav(dir.resolve(String.format("t%02d.wav", i)), 0.05);
        List<Integer> seen = java.util.Collections.synchronizedList(new ArrayList<>());
        // A slow callback widens the window in which two workers could otherwise overtake each
        // other between "count it" and "report it".
        Musikr.run(List.of(root), Interpretation.defaults(), StoredPlaylists.inMemory(), n -> {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            seen.add(n);
        });
        assertEquals(60, seen.size());
        for (int i = 0; i < seen.size(); i++) assertEquals(i + 1, seen.get(i).intValue(), "report #" + i);
    }

    @Test
    void albumAggregatesComeFromRealFiles() throws Exception {
        buildTree();
        Library lib = scan(StoredPlaylists.inMemory(), null).library();
        var album = lib.albums().stream().filter(a -> a.name().toString().equals("Album X")).findFirst().orElseThrow();
        assertEquals(3000, album.durationMs()); // 1s + 2s FLAC
        assertEquals(2, album.songs().size());
    }

    @Test
    void missingOrEmptyRootsYieldAnEmptyLibrary() throws Exception {
        Musikr.Result r = Musikr.run(List.of(root.resolve("does-not-exist"), root), Interpretation.defaults(),
                StoredPlaylists.inMemory(), null);
        assertTrue(r.library().empty());
        assertTrue(r.failed().isEmpty());
    }

    @Test
    void uidsAreStableAcrossRescans() throws Exception {
        buildTree();
        Library a = scan(StoredPlaylists.inMemory(), null).library();
        Library b = scan(StoredPlaylists.inMemory(), null).library();
        List<String> ua = a.songs().stream().map(s -> s.uid().toString()).sorted().toList();
        List<String> ub = b.songs().stream().map(s -> s.uid().toString()).sorted().toList();
        assertEquals(ua, ub);
    }

    @Test
    void playlistsSurviveARescanAndAreMatchedByUid() throws Exception {
        buildTree();
        Path store = root.resolveSibling(root.getFileName() + "-playlists.txt");
        try {
            Library first = scan(new StoredPlaylists(store), null).library();
            List<Song> picks = first.songs().stream()
                    .filter(s -> !s.name().raw().equals("loose")).limit(2).toList();
            first.createPlaylist("Favourites", picks);

            Library second = scan(new StoredPlaylists(store), null).library();
            Playlist p = second.findPlaylistByName("Favourites");
            assertNotNull(p);
            assertEquals(picks.stream().map(Song::uid).toList(), p.songs().stream().map(Song::uid).toList());
        } finally {
            Files.deleteIfExists(store);
        }
    }

    @Test
    void retaggingAFileIsPickedUpOnTheNextScan() throws Exception {
        buildTree();
        Path f = root.resolve("Artist A/Album X/01.flac");
        assertTrue(names(scan(StoredPlaylists.inMemory(), null).library().songs()).contains("First"));
        fields(f, "Renamed", "Artist A", "Album X", 1);
        List<String> after = names(scan(StoredPlaylists.inMemory(), null).library().songs());
        assertTrue(after.contains("Renamed"));
        assertFalse(after.contains("First"));
    }
}
