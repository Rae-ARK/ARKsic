package arksic.musikr;

import static org.junit.jupiter.api.Assertions.*;

import arksic.musikr.model.Music;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UidTest {
    @Test
    void hashedUidIsDeterministicAndRoundTrips() {
        Music.UID a = Music.UID.auxio(Music.UID.Item.SONG, h -> h.update("Hello").update(3));
        Music.UID b = Music.UID.auxio(Music.UID.Item.SONG, h -> h.update("hello").update(3)); // case-insensitive
        assertEquals(a, b);
        assertEquals(a, Music.UID.fromString(a.toString()));
    }

    @Test
    void differentItemsDiffer() {
        Music.UID a = Music.UID.auxio(Music.UID.Item.SONG, h -> h.update("x"));
        Music.UID b = Music.UID.auxio(Music.UID.Item.ALBUM, h -> h.update("x"));
        assertNotEquals(a, b);
    }

    @Test
    void musicBrainzRoundTrip() {
        UUID id = UUID.randomUUID();
        Music.UID u = Music.UID.musicBrainz(Music.UID.Item.ARTIST, id);
        assertEquals(u, Music.UID.fromString(u.toString()));
    }

    @Test
    void legacyFormatParses() {
        UUID id = UUID.randomUUID();
        Music.UID u = Music.UID.fromString("org.musicbrainz:a10b-" + id);
        assertNotNull(u);
        assertEquals(Music.UID.musicBrainz(Music.UID.Item.SONG, id), u);
    }

    @Test
    void garbageIsNull() {
        assertNull(Music.UID.fromString("nope"));
        assertNull(Music.UID.fromString("uzz"));
        assertNull(Music.UID.fromString("org.foo:a10b-" + UUID.randomUUID()));
        assertNull(Music.UID.fromString(null));
    }

    @Test
    void hashingMatchesAuxioByteLayout() {
        // SHA-256 of the lowercase UTF-8 bytes "abc", folded big-endian into a UUID: the same
        // construction as Auxio, so UIDs (and saved playlists) stay compatible.
        Music.UID u = Music.UID.auxio(Music.UID.Item.SONG, h -> h.update("ABC"));
        assertTrue(u.toString().endsWith("ba7816bf-8f01-cfea-4141-40de5dae2223"), u.toString());
    }
}
