package arksic.service;

import arksic.audio.AudioFormatType;
import arksic.model.Track;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Owns the on-disk scan of the music folder and the in-memory index built
 * from it. The scan runs off the caller's thread; results are reported
 * back via {@link LibraryScanListener}, whose implementation is
 * responsible for marshaling onto the EDT — see
 * docs/foundation/architecture.md#concurrency-model.
 */
public final class LibraryService {

    private final MetadataService metadataService;
    private final ExecutorService scanExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "arksic-library-scan");
                t.setDaemon(true);
                return t;
            });

    private volatile List<Track> index = List.of();

    public LibraryService(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    public List<Track> index() {
        return Collections.unmodifiableList(index);
    }

    /** Starts (or restarts) a scan of {@code folder}; abandons any scan already in flight. */
    public void scan(Path folder, LibraryScanListener listener) {
        scanExecutor.submit(() -> {
            try {
                List<Track> found = scanFolder(folder);
                index = found;
                listener.onScanComplete(found);
            } catch (IOException e) {
                listener.onScanFailed(e);
            }
        });
    }

    /** Abandons any in-flight scan cleanly; does not block waiting for it to finish. */
    public void shutdown() {
        scanExecutor.shutdownNow();
    }

    private List<Track> scanFolder(Path folder) throws IOException {
        List<Track> found = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(folder)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> AudioFormatType.fromFileName(p.getFileName().toString()).isPresent())
                    .forEach(p -> found.add(metadataService.read(p)));
        }
        return found;
    }

    /** Callback for an in-flight library scan. */
    public interface LibraryScanListener {
        void onScanComplete(List<Track> tracks);

        void onScanFailed(Exception error);
    }
}
