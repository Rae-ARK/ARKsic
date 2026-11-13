package arksic.audio;

import java.util.Locale;
import java.util.Optional;

/**
 * The closed set of formats ARKsic plays. See
 * docs/foundation/design-notes.md#scope-lock — this enum existing as a
 * closed set (not an open plugin registry) is itself part of that lock.
 */
public enum AudioFormatType {
    MP3,
    FLAC,
    WAV;

    /** Resolves a format from a file name's extension, if it's one we support. */
    public static Optional<AudioFormatType> fromFileName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return Optional.empty();
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "mp3" -> Optional.of(MP3);
            case "flac" -> Optional.of(FLAC);
            case "wav" -> Optional.of(WAV);
            default -> Optional.empty();
        };
    }
}
