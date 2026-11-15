/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.playback.state;

import arksic.musikr.model.Music;
import arksic.musikr.model.MusicParent;
import arksic.musikr.model.Song;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mirrors the state of the {@link PlaybackStateHolder} for the UI and forwards commands to it.
 * Port of PlaybackStateManagerImpl. All entry points are synchronized; listeners are called on
 * whatever thread triggered the change, so Swing listeners must hop to the EDT themselves.
 */
public final class PlaybackStateManager {

    public interface Listener {
        default void onIndexMoved(int index) {}

        default void onQueueChanged(List<Song> queue, int index, QueueChange change) {}

        default void onQueueReordered(List<Song> queue, int index, boolean isShuffled) {}

        default void onNewPlayback(MusicParent parent, List<Song> queue, int index, boolean isShuffled) {}

        default void onProgressionChanged(Progression progression) {}

        default void onRepeatModeChanged(RepeatMode repeatMode) {}

        default void onSessionEnded() {}
    }

    /** A serialisable snapshot used for persistence. Heap entries may be null if a song vanished. */
    public record SavedState(
            long positionMs,
            RepeatMode repeatMode,
            MusicParent parent,
            List<Song> heap,
            List<Integer> shuffledMapping,
            int index,
            Music.UID songUid) {}

    private record Mirror(
            Progression progression, RepeatMode repeatMode, MusicParent parent, List<Song> queue,
            int index, boolean isShuffled, RawQueue rawQueue) {
        Mirror with(Progression p) { return new Mirror(p, repeatMode, parent, queue, index, isShuffled, rawQueue); }
        Mirror with(RepeatMode r) { return new Mirror(progression, r, parent, queue, index, isShuffled, rawQueue); }
        Mirror withQueue(List<Song> q, int i, RawQueue raw) { return new Mirror(progression, repeatMode, parent, q, i, isShuffled, raw); }
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile Mirror mirror =
            new Mirror(Progression.nil(), RepeatMode.NONE, null, List.of(), -1, false, RawQueue.nil());
    private volatile PlaybackStateHolder stateHolder;
    private volatile boolean initialized;

    public Progression progression() { return mirror.progression; }
    public RepeatMode repeatMode() { return mirror.repeatMode; }
    public MusicParent parent() { return mirror.parent; }
    public List<Song> queue() { return mirror.queue; }
    public int index() { return mirror.index; }
    public boolean isShuffled() { return mirror.isShuffled; }

    public Song currentSong() {
        Mirror m = mirror;
        return m.index >= 0 && m.index < m.queue.size() ? m.queue.get(m.index) : null;
    }

    public synchronized void addListener(Listener listener) {
        listeners.add(listener);
        if (initialized) {
            Mirror m = mirror;
            listener.onNewPlayback(m.parent, m.queue, m.index, m.isShuffled);
            listener.onProgressionChanged(m.progression);
            listener.onRepeatModeChanged(m.repeatMode);
        }
    }

    public synchronized void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public synchronized void registerStateHolder(PlaybackStateHolder holder) {
        if (stateHolder != null) return;
        stateHolder = holder;
        if (initialized && currentSong() != null) {
            holder.applySavedState(mirror.parent, mirror.rawQueue, mirror.progression.calculateElapsedPositionMs(), mirror.repeatMode, null);
        }
    }

    public synchronized void unregisterStateHolder(PlaybackStateHolder holder) {
        if (stateHolder == holder) stateHolder = null;
    }

    /** Run something while holding the manager's monitor (used by the holder's engine callbacks). */
    public synchronized void runExclusive(Runnable r) {
        r.run();
    }

    // --- playing ---

    public synchronized void play(PlaybackCommand command) {
        PlaybackStateHolder h = stateHolder;
        if (h == null) return;
        initialized = true;
        h.newPlayback(command);
    }

    // --- queue ---

    public synchronized void next() { if (stateHolder != null) stateHolder.next(); }

    public synchronized void prev() { if (stateHolder != null) stateHolder.prev(); }

    public synchronized void goTo(int index) { if (stateHolder != null) stateHolder.goTo(index); }

    public synchronized void playNext(List<Song> songs) {
        if (currentSong() == null) {
            play(new QueueCommand(songs));
        } else if (stateHolder != null) {
            stateHolder.playNext(songs, new StateAck.PlayNext(mirror.index + 1, songs.size()));
        }
    }

    public void playNext(Song song) { playNext(List.of(song)); }

    public synchronized void addToQueue(List<Song> songs) {
        if (currentSong() == null) {
            play(new QueueCommand(songs));
        } else if (stateHolder != null) {
            stateHolder.addToQueue(songs, new StateAck.AddToQueue(mirror.queue.size(), songs.size()));
        }
    }

    public void addToQueue(Song song) { addToQueue(List.of(song)); }

    private record QueueCommand(List<Song> queue) implements PlaybackCommand {
        public Song song() { return null; }
        public MusicParent parent() { return null; }
        public boolean shuffled() { return false; }
    }

    public synchronized void moveQueueItem(int src, int dst) {
        if (stateHolder != null) stateHolder.move(src, dst, new StateAck.Move(src, dst));
    }

    public synchronized void removeQueueItem(int at) {
        if (stateHolder != null) stateHolder.remove(at, new StateAck.Remove(at));
    }

    public synchronized void shuffled(boolean shuffled) {
        if (stateHolder != null) stateHolder.shuffled(shuffled);
    }

    // --- player controls ---

    public synchronized void playing(boolean isPlaying) { if (stateHolder != null) stateHolder.playing(isPlaying); }

    public synchronized void repeatMode(RepeatMode mode) { if (stateHolder != null) stateHolder.repeatMode(mode); }

    public synchronized void seekTo(long positionMs) { if (stateHolder != null) stateHolder.seekTo(positionMs); }

    public synchronized void endSession() { if (stateHolder != null) stateHolder.endSession(); }

    // --- acks from the holder ---

    public synchronized void ack(PlaybackStateHolder holder, StateAck ack) {
        if (stateHolder != holder) return;
        switch (ack) {
            case StateAck.IndexMoved a -> {
                RawQueue raw = holder.resolveQueue();
                Mirror m = mirror;
                mirror = new Mirror(m.progression, m.repeatMode, m.parent, m.queue, raw.resolveIndex(), m.isShuffled, raw);
                for (Listener l : listeners) l.onIndexMoved(mirror.index);
            }
            case StateAck.PlayNext a -> queueAdded(holder, a.at(), a.size());
            case StateAck.AddToQueue a -> queueAdded(holder, a.at(), a.size());
            case StateAck.Move a -> {
                RawQueue raw = holder.resolveQueue();
                int newIndex = raw.resolveIndex();
                QueueChange change = new QueueChange(
                        mirror.index != newIndex ? QueueChange.Type.INDEX : QueueChange.Type.MAPPING,
                        new UpdateInstructions.Move(a.from(), a.to()));
                mirror = mirror.withQueue(raw.resolveSongs(), newIndex, raw);
                for (Listener l : listeners) l.onQueueChanged(mirror.queue, mirror.index, change);
            }
            case StateAck.Remove a -> {
                RawQueue raw = holder.resolveQueue();
                int newIndex = raw.resolveIndex();
                QueueChange.Type type = a.index() == mirror.index ? QueueChange.Type.SONG
                        : mirror.index != newIndex ? QueueChange.Type.INDEX : QueueChange.Type.MAPPING;
                QueueChange change = new QueueChange(type, new UpdateInstructions.Remove(a.index(), 1));
                mirror = mirror.withQueue(raw.resolveSongs(), newIndex, raw);
                for (Listener l : listeners) l.onQueueChanged(mirror.queue, mirror.index, change);
            }
            case StateAck.QueueReordered a -> {
                RawQueue raw = holder.resolveQueue();
                Mirror m = mirror;
                mirror = new Mirror(m.progression, m.repeatMode, m.parent, raw.resolveSongs(), raw.resolveIndex(), raw.isShuffled(), raw);
                for (Listener l : listeners) l.onQueueReordered(mirror.queue, mirror.index, mirror.isShuffled);
            }
            case StateAck.NewPlayback a -> {
                RawQueue raw = holder.resolveQueue();
                Mirror m = mirror;
                mirror = new Mirror(m.progression, m.repeatMode, holder.parent(), raw.resolveSongs(), raw.resolveIndex(), raw.isShuffled(), raw);
                for (Listener l : listeners) l.onNewPlayback(mirror.parent, mirror.queue, mirror.index, mirror.isShuffled);
            }
            case StateAck.ProgressionChanged a -> {
                mirror = mirror.with(holder.progression());
                for (Listener l : listeners) l.onProgressionChanged(mirror.progression);
            }
            case StateAck.RepeatModeChanged a -> {
                mirror = mirror.with(holder.repeatMode());
                for (Listener l : listeners) l.onRepeatModeChanged(mirror.repeatMode);
            }
            case StateAck.SessionEnded a -> {
                for (Listener l : listeners) l.onSessionEnded();
            }
        }
    }

    private void queueAdded(PlaybackStateHolder holder, int at, int size) {
        RawQueue raw = holder.resolveQueue();
        QueueChange change = new QueueChange(QueueChange.Type.MAPPING, new UpdateInstructions.Add(at, size));
        mirror = mirror.withQueue(raw.resolveSongs(), mirror.index, raw);
        for (Listener l : listeners) l.onQueueChanged(mirror.queue, mirror.index, change);
    }

    // --- persistence ---

    public synchronized SavedState toSavedState() {
        Song current = currentSong();
        if (current == null) return null;
        Mirror m = mirror;
        return new SavedState(
                m.progression.calculateElapsedPositionMs(), m.repeatMode, m.parent,
                m.rawQueue.heap(), m.rawQueue.shuffledMapping(), m.index, current.uid());
    }

    /**
     * Apply a saved state. The heap may differ from when it was saved (the library changed), so
     * missing songs (null entries) are dropped and the shuffled mapping is re-pointed around them.
     */
    public synchronized void applySavedState(SavedState saved, boolean destructive) {
        if (initialized && !destructive) return;
        PlaybackStateHolder h = stateHolder;
        if (h == null) return;

        List<Song> heap = new ArrayList<>();
        List<Integer> adjustments = new ArrayList<>(); // null => song removed
        int shift = 0;
        for (Song s : saved.heap()) {
            if (s != null) {
                heap.add(s);
                adjustments.add(shift);
            } else {
                adjustments.add(null);
                shift -= 1;
            }
        }

        List<Integer> shuffledMapping = new ArrayList<>();
        for (int idx : saved.shuffledMapping()) {
            Integer adj = idx >= 0 && idx < adjustments.size() ? adjustments.get(idx) : null;
            if (adj != null) shuffledMapping.add(idx + adj);
        }

        int index = saved.index();
        while (index > -1 && !pointingAtSong(heap, shuffledMapping, index, saved.songUid())) index--;

        for (int i : shuffledMapping) {
            if (i < 0 || i >= heap.size()) {
                throw new IllegalStateException("Queue inconsistency detected: Shuffled mapping indices out of heap bounds");
            }
        }

        if (index < 0) {
            h.reset(new StateAck.NewPlayback());
            return;
        }

        RawQueue raw = new RawQueue(heap, shuffledMapping, !shuffledMapping.isEmpty() ? shuffledMapping.get(index) : index);
        h.applySavedState(saved.parent(), raw, saved.positionMs(), saved.repeatMode(), new StateAck.NewPlayback());
        initialized = true;
    }

    private static boolean pointingAtSong(List<Song> heap, List<Integer> mapping, int index, Music.UID uid) {
        Song s = null;
        if (!mapping.isEmpty()) {
            if (index < mapping.size()) {
                int hi = mapping.get(index);
                if (hi >= 0 && hi < heap.size()) s = heap.get(hi);
            }
        } else if (index < heap.size()) {
            s = heap.get(index);
        }
        return s != null && s.uid().equals(uid);
    }
}
