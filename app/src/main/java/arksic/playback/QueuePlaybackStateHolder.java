/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.playback;

import arksic.audio.AudioEngine;
import arksic.musikr.model.MusicParent;
import arksic.musikr.model.Song;
import arksic.playback.replaygain.ReplayGain;
import arksic.playback.state.PlaybackCommand;
import arksic.playback.state.PlaybackStateHolder;
import arksic.playback.state.PlaybackStateManager;
import arksic.playback.state.Progression;
import arksic.playback.state.RawQueue;
import arksic.playback.state.RepeatMode;
import arksic.playback.state.StateAck;
import java.util.ArrayList;
import java.util.List;

/**
 * The queue + player owner. This replaces Auxio's ExoPlaybackStateHolder: where Auxio leaned on
 * ExoPlayer's timeline (media items, shuffle order, repeat modes, seekToNext...), this class
 * implements the same semantics itself over an {@link AudioEngine}.
 *
 * <p>Threading: this class has no lock of its own. Every entry point is either called by
 * {@link PlaybackStateManager} (which holds its monitor) or, for engine callbacks, re-enters through
 * {@link PlaybackStateManager#runExclusive}. One lock means no lock-order inversion between the
 * manager, this holder, and the audio decode thread.
 *
 * <p>State model: a {@code heap} of songs, an optional {@link BetterShuffleOrder} over heap
 * indices (null when not shuffled), and {@code current}, an index into the heap.
 *
 * <p>Deviations from Auxio, on purpose: "play next"/"add to queue"/"move" are implemented
 * directly against the heap and play order rather than via ExoPlayer's ShuffleOrder insertion,
 * which Auxio's own source marks as a hack.
 */
public final class QueuePlaybackStateHolder implements PlaybackStateHolder, AudioEngine.Listener {
    /** Exo's default: "previous" restarts the song if it has played longer than this. */
    private static final long MAX_SEEK_TO_PREVIOUS_MS = 3000;

    private final PlaybackStateManager manager;
    private final AudioEngine engine;
    private final PlaybackSettings settings;
    private final Runnable onStateChanged;

    private final List<Song> heap = new ArrayList<>();
    private BetterShuffleOrder order;
    private int current = -1;
    private RepeatMode repeat = RepeatMode.NONE;
    private MusicParent parent;
    private boolean playWhenReady;
    private float userVolume = 1f;

    /**
     * @param onStateChanged called after any change worth persisting (debounced by the callee)
     */
    public QueuePlaybackStateHolder(
            PlaybackStateManager manager, AudioEngine engine, PlaybackSettings settings, Runnable onStateChanged) {
        this.manager = manager;
        this.engine = engine;
        this.settings = settings;
        this.onStateChanged = onStateChanged;
        engine.setListener(this);
    }

    public void attach() {
        manager.registerStateHolder(this);
    }

    public void release() {
        manager.unregisterStateHolder(this);
        engine.stop();
    }

    /** User volume, 0..1. Combined with ReplayGain on every load. */
    public void setUserVolume(float volume) {
        manager.runExclusive(() -> {
            this.userVolume = Math.max(0f, Math.min(1f, volume));
            applyGain();
        });
    }

    // --- state ---

    @Override
    public MusicParent parent() {
        return parent;
    }

    @Override
    public Progression progression() {
        Song s = currentSong();
        if (s == null) return Progression.nil();
        long pos = Math.max(0, Math.min(engine.positionMs(), s.durationMs()));
        return Progression.from(playWhenReady, engine.isPlaying(), pos);
    }

    @Override
    public RepeatMode repeatMode() {
        return repeat;
    }

    @Override
    public RawQueue resolveQueue() {
        List<Integer> mapping = new ArrayList<>();
        if (order != null) for (int i : order.toArray()) mapping.add(i);
        return new RawQueue(List.copyOf(heap), mapping, current);
    }

    private Song currentSong() {
        return current >= 0 && current < heap.size() ? heap.get(current) : null;
    }

    // --- timeline helpers (the bits ExoPlayer's Timeline gave Auxio for free) ---

    private int firstIndex() {
        if (heap.isEmpty()) return -1;
        return order != null ? order.firstIndex() : 0;
    }

    private int nextIndex(int cur, boolean wrap) {
        if (heap.isEmpty() || cur < 0) return -1;
        int n;
        if (order != null) {
            n = order.nextIndex(cur);
            if (n == -1 && wrap) n = order.firstIndex();
        } else {
            n = cur + 1 < heap.size() ? cur + 1 : (wrap ? 0 : -1);
        }
        return n;
    }

