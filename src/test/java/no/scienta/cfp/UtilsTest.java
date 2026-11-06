package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import org.junit.jupiter.api.Test;

class UtilsTest {

    @Test
    void isEmpty() {
        assertTrue(Utils.isEmpty(null));
        assertTrue(Utils.isEmpty(""));
        assertTrue(Utils.isEmpty("   "));
        assertFalse(Utils.isEmpty("a"));
    }

    @Test
    void isNumeric() {
        assertTrue(Utils.isNumeric("123"));
        assertFalse(Utils.isNumeric("12a"));
        assertFalse(Utils.isNumeric(""));
    }

    @Test
    void capitalize() {
        assertEquals("Workshop", Utils.capitalize("WORKSHOP"));
    }

    // Only /admin/stats uses this: its average is cut off (4.25 -> 4.2, CFP-61). Talk averages are NOT rounded this
    // way: a talk's average is rounded half to even to two decimals since ratings-cr (4.25 stays 4.25), see
    // FormatsTest.averageRatingHasTwoDecimals and RatingsCrTest.
    // PO?: should the admin stats round like the spec too? Until answered, this pins today's behaviour.
    @Test
    void adminStatsRatingIsCutOffToOneDecimal() {
        assertEquals(4.2, Utils.roundRating(4.25));
        assertEquals(3.6, Utils.roundRating(3.6666));
        assertEquals(4.5, Utils.roundRating(4.5));
    }

    // Spring Boot 4 pin P3 (workflow/specs/spring-boot-4.md): the Ratings spec wants exactly one ordinary space
    // before AM/PM. JDK 20+ locale data puts a narrow no-break space (U+202F) there, so compare byte for byte.
    @Test
    void usShortIsByteExact() {
        assertEquals("11/4/26, 1:05 PM", Utils.usShort(utc("2026-11-04 13:05:59")));
        assertEquals("11/4/26, 12:00 AM", Utils.usShort(utc("2026-11-04 00:00:00")));
        assertEquals("11/4/26, 12:00 PM", Utils.usShort(utc("2026-11-04 12:00:00")));
        assertEquals("1/2/26, 9:07 AM", Utils.usShort(utc("2026-01-02 09:07:00")));
        assertEquals("12/31/26, 11:59 PM", Utils.usShort(utc("2026-12-31 23:59:00")));
    }

    private static Date utc(String dateTime) {
        return Utils.parse(dateTime);
    }

    // Utils.now() has second precision, so comparing it to a second clock reading fails when the second ticks over
    // in between. Check that it parses back (UTC, the DB format) to the clock time, truncated to the second.
    @Test
    void nowIsCurrentTimeInUtc() {
        long before = System.currentTimeMillis();
        String now = Utils.now();
        long after = System.currentTimeMillis();
        assertTrue(now.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"), now);
        long parsed = Utils.parse(now).getTime();
        assertTrue(parsed >= before - 1000 && parsed <= after, now + " is not between " + before + " and " + after);
    }
}
