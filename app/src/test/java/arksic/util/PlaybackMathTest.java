package arksic.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import arksic.model.PlaybackState;
import arksic.model.RepeatMode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlaybackMathTest {

    @Test
    void playFromStoppedTransitionsToPlaying() {
        assertEquals(PlaybackState.PLAYING,
                PlaybackMath.resolvePlaybackState(PlaybackState.STOPPED, PlaybackMath.Transition.PLAY));
    }

    @Test
    void pauseOnlyAppliesWhilePlaying() {
        assertEquals(PlaybackState.PAUSED,
                PlaybackMath.resolvePlaybackState(PlaybackState.PLAYING, PlaybackMath.Transition.PAUSE));
        assertEquals(PlaybackState.STOPPED,
                PlaybackMath.resolvePlaybackState(PlaybackState.STOPPED, PlaybackMath.Transition.PAUSE));
    }

    @Test
    void stopAlwaysReturnsStopped() {
        assertEquals(PlaybackState.STOPPED,
                PlaybackMath.resolvePlaybackState(PlaybackState.PAUSED, PlaybackMath.Transition.STOP));
    }

    @Test
    void nextIndexOffStopsAtEndOfList() {
        assertEquals(Optional.of(1), PlaybackMath.nextIndex(0, 3, RepeatMode.OFF));
        assertEquals(Optional.empty(), PlaybackMath.nextIndex(2, 3, RepeatMode.OFF));
    }

    @Test
    void nextIndexRepeatAllWraps() {
        assertEquals(Optional.of(0), PlaybackMath.nextIndex(2, 3, RepeatMode.REPEAT_ALL));
    }

    @Test
    void nextIndexRepeatOneStaysPut() {
        assertEquals(Optional.of(1), PlaybackMath.nextIndex(1, 3, RepeatMode.REPEAT_ONE));
    }

    @Test
    void nextIndexEmptyPlaylistIsEmpty() {
        assertEquals(Optional.empty(), PlaybackMath.nextIndex(0, 0, RepeatMode.OFF));
    }

    @Test
    void shuffleOrderKeepsCurrentTrackFirstAndCoversAllIndices() {
        List<Integer> order = PlaybackMath.buildShuffleOrder(5, 2, new java.util.Random(42));
        assertEquals(5, order.size());
        assertEquals(2, order.get(0));
        assertEquals(Set.of(0, 1, 2, 3, 4), new java.util.HashSet<>(order));
    }

    @Test
    void shuffleOrderTrivialForSingleTrack() {
        assertEquals(List.of(0), PlaybackMath.buildShuffleOrder(1, 0, new java.util.Random(1)));
    }

    @Test
    void shuffleOrderEmptyForEmptyPlaylist() {
        assertTrue(PlaybackMath.buildShuffleOrder(0, 0, new java.util.Random(1)).isEmpty());
    }

    @Test
    void formatsDurationUnderAnHour() {
        assertEquals("0:00", PlaybackMath.formatTrackDuration(0));
        assertEquals("1:05", PlaybackMath.formatTrackDuration(65));
        assertEquals("59:59", PlaybackMath.formatTrackDuration(3599));
    }

    @Test
    void formatsDurationOverAnHour() {
        assertEquals("1:00:00", PlaybackMath.formatTrackDuration(3600));
    }

    @Test
    void formatsNegativeDurationAsZero() {
        assertEquals("0:00", PlaybackMath.formatTrackDuration(-5));
    }
}
