package arksic.controller;

import arksic.model.RepeatMode;
import arksic.model.Track;
import arksic.service.PlaybackService;
import arksic.service.PlaylistService;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * UI-facing state and intent for the playlist pane: track list, current
 * selection, repeat/shuffle toggles. Mirrors {@code PlaylistService}'s
 * state for rendering; never treats its own copy as the source of truth.
 */
public final class PlaylistController {

    public interface Listener {
        void onRender(List<Track> tracks, int currentIndex, RepeatMode repeatMode, boolean shuffleEnabled);
    }

    private final PlaylistService playlistService;
    private final PlaybackService playbackService;
    private Listener viewListener;

    public PlaylistController(PlaylistService playlistService, PlaybackService playbackService) {
        this.playlistService = playlistService;
        this.playbackService = playbackService;
    }

    public void setViewListener(Listener listener) {
        this.viewListener = listener;
        render();
    }

    public void onTracksAdded(List<Track> tracks) {
        playlistService.addAll(tracks);
        render();
    }

    public void onTrackRemoved(int index) {
        playlistService.remove(index);
        render();
    }

    public void onTrackReordered(int fromIndex, int toIndex) {
        playlistService.reorder(fromIndex, toIndex);
        render();
    }

    public void onTrackActivated(int index) {
        playlistService.setCurrentIndex(index);
        playlistService.current().ifPresent(playbackService::play);
        render();
    }

    public void onRepeatModeToggled() {
        RepeatMode next = switch (playlistService.repeatMode()) {
            case OFF -> RepeatMode.REPEAT_ALL;
            case REPEAT_ALL -> RepeatMode.REPEAT_ONE;
            case REPEAT_ONE -> RepeatMode.OFF;
        };
        playlistService.setRepeatMode(next);
        render();
    }

    public void onShuffleToggled() {
        playlistService.setShuffleEnabled(!playlistService.isShuffleEnabled());
        render();
    }

    private void render() {
        if (viewListener == null) {
            return;
        }
        List<Track> tracks = playlistService.tracks();
        int currentIndex = playlistService.currentIndex();
        RepeatMode repeatMode = playlistService.repeatMode();
        boolean shuffleEnabled = playlistService.isShuffleEnabled();
        SwingUtilities.invokeLater(() -> viewListener.onRender(tracks, currentIndex, repeatMode, shuffleEnabled));
    }
}
