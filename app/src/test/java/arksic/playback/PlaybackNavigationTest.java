package arksic.playback;

import static org.junit.jupiter.api.Assertions.*;

import arksic.musikr.model.Song;
import arksic.playback.state.RepeatMode;
import arksic.playback.state.ShuffleMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaybackNavigationTest {
    private final PlaybackFixture fx = new PlaybackFixture();

    private void assertLoaded(String name, boolean playing) {
        FakeAudioEngine.Load load = fx.engine.lastLoad();
        assertEquals(fx.song(name).path(), load.path(), "engine should hold " + name);
        assertEquals(0, load.startMs());
        assertEquals(playing, load.play(), "play flag for " + name);
        assertEquals(playing, fx.manager.progression().isPlaying());
        PlaybackAssert.consistent(fx);
    }

    // --- next ---

    @Test
    void nextMovesToTheFollowingSongAndPlays() {
        fx.playUnshuffled("Charlie");
        fx.manager.next();
        assertEquals("Delta", fx.current());
        assertEquals(3, fx.manager.index());
        assertLoaded("Delta", true);
        assertEquals(1, fx.events.indexMoves);
    }

    @Test
    void nextAtTheEndWrapsToTheStartAndPausesWhenNotRepeating() {
        fx.playUnshuffled("Hotel");
        fx.manager.next();
        assertEquals("Alpha", fx.current());
        assertLoaded("Alpha", false);
    }

    @Test
    void nextAtTheEndWrapsAndKeepsPlayingWhenRepeatingAll() {
        fx.playUnshuffled("Hotel");
        fx.manager.repeatMode(RepeatMode.ALL);
        fx.manager.next();
        assertEquals("Alpha", fx.current());
        assertLoaded("Alpha", true);
    }

    @Test
    void nextIgnoresRepeatTrackAndSkipsOn() {
        fx.playUnshuffled("Charlie");
        fx.manager.repeatMode(RepeatMode.TRACK);
        fx.manager.next();
        assertEquals("Delta", fx.current());
        assertLoaded("Delta", true);
    }

    @Test
    void nextOnAnEmptyQueueIsHarmless() {
        assertDoesNotThrow(fx.manager::next);
        assertDoesNotThrow(fx.manager::prev);
        assertDoesNotThrow(() -> fx.manager.goTo(0));
        assertTrue(fx.engine.loads.isEmpty());
        assertNull(fx.manager.currentSong());
    }

    @Test
    void nextWhilePausedStartsPlayingUnlessRememberPause() {
        fx.playUnshuffled("Alpha");
        fx.manager.playing(false);
        fx.manager.next();
        assertLoaded("Bravo", true);

        fx.manager.playing(false);
        fx.settings.rememberPause = true;
        fx.manager.next();
        assertLoaded("Charlie", false);
    }

    @Test
    void rememberPauseDoesNotStopPlaybackThatWasPlayingAcrossTheWrap() {
        fx.settings.rememberPause = true;
        fx.playUnshuffled("Hotel");
        fx.manager.next();
        assertLoaded("Alpha", true);
    }

    @Test
    void shuffledNextFollowsThePlayOrder() {
        fx.playShuffled("Delta");
        List<Song> order = fx.manager.queue();
        fx.manager.next();
        assertEquals(1, fx.manager.index());
        assertSame(order.get(1), fx.manager.currentSong());
        fx.manager.next();
        assertSame(order.get(2), fx.manager.currentSong());
        PlaybackAssert.consistent(fx);
    }

    @Test
    void shuffledNextAtTheEndWrapsToTheFirstOfThePlayOrder() {
        fx.playShuffled("Delta");
        List<Song> order = fx.manager.queue();
        fx.manager.goTo(order.size() - 1);
        fx.manager.next();
        assertEquals(0, fx.manager.index());
        assertSame(order.get(0), fx.manager.currentSong());
        assertFalse(fx.engine.isPlaying(), "wrapping without repeat pauses");
    }

    // --- prev ---

    @Test
    void prevRestartsTheSongWhenItHasPlayedForAWhile() {
        fx.playUnshuffled("Charlie");
        fx.engine.position = 10_000;
        int loads = fx.engine.loads.size();
        fx.manager.prev();
        assertEquals("Charlie", fx.current());
        assertEquals(loads + 1, fx.engine.loads.size(), "restart reloads from 0");
        assertLoaded("Charlie", true);
    }

    @Test
    void prevGoesToThePreviousSongNearTheStart() {
        fx.playUnshuffled("Charlie");
        fx.engine.position = 2_000;
        fx.manager.prev();
        assertEquals("Bravo", fx.current());
        assertLoaded("Bravo", true);
    }

    @Test
    void prevAtTheThresholdStillGoesBack() {
        fx.playUnshuffled("Charlie");
        fx.engine.position = 3_000;
        fx.manager.prev();
        assertEquals("Bravo", fx.current());
    }

    @Test
    void prevOnTheFirstSongRestartsIt() {
        fx.playUnshuffled("Alpha");
        fx.engine.position = 1_000;
        fx.manager.prev();
        assertEquals("Alpha", fx.current());
        assertLoaded("Alpha", true);
    }

    @Test
    void prevOnTheFirstSongWrapsWhenRepeatingAll() {
        fx.playUnshuffled("Alpha");
        fx.manager.repeatMode(RepeatMode.ALL);
        fx.engine.position = 1_000;
        fx.manager.prev();
        assertEquals("Hotel", fx.current());
    }

    @Test
    void prevAlwaysGoesBackWhenRewindIsDisabled() {
        fx.settings.rewindWithPrev = false;
        fx.playUnshuffled("Charlie");
        fx.engine.position = 100_000;
        fx.manager.prev();
        assertEquals("Bravo", fx.current());
    }

    @Test
    void shuffledPrevRetracesThePlayOrder() {
        fx.playShuffled("Delta");
        List<Song> order = fx.manager.queue();
        fx.manager.next();
        fx.manager.next();
        fx.engine.position = 0;
        fx.manager.prev();
        assertSame(order.get(1), fx.manager.currentSong());
        PlaybackAssert.consistent(fx);
    }

    // --- goTo ---

    @Test
    void goToJumpsToAQueuePosition() {
        fx.playUnshuffled("Alpha");
        fx.manager.goTo(5);
        assertEquals("Foxtrot", fx.current());
        assertLoaded("Foxtrot", true);
    }

    @Test
    void shuffledGoToUsesQueuePositionsNotHeapPositions() {
        fx.playShuffled("Alpha");
        List<Song> order = fx.manager.queue();
        fx.manager.goTo(4);
        assertEquals(4, fx.manager.index());
        assertSame(order.get(4), fx.manager.currentSong());
        PlaybackAssert.consistent(fx);
    }

    @Test
    void goToOutOfRangeIsIgnored() {
        fx.playUnshuffled("Charlie");
        int loads = fx.engine.loads.size();
        for (int bad : new int[] {-1, 8, 100}) fx.manager.goTo(bad);
        assertEquals("Charlie", fx.current());
        assertEquals(loads, fx.engine.loads.size());
    }

    // --- end of track / repeat ---

    @Test
    void whenASongEndsTheNextOneStartsAutomatically() {
        fx.playUnshuffled("Charlie");
        fx.engine.fireEnded();
        assertEquals("Delta", fx.current());
        assertLoaded("Delta", true);
    }

    @Test
    void whenTheLastSongEndsWithoutRepeatTheQueueRewindsAndPauses() {
        fx.playUnshuffled("Hotel");
        fx.engine.fireEnded();
        assertEquals("Alpha", fx.current());
        assertLoaded("Alpha", false);
    }

    @Test
    void whenTheLastSongEndsWhileRepeatingAllTheQueueLoops() {
        fx.playUnshuffled("Hotel");
        fx.manager.repeatMode(RepeatMode.ALL);
        fx.engine.fireEnded();
        assertEquals("Alpha", fx.current());
        assertLoaded("Alpha", true);
    }

    @Test
    void repeatTrackReplaysTheSameSong() {
        fx.playUnshuffled("Charlie");
        fx.manager.repeatMode(RepeatMode.TRACK);
        int loads = fx.engine.loads.size();
        fx.engine.fireEnded();
        assertEquals("Charlie", fx.current());
        assertEquals(2, fx.manager.index());
        assertEquals(loads + 1, fx.engine.loads.size());
        assertLoaded("Charlie", true);
    }

    @Test
    void repeatTrackCanPauseAtEachRepeat() {
        fx.settings.pauseOnRepeat = true;
        fx.playUnshuffled("Charlie");
        fx.manager.repeatMode(RepeatMode.TRACK);
        fx.engine.fireEnded();
        assertEquals("Charlie", fx.current());
        assertLoaded("Charlie", false);
    }

    @Test
    void shuffledQueueEndsLikeAnyOther() {
        fx.playShuffled("Delta");
        List<Song> order = fx.manager.queue();
        fx.manager.goTo(order.size() - 1);
        fx.engine.fireEnded();
        assertEquals(0, fx.manager.index());
        assertSame(order.get(0), fx.manager.currentSong());
        assertFalse(fx.engine.isPlaying());
        PlaybackAssert.consistent(fx);
    }

    @Test
    void aPlaybackErrorSkipsToTheNextSong() {
        fx.playUnshuffled("Charlie");
        fx.engine.fireError();
        assertEquals("Delta", fx.current());
        assertLoaded("Delta", true);
    }

    @Test
    void aPlaybackErrorOnTheLastSongWrapsAndPauses() {
        fx.playUnshuffled("Hotel");
        fx.engine.fireError();
        assertEquals("Alpha", fx.current());
        assertLoaded("Alpha", false);
    }

    @Test
    void anEndEventWithNothingLoadedIsIgnored() {
        assertDoesNotThrow(fx.engine::fireEnded);
        assertDoesNotThrow(fx.engine::fireError);
        assertNull(fx.manager.currentSong());
    }

    // --- transport ---

    @Test
    void playingTogglesTheEngine() {
        fx.playUnshuffled("Alpha");
        fx.manager.playing(false);
        assertFalse(fx.engine.isPlaying());
        assertFalse(fx.manager.progression().isPlaying());
        fx.manager.playing(true);
        assertTrue(fx.engine.isPlaying());
        assertTrue(fx.manager.progression().isPlaying());
    }

    @Test
    void transportWithNothingLoadedIsHarmless() {
        assertDoesNotThrow(() -> fx.manager.playing(true));
        assertDoesNotThrow(() -> fx.manager.seekTo(1000));
        assertTrue(fx.engine.seeks.isEmpty());
        assertFalse(fx.manager.progression().isPlaying());
    }

    @Test
    void seekForwardsToTheEngineAndClampsNegatives() {
        fx.playUnshuffled("Alpha");
        fx.manager.seekTo(5_000);
        fx.manager.seekTo(-50);
        assertEquals(List.of(5_000L, 0L), fx.engine.seeks);
    }

    @Test
    void pausedProgressionIsExactAndClampedToTheSongLength() {
        fx.playUnshuffled("Alpha");
        fx.engine.position = 5_000;
        fx.manager.playing(false);
        assertEquals(5_000, fx.manager.progression().calculateElapsedPositionMs());
        fx.engine.position = 999_999;
        fx.manager.playing(false);
        assertEquals(180_000, fx.manager.progression().calculateElapsedPositionMs());
    }

    @Test
    void playingProgressionAdvancesWithTheClock() throws Exception {
        fx.playUnshuffled("Alpha");
        fx.engine.position = 1_000;
        fx.manager.playing(true);
        long a = fx.manager.progression().calculateElapsedPositionMs();
        Thread.sleep(60);
        long b = fx.manager.progression().calculateElapsedPositionMs();
        assertTrue(a >= 1_000 && a < 4_000, "starts near the engine position: " + a); // slack for slow CI
        assertTrue(b - a >= 40, "advances without being polled: " + (b - a));
    }

    @Test
    void repeatModeIsStoredAndAnnounced() {
        fx.playUnshuffled("Alpha");
        fx.manager.repeatMode(RepeatMode.ALL);
        assertEquals(RepeatMode.ALL, fx.manager.repeatMode());
        assertEquals(RepeatMode.ALL, fx.holder.repeatMode());
        assertEquals(RepeatMode.TRACK, RepeatMode.ALL.increment());
        assertEquals(RepeatMode.NONE, RepeatMode.TRACK.increment());
        assertEquals(RepeatMode.ALL, RepeatMode.NONE.increment());
    }

    @Test
    void endSessionPausesAndAnnounces() {
        fx.playUnshuffled("Alpha");
        fx.manager.endSession();
        assertFalse(fx.engine.isPlaying());
        assertEquals(1, fx.events.sessionEnds);
    }

    @Test
    void everyMutationAsksForTheStateToBeSaved() {
        fx.playUnshuffled("Alpha");
        int saves = fx.saveRequests;
        fx.manager.next();
        fx.manager.playing(false);
        fx.manager.repeatMode(RepeatMode.ALL);
        fx.manager.seekTo(1000);
        fx.manager.shuffled(true);
        assertEquals(saves + 5, fx.saveRequests);
    }

    @Test
    void usingAnImplicitShuffleCommandKeepsShuffleAcrossNewPlayback() {
        fx.playShuffled("Alpha");
        fx.manager.play(fx.factory.songFromAll(fx.song("Echo"), ShuffleMode.IMPLICIT));
        assertTrue(fx.manager.isShuffled());
        assertEquals("Echo", fx.current());
    }
}
