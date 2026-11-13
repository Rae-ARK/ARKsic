package arksic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import arksic.model.RepeatMode;
import arksic.model.Track;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlaylistServiceTest {

    private static Track track(String name) {
        return Track.untagged(Path.of(name));
    }

    @Test
    void addSetsCurrentIndexOnFirstTrack() {
        PlaylistService service = new PlaylistService();
        service.add(track("a.mp3"));
        assertEquals(0, service.currentIndex());
        assertEquals(Optional.of(track("a.mp3")), service.current());
    }

    @Test
    void advanceWithRepeatOffStopsAtEnd() {
        PlaylistService service = new PlaylistService();
        service.addAll(List.of(track("a.mp3"), track("b.mp3")));

        assertEquals(Optional.of(track("b.mp3")), service.advance());
        assertEquals(Optional.empty(), service.advance());
    }

    @Test
    void advanceWithRepeatAllWrapsAround() {
        PlaylistService service = new PlaylistService();
        service.addAll(List.of(track("a.mp3"), track("b.mp3")));
        service.setRepeatMode(RepeatMode.REPEAT_ALL);

        service.advance(); // -> b
        assertEquals(Optional.of(track("a.mp3")), service.advance());
    }

    @Test
    void removingCurrentTrackMovesIndexBack() {
        PlaylistService service = new PlaylistService();
        service.addAll(List.of(track("a.mp3"), track("b.mp3")));
        service.setCurrentIndex(1);

        service.remove(1);

        assertEquals(0, service.currentIndex());
        assertEquals(1, service.tracks().size());
    }

    @Test
    void reorderMovesTrackAndTracksCurrentIndex() {
        PlaylistService service = new PlaylistService();
        service.addAll(List.of(track("a.mp3"), track("b.mp3"), track("c.mp3")));
        service.setCurrentIndex(0);

        service.reorder(0, 2);

        assertEquals(List.of(track("b.mp3"), track("c.mp3"), track("a.mp3")), service.tracks());
        assertEquals(2, service.currentIndex());
    }

    @Test
    void clearResetsEverything() {
        PlaylistService service = new PlaylistService();
        service.addAll(List.of(track("a.mp3"), track("b.mp3")));

        service.clear();

        assertTrue(service.tracks().isEmpty());
        assertEquals(-1, service.currentIndex());
        assertEquals(Optional.empty(), service.current());
    }

    @Test
    void shuffleCoversEveryTrackExactlyOnceBeforeStoppingWithRepeatOff() {
        PlaylistService service = new PlaylistService(new java.util.Random(7));
        service.addAll(List.of(track("a.mp3"), track("b.mp3"), track("c.mp3")));
        service.setShuffleEnabled(true);

        java.util.Set<Track> seen = new java.util.HashSet<>();
        service.current().ifPresent(seen::add);
        Optional<Track> next;
        while ((next = service.advance()).isPresent()) {
            assertFalse(seen.contains(next.get()), "shuffle revisited a track before covering the rest");
            seen.add(next.get());
        }
        assertEquals(3, seen.size());
    }
}
