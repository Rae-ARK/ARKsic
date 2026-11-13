package arksic.audio;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * The single {@link AudioDecoder} implementation for all three supported
 * formats. It never imports a format-specific library: WAV is read
 * natively by {@code javax.sound.sampled}, and MP3/FLAC are read the same
 * way once their SPI providers (added as {@code runtimeOnly} dependencies
 * in {@code app/build.gradle.kts}) are on the classpath and have
 * registered themselves. If a provider is missing at runtime, opening that
 * format fails with {@link UnsupportedAudioFileException} — the same
 * exception a genuinely unsupported file would raise — rather than a
 * decoder-not-found error, because from this class's point of view there
 * is no difference between the two.
 */
public final class SpiAudioDecoder implements AudioDecoder {

    @Override
    public AudioInputStream open(Path path) throws IOException, UnsupportedAudioFileException {
        AudioInputStream rawStream =
                AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(path)));
        AudioFormat sourceFormat = rawStream.getFormat();
        AudioFormat pcmFormat = toPcmSigned(sourceFormat);

        if (sourceFormat.matches(pcmFormat)) {
            return rawStream;
        }
        // Already-PCM WAV streams pass through matches() above; MP3/FLAC
        // need this conversion step, performed by the same SPI providers.
        return AudioSystem.getAudioInputStream(pcmFormat, rawStream);
    }

    @Override
    public boolean supports(AudioFormatType format) {
        // All three formats are routed through the same SPI mechanism; a
        // provider being absent at runtime is a packaging problem, not a
        // reason to report the format itself as unsupported.
        return true;
    }

    private static AudioFormat toPcmSigned(AudioFormat source) {
        return new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                source.getSampleRate(),
                16,
                source.getChannels(),
                source.getChannels() * 2,
                source.getSampleRate(),
                false);
    }
}
