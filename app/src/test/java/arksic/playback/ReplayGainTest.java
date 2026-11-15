package arksic.playback;

import static arksic.musikr.TestLibrary.song;
import static org.junit.jupiter.api.Assertions.*;

import arksic.musikr.TestLibrary;
import arksic.musikr.model.Library;
import arksic.musikr.model.Song;
import arksic.playback.replaygain.ReplayGain;
import arksic.playback.replaygain.ReplayGain.Mode;
import arksic.playback.replaygain.ReplayGain.PreAmp;
import arksic.playback.state.ShuffleMode;
import org.junit.jupiter.api.Test;

class ReplayGainTest {
    private static float db(float volume) {
        return (float) (20 * Math.log10(volume));
    }

    private final Library lib = TestLibrary.of(
            song().title("Both").album("A").artists("X").replayGain(-6f, 3f),
            song().title("TrackOnly").album("A").artists("X").replayGain(-4f, null),
            song().title("AlbumOnly").album("A").artists("X").replayGain(null, 2f),
            song().title("Bare").album("B").artists("Y"));
    private final PlaybackFixture fx = new PlaybackFixture(lib);
    private final PreAmp flat = new PreAmp(0f, 0f);

    private Song s(String n) { return fx.song(n); }

    @Test
    void offAppliesNoGain() {
        assertEquals(0f, db(ReplayGain.volumeFor(s("Both"), Mode.OFF, flat, null)), 0.001f);
    }

    @Test
    void trackModePrefersTrackGainThenAlbum() {
        assertEquals(-6f, db(ReplayGain.volumeFor(s("Both"), Mode.TRACK, flat, null)), 0.001f);
        assertEquals(2f, db(ReplayGain.volumeFor(s("AlbumOnly"), Mode.TRACK, flat, null)), 0.001f);
    }

    @Test
    void albumModePrefersAlbumGainThenTrack() {
        assertEquals(3f, db(ReplayGain.volumeFor(s("Both"), Mode.ALBUM, flat, null)), 0.001f);
        assertEquals(-4f, db(ReplayGain.volumeFor(s("TrackOnly"), Mode.ALBUM, flat, null)), 0.001f);
    }

    @Test
    void dynamicModeUsesAlbumGainOnlyWhilePlayingThatAlbum() {
        Song both = s("Both");
        assertEquals(3f, db(ReplayGain.volumeFor(both, Mode.DYNAMIC, flat, both.album())), 0.001f);
        assertEquals(-6f, db(ReplayGain.volumeFor(both, Mode.DYNAMIC, flat, null)), 0.001f);
        assertEquals(-6f, db(ReplayGain.volumeFor(both, Mode.DYNAMIC, flat, both.artists().get(0))), 0.001f);
        assertEquals(-6f, db(ReplayGain.volumeFor(both, Mode.DYNAMIC, flat, s("Bare").album())), 0.001f);
        // No track gain and not in the album: nothing to apply, so the "without" pre-amp.
        Song albumOnly = s("AlbumOnly");
        assertEquals(0f, db(ReplayGain.volumeFor(albumOnly, Mode.DYNAMIC, flat, null)), 0.001f);
    }

    @Test
    void preAmpDependsOnWhetherATagWasFound() {
        PreAmp amp = new PreAmp(2f, -3f);
        assertEquals(-4f, db(ReplayGain.volumeFor(s("Both"), Mode.TRACK, amp, null)), 0.001f); // -6 + 2
        assertEquals(-3f, db(ReplayGain.volumeFor(s("Bare"), Mode.TRACK, amp, null)), 0.001f);
    }

    @Test
    void offStillAppliesTheWithoutPreAmpLikeAuxio() {
        // Characterisation of Auxio's behaviour: "no adjustment resolved" uses preAmp.without, and
        // mode OFF never resolves one. (The settings screen is expected to hide it when OFF.)
        assertEquals(-3f, db(ReplayGain.volumeFor(s("Both"), Mode.OFF, new PreAmp(2f, -3f), null)), 0.001f);
    }

    @Test
    void nothingPlayingMeansUnityGain() {
        assertEquals(1f, ReplayGain.volumeFor(null, Mode.TRACK, new PreAmp(5f, 5f), null));
    }

