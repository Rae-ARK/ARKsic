/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.playback;

import java.util.Random;

/**
 * Port of BetterShuffleOrder.kt, which itself replaced ExoPlayer's ShuffleOrder. A permutation of
 * heap indices that supports insertion/removal while keeping a "play next" feel.
 */
public final class BetterShuffleOrder {
    public static final int INDEX_UNSET = -1;
    private static final Random RANDOM = new Random();

    private final int[] shuffled;
    private final int[] indexInShuffled;

    public BetterShuffleOrder(int[] shuffled) {
        this.shuffled = shuffled;
        this.indexInShuffled = new int[shuffled.length];
        for (int i = 0; i < shuffled.length; i++) indexInShuffled[shuffled[i]] = i;
    }

    public BetterShuffleOrder(int length, int startIndex) {
        this(createShuffledList(length, startIndex));
    }

    public int length() {
        return shuffled.length;
    }

    public int[] toArray() {
        return shuffled.clone();
    }

    public int nextIndex(int index) {
        int s = indexInShuffled[index] + 1;
        return s < shuffled.length ? shuffled[s] : INDEX_UNSET;
    }

    public int previousIndex(int index) {
        int s = indexInShuffled[index] - 1;
        return s >= 0 ? shuffled[s] : INDEX_UNSET;
    }

    public int lastIndex() {
        return shuffled.length > 0 ? shuffled[shuffled.length - 1] : INDEX_UNSET;
    }

    public int firstIndex() {
        return shuffled.length > 0 ? shuffled[0] : INDEX_UNSET;
    }

    /** Insert {@code insertionCount} new heap items at {@code insertionIndex}. */
    public BetterShuffleOrder cloneAndInsert(int insertionIndex, int insertionCount) {
        if (shuffled.length == 0) return new BetterShuffleOrder(insertionCount, -1);

        int[] out = new int[shuffled.length + insertionCount];
        int pivot = insertionIndex < shuffled.length ? indexInShuffled[insertionIndex] : indexInShuffled.length;
        for (int i = 0; i < shuffled.length; i++) {
            int current = shuffled[i];
            if (current > insertionIndex) current += insertionCount;
            if (i <= pivot) out[i] = current;
            else out[i + insertionCount] = current;
        }
        if (insertionIndex < shuffled.length) {
            for (int i = 0; i < insertionCount; i++) out[pivot + i + 1] = insertionIndex + i + 1;
        } else {
            for (int i = 0; i < insertionCount; i++) out[pivot + i] = insertionIndex + i;
        }
        return new BetterShuffleOrder(out);
    }

    public BetterShuffleOrder cloneAndRemove(int indexFrom, int indexToExclusive) {
        int n = indexToExclusive - indexFrom;
        int[] out = new int[shuffled.length - n];
        int found = 0;
        for (int i = 0; i < shuffled.length; i++) {
            if (shuffled[i] >= indexFrom && shuffled[i] < indexToExclusive) {
                found++;
            } else {
                out[i - found] = shuffled[i] >= indexFrom ? shuffled[i] - n : shuffled[i];
            }
        }
        return new BetterShuffleOrder(out);
    }

    public BetterShuffleOrder cloneAndClear() {
        return new BetterShuffleOrder(0, -1);
    }

    /** Move one item within the play order (not the heap). */
    public BetterShuffleOrder cloneAndMovePosition(int fromPos, int toPos) {
        int[] out = shuffled.clone();
        int v = out[fromPos];
        if (fromPos < toPos) System.arraycopy(out, fromPos + 1, out, fromPos, toPos - fromPos);
        else System.arraycopy(out, toPos, out, toPos + 1, fromPos - toPos);
        out[toPos] = v;
        return new BetterShuffleOrder(out);
    }

    private static int[] createShuffledList(int length, int startIndex) {
        int[] shuffled = new int[length];
        for (int i = 0; i < length; i++) {
            int swap = RANDOM.nextInt(i + 1);
            shuffled[i] = shuffled[swap];
            shuffled[swap] = i;
        }
        if (startIndex != -1) {
            int at = -1;
            for (int i = 0; i < length; i++) if (shuffled[i] == startIndex) at = i;
            int tmp = shuffled[0];
            shuffled[0] = shuffled[at];
            shuffled[at] = tmp;
        }
        return shuffled;
    }
}
