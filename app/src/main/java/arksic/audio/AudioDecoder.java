package arksic.audio;

import java.io.IOException;
import java.nio.file.Path;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Opens a playable file as a decoded PCM {@link AudioInputStream}, whatever
 * its format. One abstraction covers MP3/FLAC/WAV only because the format
 * set is closed (docs/foundation/design-notes.md#scope-lock) — a fourth
 * format would need this interface re-examined, not just a new branch.
 */
public interface AudioDecoder {

    /**
     * Opens {@code path} and returns a stream of decoded PCM frames ready
     * for a {@code SourceDataLine}. The caller owns the returned stream and
     * must close it.
     */
    AudioInputStream open(Path path) throws IOException, UnsupportedAudioFileException;

    /** Whether this decoder can open the given format. */
    boolean supports(AudioFormatType format);
}