    // --- sample processing ---

    private static byte[] pcm(int... samples) {
        byte[] b = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            b[2 * i] = (byte) samples[i];
            b[2 * i + 1] = (byte) (samples[i] >> 8);
        }
        return b;
    }

    private static int sample(byte[] b, int i) {
        return (short) ((b[2 * i + 1] << 8) | (b[2 * i] & 0xFF));
    }

    @Test
    void gainScalesSamples() {
        byte[] b = pcm(1000, -1000, 0);
        ReplayGain.applyGain16le(b, b.length, 0.5f);
        assertEquals(500, sample(b, 0));
        assertEquals(-500, sample(b, 1));
        assertEquals(0, sample(b, 2));
    }

    @Test
    void amplificationClampsInsteadOfWrappingAround() {
        byte[] b = pcm(30000, -30000, 20000);
        ReplayGain.applyGain16le(b, b.length, 2f);
        assertEquals(32767, sample(b, 0));
        assertEquals(-32768, sample(b, 1));
        assertEquals(32767, sample(b, 2));
    }

    @Test
    void unityGainLeavesTheBufferUntouched() {
        byte[] b = pcm(123, -456);
        byte[] copy = b.clone();
        ReplayGain.applyGain16le(b, b.length, 1f);
        assertArrayEqualsBytes(copy, b);
    }

    @Test
    void onlyTheRequestedLengthIsProcessedAndOddTailsAreSafe() {
        byte[] b = pcm(1000, 1000, 1000);
        ReplayGain.applyGain16le(b, 5, 0.5f); // 2 whole samples + 1 stray byte
        assertEquals(500, sample(b, 0));
        assertEquals(500, sample(b, 1));
        assertEquals(1000, sample(b, 2));
    }

    private static void assertArrayEqualsBytes(byte[] a, byte[] b) {
        assertEquals(a.length, b.length);
        for (int i = 0; i < a.length; i++) assertEquals(a[i], b[i], "byte " + i);
    }

    // --- wired into the holder ---

    @Test
    void theEngineGainFollowsTheSongAndMode() {
        fx.settings.replayGainMode = Mode.TRACK;
        fx.manager.play(fx.factory.songFromAll(s("Both"), ShuffleMode.OFF));
        assertEquals(-6f, db(fx.engine.gain), 0.01f);
        fx.manager.next(); // name order: AlbumOnly, Bare, Both, TrackOnly
        assertEquals("TrackOnly", fx.current());
        assertEquals(-4f, db(fx.engine.gain), 0.01f);
    }

    @Test
    void dynamicModeUsesTheAlbumGainWhenPlayedFromTheAlbum() {
        fx.settings.replayGainMode = Mode.DYNAMIC;
        Song both = s("Both");
        fx.manager.play(fx.factory.songFromAlbum(both, ShuffleMode.OFF));
        assertEquals(3f, db(fx.engine.gain), 0.01f);
        fx.manager.play(fx.factory.songFromAll(both, ShuffleMode.OFF));
        assertEquals(-6f, db(fx.engine.gain), 0.01f);
    }

    @Test
    void userVolumeMultipliesWithReplayGainAndIsClamped() {
        fx.settings.replayGainMode = Mode.TRACK;
        fx.manager.play(fx.factory.songFromAll(s("Both"), ShuffleMode.OFF));
        float rg = fx.engine.gain;
        fx.holder.setUserVolume(0.5f);
        assertEquals(rg * 0.5f, fx.engine.gain, 0.0001f);
        fx.holder.setUserVolume(7f);
        assertEquals(rg, fx.engine.gain, 0.0001f);
        fx.holder.setUserVolume(-1f);
        assertEquals(0f, fx.engine.gain, 0.0001f);
    }

    @Test
    void changingTheModeAppliesOnTheNextSongLoad() {
        fx.settings.replayGainMode = Mode.OFF;
        fx.manager.play(fx.factory.songFromAll(s("Both"), ShuffleMode.OFF));
        assertEquals(1f, fx.engine.gain, 0.0001f);
        fx.settings.replayGainMode = Mode.TRACK;
        fx.manager.goTo(fx.manager.index()); // reload the same song
        assertEquals(-6f, db(fx.engine.gain), 0.01f);
    }
}
