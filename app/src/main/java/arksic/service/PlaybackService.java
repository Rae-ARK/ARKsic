package arksic.service;

import arksic.audio.AudioDecoder;
import arksic.model.PlaybackState;
import arksic.model.Track;
import arksic.util.PlaybackMath;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Owns the one open audio line and decode thread for whatever is currently
 * playing. See docs/foundation/architecture.md#ownership — no controller
 * or view is allowed to hold these directly.
 *
 * <p>Never imports a Swing type; every listener callback happens on this
 * service's own decode thread; marshaling to the EDT is each listener's
 * job.
 */
public final class PlaybackService {

    private static final int BUFFER_SIZE = 4096;

    private final AudioDecoder decoder;
    private final List<PlaybackListener> listeners = new CopyOnWriteArrayList<>();

    private volatile PlaybackState state = PlaybackState.STOPPED;
    private volatile int volumePercent = 100;
    private Thread decodeThread;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final AtomicBoolean pauseRequested = new AtomicBoolean(false);

    public PlaybackService(AudioDecoder decoder) {
        this.decoder = decoder;
    }

    public void addListener(PlaybackListener listener) {
        listeners.add(listener);
    }

    public void removeListener(PlaybackListener listener) {
        listeners.remove(listener);
    }

    public PlaybackState state() {
        return state;
    }

    public void setVolumePercent(int percent) {
        this.volumePercent = Math.max(0, Math.min(100, percent));
    }

    /** Stops whatever is playing (if anything) and starts {@code track}. */
    public void play(Track track) {
        stopInternal();
        stopRequested.set(false);
        pauseRequested.set(false);
        setState(PlaybackState.PLAYING);

        decodeThread = new Thread(() -> runDecodeLoop(track), "arksic-playback-decode");
        decodeThread.setDaemon(true);
        decodeThread.start();
    }

    public void pause() {
        if (state == PlaybackState.PLAYING) {
            pauseRequested.set(true);
            setState(PlaybackState.PAUSED);
        }
    }

    public void resume() {
        if (state == PlaybackState.PAUSED) {
            pauseRequested.set(false);
            setState(PlaybackState.PLAYING);
        }
    }

    /** Stops playback and releases the line. Safe to call when already stopped. */
    public void stop() {
        stopInternal();
        setState(PlaybackState.STOPPED);
    }

    private void stopInternal() {
        stopRequested.set(true);
        Thread toJoin = decodeThread;
        if (toJoin != null && toJoin != Thread.currentThread()) {
            try {
                toJoin.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void runDecodeLoop(Track track) {
        try (AudioInputStream audioStream = decoder.open(track.path())) {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioStream.getFormat());
            try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                line.open(audioStream.getFormat());
                line.start();

                byte[] buffer = new byte[BUFFER_SIZE];
                int framesPerSecond = (int) audioStream.getFormat().getFrameRate();
                int frameSize = audioStream.getFormat().getFrameSize();
                long framesWritten = 0;
                int read;

                while (!stopRequested.get() && (read = audioStream.read(buffer)) != -1) {
                    while (pauseRequested.get() && !stopRequested.get()) {
                        Thread.sleep(50);
                    }
                    if (stopRequested.get()) {
                        break;
                    }
                    applyVolume(buffer, read, audioStream.getFormat());
                    line.write(buffer, 0, read);
                    framesWritten += read / Math.max(1, frameSize);
                    int positionSeconds = framesPerSecond > 0 ? (int) (framesWritten / framesPerSecond) : 0;
                    notifyPosition(positionSeconds, track.durationSeconds());
                }
                line.drain();

                if (!stopRequested.get()) {
                    setState(PlaybackState.STOPPED);
                    notifyTrackFinished();
                }
            }
        } catch (IOException | UnsupportedAudioFileException | LineUnavailableException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            setState(PlaybackState.STOPPED);
            notifyError(e);
        }
    }

    /** A simple linear gain pass over 16-bit PCM; adequate until a real mixer is warranted. */
    private void applyVolume(byte[] buffer, int length, javax.sound.sampled.AudioFormat format) {
        if (volumePercent >= 100 || format.getSampleSizeInBits() != 16) {
            return;
        }
        float gain = volumePercent / 100f;
        for (int i = 0; i + 1 < length; i += 2) {
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            sample = (short) (sample * gain);
            buffer[i] = (byte) (sample & 0xFF);
            buffer[i + 1] = (byte) ((sample >> 8) & 0xFF);
        }
    }

    private void setState(PlaybackState newState) {
        state = newState;
        for (PlaybackListener listener : listeners) {
            listener.onStateChanged(newState);
        }
    }

    private void notifyPosition(int positionSeconds, int durationSeconds) {
        for (PlaybackListener listener : listeners) {
            listener.onPositionChanged(positionSeconds, durationSeconds);
        }
    }

    private void notifyTrackFinished() {
        for (PlaybackListener listener : listeners) {
            listener.onTrackFinished();
        }
    }

    private void notifyError(Exception error) {
        for (PlaybackListener listener : listeners) {
            listener.onError(error);
        }
    }

    // Exposed for testing the pure state-transition logic without a real line.
    static PlaybackState resolveStateForTest(PlaybackState current, PlaybackMath.Transition transition) {
        return PlaybackMath.resolvePlaybackState(current, transition);
    }
}
