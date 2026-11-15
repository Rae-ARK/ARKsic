package arksic.musikr;

import static org.junit.jupiter.api.Assertions.*;

import arksic.musikr.tag.Date;
import org.junit.jupiter.api.Test;

class DateTest {
    @Test
    void parsesVariablePrecision() {
        assertEquals("2020", Date.from("2020").toString());
        assertEquals("2020-05", Date.from("2020-05").toString());
        assertEquals("2020-05-17", Date.from("2020-05-17").toString());
        assertEquals("2020-05-17T13:45Z", Date.from("2020-05-17T13:45").toString());
        assertEquals("2020-05-17T13:45:09Z", Date.from("2020-05-17 13:45:09Z").toString());
    }

    @Test
    void invalidComponentsTruncatePrecision() {
        assertEquals("2020", Date.from("2020-13-01").toString());
        assertEquals("2020-02", Date.from("2020-02-45").toString());
    }

    @Test
    void garbageIsNull() {
        assertNull(Date.from("not a date"));
        assertNull(Date.from(""));
        assertNull(Date.from(0));
        assertNull(Date.from(-5));
    }

    @Test
    void packedYyyymmddIntegerIsUnpacked() {
        assertEquals("2019-03-07", Date.from(20190307).toString());
    }

    @Test
    void lessPreciseSortsBeforeMorePrecise() {
        assertTrue(Date.from("2020").compareTo(Date.from("2020-01")) < 0);
        assertTrue(Date.from("2019-12-31").compareTo(Date.from("2020")) < 0);
        assertEquals(Date.from("2020-01"), Date.from("2020-01"));
    }

    @Test
    void rangeRequiresOrdering() {
        assertThrows(IllegalStateException.class, () -> new Date.Range(Date.from("2021"), Date.from("2020")));
        assertDoesNotThrow(() -> new Date.Range(Date.from("2020"), Date.from("2020")));
    }
}
