package arksic.model;

import java.nio.file.Path;

/**
 * A single playable file plus the metadata read from its tags. Immutable —
 * a rescan or re-tag produces a new {@code Track} rather than mutating one.
 */
public record Track(
        Path path,
        String title,
        String artist,
        String album,
        int durationSeconds) {

    /** A track with no tag metadata available yet, identified by path alone. */
    public static Track untagged(Path path) {
        String fileName = path.getFileName().toString();
        return new Track(path, fileName, "Unknown Artist", "Unknown Album", 0);
    }
}
