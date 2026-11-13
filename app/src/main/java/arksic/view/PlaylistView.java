package arksic.view;

import arksic.controller.PlaylistController;
import arksic.model.RepeatMode;
import arksic.model.Track;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * The playlist pane: the ordered track list, repeat/shuffle toggles, and
 * track activation/removal. Renders whatever {@code PlaylistController}
 * hands it; holds no playlist state of its own beyond the Swing list model.
 */
public final class PlaylistView extends JPanel implements PlaylistController.Listener {

    private final PlaylistController controller;
    private final DefaultListModel<Track> listModel = new DefaultListModel<>();
    private final JList<Track> trackList = new JList<>(listModel);
    private final JButton repeatButton = new JButton(Icons.repeat());
    private final JButton shuffleButton = new JButton(Icons.shuffle());
    private final JButton removeButton = new JButton(Icons.close());

    public PlaylistView(PlaylistController controller) {
        this.controller = controller;
        setLayout(new BorderLayout());

        trackList.setCellRenderer((list, track, index, isSelected, hasFocus) ->
                new javax.swing.JLabel(" " + track.artist() + " — " + track.title()));
        trackList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = trackList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        controller.onTrackActivated(index);
                    }
                }
            }
        });

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        repeatButton.addActionListener(e -> controller.onRepeatModeToggled());
        shuffleButton.addActionListener(e -> controller.onShuffleToggled());
        removeButton.addActionListener(e -> {
            int index = trackList.getSelectedIndex();
            if (index >= 0) {
                controller.onTrackRemoved(index);
            }
        });
        toolbar.add(repeatButton);
        toolbar.add(shuffleButton);
        toolbar.add(removeButton);

        add(toolbar, BorderLayout.NORTH);
        add(new JScrollPane(trackList), BorderLayout.CENTER);

        controller.setViewListener(this);
    }

    @Override
    public void onRender(List<Track> tracks, int currentIndex, RepeatMode repeatMode, boolean shuffleEnabled) {
        listModel.clear();
        tracks.forEach(listModel::addElement);
        if (currentIndex >= 0 && currentIndex < listModel.size()) {
            trackList.setSelectedIndex(currentIndex);
        }
        repeatButton.setToolTipText("Repeat: " + repeatMode);
        shuffleButton.setToolTipText(shuffleEnabled ? "Shuffle: on" : "Shuffle: off");
    }
}
