package arksic;

import arksic.audio.SpiAudioDecoder;
import arksic.controller.LibraryController;
import arksic.controller.PlaylistController;
import arksic.controller.TransportController;
import arksic.model.Preferences;
import arksic.service.LibraryService;
import arksic.service.MetadataService;
import arksic.service.PlaybackService;
import arksic.service.PlaylistService;
import arksic.service.PreferencesService;
import arksic.view.MainFrame;
import java.nio.file.Path;
import javax.swing.SwingUtilities;

/**
 * Entry point. Wires services, then controllers, then the view, per
 * docs/foundation/architecture.md#lifecycle: nothing reaches out and finds
 * its own dependencies.
 */
public final class App {

    public static void main(String[] args) {
        // 1. PreferencesService loads settings (or defaults) before anything else.
        PreferencesService preferencesService = new PreferencesService();
        Preferences preferences = preferencesService.current();

        // 2. Other services are constructed with dependencies injected.
        MetadataService metadataService = new MetadataService();
        LibraryService libraryService = new LibraryService(metadataService);
        PlaylistService playlistService = new PlaylistService();
        PlaybackService playbackService = new PlaybackService(new SpiAudioDecoder());

        TransportController transportController = new TransportController(playbackService, playlistService);
        PlaylistController playlistController = new PlaylistController(playlistService, playbackService);
        LibraryController libraryController = new LibraryController(libraryService);

        // 3. The main window is built and shown on the EDT.
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(transportController, playlistController, libraryController, preferences);
            frame.setVisible(true);

            // 4. If a library folder is already known, kick off a scan off the EDT.
            if (preferences.lastLibraryFolder() != null) {
                libraryController.onFolderChosen(Path.of(preferences.lastLibraryFolder()));
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            playbackService.stop();
            preferencesService.save(preferencesService.current());
            libraryService.shutdown();
        }, "arksic-shutdown"));
    }

    private App() {
    }
}