    private int prevIndex(int cur, boolean wrap) {
        if (heap.isEmpty() || cur < 0) return -1;
        int p;
        if (order != null) {
            p = order.previousIndex(cur);
            if (p == -1 && wrap) p = order.lastIndex();
        } else {
            p = cur - 1 >= 0 ? cur - 1 : (wrap ? heap.size() - 1 : -1);
        }
        return p;
    }

    /** Heap index for a position in the (possibly shuffled) queue. */
    private int heapIndexAt(int queuePos) {
        return order != null ? order.toArray()[queuePos] : queuePos;
    }

    private void loadCurrent(long startMs, boolean play) {
        Song s = currentSong();
        if (s == null) {
            engine.stop();
            return;
        }
        applyGain();
        playWhenReady = play;
        engine.load(s.path(), startMs, play);
    }

    private void applyGain() {
        Song s = currentSong();
        float rg = ReplayGain.volumeFor(s, settings.replayGainMode, settings.replayGainPreAmp, parent);
        engine.setGain(rg * userVolume);
    }

    private void changed() {
        if (onStateChanged != null) onStateChanged.run();
    }

    // --- commands ---

    @Override
    public void newPlayback(PlaybackCommand command) {
        parent = command.parent();
        heap.clear();
        heap.addAll(command.queue());
        int start = -1;
        if (command.song() != null) {
            start = heap.indexOf(command.song());
            if (start == -1) throw new IllegalStateException("Start song not in queue");
        }
        order = command.shuffled() ? new BetterShuffleOrder(heap.size(), start) : null;
        current = start != -1 ? start : firstIndex();
        loadCurrent(0, true);
        manager.ack(this, new StateAck.NewPlayback());
        manager.ack(this, new StateAck.ProgressionChanged());
        changed();
    }

    @Override
    public void playing(boolean playing) {
        if (currentSong() == null) return;
        playWhenReady = playing;
        engine.setPlaying(playing);
        manager.ack(this, new StateAck.ProgressionChanged());
        changed();
    }

    @Override
    public void seekTo(long positionMs) {
        if (currentSong() == null) return;
        engine.seekTo(Math.max(0, positionMs));
        manager.ack(this, new StateAck.ProgressionChanged());
        changed();
    }

    @Override
    public void repeatMode(RepeatMode mode) {
        this.repeat = mode;
        manager.ack(this, new StateAck.RepeatModeChanged());
        changed();
    }

    @Override
    public void next() {
        if (heap.isEmpty()) return;
        // Pseudo-circular queue, as in Auxio: skipping forward wraps around, but when not
        // repeating, the wrap lands on the first song and pauses.
        int n = nextIndex(current, repeat == RepeatMode.ALL);
        if (n != -1) {
            current = n;
            loadCurrent(0, settings.rememberPause ? playWhenReady : true);
        } else {
            current = firstIndex();
            loadCurrent(0, settings.rememberPause && playWhenReady);
        }
        manager.ack(this, new StateAck.IndexMoved());
        manager.ack(this, new StateAck.ProgressionChanged());
        changed();
    }

    @Override
    public void prev() {
        if (heap.isEmpty()) return;
        int p = prevIndex(current, repeat == RepeatMode.ALL);
        boolean toPrevious;
        if (settings.rewindWithPrev) {
            toPrevious = p != -1 && engine.positionMs() <= MAX_SEEK_TO_PREVIOUS_MS;
        } else {
            toPrevious = p != -1;
        }
        if (toPrevious) current = p;
        loadCurrent(0, settings.rememberPause ? playWhenReady : true);
        manager.ack(this, new StateAck.IndexMoved());
        manager.ack(this, new StateAck.ProgressionChanged());
        changed();
    }

    @Override
    public void goTo(int index) {
        if (heap.isEmpty() || index < 0 || index >= heap.size()) return;
        current = heapIndexAt(index);
        loadCurrent(0, settings.rememberPause ? playWhenReady : true);
        manager.ack(this, new StateAck.IndexMoved());
        manager.ack(this, new StateAck.ProgressionChanged());
        changed();
    }

    @Override
    public void playNext(List<Song> songs, StateAck.PlayNext ack) {
        insert(songs, true);
        manager.ack(this, ack);
        changed();
    }

    @Override
    public void addToQueue(List<Song> songs, StateAck.AddToQueue ack) {
        insert(songs, false);
        manager.ack(this, ack);
        changed();
    }

    private void insert(List<Song> songs, boolean afterCurrent) {
        if (songs.isEmpty()) return;
        if (order == null) {
            int at = afterCurrent && current >= 0 ? current + 1 : heap.size();
            heap.addAll(at, songs);
        } else {
            int base = heap.size();
            heap.addAll(songs);
            int[] old = order.toArray();
            int pos = afterCurrent ? indexOf(old, current) + 1 : old.length;
            int[] next = new int[old.length + songs.size()];
            System.arraycopy(old, 0, next, 0, pos);
            for (int i = 0; i < songs.size(); i++) next[pos + i] = base + i;
            System.arraycopy(old, pos, next, pos + songs.size(), old.length - pos);
            order = new BetterShuffleOrder(next);
        }
    }

