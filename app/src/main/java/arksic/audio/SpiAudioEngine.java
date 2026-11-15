/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.audio;

import arksic.playback.replaygain.ReplayGain;
import java.io.IOException;
import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * {@link AudioEngine} over javax.sound with the {@link AudioDecoder} SPI chain. Seeking reopens
 * the stream and skips forward: MP3/FLAC SPI streams aren't random access. That's O(position) per
 * seek, acceptable for a first take.
 */
public final class SpiAudioEngine implements AudioEngine {
    private static final int BUFFER_SIZE = 8192;

    private final AudioDecoder decoder;
    private volatile Listener listener;

    private final Object lock = new Object();
    private Thread thread;
    private int generation;

    private volatile Path path;
    private volatile boolean playing;
    private volatile boolean ended;
    private volatile float gain = 1f;
    private volatile long positionMs;

    public SpiAudioEngine(AudioDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void load(Path path, long startMs, boolean play) {
        int gen;
        Thread old;
        synchronized (lock) {
            gen = ++generation;
            old = thread;
            this.path = path;
            this.playing = play;
            this.ended = false;
            this.positionMs = Math.max(0, startMs);
        }
        if (old != null && old != Thread.currentThread()) {
            old.interrupt();
            try {
                old.join(150); // short: the old thread may be waiting on a lock we hold
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Thread t = new Thread(() -> run(path, Math.max(0, startMs), gen), "arksic-audio-decode");
        t.setDaemon(true);
        synchronized (lock) {
            thread = t;
        }
        t.start();
    }

    @Override
    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    @Override
    public boolean isPlaying() {
        return playing && path != null && !ended;
    }

    @Override
    public void seekTo(long positionMs) {
        Path p = path;
        if (p != null) load(p, positionMs, playing);
    }

    @Override
    public long positionMs() {
        return positionMs;
    }

    @Override
    public void setGain(float linear) {
        this.gain = linear;
    }

    @Override
    public void stop() {
        Thread old;
        synchronized (lock) {
            generation++;
            old = thread;
            thread = null;
            path = null;
            playing = false;
            positionMs = 0;
        }
        if (old != null && old != Thread.currentThread()) old.interrupt();
    }

    private boolean current(int gen) {
        synchronized (lock) {
            return generation == gen;
        }
    }

    private void run(Path p, long startMs, int gen) {
        try (AudioInputStream in = decoder.open(p)) {
            AudioFormat fmt = in.getFormat();
            int frameSize = Math.max(1, fmt.getFrameSize());
            float frameRate = fmt.getFrameRate() > 0 ? fmt.getFrameRate() : fmt.getSampleRate();

            // Skip to the start position.
            long skipBytes = (long) (startMs / 1000.0 * frameRate) * frameSize;
            while (skipBytes > 0 && current(gen)) {
                long s = in.skip(skipBytes);
                if (s <= 0) {
                    // Some SPI streams return 0 from skip(); fall back to reading.
                    byte[] junk = new byte[(int) Math.min(skipBytes, BUFFER_SIZE)];
                    int r = in.read(junk);
                    if (r < 0) break;
                    s = r;
                }
                skipBytes -= s;
            }

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
            try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                line.open(fmt);
                line.start();
                boolean lineRunning = true;
                byte[] buf = new byte[BUFFER_SIZE - (BUFFER_SIZE % frameSize)];
                long framesWritten = 0;
                int read;
                while (current(gen) && (read = in.read(buf)) != -1) {
                    while (!playing && current(gen)) {
                        if (lineRunning) {
                            line.stop();
                            lineRunning = false;
                        }
                        Thread.sleep(25);
                    }
                    if (!current(gen)) return;
                    if (!lineRunning) {
                        line.start();
                        lineRunning = true;
                    }
                    if (fmt.getSampleSizeInBits() == 16 && !fmt.isBigEndian()) {
                        ReplayGain.applyGain16le(buf, read, gain);
                    }
                    line.write(buf, 0, read);
                    framesWritten += read / frameSize;
                    long played = Math.max(0, line.getLongFramePosition());
                    positionMs = startMs + (long) (Math.min(played, framesWritten) * 1000.0 / frameRate);
                }
                if (current(gen)) line.drain();
            }
            if (current(gen)) {
                ended = true;
                Listener l = listener;
                if (l != null) l.onEnded();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | UnsupportedAudioFileException | LineUnavailableException | IllegalArgumentException e) {
            if (current(gen)) {
                Listener l = listener;
                if (l != null) l.onError(e);
            }
        }
    }
}
