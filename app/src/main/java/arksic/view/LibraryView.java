package arksic.view;

import arksic.controller.LibraryController;
import arksic.controller.PlaylistController;
import arksic.model.Track;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * The library browser: pick a folder, trigger a scan, show what's indexed,
 * and queue selections onto the playlist via {@code PlaylistController}.
 */
public final class LibraryView extends JPanel implements LibraryController.Listener {

    private final LibraryController libraryController;
    private final PlaylistController playlistController;
    private final DefaultListModel<Track> listModel = new DefaultListModel<>();
    private final JList<Track> trackList = new JList<>(listModel);
    private final JLabel statusLabel = new JLabel("No folder chosen");
    private final JButton chooseFolderButton = new JButton("Choose folder…");
    private final JButton queueSelectedButton = new JButton("Add to playlist");

    public LibraryView(LibraryController libraryController, PlaylistController playlistController) {
        this.libraryController = libraryController;
        this.playlistController = playlistController;
        setLayout(new BorderLayout());

        trackList.setCellRenderer((list, track, index, isSelected, hasFocus) ->
                new JLabel(" " + track.artist() + " — " + track.title()));

        chooseFolderButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                libraryController.onFolderChosen(chooser.getSelectedFile().toPath());
            }
        });
        queueSelectedButton.addActionListener(e -> {
            List<Track> selected = trackList.getSelectedValuesList();
            if (!selected.isEmpty()) {
                playlistController.onTracksAdded(selected);
            }
        });

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(chooseFolderButton);
        toolbar.add(queueSelectedButton);
        toolbar.add(statusLabel);

        add(toolbar, BorderLayout.NORTH);
        add(new JScrollPane(trackList), BorderLayout.CENTER);

        libraryController.setViewListener(this);
    }

    @Override
    public void onScanStarted() {
        statusLabel.setText("Scanning…");
    }

    @Override
    public void onRender(List<Track> indexedTracks) {
        listModel.clear();
        indexedTracks.forEach(listModel::addElement);
        statusLabel.setText(indexedTracks.size() + " track(s) found");
    }

    @Override
    public void onScanFailed(String message) {
        statusLabel.setText("Scan failed: " + message);
    }
}
