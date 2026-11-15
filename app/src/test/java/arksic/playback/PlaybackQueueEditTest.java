package arksic.playback;

import static org.junit.jupiter.api.Assertions.*;

import arksic.musikr.model.Song;
import arksic.playback.state.QueueChange;
import arksic.playback.state.ShuffleMode;
import arksic.playback.state.UpdateInstructions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Every queue edit is checked against a plain list model, in both modes: whatever the manager
 * reports as "the queue" must equal the model, and the playing song must follow its own entry.
 */
class PlaybackQueueEditTest {
    private final PlaybackFixture fx = new PlaybackFixture();

    /** All 8 songs, positioned on queue index {@code at}. */
    private void startFull(boolean shuffled, int at) {
        fx.play("Alpha", shuffled);
        fx.manager.goTo(at);
        PlaybackAssert.consistent(fx);
    }

    /** Only the first four songs queued, so Echo..Hotel are free to insert. */
    private void startSmall(boolean shuffled) {
        fx.manager.play(fx.factory.songs(fx.songs("Alpha", "Bravo", "Charlie", "Delta"),
                shuffled ? ShuffleMode.ON : ShuffleMode.OFF));
        PlaybackAssert.consistent(fx);
    }

    // --- play next ---

    private void playNextInsertsAfterTheCurrentSong(boolean shuffled) {
        startSmall(shuffled);
        fx.manager.next(); // so "after current" isn't just "after the first"
        List<String> before = fx.queue();
        int cur = fx.manager.index();
        Song playing = fx.manager.currentSong();
        int loads = fx.engine.loads.size();

        fx.manager.playNext(fx.songs("Echo", "Foxtrot"));

        List<String> expected = new ArrayList<>(before);
        expected.addAll(cur + 1, List.of("Echo", "Foxtrot"));
        assertEquals(expected, fx.queue());
        assertSame(playing, fx.manager.currentSong());
        assertEquals(cur, fx.manager.index());
        assertEquals(loads, fx.engine.loads.size(), "inserting must not reload the song");
        assertEquals(new UpdateInstructions.Add(cur + 1, 2), fx.events.lastChange().instructions());
        PlaybackAssert.consistent(fx);

        fx.manager.next();
        assertEquals("Echo", fx.current(), "the inserted songs play next, in order");
        fx.manager.next();
        assertEquals("Foxtrot", fx.current());
    }

    @Test void playNextUnshuffled() { playNextInsertsAfterTheCurrentSong(false); }

    @Test void playNextShuffled() { playNextInsertsAfterTheCurrentSong(true); }

    @Test
    void playNextWithASingleSong() {
        startSmall(false);
        fx.manager.playNext(fx.song("Echo"));
        assertEquals(List.of("Alpha", "Echo", "Bravo", "Charlie", "Delta"), fx.queue());
    }

    @Test
    void playNextWithNothingPlayingStartsPlayback() {
        fx.manager.playNext(fx.songs("Echo", "Foxtrot"));
        assertEquals(List.of("Echo", "Foxtrot"), fx.queue());
        assertEquals("Echo", fx.current());
        assertTrue(fx.engine.isPlaying());
        PlaybackAssert.consistent(fx);
    }

    @Test
    void addingNothingChangesNothing() {
        startSmall(false);
        List<String> before = fx.queue();
        fx.manager.playNext(List.of());
        fx.manager.addToQueue(List.of());
        assertEquals(before, fx.queue());
        PlaybackAssert.consistent(fx);
    }

    // --- add to queue ---

    private void addToQueueAppends(boolean shuffled) {
        startSmall(shuffled);
        List<String> before = fx.queue();
        Song playing = fx.manager.currentSong();
        int idx = fx.manager.index();

        fx.manager.addToQueue(fx.songs("Echo", "Foxtrot"));

        List<String> expected = new ArrayList<>(before);
        expected.addAll(List.of("Echo", "Foxtrot"));
        assertEquals(expected, fx.queue());
        assertSame(playing, fx.manager.currentSong());
        assertEquals(idx, fx.manager.index());
        assertEquals(new UpdateInstructions.Add(before.size(), 2), fx.events.lastChange().instructions());
        assertEquals(QueueChange.Type.MAPPING, fx.events.lastChange().type());
        PlaybackAssert.consistent(fx);
    }

