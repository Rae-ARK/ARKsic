package arksic.playback;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BetterShuffleOrderTest {
    private static void assertPermutation(int[] a, int n) {
        int[] sorted = a.clone();
        Arrays.sort(sorted);
        for (int i = 0; i < n; i++) assertEquals(i, sorted[i]);
        assertEquals(n, a.length);
    }

    @Test
    void isAPermutationAnchoredOnStart() {
        for (int trial = 0; trial < 50; trial++) {
            BetterShuffleOrder o = new BetterShuffleOrder(20, 7);
            assertPermutation(o.toArray(), 20);
            assertEquals(7, o.firstIndex());
        }
    }

    @Test
    void emptyAndUnanchored() {
        BetterShuffleOrder empty = new BetterShuffleOrder(0, -1);
        assertEquals(BetterShuffleOrder.INDEX_UNSET, empty.firstIndex());
        assertEquals(BetterShuffleOrder.INDEX_UNSET, empty.lastIndex());
        assertPermutation(new BetterShuffleOrder(5, -1).toArray(), 5);
    }

    @Test
    void nextAndPreviousWalkTheOrder() {
        BetterShuffleOrder o = new BetterShuffleOrder(new int[] {2, 0, 3, 1});
        assertEquals(2, o.firstIndex());
        assertEquals(1, o.lastIndex());
        assertEquals(0, o.nextIndex(2));
        assertEquals(3, o.nextIndex(0));
        assertEquals(BetterShuffleOrder.INDEX_UNSET, o.nextIndex(1));
        assertEquals(BetterShuffleOrder.INDEX_UNSET, o.previousIndex(2));
        assertEquals(3, o.previousIndex(1));
    }

    @Test
    void removeRenumbersRemainingIndices() {
        BetterShuffleOrder o = new BetterShuffleOrder(new int[] {2, 0, 3, 1});
        BetterShuffleOrder r = o.cloneAndRemove(0, 1); // heap item 0 removed
        assertArrayEquals(new int[] {1, 2, 0}, r.toArray());
        BetterShuffleOrder r2 = o.cloneAndRemove(1, 3);
        assertArrayEquals(new int[] {0, 1}, r2.toArray());
    }

    @Test
    void insertKeepsPermutation() {
        BetterShuffleOrder o = new BetterShuffleOrder(new int[] {2, 0, 3, 1});
        assertPermutation(o.cloneAndInsert(1, 2).toArray(), 6);
        assertPermutation(o.cloneAndInsert(4, 3).toArray(), 7);
        assertPermutation(new BetterShuffleOrder(0, -1).cloneAndInsert(0, 3).toArray(), 3);
    }

    @Test
    void movePositionReordersOnlyPlayOrder() {
        BetterShuffleOrder o = new BetterShuffleOrder(new int[] {2, 0, 3, 1});
        assertArrayEquals(new int[] {0, 3, 2, 1}, o.cloneAndMovePosition(0, 2).toArray());
        assertArrayEquals(new int[] {1, 2, 0, 3}, o.cloneAndMovePosition(3, 0).toArray());
    }
}
