package arksic.musikr;

import static org.junit.jupiter.api.Assertions.*;

import arksic.musikr.interpret.Naming;
import arksic.musikr.tag.Name;
import arksic.musikr.tag.Placeholder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NameTest {
    private final Naming naming = Naming.intelligent();

    private List<String> sorted(String... raws) {
        List<Name.Known> names = new ArrayList<>();
        for (String r : raws) names.add(naming.name(r, null));
        names.sort(null);
        return names.stream().map(Name.Known::raw).toList();
    }

    @Test
    void numbersSortByMagnitudeNotLexically() {
        assertEquals(List.of("Track 2", "Track 10", "Track 100"), sorted("Track 100", "Track 2", "Track 10"));
    }

    @Test
    void leadingArticlesAreIgnored() {
        assertEquals(List.of("Alpha", "The Beatles", "Cure"), sorted("Cure", "The Beatles", "Alpha"));
    }

    @Test
    void punctuationBecomesTokenBoundary() {
        assertEquals(List.of("15-9", "15-10"), sorted("15-10", "15-9"));
    }

    @Test
    void leadingZerosDoNotAffectOrder() {
        assertEquals(List.of("007", "8", "09"), sorted("09", "8", "007"));
    }

    @Test
    void sortNameOverridesRaw() {
        Name.Known a = naming.name("Zebra", "Aardvark");
        Name.Known b = naming.name("Mouse", null);
        assertTrue(a.compareTo(b) < 0);
    }

    @Test
    void unknownSortsBeforeKnown() {
        Name unknown = naming.name(null, null, Placeholder.ARTIST);
        Name known = naming.name("A", null);
        assertTrue(unknown.compareTo(known) < 0);
        assertTrue(known.compareTo(unknown) > 0);
        assertEquals(0, unknown.compareTo(new Name.Unknown(Placeholder.GENRE)));
    }

    @Test
    void equalNamesAreEqual() {
        assertEquals(naming.name("X", "y"), naming.name("X", "y"));
        assertNotEquals(naming.name("X", "y"), naming.name("X", null));
    }
}