    @Test void addToQueueUnshuffled() { addToQueueAppends(false); }

    @Test void addToQueueShuffled() { addToQueueAppends(true); }

    @Test
    void addToQueueWithNothingPlayingStartsPlayback() {
        fx.manager.addToQueue(fx.song("Golf"));
        assertEquals("Golf", fx.current());
        assertTrue(fx.engine.isPlaying());
    }

    @Test
    void duplicatesAreAllowedAndIndependent() {
        startSmall(false);
        fx.manager.addToQueue(fx.song("Alpha"));
        assertEquals(List.of("Alpha", "Bravo", "Charlie", "Delta", "Alpha"), fx.queue());
        fx.manager.removeQueueItem(0);
        assertEquals(List.of("Bravo", "Charlie", "Delta", "Alpha"), fx.queue());
        PlaybackAssert.consistent(fx);
    }

    // --- move ---

    private void checkMove(boolean shuffled, int current, int from, int to) {
        PlaybackFixture f = new PlaybackFixture();
        f.play("Alpha", shuffled);
        f.manager.goTo(current);
        List<Song> model = new ArrayList<>(f.manager.queue());
        Song playing = f.manager.currentSong();
        int loads = f.engine.loads.size();

        f.manager.moveQueueItem(from, to);

        model.add(to, model.remove(from));
        String where = (shuffled ? "shuffled" : "plain") + " cur=" + current + " " + from + "->" + to;
        assertEquals(model, f.manager.queue(), where);
        assertSame(playing, f.manager.currentSong(), where);
        assertEquals(model.indexOf(playing), f.manager.index(), where);
        assertEquals(loads, f.engine.loads.size(), where + ": moving must not reload");
        PlaybackAssert.consistent(f);
    }

    @Test
    void everyMoveKeepsThePlayingSongUnderItsOwnEntry() {
        for (boolean shuffled : new boolean[] {false, true}) {
            for (int current : new int[] {0, 3, 7}) {
                for (int from = 0; from < 8; from++) {
                    for (int to = 0; to < 8; to++) {
                        if (from != to) checkMove(shuffled, current, from, to);
                    }
                }
            }
        }
    }

    @Test
    void manyRandomMovesStayConsistent() {
        for (boolean shuffled : new boolean[] {false, true}) {
            startFull(shuffled, 4);
            Random r = new Random(7);
            List<Song> model = new ArrayList<>(fx.manager.queue());
            for (int i = 0; i < 300; i++) {
                int from = r.nextInt(8);
                int to = r.nextInt(8);
                Song playing = fx.manager.currentSong();
                fx.manager.moveQueueItem(from, to);
                if (from != to) model.add(to, model.remove(from));
                assertEquals(model, fx.manager.queue());
                assertSame(playing, fx.manager.currentSong());
                PlaybackAssert.consistent(fx);
            }
        }
    }

    @Test
    void movingChangesTheIndexOnlyWhenThePlayingSongIsAffected() {
        startFull(false, 3);
        fx.manager.moveQueueItem(6, 7); // both after the current song
        assertEquals(QueueChange.Type.MAPPING, fx.events.lastChange().type());
        assertEquals(new UpdateInstructions.Move(6, 7), fx.events.lastChange().instructions());
        fx.manager.moveQueueItem(0, 7); // crosses the current song: its index shifts
        assertEquals(QueueChange.Type.INDEX, fx.events.lastChange().type());
        assertEquals(2, fx.manager.index());
    }

    @Test
    void invalidMovesAreIgnored() {
        startFull(false, 3);
        List<String> before = fx.queue();
        int events = fx.events.changes.size();
        fx.manager.moveQueueItem(2, 2);
        fx.manager.moveQueueItem(-1, 2);
        fx.manager.moveQueueItem(2, 99);
        fx.manager.moveQueueItem(99, 0);
        assertEquals(before, fx.queue());
        assertEquals(events, fx.events.changes.size());
    }

    // --- remove ---

