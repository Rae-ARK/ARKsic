/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.musikr;

import arksic.musikr.model.Library;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds the current {@link Library} and tells listeners when it changes. Stand-in for Auxio's
 * MusicRepository (which is entangled with Android permissions/MediaStore).
 */
public final class MusicRepository {
    public interface Listener {
        void onLibraryChanged(Library library);
    }

    public sealed interface IndexingState {
        record Idle() implements IndexingState {}

        record Indexing(int songsLoaded) implements IndexingState {}
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile Library library;

    public Library library() {
        return library;
    }

    public void library(Library library) {
        this.library = library;
        for (Listener l : listeners) l.onLibraryChanged(library);
    }

    public void addListener(Listener l) {
        listeners.add(l);
        if (library != null) l.onLibraryChanged(library);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }
}
