package arksic.util;

import arksic.model.PlaybackState;
import arksic.model.RepeatMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Pure, top-level functions used by the playback/playlist services. Kept
 * free of any service's state so each is independently testable — see
 * design-notes.md#code-philosophy, rule 3.
 */
public final class PlaybackMath {

    private PlaybackMath() {
    }

    /**
     * Resolves the next transport state for a given current state and
     * requested transition. A plain state machine, isolated from
     * {@code PlaybackService} so it can be tested without an audio line.
     */
    public static PlaybackState resolvePlaybackState(PlaybackState current, Transition transition) {
        return switch (transition) {
            case PLAY -> PlaybackState.PLAYING;
            case PAUSE -> current == PlaybackState.PLAYING ? PlaybackState.PAUSED : current;
            case RESUME -> current == PlaybackState.PAUSED ? PlaybackState.PLAYING : current;
            case STOP -> PlaybackState.STOPPED;
        };
    }

    /** What {@code PlaybackService} does after a transition request. */
    public enum Transition { PLAY, PAUSE, RESUME, STOP }

    /**
     * Builds a shuffled play order over {@code [0, trackCount)}, keeping
     * {@code currentIndex} first so shuffling mid-playlist doesn't restart
     * the current track. {@code trackCount <= 1} returns a trivial order.
     */
    public static List<Integer> buildShuffleOrder(int trackCount, int currentIndex, Random random) {
        if (trackCount <= 1) {
            return trackCount == 1 ? List.of(0) : List.of();
        }
        List<Integer> rest = new ArrayList<>();
        for (int i = 0; i < trackCount; i++) {
            if (i != currentIndex) {
                rest.add(i);
            }
        }
        Collections.shuffle(rest, random);
        List<Integer> order = new ArrayList<>();
        order.add(currentIndex);
        order.addAll(rest);
        return order;
    }

    /**
     * The next playlist index after {@code currentIndex}, or empty if
     * playback should stop (end of a non-repeating list).
     */
    public static java.util.Optional<Integer> nextIndex(int currentIndex, int trackCount, RepeatMode mode) {
        if (trackCount == 0) {
            return java.util.Optional.empty();
        }
        return switch (mode) {
            case REPEAT_ONE -> java.util.Optional.of(currentIndex);
            case REPEAT_ALL -> java.util.Optional.of((currentIndex + 1) % trackCount);
            case OFF -> {
                int next = currentIndex + 1;
                yield next < trackCount ? java.util.Optional.of(next) : java.util.Optional.empty();
            }
        };
    }

    /** Formats a duration in seconds as {@code m:ss} (or {@code h:mm:ss} past an hour). */
    public static String formatTrackDuration(int totalSeconds) {
        if (totalSeconds < 0) {
            return "0:00";
        }
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%d:%02d", minutes, seconds);
    }
}
