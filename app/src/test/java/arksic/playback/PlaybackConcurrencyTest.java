package arksic.playback;

import static org.junit.jupiter.api.Assertions.*;

import arksic.playback.state.RepeatMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Smoke test for the single-lock design. The UI thread(s) issue arbitrary commands while the
 * audio thread fires end-of-track/error events and an observer verifies the invariants under the
 * manager's lock. A lock-order inversion shows up as a hang; a lost update as a failed invariant.
 */
class PlaybackConcurrencyTest {
    private static final String[] NAMES = PlaybackFixture.NAMES;

    private static void randomOp(PlaybackFixture fx, Random r) {
        var m = fx.manager;
        switch (r.nextInt(16)) {
            case 0 -> m.next();
            case 1 -> m.prev();
            case 2 -> m.goTo(r.nextInt(12) - 2);
            case 3 -> m.playNext(fx.song(NAMES[r.nextInt(NAMES.length)]));
            case 4 -> m.addToQueue(fx.songs(NAMES[r.nextInt(NAMES.length)], NAMES[r.nextInt(NAMES.length)]));
            case 5 -> m.moveQueueItem(r.nextInt(12) - 2, r.nextInt(12) - 2);
            case 6 -> m.removeQueueItem(r.nextInt(12) - 2);
            case 7 -> m.shuffled(r.nextBoolean());
            case 8 -> m.repeatMode(RepeatMode.values()[r.nextInt(3)]);
            case 9 -> m.playing(r.nextBoolean());
            case 10 -> m.seekTo(r.nextInt(200_000));
            case 11 -> fx.play(NAMES[r.nextInt(NAMES.length)], r.nextBoolean());
            case 12 -> m.endSession();
            case 13 -> fx.holder.setUserVolume(r.nextFloat());
            case 14 -> m.toSavedState();
            default -> {
                // keep the queue from growing without bound
                if (m.queue().size() > 40) fx.play(NAMES[r.nextInt(NAMES.length)], r.nextBoolean());
            }
        }
    }

    @Test
    void hammeringFromManyThreadsNeitherDeadlocksNorCorruptsTheQueue() throws Exception {
        PlaybackFixture fx = new PlaybackFixture();
        fx.manager.removeListener(fx.events); // don't accumulate millions of log lines
        fx.playUnshuffled("Alpha");

        AtomicBoolean stop = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger ops = new AtomicInteger();
        AtomicInteger observations = new AtomicInteger();

        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 2; t++) {
            long seed = 100 + t;
            threads.add(thread("ui-" + t, stop, failure, () -> {
                Random r = new Random(seed);
                randomOp(fx, r);
                ops.incrementAndGet();
            }));
        }
        for (int t = 0; t < 2; t++) {
            long seed = 200 + t;
            threads.add(thread("audio-" + t, stop, failure, () -> {
                Random r = new Random(seed);
                if (r.nextInt(8) == 0) fx.engine.fireError();
                else fx.engine.fireEnded();
                ops.incrementAndGet();
                Thread.onSpinWait();
            }));
        }
        threads.add(thread("observer", stop, failure, () -> {
            fx.manager.runExclusive(() -> PlaybackAssert.consistent(fx));
            observations.incrementAndGet();
        }));

        for (Thread t : threads) t.start();
        Thread.sleep(1500);
        stop.set(true);

        List<String> stuck = new ArrayList<>();
        for (Thread t : threads) {
            t.join(5000);
            if (t.isAlive()) stuck.add(t.getName());
        }
        if (!stuck.isEmpty()) {
            StringBuilder dump = new StringBuilder("threads still running after 5s (deadlock?): " + stuck + "\n");
            for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
                if (stuck.contains(e.getKey().getName())) {
                    dump.append(e.getKey().getName()).append(":\n");
                    for (StackTraceElement el : e.getValue()) dump.append("    at ").append(el).append('\n');
                }
            }
            fail(dump.toString());
        }
        if (failure.get() != null) {
            throw new AssertionError("invariant broken under concurrency: " + failure.get(), failure.get());
        }
        assertTrue(ops.get() > 200, "the workers should have done real work: " + ops.get());
        assertTrue(observations.get() > 50, "the observer should have run often: " + observations.get());
        fx.manager.runExclusive(() -> PlaybackAssert.consistent(fx));
    }

    private static Thread thread(String name, AtomicBoolean stop, AtomicReference<Throwable> failure, Runnable step) {
        Thread t = new Thread(() -> {
            try {
                while (!stop.get()) step.run();
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
                stop.set(true);
            }
        }, name);
        t.setDaemon(true);
        return t;
    }

    @Test
    void engineCallbacksFromAnotherThreadAreAppliedExactlyOnceEach() throws Exception {
        PlaybackFixture fx = new PlaybackFixture();
        fx.manager.removeListener(fx.events);
        fx.settings.rememberPause = false;
        fx.manager.play(fx.factory.all(arksic.playback.state.ShuffleMode.OFF));
        fx.manager.repeatMode(RepeatMode.ALL); // never runs out of queue, never pauses

        int ends = 400;
        Thread audio = new Thread(() -> {
            for (int i = 0; i < ends; i++) fx.engine.fireEnded();
        }, "audio");
        audio.start();
        audio.join(10_000);
        assertFalse(audio.isAlive(), "audio thread should finish");

        // 400 advances around a queue of 8 land back where 400 % 8 says.
        assertEquals(ends % PlaybackFixture.NAMES.length, fx.manager.index());
        PlaybackAssert.consistent(fx);
    }
}
