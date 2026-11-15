package arksic.playback;

import static arksic.musikr.TestLibrary.song;
import static org.junit.jupiter.api.Assertions.*;

import arksic.musikr.TestLibrary;
import arksic.musikr.model.Library;
import arksic.musikr.model.Playlist;
import arksic.musikr.model.Song;
import arksic.playback.state.PlaybackCommand;
import arksic.playback.state.PlaybackStateManager;
import arksic.playback.state.RepeatMode;
import arksic.playback.state.ShuffleMode;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaybackStartTest {
    private final PlaybackFixture fx = new PlaybackFixture();
    private static final List<String> ALL = List.of(PlaybackFixture.NAMES);

    @Test
    void unshuffledPlaybackStartsOnTheRequestedSong() {
        fx.playUnshuffled("Charlie");
        assertEquals(ALL, fx.queue());
        assertEquals(2, fx.manager.index());
        assertEquals("Charlie", fx.current());
        assertFalse(fx.manager.isShuffled());
        assertEquals(new FakeAudioEngine.Load(fx.song("Charlie").path(), 0, true), fx.engine.lastLoad());
        assertTrue(fx.manager.progression().isPlaying());
        assertEquals(1, fx.events.newPlaybacks);
        assertTrue(fx.saveRequests > 0, "playback state should be queued for saving");
        PlaybackAssert.consistent(fx);
    }

    @Test
    void shuffledPlaybackAnchorsOnTheRequestedSong() {
        for (int trial = 0; trial < 20; trial++) {
            PlaybackFixture f = new PlaybackFixture();
            f.playShuffled("Foxtrot");
            assertEquals("Foxtrot", f.current());
            assertEquals(0, f.manager.index(), "the chosen song leads the shuffled queue");
            assertTrue(f.manager.isShuffled());
            assertEquals(new HashSet<>(ALL), new HashSet<>(f.queue()));
            assertEquals(ALL.size(), f.queue().size());
            PlaybackAssert.consistent(f);
        }
    }

    @Test
    void startingWithoutASongUnshuffledBeginsAtTheFront() {
        fx.manager.play(fx.factory.all(ShuffleMode.OFF));
        assertEquals("Alpha", fx.current());
        assertEquals(0, fx.manager.index());
    }

    @Test
    void startingWithoutASongShuffledBeginsAtTheFrontOfTheShuffle() {
        fx.manager.play(fx.factory.all(ShuffleMode.ON));
        assertEquals(0, fx.manager.index());
        assertEquals(fx.queue().get(0), fx.current());
        assertTrue(fx.manager.isShuffled());
        PlaybackAssert.consistent(fx);
    }

    @Test
    void aStartSongOutsideTheQueueIsRejected() {
        var cmd = new PlaybackCommand.Impl(fx.song("Hotel"), null, fx.songs("Alpha", "Bravo"), false);
        assertThrows(IllegalStateException.class, () -> fx.manager.play(cmd));
    }

    @Test
    void theFactoryRefusesEmptyQueuesAndSongsNotInThem() {
        assertNull(fx.factory.songs(List.of(), ShuffleMode.OFF));
        Library withPl = fx.library.createPlaylist("P", fx.songs("Alpha"));
        Playlist p = withPl.findPlaylistByName("P");
        assertNull(fx.factory.songFromPlaylist(fx.song("Bravo"), p, ShuffleMode.OFF));
        assertNotNull(fx.factory.songFromPlaylist(fx.song("Alpha"), p, ShuffleMode.OFF));
    }

    @Test
    void playingAPlaylistKeepsItsOrderAndRecordsItAsTheParent() {
        Library withPl = fx.library.createPlaylist("Mix", fx.songs("Echo", "Alpha", "Delta"));
        Playlist p = withPl.findPlaylistByName("Mix");
        fx.manager.play(fx.factory.playlist(p, ShuffleMode.OFF));
        assertEquals(List.of("Echo", "Alpha", "Delta"), fx.queue()); // playlist order, not name order
        assertSame(p, fx.manager.parent());
    }

    @Test
    void parentIsTheAlbumWhenPlayedFromAnAlbum() {
        Song delta = fx.song("Delta");
        fx.manager.play(fx.factory.songFromAlbum(delta, ShuffleMode.OFF));
        assertSame(delta.album(), fx.manager.parent());
        assertEquals("Delta", fx.current());
        assertEquals(8, fx.manager.queue().size());
    }

    @Test
    void playingFromAllSongsHasNoParent() {
        fx.playUnshuffled("Alpha");
        assertNull(fx.manager.parent());
    }

    @Test
    void implicitShuffleFollowsTheCurrentStateOnlyWhenKeepShuffleIsOn() {
        fx.playShuffled("Alpha");
        assertTrue(((PlaybackCommand) fx.factory.all(ShuffleMode.IMPLICIT)).shuffled());
        fx.manager.shuffled(false);
        assertFalse(fx.factory.all(ShuffleMode.IMPLICIT).shuffled());
        fx.manager.shuffled(true);
        fx.settings.keepShuffle = false;
        assertFalse(fx.factory.all(ShuffleMode.IMPLICIT).shuffled());
        assertTrue(fx.factory.all(ShuffleMode.ON).shuffled());
        assertFalse(fx.factory.all(ShuffleMode.OFF).shuffled());
    }

    @Test
    void ambiguousArtistOrGenreRequestsYieldNothing() {
        Library lib = TestLibrary.of(
                song().title("Duet").album("X").artists("A; B").genres("Rock; Pop"),
                song().title("Solo").album("X").artists("C").genres("Jazz"));
        PlaybackFixture f = new PlaybackFixture(lib);
        Song duet = f.song("Duet");
        Song solo = f.song("Solo");
        assertNull(f.factory.songFromArtist(duet, null, ShuffleMode.OFF), "two artists, none chosen");
        assertNull(f.factory.songFromGenre(duet, null, ShuffleMode.OFF), "two genres, none chosen");
        // Unambiguous or explicit requests work.
        assertNotNull(f.factory.songFromArtist(solo, null, ShuffleMode.OFF));
        assertNotNull(f.factory.songFromGenre(solo, null, ShuffleMode.OFF));
        var chosen = f.factory.songFromArtist(duet, duet.artists().get(1), ShuffleMode.OFF);
        assertSame(duet.artists().get(1), chosen.parent());
        assertEquals(List.of("Duet"), PlaybackFixture.names(chosen.queue()));
    }

    @Test
    void albumSongsFollowTheAlbumSortAndArtistSongsTheArtistSort() {
        Library lib = TestLibrary.of(
                song().title("Zulu").album("X").artists("A").disc(1).track(1).date("2000"),
                song().title("Yankee").album("X").artists("A").disc(1).track(2).date("2000"),
                song().title("Xray").album("Old").artists("A").disc(1).track(1).date("1990"));
        PlaybackFixture f = new PlaybackFixture(lib);
        var album = f.factory.songFromAlbum(f.song("Zulu"), ShuffleMode.OFF);
        assertEquals(List.of("Zulu", "Yankee"), PlaybackFixture.names(album.queue())); // disc, then track
        var artist = f.factory.artist(f.song("Zulu").artists().get(0), ShuffleMode.OFF);
        // Artist songs default to date descending: the 2000 album before the 1990 one.
        assertEquals("Xray", PlaybackFixture.names(artist.queue()).get(2));
    }

    @Test
    void startingNewPlaybackReplacesTheOldQueue() {
        fx.playUnshuffled("Alpha");
        fx.play("Hotel", true);
        assertEquals("Hotel", fx.current());
        assertEquals(2, fx.events.newPlaybacks);
        assertEquals(2, fx.engine.loads.size());
        PlaybackAssert.consistent(fx);
    }

    @Test
    void playingWithoutAStateHolderDoesNothing() {
        PlaybackStateManager bare = new PlaybackStateManager();
        assertDoesNotThrow(() -> bare.play(fx.factory.all(ShuffleMode.OFF)));
        assertNull(bare.currentSong());
        assertDoesNotThrow(bare::next);
        assertDoesNotThrow(bare::prev);
        assertDoesNotThrow(() -> bare.playing(true));
        assertDoesNotThrow(() -> bare.repeatMode(RepeatMode.ALL));
    }

    @Test
    void lateListenersAreCaughtUpOnTheCurrentState() {
        fx.playUnshuffled("Charlie");
        PlaybackFixture.Recorder late = new PlaybackFixture.Recorder();
        fx.manager.addListener(late);
        assertEquals(List.of("new:2:false"), late.log);
    }
}
