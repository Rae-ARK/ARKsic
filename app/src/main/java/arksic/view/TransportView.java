package arksic.view;

import arksic.controller.TransportController;
import arksic.model.PlaybackState;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

/**
 * Transport controls: play/pause, stop, next, volume, and the current
 * track/position labels. Lays out what it's given and forwards intent to
 * {@code TransportController} — no logic of its own beyond that.
 */
public final class TransportView extends JPanel implements TransportController.Listener {

    private final TransportController controller;
    private final JButton playPauseButton = new JButton(Icons.play());
    private final JButton stopButton = new JButton(Icons.stop());
    private final JButton nextButton = new JButton(Icons.next());
    private final JLabel trackLabel = new JLabel("No track selected");
    private final JLabel positionLabel = new JLabel("0:00 / 0:00");
    private final JSlider volumeSlider = new JSlider(0, 100, 100);

    public TransportView(TransportController controller) {
        this.controller = controller;
        setLayout(new FlowLayout(FlowLayout.LEFT));

        playPauseButton.addActionListener(e -> controller.onPlayPauseRequested());
        stopButton.addActionListener(e -> controller.onStopRequested());
        nextButton.addActionListener(e -> controller.onNextRequested());
        volumeSlider.addChangeListener(e -> controller.onVolumeChanged(volumeSlider.getValue()));

        add(trackLabel);
        add(playPauseButton);
        add(stopButton);
        add(nextButton);
        add(new JLabel(Icons.volume()));
        add(volumeSlider);
        add(positionLabel);

        controller.setViewListener(this);
    }

    @Override
    public void onRender(TransportController.TransportViewState state) {
        trackLabel.setText(state.trackLabel());
        positionLabel.setText(state.positionLabel() + " / " + state.durationLabel());
        playPauseButton.setIcon(state.playbackState() == PlaybackState.PLAYING ? Icons.pause() : Icons.play());
    }
}
