package arksic.playback;

import arksic.audio.AudioEngine;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** A scriptable {@link AudioEngine}: records what it was asked to do and lets tests fire events. */
final class FakeAudioEngine implements AudioEngine {
    record Load(Path path, long startMs, boolean play) {}

    final List<Load> loads = new CopyOnWriteArrayList<>();
    final List<Long> seeks = new CopyOnWriteArrayList<>();
    final AtomicInteger stops = new AtomicInteger();
    volatile Listener listener;
    volatile Path path;
    volatile boolean playing;
    volatile boolean ended;
    volatile long position;
    volatile float gain = 1f;

    @Override public void setListener(Listener listener) { this.listener = listener; }

    @Override
    public void load(Path path, long startMs, boolean play) {
        loads.add(new Load(path, startMs, play));
        this.path = path;
        this.position = startMs;
        this.playing = play;
        this.ended = false;
    }

    @Override public void setPlaying(boolean playing) { this.playing = playing; }

    @Override public boolean isPlaying() { return playing && path != null && !ended; }

    @Override
    public void seekTo(long positionMs) {
        seeks.add(positionMs);
        this.position = positionMs;
    }

    @Override public long positionMs() { return position; }

    @Override public void setGain(float linear) { this.gain = linear; }

    @Override
    public void stop() {
        stops.incrementAndGet();
        path = null;
        playing = false;
        position = 0;
    }

    // --- test controls ---

    Load lastLoad() { return loads.isEmpty() ? null : loads.get(loads.size() - 1); }

    /** The loaded file played to the end. */
    void fireEnded() {
        ended = true;
        listener.onEnded();
    }

    void fireError() { listener.onError(new RuntimeException("boom")); }
}
