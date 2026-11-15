package arksic.playback;

import static org.junit.jupiter.api.Assertions.*;

import arksic.musikr.model.Song;
import java.util.List;

/** Invariants that must hold after every operation, in every mode. */
final class PlaybackAssert {
    private PlaybackAssert() {}

    /** The manager's mirror, the holder's real state and the engine must all agree. */
    static void consistent(PlaybackFixture fx) {
        List<Song> queue = fx.manager.queue();
        int index = fx.manager.index();
        var raw = fx.holder.resolveQueue();

        assertEquals(raw.resolveSongs(), queue, "mirror queue == holder queue");
        assertEquals(raw.resolveIndex(), index, "mirror index == holder index");
        for (Song s : queue) assertNotNull(s, "queue never holds null");

        if (queue.isEmpty()) {
            assertEquals(-1, index, "empty queue has no index");
            assertNull(fx.manager.currentSong());
            assertNull(fx.engine.path, "nothing loaded when the queue is empty");
        } else {
            assertTrue(index >= 0 && index < queue.size(), "index " + index + " in 0.." + queue.size());
            assertSame(queue.get(index), fx.manager.currentSong());
            assertEquals(queue.get(index).path(), fx.engine.path, "engine has the current song loaded");
        }
        if (raw.isShuffled()) {
            assertEquals(raw.heap().size(), raw.shuffledMapping().size(), "mapping covers the heap");
            assertEquals(raw.heap().size(), raw.shuffledMapping().stream().distinct().count(), "mapping has no repeats");
            for (int i : raw.shuffledMapping()) assertTrue(i >= 0 && i < raw.heap().size(), "mapping index in range");
        }
    }
}