    private void removeBeforeCurrent(boolean shuffled) {
        startFull(shuffled, 3);
        Song playing = fx.manager.currentSong();
        List<String> model = new ArrayList<>(fx.queue());
        model.remove(1);
        int loads = fx.engine.loads.size();
        fx.manager.removeQueueItem(1);
        assertEquals(model, fx.queue());
        assertSame(playing, fx.manager.currentSong());
        assertEquals(2, fx.manager.index());
        assertEquals(loads, fx.engine.loads.size());
        assertEquals(QueueChange.Type.INDEX, fx.events.lastChange().type());
        assertEquals(new UpdateInstructions.Remove(1, 1), fx.events.lastChange().instructions());
        PlaybackAssert.consistent(fx);
    }

    @Test void removeBeforeCurrentUnshuffled() { removeBeforeCurrent(false); }

    @Test void removeBeforeCurrentShuffled() { removeBeforeCurrent(true); }

    private void removeAfterCurrent(boolean shuffled) {
        startFull(shuffled, 3);
        Song playing = fx.manager.currentSong();
        fx.manager.removeQueueItem(6);
        assertEquals(7, fx.manager.queue().size());
        assertSame(playing, fx.manager.currentSong());
        assertEquals(3, fx.manager.index());
        assertEquals(QueueChange.Type.MAPPING, fx.events.lastChange().type());
        PlaybackAssert.consistent(fx);
    }

    @Test void removeAfterCurrentUnshuffled() { removeAfterCurrent(false); }

    @Test void removeAfterCurrentShuffled() { removeAfterCurrent(true); }

    private void removeCurrentPlaysTheNextSong(boolean shuffled) {
        startFull(shuffled, 3);
        List<Song> before = new ArrayList<>(fx.manager.queue());
        int loads = fx.engine.loads.size();
        fx.manager.removeQueueItem(3);
        assertEquals(7, fx.manager.queue().size());
        assertSame(before.get(4), fx.manager.currentSong(), "the next song in play order takes over");
        assertEquals(3, fx.manager.index());
        assertEquals(loads + 1, fx.engine.loads.size());
        assertTrue(fx.engine.isPlaying());
        assertEquals(QueueChange.Type.SONG, fx.events.lastChange().type());
        assertFalse(fx.manager.queue().contains(before.get(3)));
        PlaybackAssert.consistent(fx);
    }

    @Test void removeCurrentUnshuffled() { removeCurrentPlaysTheNextSong(false); }

    @Test void removeCurrentShuffled() { removeCurrentPlaysTheNextSong(true); }

    private void removeCurrentAtTheEndFallsBack(boolean shuffled) {
        startFull(shuffled, 7);
        List<Song> before = new ArrayList<>(fx.manager.queue());
        fx.manager.removeQueueItem(7);
        assertSame(before.get(6), fx.manager.currentSong());
        assertEquals(6, fx.manager.index());
        PlaybackAssert.consistent(fx);
    }

    @Test void removeLastCurrentUnshuffled() { removeCurrentAtTheEndFallsBack(false); }

    @Test void removeLastCurrentShuffled() { removeCurrentAtTheEndFallsBack(true); }

    @Test
    void removingTheCurrentSongWhilePausedKeepsTheNewOnePausedWithRememberPause() {
        fx.settings.rememberPause = true;
        startFull(false, 3);
        fx.manager.playing(false);
        fx.manager.removeQueueItem(3);
        assertFalse(fx.engine.lastLoad().play());
        PlaybackAssert.consistent(fx);
    }

    private void removingEverythingOneByOne(boolean shuffled, boolean fromFront) {
        startFull(shuffled, 2);
        Random r = new Random(3);
        while (!fx.manager.queue().isEmpty()) {
            int at = fromFront ? 0 : r.nextInt(fx.manager.queue().size());
            int sizeBefore = fx.manager.queue().size();
            fx.manager.removeQueueItem(at);
            assertEquals(sizeBefore - 1, fx.manager.queue().size());
            PlaybackAssert.consistent(fx);
        }
        assertNull(fx.manager.currentSong());
        assertEquals(-1, fx.manager.index());
        assertNull(fx.engine.path);
        assertTrue(fx.engine.stops.get() > 0);
    }

    @Test void removeAllFromFrontUnshuffled() { removingEverythingOneByOne(false, true); }

