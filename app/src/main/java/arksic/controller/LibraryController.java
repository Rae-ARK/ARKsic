package arksic.controller;

import arksic.model.Track;
import arksic.service.LibraryService;
import java.nio.file.Path;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * UI-facing state and intent for the library browser: triggers folder
 * scans and renders whatever {@code LibraryService} has indexed so far.
 */
public final class LibraryController {

    public interface Listener {
        void onScanStarted();

        void onRender(List<Track> indexedTracks);

        void onScanFailed(String message);
    }

    private final LibraryService libraryService;
    private Listener viewListener;

    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    public void setViewListener(Listener listener) {
        this.viewListener = listener;
    }

    public void onFolderChosen(Path folder) {
        if (viewListener != null) {
            SwingUtilities.invokeLater(viewListener::onScanStarted);
        }
        libraryService.scan(folder, new LibraryService.LibraryScanListener() {
            @Override
            public void onScanComplete(List<Track> tracks) {
                if (viewListener != null) {
                    SwingUtilities.invokeLater(() -> viewListener.onRender(tracks));
                }
            }

            @Override
            public void onScanFailed(Exception error) {
                if (viewListener != null) {
                    SwingUtilities.invokeLater(() -> viewListener.onScanFailed(String.valueOf(error.getMessage())));
                }
            }
        });
    }

    public List<Track> currentIndex() {
        return libraryService.index();
    }
}
