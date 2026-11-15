package arksic.playback;

import static org.junit.jupiter.api.Assertions.*;

import arksic.musikr.model.Library;
import arksic.musikr.model.Playlist;
import arksic.musikr.model.Song;
import arksic.playback.persist.PlaybackPersistence;
import arksic.playback.state.PlaybackStateManager.SavedState;
import arksic.playback.state.RepeatMode;
import arksic.playback.state.ShuffleMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlaybackPersistenceTest {
    private Path dir;
    private Path file;

    @BeforeEach
    void setUp() throws IOException {
        dir = Files.createTempDirectory("arksic-persist");
        file = dir.resolve("state.txt");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(file)) Files.delete(file);
        Files.deleteIfExists(file.resolveSibling("state.txt.tmp"));
        Files.deleteIfExists(dir);
    }

    /** Play, pause at a known position, and return the resulting saved state. */
    private SavedState playAndSave(PlaybackFixture fx, boolean shuffled, String start, long positionMs) {
        fx.play(start, shuffled);
        fx.manager.repeatMode(RepeatMode.ALL);
        fx.engine.position = positionMs;
        fx.manager.playing(false); // pausing freezes the mirrored position exactly
        SavedState saved = fx.manager.toSavedState();
        new PlaybackPersistence(file).save(saved);
        return saved;
    }

    private SavedState reload(Library library) {
        return new PlaybackPersistence(file).read(library);
    }

    private static List<String> uids(List<Song> songs) {
        return songs.stream().map(s -> s == null ? "null" : s.uid().toString()).toList();
    }

    // --- file level ---

    @Test
    void savedStateRoundTripsThroughTheFile() {
        PlaybackFixture fx = new PlaybackFixture();
        SavedState saved = playAndSave(fx, true, "Delta", 42_000);
        SavedState read = reload(fx.library);

        assertEquals(saved.positionMs(), read.positionMs());
        assertEquals(RepeatMode.ALL, read.repeatMode());
        assertEquals(saved.index(), read.index());
        assertEquals(saved.songUid(), read.songUid());
        assertEquals(uids(saved.heap()), uids(read.heap()));
        assertEquals(saved.shuffledMapping(), read.shuffledMapping());
        assertNull(read.parent());
    }

    @Test
    void theParentIsRestoredByUid() {
        PlaybackFixture fx = new PlaybackFixture();
        Song delta = fx.song("Delta");
        fx.manager.play(fx.factory.songFromAlbum(delta, ShuffleMode.OFF));
        fx.manager.playing(false);
        new PlaybackPersistence(file).save(fx.manager.toSavedState());
        assertSame(delta.album(), reload(fx.library).parent());
    }

    @Test
    void nothingPlayingMeansNothingToSave() {
        PlaybackFixture fx = new PlaybackFixture();
        assertNull(fx.manager.toSavedState());
        new PlaybackPersistence(file).save(null);
        assertFalse(Files.exists(file));
    }

    @Test
    void missingOrCorruptFilesReadAsNothing() throws IOException {
        PlaybackFixture fx = new PlaybackFixture();
        assertNull(reload(fx.library));
        Files.writeString(file, "this is not a state file\nat all\n");
        assertNull(reload(fx.library));
        Files.writeString(file, "position abc\nrepeat NONE\nindex 0\nsong x\nparent -\nmapping \nheap y\n");
        assertNull(reload(fx.library));
        Files.writeString(file, "position 0\nrepeat SIDEWAYS\nindex 0\n");
        assertNull(reload(fx.library));
    }

    @Test
    void aFileOfOnlyUnknownSongsRestoresToAnEmptyQueue() throws IOException {
        PlaybackFixture fx = new PlaybackFixture();
        playAndSave(fx, false, "Charlie", 1000);
        PlaybackFixture other = new PlaybackFixture(arksic.musikr.TestLibrary.of(
                arksic.musikr.TestLibrary.song().title("Totally").album("Else").artists("Someone")));
        SavedState read = reload(other.library);
        assertNotNull(read);
        assertDoesNotThrow(() -> other.manager.applySavedState(read, false));
        assertNull(other.manager.currentSong());
        assertTrue(other.manager.queue().isEmpty());
    }

    // --- applying ---

    private void roundTripIntoFreshPlayer(boolean shuffled) {
        PlaybackFixture fx = new PlaybackFixture();
        fx.play("Delta", shuffled);
        fx.manager.next();
        fx.manager.addToQueue(fx.song("Alpha")); // duplicate entry, to prove heap fidelity
        fx.engine.position = 42_000;
        fx.manager.repeatMode(RepeatMode.TRACK);
        fx.manager.playing(false);
        new PlaybackPersistence(file).save(fx.manager.toSavedState());
        List<String> queue = fx.queue();
        String current = fx.current();

        PlaybackFixture fresh = new PlaybackFixture();
        fresh.manager.applySavedState(reload(fresh.library), false);

        assertEquals(queue, fresh.queue());
        assertEquals(current, fresh.current());
        assertEquals(shuffled, fresh.manager.isShuffled());
        assertEquals(RepeatMode.TRACK, fresh.manager.repeatMode());
        assertEquals(new FakeAudioEngine.Load(fresh.manager.currentSong().path(), 42_000, false), fresh.engine.lastLoad(),
                "restored sessions come back paused at the saved position");
        assertEquals(42_000, fresh.manager.progression().calculateElapsedPositionMs());
        assertFalse(fresh.manager.progression().isPlaying());
        PlaybackAssert.consistent(fresh);
    }

    @Test void restoreUnshuffled() { roundTripIntoFreshPlayer(false); }

    @Test void restoreShuffled() { roundTripIntoFreshPlayer(true); }

    @Test
    void restoredPlayersKeepWorking() {
        PlaybackFixture fx = new PlaybackFixture();
        playAndSave(fx, false, "Charlie", 5_000);
        PlaybackFixture fresh = new PlaybackFixture();
        fresh.manager.applySavedState(reload(fresh.library), false);
        fresh.manager.playing(true);
        fresh.manager.next();
        assertEquals("Delta", fresh.current());
        fresh.manager.removeQueueItem(0);
        PlaybackAssert.consistent(fresh);
    }

    private void songDeletedFromLibrary(boolean shuffled, String start, String deleted) {
        PlaybackFixture fx = new PlaybackFixture();
        playAndSave(fx, shuffled, start, 7_000);
        List<String> expected = new java.util.ArrayList<>(fx.queue());
        expected.remove(deleted);
        String current = fx.current();

        Library smaller = PlaybackFixture.buildLibrary(8, deleted);
        PlaybackFixture fresh = new PlaybackFixture(smaller);
        fresh.manager.applySavedState(reload(smaller), false);

        String where = (shuffled ? "shuffled" : "plain") + " start=" + start + " deleted=" + deleted;
        assertEquals(expected, fresh.queue(), where);
        assertEquals(current, fresh.current(), where);
        assertEquals(expected.indexOf(current), fresh.manager.index(), where);
        PlaybackAssert.consistent(fresh);
    }

    @Test
    void aSongDeletedBeforeTheCurrentOneShiftsTheRestoredIndex() {
        songDeletedFromLibrary(false, "Echo", "Bravo");
    }

    @Test
    void aSongDeletedAfterTheCurrentOneLeavesItAlone() {
        songDeletedFromLibrary(false, "Charlie", "Hotel");
    }

    @Test
    void deletedSongsAreDroppedFromAShuffledQueueToo() {
        for (String deleted : new String[] {"Alpha", "Bravo", "Charlie", "Echo", "Foxtrot", "Golf", "Hotel"}) {
            songDeletedFromLibrary(true, "Delta", deleted);
        }
    }

    @Test
    void ifTheCurrentSongWasDeletedThereIsNothingToRestore() {
        PlaybackFixture fx = new PlaybackFixture();
        playAndSave(fx, false, "Charlie", 7_000);
        Library smaller = PlaybackFixture.buildLibrary(8, "Charlie");
        PlaybackFixture fresh = new PlaybackFixture(smaller);
        fresh.manager.applySavedState(reload(smaller), false);
        assertNull(fresh.manager.currentSong());
        assertTrue(fresh.manager.queue().isEmpty());
        assertTrue(fresh.engine.loads.isEmpty());
    }

    @Test
    void restoreNeverOverwritesPlaybackThatAlreadyStartedUnlessAsked() {
        PlaybackFixture fx = new PlaybackFixture();
        playAndSave(fx, false, "Charlie", 7_000);
        SavedState saved = reload(fx.library);

        PlaybackFixture fresh = new PlaybackFixture();
        fresh.play("Hotel", false);
        fresh.manager.applySavedState(saved, false);
        assertEquals("Hotel", fresh.current(), "user already started something else");

        fresh.manager.applySavedState(saved, true);
        assertEquals("Charlie", fresh.current(), "destructive restore replaces it");
    }

    @Test
    void aPlaylistParentSurvivesTheTrip() {
        PlaybackFixture fx = new PlaybackFixture();
        Library withPl = fx.library.createPlaylist("Mix", fx.songs("Echo", "Alpha"));
        Playlist p = withPl.findPlaylistByName("Mix");
        fx.manager.play(fx.factory.playlist(p, ShuffleMode.OFF));
        fx.manager.playing(false);
        new PlaybackPersistence(file).save(fx.manager.toSavedState());
        SavedState read = reload(withPl);
        assertSame(p, read.parent());
        assertEquals(List.of("Echo", "Alpha"), PlaybackFixture.names(read.heap()));
    }
}