    private static int indexOf(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return -1;
    }

    @Override
    public void move(int from, int to, StateAck.Move ack) {
        if (heap.isEmpty() || from == to || from < 0 || to < 0 || from >= heap.size() || to >= heap.size()) return;
        if (order != null) {
            // Shuffled: only the play order changes; heap indices (and `current`) stay valid.
            order = order.cloneAndMovePosition(from, to);
        } else {
            Song s = heap.remove(from);
            heap.add(to, s);
            if (current == from) current = to;
            else if (from < current && current <= to) current--;
            else if (to <= current && current < from) current++;
        }
        manager.ack(this, ack);
        changed();
    }

    @Override
    public void remove(int at, StateAck.Remove ack) {
        if (heap.isEmpty() || at < 0 || at >= heap.size()) return;
        int hi = heapIndexAt(at);
        boolean songWillChange = hi == current;
        int replacement = -1;
        if (songWillChange) {
            replacement = nextIndex(current, false);
            if (replacement == -1) replacement = prevIndex(current, false);
            if (replacement > hi) replacement--;
        }
        heap.remove(hi);
        if (order != null) order = heap.isEmpty() ? null : order.cloneAndRemove(hi, hi + 1);
        if (songWillChange) current = heap.isEmpty() ? -1 : replacement;
        else if (current > hi) current--;

        if (songWillChange) loadCurrent(0, settings.rememberPause ? playWhenReady : true);
        manager.ack(this, ack);
        if (songWillChange) manager.ack(this, new StateAck.ProgressionChanged());
        changed();
    }

    @Override
    public void shuffled(boolean shuffled) {
        if (shuffled) {
            // Fresh seed, anchored on the current song.
            order = new BetterShuffleOrder(heap.size(), current);
        } else {
            order = null;
        }
        manager.ack(this, new StateAck.QueueReordered());
        changed();
    }

    @Override
    public void applySavedState(
            MusicParent parent, RawQueue rawQueue, long positionMs, RepeatMode repeatMode, StateAck.NewPlayback ack) {
        this.parent = parent;
        heap.clear();
        heap.addAll(rawQueue.heap());
        if (rawQueue.isShuffled()) {
            int[] a = new int[rawQueue.shuffledMapping().size()];
            for (int i = 0; i < a.length; i++) a[i] = rawQueue.shuffledMapping().get(i);
            order = new BetterShuffleOrder(a);
        } else {
            order = null;
        }
        current = rawQueue.heapIndex();
        loadCurrent(positionMs, false); // restored sessions come back paused
        this.repeat = repeatMode;
        manager.ack(this, new StateAck.RepeatModeChanged());
        if (ack != null) manager.ack(this, ack);
        manager.ack(this, new StateAck.ProgressionChanged());
    }

    @Override
    public void endSession() {
        playing(false);
        manager.ack(this, new StateAck.SessionEnded());
    }

    @Override
    public void reset(StateAck.NewPlayback ack) {
        heap.clear();
        order = null;
        current = -1;
        engine.stop();
        playWhenReady = false;
        manager.ack(this, ack);
        changed();
    }

    // --- engine callbacks (decode thread) ---

    @Override
    public void onEnded() {
        manager.runExclusive(this::handleEnded);
    }

    private void handleEnded() {
        if (currentSong() == null) return;
        if (repeat == RepeatMode.TRACK) {
            // Repeat the same song; optionally pause at each repeat.
            loadCurrent(0, !settings.pauseOnRepeat);
            manager.ack(this, new StateAck.ProgressionChanged());
            return;
        }
        int n = nextIndex(current, repeat == RepeatMode.ALL);
        if (n != -1) {
            current = n;
            loadCurrent(0, true);
            manager.ack(this, new StateAck.IndexMoved());
        } else {
            // End of the queue with no repeat: go back to the start and pause (Auxio: goto(0); pause()).
            current = firstIndex();
            loadCurrent(0, false);
            manager.ack(this, new StateAck.IndexMoved());
        }
        manager.ack(this, new StateAck.ProgressionChanged());
        changed();
    }

    @Override
    public void onError(Exception error) {
        // Auxio: if there's any issue, just go to the next song.
        System.err.println("Playback error, skipping: " + error);
        manager.runExclusive(() -> {
            if (!heap.isEmpty()) next();
        });
    }
}
