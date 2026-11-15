/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.playback.state;

import arksic.musikr.model.Song;
import java.util.ArrayList;
import java.util.List;

/**
 * The queue as the engine sees it: an unordered "heap" of songs, an optional shuffled mapping
 * (indices into the heap in play order), and where in the heap we currently are.
 */
public record RawQueue(List<Song> heap, List<Integer> shuffledMapping, int heapIndex) {
    public boolean isShuffled() {
        return !shuffledMapping.isEmpty();
    }

    public List<Song> resolveSongs() {
        if (!isShuffled()) return heap;
        List<Song> out = new ArrayList<>(shuffledMapping.size());
        for (int i : shuffledMapping) out.add(heap.get(i));
        return out;
    }

    public int resolveIndex() {
        return isShuffled() ? shuffledMapping.indexOf(heapIndex) : heapIndex;
    }

    public static RawQueue nil() {
        return new RawQueue(List.of(), List.of(), -1);
    }
}
