package arksic.service;

import arksic.model.Track;
import java.nio.file.Path;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;

/**
 * Owns tag reads (title/artist/album/duration) via JAudioTagger — the one
 * service allowed to import a format-specific library directly, per
 * design-notes.md#scope-lock ("a dedicated tag-reading library ... not
 * hand-rolled parsing"). Falls back to {@link Track#untagged(Path)} if a
 * file has no readable tag, rather than failing the whole scan over one
 * bad file.
 */
public final class MetadataService {

    public Track read(Path path) {
        try {
            AudioFile audioFile = AudioFileIO.read(path.toFile());
            int durationSeconds = audioFile.getAudioHeader().getTrackLength();
            Tag tag = audioFile.getTag();
            if (tag == null) {
                return withDuration(Track.untagged(path), durationSeconds);
            }
            String title = firstNonBlank(tag.getFirst(org.jaudiotagger.tag.FieldKey.TITLE), path.getFileName().toString());
            String artist = firstNonBlank(tag.getFirst(org.jaudiotagger.tag.FieldKey.ARTIST), "Unknown Artist");
            String album = firstNonBlank(tag.getFirst(org.jaudiotagger.tag.FieldKey.ALBUM), "Unknown Album");
            return new Track(path, title, artist, album, durationSeconds);
        } catch (Exception e) {
            // Any tagging-library failure (corrupt tag, unreadable header, ...)
            // degrades to an untagged entry rather than breaking the scan.
            return Track.untagged(path);
        }
    }

    private static Track withDuration(Track track, int durationSeconds) {
        return new Track(track.path(), track.title(), track.artist(), track.album(), durationSeconds);
    }

    private static String firstNonBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
