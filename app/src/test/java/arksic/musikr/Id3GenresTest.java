package arksic.musikr;

import static org.junit.jupiter.api.Assertions.*;

import arksic.musikr.interpret.Id3Genres;
import java.util.List;
import org.junit.jupiter.api.Test;

class Id3GenresTest {
    @Test
    void v1NumericGenre() {
        assertEquals(List.of("Blues"), Id3Genres.parseNames(List.of("0")));
        assertEquals(List.of("Rock"), Id3Genres.parseNames(List.of("17")));
    }

    @Test
    void crAndRx() {
        assertEquals(List.of("Cover"), Id3Genres.parseNames(List.of("CR")));
        assertEquals(List.of("Remix"), Id3Genres.parseNames(List.of("RX")));
    }

    @Test
    void v23ParenthesisedIdsWithRefinement() {
        assertEquals(List.of("Rock", "Remix"), Id3Genres.parseNames(List.of("(17)(RX)")));
        assertEquals(List.of("Rock", "Shoegaze"), Id3Genres.parseNames(List.of("(17)Shoegaze")));
    }

    @Test
    void escapedParenthesis() {
        assertEquals(List.of("(Weird"), Id3Genres.parseNames(List.of("((Weird")));
    }

    @Test
    void plainNameIsNotHandledSoCallerCanSplit() {
        assertNull(Id3Genres.parseNames(List.of("Shoegaze")));
    }

    @Test
    void multipleValuesOnlyMapNumbers() {
        assertEquals(List.of("Rock", "Indie"), Id3Genres.parseNames(List.of("17", "Indie")));
    }

    @Test
    void outOfRangeNumberFallsThrough() {
        assertNull(Id3Genres.parseNames(List.of("99999")));
    }
}
