package arksic.controller;

import arksic.model.PlaybackState;
import arksic.model.Track;
import arksic.service.PlaybackListener;
import arksic.service.PlaybackService;
import arksic.service.PlaylistService;
import arksic.util.PlaybackMath;
import java.util.Optional;
import javax.swing.SwingUtilities;

/**
 * UI-facing state and intent-handling for the transport controls (play,
 * pause, next, previous, seek, volume). Holds what the transport view
 * needs to render; forwards intent to {@code PlaybackService} and
 * {@code PlaylistService}. Never touches a Swing component directly — see
 * docs/foundation/architecture.md#layers.
 */
public final class TransportController implements PlaybackListener {

    /** Rendered by the transport view after any state change. */
    public record TransportViewState(
            PlaybackState playbackState,
            String trackLabel,
            int positionSeconds,
            int durationSeconds,
            String positionLabel,
            String durationLabel) {

        static TransportViewState idle() {
            return new TransportViewState(PlaybackState.STOPPED, "No track selected", 0, 0, "0:00", "0:00");
        }
    }

    /** Implemented by the view to receive renders; always called on the EDT. */
    public interface Listener {
        void onRender(TransportViewState state);
    }

    private final PlaybackService playbackService;
    private final PlaylistService playlistService;
    private Listener viewListener;
    private TransportViewState lastState = TransportViewState.idle();

    public TransportController(PlaybackService playbackService, PlaylistService playlistService) {
        this.playbackService = playbackService;
        this.playlistService = playlistService;
        playbackService.addListener(this);
    }

    public void setViewListener(Listener listener) {
        this.viewListener = listener;
        render();
    }

    public void onPlayPauseRequested() {
        playlistService.current().ifPresentOrElse(track -> {
            if (playbackService.state() == PlaybackState.PLAYING) {
                playbackService.pause();
            } else if (playbackService.state() == PlaybackState.PAUSED) {
                playbackService.resume();
            } else {
                playbackService.play(track);
            }
        }, () -> { /* nothing queued; nothing to play */ });
    }

    public void onStopRequested() {
        playbackService.stop();
    }

    public void onNextRequested() {
        Optional<Track> next = playlistService.advance();
        next.ifPresent(playbackService::play);
        if (next.isEmpty()) {
            playbackService.stop();
        }
    }

    public void onVolumeChanged(int percent) {
        playbackService.setVolumePercent(percent);
    }

    @Override
    public void onStateChanged(PlaybackState newState) {
        lastState = new TransportViewState(
                newState,
                currentTrackLabel(),
                lastState.positionSeconds(),
                lastState.durationSeconds(),
                lastState.positionLabel(),
                lastState.durationLabel());
        render();
    }

    @Override
    public void onPositionChanged(int positionSeconds, int durationSeconds) {
        lastState = new TransportViewState(
                lastState.playbackState(),
                lastState.trackLabel(),
                positionSeconds,
                durationSeconds,
                PlaybackMath.formatTrackDuration(positionSeconds),
                PlaybackMath.formatTrackDuration(durationSeconds));
        render();
    }

    @Override
    public void onTrackFinished() {
        onNextRequested();
    }

    @Override
    public void onError(Exception error) {
        lastState = new TransportViewState(
                PlaybackState.STOPPED,
                "Playback error: " + error.getMessage(),
                0, 0, "0:00", "0:00");
        render();
    }

    private String currentTrackLabel() {
        return playlistService.current()
                .map(t -> t.artist() + " — " + t.title())
                .orElse("No track selected");
    }

    private void render() {
        if (viewListener == null) {
            return;
        }
        TransportViewState toRender = lastState;
        SwingUtilities.invokeLater(() -> viewListener.onRender(toRender));
    }
}
