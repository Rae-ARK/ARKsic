package arksic.service;

import arksic.model.PlaybackState;

/**
 * Receives playback updates from {@code PlaybackService}. Implementations
 * must assume they are called from the decode thread and are responsible
 * for marshaling onto the EDT themselves before touching Swing — see
 * docs/foundation/architecture.md#concurrency-model.
 */
public interface PlaybackListener {

    void onStateChanged(PlaybackState newState);

    void onPositionChanged(int positionSeconds, int durationSeconds);

    void onTrackFinished();

    void onError(Exception error);
}
