package arksic.view;

import arksic.controller.LibraryController;
import arksic.controller.PlaylistController;
import arksic.controller.TransportController;
import arksic.model.Preferences;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

/**
 * The single application window. Composes the three UI surfaces
 * (transport, playlist, library) — holds no logic of its own beyond
 * layout, per docs/foundation/architecture.md#layers.
 */
public final class MainFrame extends JFrame {

    public MainFrame(
            TransportController transportController,
            PlaylistController playlistController,
            LibraryController libraryController,
            Preferences preferences) {
        super("ARKsic");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        TransportView transportView = new TransportView(transportController);
        PlaylistView playlistView = new PlaylistView(playlistController);
        LibraryView libraryView = new LibraryView(libraryController, playlistController);

        javax.swing.JPanel center = new javax.swing.JPanel(new GridLayout(1, 2));
        center.add(libraryView);
        center.add(playlistView);

        add(transportView, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);

        setSize(preferences.windowWidth() * 2, preferences.windowHeight() * 2);
        setLocationRelativeTo(null);
    }
}
