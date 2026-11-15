package arksic.musikr.metadata;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.ID3v24Frame;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX;
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag;
import org.jaudiotagger.tag.wav.WavTag;

/**
 * Builds tiny but genuinely valid audio files for tests, so tag reading is exercised against real
 * container parsing rather than mocks. No encoder is needed:
 * <ul>
 *   <li>WAV: written by javax.sound from silent PCM.
 *   <li>MP3: a run of identical, all-zero (silent) MPEG-1 Layer III frames, 128 kbps / 44.1 kHz.
 *   <li>FLAC: STREAMINFO plus a padding block and no audio frames. jaudiotagger refuses files under
 *       ~150 bytes, hence the padding; header and tag parsing don't need real frames.
 * </ul>
 */
public final class AudioFixtures {
    private AudioFixtures() {}

    // Same weak-reference gotcha as TagReader: keep the logger alive so OFF sticks.
    private static final java.util.logging.Logger JAUDIOTAGGER_LOG =
            java.util.logging.Logger.getLogger("org.jaudiotagger");

    static {
        JAUDIOTAGGER_LOG.setLevel(java.util.logging.Level.OFF);
    }

    public static final int SAMPLE_RATE = 44_100;
    /** MPEG-1 Layer III, 128 kbps, 44.1 kHz, no padding: floor(144 * 128000 / 44100). */
    private static final int MP3_FRAME_BYTES = 417;
    private static final int MP3_SAMPLES_PER_FRAME = 1152;

    public static Path wav(Path file, double seconds) throws IOException {
        int frames = (int) (seconds * SAMPLE_RATE);
        byte[] pcm = new byte[frames * 2]; // 16-bit mono silence
        AudioFormat fmt = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        try (AudioInputStream in = new AudioInputStream(new ByteArrayInputStream(pcm), fmt, frames)) {
            AudioSystem.write(in, AudioFileFormat.Type.WAVE, file.toFile());
        }
        return file;
    }

    /** Duration in ms that {@link #mp3} produces for the given frame count. */
    public static long mp3DurationMs(int frames) {
        return Math.round(frames * MP3_SAMPLES_PER_FRAME * 1000.0 / SAMPLE_RATE);
    }

    public static Path mp3(Path file, int frames) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < frames; i++) {
            out.write(0xFF);
            out.write(0xFB); // MPEG-1, Layer III, no CRC
            out.write(0x90); // 128 kbps, 44.1 kHz, no padding
            out.write(0x00); // stereo
            out.write(new byte[MP3_FRAME_BYTES - 4]);
        }
        Files.write(file, out.toByteArray());
        return file;
    }

    public static Path flac(Path file, long totalSamples) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {'f', 'L', 'a', 'C'});
        out.write(0x00); // not the last metadata block, type 0 (STREAMINFO)
        out.write(new byte[] {0, 0, 34});
        out.write(new byte[] {0x10, 0x00, 0x10, 0x00}); // min/max block size 4096
        out.write(new byte[6]); // min/max frame size unknown
        // 20 bits sample rate | 3 bits channels-1 | 5 bits bps-1 | 36 bits total samples
        long packed = ((long) SAMPLE_RATE << 44) | (0L << 41) | (15L << 36) | totalSamples;
        for (int shift = 56; shift >= 0; shift -= 8) out.write((int) (packed >>> shift) & 0xFF);
        out.write(new byte[16]); // MD5 unset
        out.write(0x81); // last metadata block, type 1 (PADDING)
        out.write(new byte[] {0, 0x02, 0x00}); // 512 bytes
        out.write(new byte[512]);
        Files.write(file, out.toByteArray());
        return file;
    }

    /** Open the file, give the callback its tag (creating one if needed), and write it back. */
    public static void tag(Path file, Consumer<Tag> edit) throws Exception {
        AudioFile audio = AudioFileIO.read(file.toFile());
        Tag tag = audio.getTagOrCreateAndSetDefault();
        edit.accept(tag);
        audio.setTag(tag);
        audio.commit();
    }

    /** A valid 2x2 PNG (FLAC picture blocks need real dimensions). */
    public static byte[] png() throws IOException {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    public static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (java.util.stream.Stream<Path> s = Files.walk(root)) {
            for (Path p : (Iterable<Path>) s.sorted(java.util.Comparator.reverseOrder())::iterator) Files.deleteIfExists(p);
        }
    }

    /** Set a user-defined Vorbis comment (FLAC). */
    public static void vorbis(Tag tag, String key, String value) throws Exception {
        VorbisCommentTag v = tag instanceof FlacTag f ? f.getVorbisCommentTag() : (VorbisCommentTag) tag;
        v.setField(key, value);
    }

    /**
     * Add TXXX (user-defined text) frames to an ID3 tag, including one inside a WAV. Pass
     * description/text pairs. Several TXXX frames must be set as one list: setting them one at a
     * time makes jaudiotagger replace the previous one.
     */
    public static void txxx(Tag tag, String... descriptionAndText) {
        AbstractID3v2Tag id3 = tag instanceof WavTag w ? w.getID3Tag() : (AbstractID3v2Tag) tag;
        java.util.List<org.jaudiotagger.tag.id3.AbstractID3v2Frame> frames = new java.util.ArrayList<>();
        for (int i = 0; i < descriptionAndText.length; i += 2) {
            FrameBodyTXXX body = new FrameBodyTXXX();
            body.setDescription(descriptionAndText[i]);
            body.setText(descriptionAndText[i + 1]);
            ID3v24Frame frame = new ID3v24Frame("TXXX");
            frame.setBody(body);
            frames.add(frame);
        }
        if (frames.size() == 1) id3.setFrame(frames.get(0));
        else id3.setFrame("TXXX", frames);
    }
}
