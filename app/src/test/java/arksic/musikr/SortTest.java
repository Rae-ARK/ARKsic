package arksic.musikr;

import static arksic.musikr.TestLibrary.song;
import static org.junit.jupiter.api.Assertions.*;

import arksic.list.Sort;
import arksic.list.Sort.Direction;
import arksic.list.Sort.Mode;
import arksic.musikr.model.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class SortTest {
    private static List<String> names(List<Song> l) {
        return l.stream().map(s -> s.name().raw()).toList();
    }

    private final Library lib = TestLibrary.of(
            song().title("Beta").album("Zed").artists("Y").track(2).disc(1).durationMs(300).date("2000"),
            song().title("Alpha").album("Zed").artists("Y").track(1).disc(1).durationMs(100).date("2000"),
            song().title("Gamma").album("Zed").artists("Y").track(1).disc(2).durationMs(200).date("2000"),
            song().title("Delta").album("Able").artists("X").track(1).disc(1).durationMs(400).date("2010"));

    @Test
    void byNameBothDirections() {
        assertEquals(List.of("Alpha", "Beta", "Delta", "Gamma"),
                names(new Sort(Mode.BY_NAME, Direction.ASCENDING).songs(lib.songs())));
        assertEquals(List.of("Gamma", "Delta", "Beta", "Alpha"),
                names(new Sort(Mode.BY_NAME, Direction.DESCENDING).songs(lib.songs())));
    }

    @Test
    void byDiscThenTrack() {
        Album zed = lib.albums().stream().filter(a -> a.name().toString().equals("Zed")).findFirst().orElseThrow();
        assertEquals(List.of("Alpha", "Beta", "Gamma"),
                names(new Sort(Mode.BY_DISC, Direction.ASCENDING).songs(zed.songs())));
    }

    @Test
    void byTrackKeepsDiscAsPrimaryKey() {
        Album zed = lib.albums().stream().filter(a -> a.name().toString().equals("Zed")).findFirst().orElseThrow();
        assertEquals(List.of("Alpha", "Beta", "Gamma"),
                names(new Sort(Mode.BY_TRACK, Direction.ASCENDING).songs(zed.songs())));
    }

    @Test
    void byDurationDescending() {
        assertEquals(List.of("Delta", "Beta", "Gamma", "Alpha"),
                names(new Sort(Mode.BY_DURATION, Direction.DESCENDING).songs(lib.songs())));
    }

    @Test
    void byDateAscendingThenName() {
        List<String> n = names(new Sort(Mode.BY_DATE, Direction.ASCENDING).songs(lib.songs()));
        assertEquals("Delta", n.get(3)); // 2010 is last
    }

    @Test
    void albumsByName() {
        var albums = new Sort(Mode.BY_NAME, Direction.ASCENDING).albums(lib.albums());
        assertEquals("Able", albums.get(0).name().toString());
    }

    @Test
    void albumsByCountDescending() {
        var albums = new Sort(Mode.BY_COUNT, Direction.DESCENDING).albums(lib.albums());
        assertEquals("Zed", albums.get(0).name().toString());
    }

    @Test
    void unsupportedModesThrow() {
        assertThrows(UnsupportedOperationException.class,
                () -> new Sort(Mode.BY_COUNT, Direction.ASCENDING).songs(lib.songs()));
        assertThrows(UnsupportedOperationException.class,
                () -> new Sort(Mode.BY_DISC, Direction.ASCENDING).artists(lib.artists()));
    }

    @Test
    void intCodeRoundTrips() {
        for (Mode m : Mode.values()) {
            for (Direction d : Direction.values()) {
                Sort s = new Sort(m, d);
                assertEquals(s, Sort.fromIntCode(s.intCode()));
            }
        }
        assertNull(Sort.fromIntCode(0));
    }

    @Test
    void sortDoesNotMutateInput() {
        var before = List.copyOf(lib.songs());
        new Sort(Mode.BY_NAME, Direction.DESCENDING).songs(lib.songs());
        assertEquals(before, List.copyOf(lib.songs()));
    }
}