    @Test void removeAllFromFrontShuffled() { removingEverythingOneByOne(true, true); }

    @Test void removeAllRandomlyUnshuffled() { removingEverythingOneByOne(false, false); }

    @Test void removeAllRandomlyShuffled() { removingEverythingOneByOne(true, false); }

    @Test
    void anEmptiedQueueCanBeRestartedAndIgnoresNavigation() {
        startSmall(false);
        for (int i = 0; i < 4; i++) fx.manager.removeQueueItem(0);
        assertDoesNotThrow(fx.manager::next);
        assertDoesNotThrow(fx.manager::prev);
        assertDoesNotThrow(() -> fx.manager.goTo(0));
        assertDoesNotThrow(() -> fx.manager.moveQueueItem(0, 1));
        assertDoesNotThrow(() -> fx.manager.removeQueueItem(0));
        PlaybackAssert.consistent(fx);
        fx.manager.playNext(fx.song("Hotel"));
        assertEquals("Hotel", fx.current());
        PlaybackAssert.consistent(fx);
    }

    @Test
    void invalidRemovalsAreIgnored() {
        startFull(false, 3);
        List<String> before = fx.queue();
        fx.manager.removeQueueItem(-1);
        fx.manager.removeQueueItem(8);
        assertEquals(before, fx.queue());
    }

    // --- shuffle toggling ---

    @Test
    void turningShuffleOnKeepsThePlayingSongAndLeadsWithIt() {
        startFull(false, 3);
        int loads = fx.engine.loads.size();
        Song playing = fx.manager.currentSong();
        fx.manager.shuffled(true);
        assertTrue(fx.manager.isShuffled());
        assertSame(playing, fx.manager.currentSong());
        assertEquals(0, fx.manager.index());
        assertEquals(new HashSet<>(fx.queue()), new HashSet<>(List.of(PlaybackFixture.NAMES)));
        assertEquals(8, fx.queue().size());
        assertEquals(loads, fx.engine.loads.size());
        assertEquals(1, fx.events.reorders);
        PlaybackAssert.consistent(fx);
    }

    @Test
    void turningShuffleOffRestoresTheOriginalOrderAroundThePlayingSong() {
        startFull(false, 3);
        fx.manager.shuffled(true);
        fx.manager.next();
        Song playing = fx.manager.currentSong();
        fx.manager.shuffled(false);
        assertFalse(fx.manager.isShuffled());
        assertEquals(List.of(PlaybackFixture.NAMES), fx.queue());
        assertSame(playing, fx.manager.currentSong());
        assertEquals(fx.songs.indexOf(playing), fx.manager.index());
        PlaybackAssert.consistent(fx);
    }

    @Test
    void shuffleRoundTripKeepsSongsAddedBeforeIt() {
        startSmall(false);
        fx.manager.playNext(fx.song("Echo")); // heap order: Alpha, Echo, Bravo, Charlie, Delta
        List<String> heapOrder = fx.queue();
        fx.manager.shuffled(true);
        fx.manager.shuffled(false);
        assertEquals(heapOrder, fx.queue());
    }

    @Test
    void songsAddedWhileShuffledKeepTheirPlaceWhenShuffleTurnsOff() {
        startSmall(true);
        fx.manager.addToQueue(fx.song("Echo"));
        fx.manager.shuffled(false);
        assertEquals(5, fx.queue().size());
        assertTrue(fx.queue().contains("Echo"));
        PlaybackAssert.consistent(fx);
    }

    @Test
    void shufflingAnEmptyQueueIsHarmless() {
        assertDoesNotThrow(() -> fx.manager.shuffled(true));
        assertDoesNotThrow(() -> fx.manager.shuffled(false));
        assertNull(fx.manager.currentSong());
    }

    @Test
    void reshufflingGivesAFreshPermutationEachTime() {
        startFull(false, 0);
        HashSet<List<String>> seen = new HashSet<>();
        for (int i = 0; i < 12; i++) {
            fx.manager.shuffled(true);
            seen.add(fx.queue());
            PlaybackAssert.consistent(fx);
        }
        assertTrue(seen.size() > 1, "twelve reshuffles of 8 songs should not all match");
    }
}
