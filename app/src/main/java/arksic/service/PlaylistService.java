package arksic.service;

import arksic.model.RepeatMode;
import arksic.model.Track;
import arksic.util.PlaybackMath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Owns the in-memory ordered track list, the current index, and
 * repeat/shuffle state. Never imports a Swing type or reaches into
 * {@code PlaybackService} — see docs/foundation/architecture.md#services.
 */
public final class PlaylistService {

    private final List<Track> tracks = new ArrayList<>();
    private final Random random;

    private int currentIndex = -1;
    private RepeatMode repeatMode = RepeatMode.OFF;
    private boolean shuffleEnabled = false;
    private List<Integer> shuffleOrder = List.of();
    private int shufflePosition = 0;

    public PlaylistService() {
        this(new java.security.SecureRandom());
    }

    public PlaylistService(Random random) {
        this.random = random;
    }

    public List<Track> tracks() {
        return Collections.unmodifiableList(tracks);
    }

    public void add(Track track) {
        tracks.add(track);
        if (currentIndex == -1) {
            currentIndex = 0;
        }
        rebuildShuffleOrderIfNeeded();
    }

    public void addAll(List<Track> newTracks) {
        tracks.addAll(newTracks);
        if (currentIndex == -1 && !tracks.isEmpty()) {
            currentIndex = 0;
        }
        rebuildShuffleOrderIfNeeded();
    }

    public void remove(int index) {
        if (index < 0 || index >= tracks.size()) {
            return;
        }
        tracks.remove(index);
        if (tracks.isEmpty()) {
            currentIndex = -1;
        } else if (currentIndex >= tracks.size()) {
            currentIndex = tracks.size() - 1;
        } else if (index < currentIndex) {
            currentIndex--;
        }
        rebuildShuffleOrderIfNeeded();
    }

    public void reorder(int fromIndex, int toIndex) {
        if (fromIndex == toIndex
                || fromIndex < 0 || fromIndex >= tracks.size()
                || toIndex < 0 || toIndex >= tracks.size()) {
            return;
        }
        Track moved = tracks.remove(fromIndex);
        tracks.add(toIndex, moved);
        if (currentIndex == fromIndex) {
            currentIndex = toIndex;
        }
        rebuildShuffleOrderIfNeeded();
    }

    public void clear() {
        tracks.clear();
        currentIndex = -1;
        shuffleOrder = List.of();
        shufflePosition = 0;
    }

    public Optional<Track> current() {
        return currentIndex >= 0 && currentIndex < tracks.size()
                ? Optional.of(tracks.get(currentIndex))
                : Optional.empty();
    }

    public int currentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int index) {
        if (index >= 0 && index < tracks.size()) {
            currentIndex = index;
            shufflePosition = shuffleOrder.indexOf(index);
        }
    }

    public RepeatMode repeatMode() {
        return repeatMode;
    }

    public void setRepeatMode(RepeatMode mode) {
        this.repeatMode = mode;
    }

    public boolean isShuffleEnabled() {
        return shuffleEnabled;
    }

    public void setShuffleEnabled(boolean enabled) {
        this.shuffleEnabled = enabled;
        rebuildShuffleOrderIfNeeded();
    }

    /** Advances to (and returns) the next track per repeat/shuffle state, or empty at the end. */
    public Optional<Track> advance() {
        if (tracks.isEmpty()) {
            return Optional.empty();
        }
        if (shuffleEnabled) {
            return advanceShuffled();
        }
        Optional<Integer> next = PlaybackMath.nextIndex(currentIndex, tracks.size(), repeatMode);
        next.ifPresent(i -> currentIndex = i);
        return next.map(tracks::get);
    }

    private Optional<Track> advanceShuffled() {
        if (shuffleOrder.isEmpty()) {
            rebuildShuffleOrderIfNeeded();
        }
        if (repeatMode == RepeatMode.REPEAT_ONE) {
            return current();
        }
        int nextPosition = shufflePosition + 1;
        if (nextPosition >= shuffleOrder.size()) {
            if (repeatMode != RepeatMode.REPEAT_ALL) {
                return Optional.empty();
            }
            nextPosition = 0;
        }
        shufflePosition = nextPosition;
        currentIndex = shuffleOrder.get(shufflePosition);
        return current();
    }

    private void rebuildShuffleOrderIfNeeded() {
        if (shuffleEnabled && !tracks.isEmpty()) {
            int anchor = Math.max(0, currentIndex);
            shuffleOrder = PlaybackMath.buildShuffleOrder(tracks.size(), anchor, random);
            shufflePosition = 0;
        } else {
            shuffleOrder = List.of();
            shufflePosition = 0;
        }
    }
}
