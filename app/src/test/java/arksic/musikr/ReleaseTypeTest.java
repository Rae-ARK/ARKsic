package arksic.musikr;

import static org.junit.jupiter.api.Assertions.*;

import arksic.musikr.tag.ReleaseType;
import arksic.musikr.tag.ReleaseType.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReleaseTypeTest {
    @Test
    void emptyIsNull() {
        assertNull(ReleaseType.parse(List.of()));
    }

    @Test
    void primaryTypes() {
        assertEquals(new Album(null), ReleaseType.parse(List.of("album")));
        assertEquals(new EP(null), ReleaseType.parse(List.of("EP")));
        assertEquals(new Single(null), ReleaseType.parse(List.of("Single")));
    }

    @Test
    void refinements() {
        assertEquals(new Album(Refinement.LIVE), ReleaseType.parse(List.of("album", "live")));
        assertEquals(new EP(Refinement.REMIX), ReleaseType.parse(List.of("ep", "remix")));
    }

    @Test
    void compilationsTakeAThirdType() {
        assertEquals(new Compilation(null), ReleaseType.parse(List.of("album", "compilation")));
        assertEquals(new Compilation(Refinement.LIVE), ReleaseType.parse(List.of("album", "compilation", "live")));
    }

    @Test
    void orphanSecondaryTypesBecomeAlbums() {
        assertEquals(new Album(Refinement.LIVE), ReleaseType.parse(List.of("live")));
        assertEquals(new Soundtrack(), ReleaseType.parse(List.of("soundtrack")));
        assertEquals(new Mix(), ReleaseType.parse(List.of("album", "dj-mix")));
        assertEquals(new Mixtape(), ReleaseType.parse(List.of("album", "mixtape/street")));
        assertEquals(new Demo(), ReleaseType.parse(List.of("album", "demo")));
    }
}
